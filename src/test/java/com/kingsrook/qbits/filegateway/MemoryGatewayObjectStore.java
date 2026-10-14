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

package com.kingsrook.qbits.filegateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;

/*******************************************************************************
 ** Test-only object storage.
 *******************************************************************************/
public class MemoryGatewayObjectStore implements GatewayObjectStore
{
   public final Map<String, byte[]> objects = new ConcurrentHashMap<>();
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public Receipt put(String key, InputStream content) throws IOException
   {
      try
      {
         byte[] bytes = content.readAllBytes();
         objects.put(key, bytes);
         return (new Receipt(bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
      }
      catch(java.security.NoSuchAlgorithmException e)
      {
         throw new IOException(e);
      }
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public InputStream open(String key) throws IOException
   {
      byte[] bytes = objects.get(key);
      if(bytes == null)
      {
         throw (new IOException("Object missing"));
      }
      return (new ByteArrayInputStream(bytes));
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public InputStream openRange(String key, long start, long end) throws IOException
   {
      try(var in = open(key))
      {
         in.skipNBytes(start);
         return (new ByteArrayInputStream(in.readNBytes((int) (end - start + 1))));
      }
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void copy(String source, String destination) throws IOException
   {
      try(var in = open(source))
      {
         objects.put(destination, in.readAllBytes());
      }
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void delete(String key)
   {
      objects.remove(key);
   }
}
