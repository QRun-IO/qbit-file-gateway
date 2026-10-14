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

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;

/*******************************************************************************
 ** Independent, short-lived account-user credentials for an upload worker.
 *******************************************************************************/
public final class WorkerUserSession implements AutoCloseable
{
   private final QInstance instance;
   private final UiApiInvoker invoker;
   private final String userId;
   private final String detail;
   private final Clock clock;
   private String token;
   private Instant minted;

   /*******************************************************************************
    ** The caller owns restoration of its QContext after the job.
    *******************************************************************************/
   public WorkerUserSession(QInstance instance, UiApiInvoker invoker, String userId, String detail, Clock clock) throws QException
   {
      this.instance = instance;
      this.invoker = invoker;
      this.userId = userId;
      this.detail = detail;
      this.clock = clock;
      refresh();
   }

   /*******************************************************************************
    ** Re-mint permissions even after the originating SSH session has disconnected.
    *******************************************************************************/
   private void refresh() throws QException
   {
      if(minted == null || !minted.plusSeconds(300).isAfter(clock.instant()))
      {
         var module = new QAuthenticationModuleDispatcher().getQModule(instance.getAuthentication());
         var user = new AuditWriter(instance).system(() -> module.createAutomatedSessionForUser(instance, userId));
         if(user == null || !user.hasPermission(FileGatewayPermissions.CONNECT))
         {
            throw (new QException("Missing file_gateway.connect"));
         }
         user.setClientChannel("sftp/" + detail);
         String replacement = UiApiInProcessSessions.register(user, Duration.ofMinutes(10));
         UiApiInProcessSessions.revoke(token);
         token = replacement;
         minted = clock.instant();
         QContext.init(instance, user);
      }
   }

   /*******************************************************************************
    ** Only published operations, with fixed channel identity and user credentials.
    *******************************************************************************/
   public JsonNode invoke(UiApiRequest request) throws IOException, QException
   {
      refresh();
      if(invoker.findOperation(request.getMethod(), request.getPath()).isEmpty())
      {
         throw (new IOException("Gateway requires a spec-defined UI API operation"));
      }
      var response = invoker.invoke(request.withCredentials(UiApiCredentials.ofInProcessSession(token))
         .withInvokedBy("file-gateway").withClientDetail(detail));
      if(!response.isSuccess())
      {
         throw (new IOException("UI API request failed with status " + response.getStatus()));
      }
      return (response.getJson());
   }

   /*******************************************************************************
    ** Never retain worker credentials after a job.
    *******************************************************************************/
   @Override
   public void close()
   {
      UiApiInProcessSessions.revoke(token);
   }
}
