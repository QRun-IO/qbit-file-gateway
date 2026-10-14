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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.objectstorage.api.CopyRequest;
import com.kingsrook.qbits.objectstorage.api.DeleteRequest;
import com.kingsrook.qbits.objectstorage.api.ObjectStorage;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.Range;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;

/*******************************************************************************
 ** Only the allow-listed object-storage API is used, under a named system caller.
 *******************************************************************************/
public final class ObjectStorageGatewayStore implements GatewayObjectStore
{
   private final AuditWriter context;
   private final String alias;

   /*******************************************************************************
    ** Bind the host instance and bucket alias without touching storage at startup.
    *******************************************************************************/
   public ObjectStorageGatewayStore(QInstance instance, String alias)
   {
      context = new AuditWriter(instance);
      this.alias = alias;
   }

   /*******************************************************************************
    ** Unknown-length puts use object-storage's bounded multipart implementation.
    *******************************************************************************/
   @Override
   public Receipt put(String key, InputStream content) throws IOException
   {
      try
      {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         var result = context.system(() -> ObjectStorage.asSystem("file-gateway", key).bucket(alias)
            .put(PutRequest.of(key, new DigestInputStream(content, digest))));
         return (new Receipt(result.getBytes(), HexFormat.of().formatHex(digest.digest())));
      }
      catch(Exception e)
      {
         throw (new IOException("Object upload failed", e));
      }
   }

   /*******************************************************************************
    ** Stream the complete object.
    *******************************************************************************/
   @Override
   public InputStream open(String key) throws IOException
   {
      return (read(key, Range.all()));
   }

   /*******************************************************************************
    ** Stream only the requested range.
    *******************************************************************************/
   @Override
   public InputStream openRange(String key, long start, long end) throws IOException
   {
      return (read(key, Range.bytes(start, end)));
   }

   /*******************************************************************************
    ** Closing the returned stream also releases its object response.
    *******************************************************************************/
   private InputStream read(String key, Range range) throws IOException
   {
      try
      {
         var object = context.system(() -> ObjectStorage.asSystem("file-gateway", key).bucket(alias).get(key, range));
         return (new FilterInputStream(object.getInputStream())
         {
            @Override
            public void close() throws IOException
            {
               object.close();
            }
         });
      }
      catch(Exception e)
      {
         throw (new IOException("Object read failed", e));
      }
   }

   /*******************************************************************************
    ** Server-side quarantine copy.
    *******************************************************************************/
   @Override
   public void copy(String source, String destination) throws IOException
   {
      try
      {
         context.system(() ->
         {
            ObjectStorage.asSystem("file-gateway", source).bucket(alias).copy(CopyRequest.of(source, destination));
            return (null);
         });
      }
      catch(Exception e)
      {
         throw (new IOException("Object copy failed", e));
      }
   }

   /*******************************************************************************
    ** Remove all versions of a gateway-owned object.
    *******************************************************************************/
   @Override
   public void delete(String key) throws IOException
   {
      try
      {
         context.system(() -> ObjectStorage.asSystem("file-gateway", key).bucket(alias).delete(DeleteRequest.of(key).permanent()));
      }
      catch(Exception e)
      {
         throw (new IOException("Object deletion failed", e));
      }
   }
}
