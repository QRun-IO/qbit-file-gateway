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

package com.kingsrook.qbits.filegateway.sftp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.server.session.ServerSession;


/*******************************************************************************
 ** One SSH connection, its audit identity and refreshable in-process credentials.
 *******************************************************************************/
public class GatewaySession
{
   public static final AttributeKey<GatewaySession> ATTRIBUTE = new AttributeKey<>();
   public static final AttributeKey<String> TOKEN = new AttributeKey<>();
   public final String id = UUID.randomUUID().toString();
   public final ServerSession ssh;
   public final String remoteIp;
   public Integer connectionId;
   public java.time.Instant connectedAt;
   public QRecord account;
   public QRecord key;
   public long operations;
   public String disconnectReason = "Client disconnected";
   private Instant mintedAt;
   private final QInstance instance;
   private final Clock clock;



   /*******************************************************************************
    ** Bind an accepted SSH connection.
    *******************************************************************************/
   public GatewaySession(QInstance instance, ServerSession ssh, Clock clock)
   {
      this.instance = instance;
      this.ssh = ssh;
      this.clock = clock;
      this.remoteIp = ((InetSocketAddress) ssh.getClientAddress()).getAddress().getHostAddress();
   }



   /*******************************************************************************
    ** Mint via the host authentication module and refresh permissions every five minutes.
    *******************************************************************************/
   public synchronized String token() throws QException
   {
      if(account == null || ssh.isClosing() || !ssh.isOpen())
      {
         throw (new QException("Unauthenticated connection"));
      }
      if(mintedAt == null || !mintedAt.plus(Duration.ofMinutes(5)).isAfter(clock.instant()))
      {
         var module = new QAuthenticationModuleDispatcher().getQModule(instance.getAuthentication());
         var user = new AuditWriter(instance).system(() -> module.createAutomatedSessionForUser(instance, account.getValueString("userId")));
         if(user == null || !user.hasPermission(FileGatewayPermissions.CONNECT))
         {
            revoke();
            throw (new QException("no file_gateway.connect"));
         }
         String next = UiApiInProcessSessions.register(user, Duration.ofMinutes(10));
         UiApiInProcessSessions.revoke(ssh.getAttribute(TOKEN));
         ssh.setAttribute(TOKEN, next);
         mintedAt = clock.instant();
      }
      return (ssh.getAttribute(TOKEN));
   }



   /*******************************************************************************
    ** Release credentials immediately on disconnect or revocation.
    *******************************************************************************/
   public synchronized void revoke()
   {
      UiApiInProcessSessions.revoke(ssh.removeAttribute(TOKEN));
      mintedAt = null;
   }



   /*******************************************************************************
    ** Count operations locally and charge the shared account minute window.
    *******************************************************************************/
   public synchronized boolean operationAllowed() throws IOException
   {
      operations++;
      Integer maximum = account.getValueInteger("maxOperationsPerMinute");
      try
      {
         return (new AuditWriter(instance).operationAllowed(account.getValueInteger("id"), clock.instant(), maximum == null ? 600 : maximum));
      }
      catch(QException e)
      {
         throw (new IOException("Unable to verify account operation limit", e));
      }
   }
}
