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

package com.kingsrook.qbits.filegateway.permissions;


import java.util.List;


/*******************************************************************************
 ** Permissions granted by the host application.
 *******************************************************************************/
public final class FileGatewayPermissions
{
   public static final String VIEW = "file_gateway.view";
   public static final String ADMIN = "file_gateway.admin";
   public static final String CONNECT = "file_gateway.connect";
   public static final List<String> ALL = List.of(VIEW, ADMIN, CONNECT);



   /*******************************************************************************
    ** Constants only.
    *******************************************************************************/
   private FileGatewayPermissions()
   {
   }
}
