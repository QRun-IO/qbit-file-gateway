/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.filegateway.upload;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;

/*******************************************************************************
 ** Sequential storage stream with a bounded SFTP reorder window and backpressure.
 *******************************************************************************/
public final class UploadChannel implements SeekableByteChannel
{
   /*******************************************************************************
    ** Persist reservations and terminal state before acknowledging SFTP operations.
    *******************************************************************************/
   public interface Lifecycle
   {
      /*******************************************************************************
       ** Atomically reserve the current high-water size against account limits.
       *******************************************************************************/
      void reserve(long size) throws IOException;

      /*******************************************************************************
       ** Commit a completed object.
       *******************************************************************************/
      void complete(GatewayObjectStore.Receipt receipt) throws IOException;

      /*******************************************************************************
       ** Persist an aborted transfer and its reason.
       *******************************************************************************/
      void abort(String message, boolean limit) throws IOException;
   }

   private final GatewayObjectStore store;
   private final String key;
   private final Lifecycle lifecycle;
   private final long window;
   private final PipedInputStream input;
   private final PipedOutputStream output;
   private final CompletableFuture<GatewayObjectStore.Receipt> result = new CompletableFuture<>();
   private final TreeMap<Long, byte[]> pending = new TreeMap<>();
   private volatile boolean aborted;
   private boolean open = true;
   private long position;
   private long contiguous;
   private long size;

   /*******************************************************************************
    ** A virtual thread consumes the pipe; only bounded buffers hold upload content.
    *******************************************************************************/
   public UploadChannel(GatewayObjectStore store, String key, int bufferSize, long window, Lifecycle lifecycle) throws IOException
   {
      this.store = store;
      this.key = key;
      this.lifecycle = lifecycle;
      this.window = window;
      input = new PipedInputStream(bufferSize);
      output = new PipedOutputStream(input);
      Thread.startVirtualThread(() ->
      {
         try(var source = new FilterInputStream(input)
         {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException
            {
               if(aborted)
               {
                  throw (new IOException("Upload aborted"));
               }
               int count = super.read(bytes, offset, length);
               if(aborted)
               {
                  throw (new IOException("Upload aborted"));
               }
               return (count);
            }
         })
         {
            var receipt = store.put(key, source);
            result.complete(receipt);
         }
         catch(Exception e)
         {
            result.completeExceptionally(e);
         }
         finally
         {
            if(aborted)
            {
               try
               {
                  store.delete(key);
               }
               catch(IOException ignored)
               {
                  // The caller also deletes after waiting for completion.
               }
            }
         }
      });
   }

   /*******************************************************************************
    ** Fail closed and release the streaming producer before deleting the object.
    *******************************************************************************/
   private void fail(String message, boolean limit) throws IOException
   {
      aborted = true;
      open = false;
      pending.clear();
      output.close();
      input.close();
      try
      {
         result.join();
      }
      catch(RuntimeException ignored)
      {
         // A failing input is how the storage client aborts multipart uploads.
      }
      try
      {
         store.delete(key);
      }
      finally
      {
         lifecycle.abort(message, limit);
      }
   }

   /*******************************************************************************
    ** Writes behind the committed prefix and overlapping blocks are unsupported.
    *******************************************************************************/
   @Override
   public synchronized int write(ByteBuffer source) throws IOException
   {
      checkOpen();
      int count = source.remaining();
      if(count == 0)
      {
         return (0);
      }
      try
      {
         long end = Math.addExact(position, count);
         var before = pending.floorEntry(position);
         var after = pending.ceilingEntry(position);
         if(position < contiguous || (before != null && before.getKey() + before.getValue().length > position)
            || (after != null && after.getKey() < end) || end - contiguous > window)
         {
            throw (new IOException("Upload exceeds reorder window or overlaps an earlier write"));
         }
         lifecycle.reserve(Math.max(size, end));
         byte[] bytes = new byte[count];
         source.get(bytes);
         pending.put(position, bytes);
         size = Math.max(size, end);
         position = end;
         while(pending.containsKey(contiguous))
         {
            byte[] next = pending.remove(contiguous);
            output.write(next);
            contiguous += next.length;
         }
         return (count);
      }
      catch(Exception e)
      {
         boolean limit = e instanceof UploadLimitException;
         fail(limit ? e.getMessage() : "Upload stream failed", limit);
         throw (new IOException(limit ? e.getMessage() : "Upload stream failed", e));
      }
   }

   /*******************************************************************************
    ** Complete only a contiguous upload; close of a temporary file does not queue it.
    *******************************************************************************/
   @Override
   public synchronized void close() throws IOException
   {
      if(!open)
      {
         return;
      }
      try
      {
         if(!pending.isEmpty())
         {
            throw (new IOException("Upload has missing blocks"));
         }
         output.close();
         lifecycle.complete(result.join());
         open = false;
      }
      catch(Exception e)
      {
         fail("Upload did not complete", false);
         throw (new IOException("Upload did not complete", e));
      }
   }

   /*******************************************************************************
    ** Verify this handle is usable.
    *******************************************************************************/
   private void checkOpen() throws ClosedChannelException
   {
      if(!open)
      {
         throw (new ClosedChannelException());
      }
   }

   /*******************************************************************************
    ** Upload handles cannot read.
    *******************************************************************************/
   @Override
   public int read(ByteBuffer destination)
   {
      throw (new NonReadableChannelException());
   }

   /*******************************************************************************
    ** Current SFTP write offset.
    *******************************************************************************/
   @Override
   public synchronized long position() throws IOException
   {
      checkOpen();
      return (position);
   }

   /*******************************************************************************
    ** SFTP clients set the offset before each write.
    *******************************************************************************/
   @Override
   public synchronized SeekableByteChannel position(long value) throws IOException
   {
      checkOpen();
      if(value < 0)
      {
         throw (new IllegalArgumentException("Negative upload offset"));
      }
      position = value;
      return (this);
   }

   /*******************************************************************************
    ** Highest received offset.
    *******************************************************************************/
   @Override
   public synchronized long size()
   {
      return (size);
   }

   /*******************************************************************************
    ** Arbitrary truncation would invalidate a streamed object.
    *******************************************************************************/
   @Override
   public SeekableByteChannel truncate(long value) throws IOException
   {
      if(value != size)
      {
         throw (new IOException("Upload truncation is unsupported"));
      }
      return (this);
   }

   /*******************************************************************************
    ** Handle lifecycle.
    *******************************************************************************/
   @Override
   public boolean isOpen()
   {
      return (open);
   }
}
