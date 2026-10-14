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

package com.kingsrook.qbits.filegateway.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/*******************************************************************************
 ** Seekable range reads with bounded per-request memory and prompt response cleanup.
 *******************************************************************************/
public final class ObjectReadChannel implements SeekableByteChannel
{
   /*******************************************************************************
    ** Open an inclusive range of an immutable object or generated sidecar.
    *******************************************************************************/
   @FunctionalInterface
   public interface Ranges
   {
      /*******************************************************************************
       ** Caller closes the response stream.
       *******************************************************************************/
      InputStream open(long start, long end) throws IOException;
   }

   /*******************************************************************************
    ** Persist one transfer outcome when the handle closes.
    *******************************************************************************/
   @FunctionalInterface
   public interface Completion
   {
      /*******************************************************************************
       ** Report actual bytes sent and any read failure.
       *******************************************************************************/
      void done(long bytes, boolean failed) throws IOException;
   }

   private final long size;
   private final Ranges ranges;
   private final Completion completion;
   private long bytesRead;
   private Completion fullDownload;
   private long contiguousEnd;
   private final java.util.TreeMap<Long, Long> readRanges = new java.util.TreeMap<>();
   private boolean failed;
   private long position;
   private boolean open = true;

   /*******************************************************************************
    ** Bind a stored size and ranged stream supplier.
    *******************************************************************************/
   public ObjectReadChannel(long size, Ranges ranges)
   {
      this(size, ranges, (bytes, failure) ->
      {
         // Direct channel users may omit a transfer audit callback.
      });
   }

   /*******************************************************************************
    ** Bind an audited object read.
    *******************************************************************************/
   public ObjectReadChannel(long size, Ranges ranges, Completion completion)
   {
      this.size = size;
      this.ranges = ranges;
      this.completion = completion;
   }

   /*******************************************************************************
    ** At most 64 KiB is buffered, regardless of object size or seek distance.
    *******************************************************************************/
   @Override
   public int read(ByteBuffer destination) throws IOException
   {
      checkOpen();
      if(!destination.hasRemaining())
      {
         return (0);
      }
      if(position >= size)
      {
         return (-1);
      }
      int length = (int) Math.min(Math.min(destination.remaining(), 65536), size - position);
      try(InputStream input = ranges.open(position, position + length - 1))
      {
         byte[] bytes = input.readNBytes(length);
         if(bytes.length == 0)
         {
            throw (new IOException("Stored object ended before its recorded size"));
         }
         destination.put(bytes);
         recordRange(position, position + bytes.length);
         position += bytes.length;
         bytesRead += bytes.length;
         return (bytes.length);
      }
      catch(IOException e)
      {
         failed = true;
         throw (e);
      }
   }

   /*******************************************************************************
    ** Stored objects are immutable.
    *******************************************************************************/
   @Override
   public int write(ByteBuffer source)
   {
      throw (new NonWritableChannelException());
   }

   /*******************************************************************************
    ** Current read offset.
    *******************************************************************************/
   @Override
   public long position() throws IOException
   {
      checkOpen();
      return (position);
   }

   /*******************************************************************************
    ** Seek without reading preceding bytes.
    *******************************************************************************/
   @Override
   public SeekableByteChannel position(long value) throws IOException
   {
      checkOpen();
      if(value < 0)
      {
         throw (new IllegalArgumentException("Negative read offset"));
      }
      position = value;
      return (this);
   }

   /*******************************************************************************
    ** Persisted object length.
    *******************************************************************************/
   @Override
   public long size()
   {
      return (size);
   }

   /*******************************************************************************
    ** Stored objects cannot be truncated.
    *******************************************************************************/
   @Override
   public SeekableByteChannel truncate(long value)
   {
      throw (new NonWritableChannelException());
   }

   /*******************************************************************************
    ** Handle state.
    *******************************************************************************/
   @Override
   public boolean isOpen()
   {
      return (open);
   }

   /*******************************************************************************
    ** No stream is retained between reads.
    *******************************************************************************/
   @Override
   public void close() throws IOException
   {
      if(open)
      {
         open = false;
         completion.done(bytesRead, failed);
         if(!failed && contiguousEnd >= size && fullDownload != null)
         {
            fullDownload.done(bytesRead, false);
         }
      }
   }

   /*******************************************************************************
    ** Optional acknowledgment, only after all bytes have been read on this handle.
    *******************************************************************************/
   public ObjectReadChannel withFullDownload(Completion callback)
   {
      fullDownload = callback;
      return (this);
   }



   /*******************************************************************************
    ** Bounded interval tracking avoids counting repeated ranged reads as a complete file.
    *******************************************************************************/
   private void recordRange(long start, long end)
   {
      if(start <= contiguousEnd)
      {
         contiguousEnd = Math.max(contiguousEnd, end);
      }
      else if(readRanges.size() < 4096)
      {
         readRanges.merge(start, end, Math::max);
      }
      while(!readRanges.isEmpty() && readRanges.firstKey() <= contiguousEnd)
      {
         contiguousEnd = Math.max(contiguousEnd, readRanges.pollFirstEntry().getValue());
      }
   }



   /*******************************************************************************
    ** Reject use after close.
    *******************************************************************************/
   private void checkOpen() throws ClosedChannelException
   {
      if(!open)
      {
         throw (new ClosedChannelException());
      }
   }
}
