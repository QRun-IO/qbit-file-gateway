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

package com.kingsrook.qbits.filegateway.vfs;


import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.openapi.model.HttpMethod;


/*******************************************************************************
 ** Every visible object comes from a spec-defined UI API operation as the signed-in user.
 *******************************************************************************/
public class GatewayUiApiClient
{
   private final UiApiInvoker invoker;
   private final GatewaySession session;



   /*******************************************************************************
    ** Bind this client to one SFTP identity.
    *******************************************************************************/
   public GatewayUiApiClient(UiApiInvoker invoker, GatewaySession session)
   {
      this.invoker = invoker;
      this.session = session;
   }



   /*******************************************************************************
    ** Get the permission-filtered app tree.
    *******************************************************************************/
   public JsonNode metadata() throws IOException
   {
      return (invoke(new UiApiRequest().withMethod(HttpMethod.GET).withPath("/metaData")));
   }



   /*******************************************************************************
    ** Query visible saved objects of a table.
    *******************************************************************************/
   public JsonNode savedObjects(String source, String table) throws IOException
   {
      var records = JsonNodeFactory.instance.arrayNode();
      int pageSize = 250;
      for(int skip = 0; ; skip += pageSize)
      {
         QQueryFilter filter = new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, table))
            .withOrderBy(new QFilterOrderBy("id", true)).withLimit(pageSize).withSkip(skip);
         JsonNode page = invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/table/{tableName}/query")
            .withPathParam("tableName", source).withJsonBody(java.util.Map.of("filter", filter))).path("records");
         page.forEach(records::add);
         if(page.size() < pageSize)
         {
            return (records);
         }
      }
   }



   /*******************************************************************************
    ** Apply the fixed channel identity, credentials and contract check on every call.
    *******************************************************************************/
   protected JsonNode invoke(UiApiRequest request) throws IOException
   {
      if(invoker.findOperation(request.getMethod(), request.getPath()).isEmpty())
      {
         throw (new IOException("Gateway requires a spec-defined UI API operation"));
      }
      try
      {
         var response = invoker.invoke(request.withCredentials(UiApiCredentials.ofInProcessSession(session.token()))
            .withInvokedBy("file-gateway").withClientDetail(session.id));
         if(response.getStatus() == 404)
         {
            throw (new NoSuchFileException(request.getPath()));
         }
         if(response.getStatus() == 401 || response.getStatus() == 403)
         {
            throw (new AccessDeniedException(request.getPath()));
         }
         if(!response.isSuccess())
         {
            throw (new IOException("UI API request failed with status " + response.getStatus()));
         }
         return (response.getJson());
      }
      catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
      {
         throw (new IOException("User session unavailable", e));
      }
   }
}
