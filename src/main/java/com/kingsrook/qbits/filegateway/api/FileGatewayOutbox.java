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

package com.kingsrook.qbits.filegateway.api;


import java.io.InputStream;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Optional outbox facade; the host runtime owns provider registration.
 *******************************************************************************/
public final class FileGatewayOutbox
{
   private static volatile FileGatewayOutboxProvider provider;



   /*******************************************************************************
    ** Static facade.
    *******************************************************************************/
   private FileGatewayOutbox()
   {
   }



   /*******************************************************************************
    ** Install or clear the runtime provider.
    *******************************************************************************/
   public static void setProvider(FileGatewayOutboxProvider provider)
   {
      FileGatewayOutbox.provider = provider;
   }



   /*******************************************************************************
    ** Whether the runtime supports outbox writes.
    *******************************************************************************/
   public static boolean isAvailable()
   {
      return (provider != null);
   }



   /*******************************************************************************
    ** Delegate to the installed provider.
    *******************************************************************************/
   public static OutboxFileRef put(String endpointName, String fileName, InputStream content, long sizeBytes) throws FileGatewayUnavailableException, QException
   {
      FileGatewayOutboxProvider current = provider;
      if(current == null)
      {
         throw (new FileGatewayUnavailableException());
      }
      return (current.put(endpointName, fileName, content, sizeBytes));
   }
}
