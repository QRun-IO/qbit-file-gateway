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

/*******************************************************************************
 ** Streaming storage boundary. Production uses the object-storage system API.
 *******************************************************************************/
public interface GatewayObjectStore
{
   /*******************************************************************************
    ** Completed immutable object identity, measured while streaming.
    *******************************************************************************/
   record Receipt(long size, String sha256)
   {
   }

   /*******************************************************************************
    ** Consume an unknown-length stream; a read failure must abort multipart upload.
    *******************************************************************************/
   Receipt put(String key, InputStream content) throws IOException;

   /*******************************************************************************
    ** Open a fresh stream owned by the caller.
    *******************************************************************************/
   InputStream open(String key) throws IOException;

   /*******************************************************************************
    ** Open an inclusive byte range, owned by the caller.
    *******************************************************************************/
   InputStream openRange(String key, long start, long end) throws IOException;

   /*******************************************************************************
    ** Copy within the configured bucket.
    *******************************************************************************/
   void copy(String source, String destination) throws IOException;

   /*******************************************************************************
    ** Permanently remove an aborted or quarantined source object.
    *******************************************************************************/
   void delete(String key) throws IOException;
}
