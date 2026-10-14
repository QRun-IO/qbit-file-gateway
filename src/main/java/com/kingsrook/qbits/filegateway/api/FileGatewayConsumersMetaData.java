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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QSupplementalInstanceMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** Host-declared consumer implementations; contains no gateway runtime dependencies.
 *******************************************************************************/
public class FileGatewayConsumersMetaData implements QSupplementalInstanceMetaData
{
   public static final String NAME = "fileGatewayConsumers";
   private final List<QCodeReference> consumers = new ArrayList<>();



   /*******************************************************************************
    ** Metadata registry name.
    *******************************************************************************/
   public String getName()
   {
      return (NAME);
   }



   /*******************************************************************************
    ** Get or install the registry.
    *******************************************************************************/
   public static FileGatewayConsumersMetaData ofOrWithNew(QInstance instance)
   {
      return (QSupplementalInstanceMetaData.ofOrWithNew(instance, NAME, FileGatewayConsumersMetaData::new));
   }



   /*******************************************************************************
    ** Register a consumer implementation.
    *******************************************************************************/
   public FileGatewayConsumersMetaData withConsumer(QCodeReference consumer)
   {
      consumers.add(consumer);
      return (this);
   }



   /*******************************************************************************
    ** Registered consumer implementations.
    *******************************************************************************/
   public List<QCodeReference> getConsumers()
   {
      return (List.copyOf(consumers));
   }
}
