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


import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.filegateway.sftp.CidrMatcher;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real protocol acceptance tests for listing, authentication, auditing and no-disk operation.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class SftpIntegrationTest
{
   /*******************************************************************************
    ** Pair.
    *******************************************************************************/
   private KeyPair pair() throws Exception
   {
      return (KeyUtils.generateKeyPair("ssh-ed25519", 256));
   }



   /*******************************************************************************
    ** Names.
    *******************************************************************************/
   private List<String> names(SftpClient client, String path) throws Exception
   {
      List<String> result = new ArrayList<>();
      for(SftpClient.DirEntry entry : client.readDir(path))
      {
         if(!entry.getFilename().equals(".") && !entry.getFilename().equals(".."))
         {
            result.add(entry.getFilename());
         }
      }
      return (result);
   }



   /*******************************************************************************
    ** Permission filtered listings and audit.
    *******************************************************************************/
   @Test
   void permissionFilteredListingsAndAudit() throws Exception
   {
      try(var harness = new FileGatewayTestServer())
      {
         KeyPair a = pair();
         KeyPair b = pair();
         harness.key(harness.account("alice", "A"), a);
         harness.key(harness.account("bob", "B"), b);
         assertThat(harness.saved("savedBulkLoadProfile", "alpha", "Orders In").getErrors()).isEmpty();
         assertThat(harness.saved("savedView", "alpha", "All Orders").getErrors()).isEmpty();
         assertThat(harness.saved("savedReport", "alpha", "Daily Orders").getErrors()).isEmpty();
         int tokens = UiApiInProcessSessions.size();
         try(var sa = harness.login("alice", a); var sb = harness.login("bob", b);
             var ca = SftpClientFactory.instance().createSftpClient(sa); var cb = SftpClientFactory.instance().createSftpClient(sb))
         {
            assertThat(names(ca, "/")).contains("testapp");
            assertThat(names(ca, "/testapp")).containsExactlyInAnyOrder("alpha", "beta");
            assertThat(names(cb, "/testapp")).containsExactly("beta");
            assertThatThrownBy(() -> cb.stat("/testapp/alpha")).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE));
            assertThat(names(ca, "/testapp/alpha")).contains("upload", "views", "reports", "results", "archive");
            assertThat(names(ca, "/testapp/beta")).doesNotContain("upload");
            assertThat(names(ca, "/testapp/alpha/upload")).containsExactly("orders-in");
            assertThat(names(ca, "/testapp/alpha/upload/orders-in")).isEmpty();
            assertThat(names(ca, "/testapp/alpha/views")).containsExactlyInAnyOrder("all-orders.csv", "all-orders.xlsx");
            assertThat(names(ca, "/testapp/alpha/reports")).contains("daily-orders.csv", "daily-orders.xlsx");
            assertThat(ca.stat("/testapp/alpha/views/all-orders.csv").getSize()).isZero();
            assertThatThrownBy(() -> ca.read("/testapp/alpha/views/all-orders.csv")).isInstanceOf(SftpException.class);
            assertThatThrownBy(() -> ca.write("/testapp/alpha/test.csv")).isInstanceOf(SftpException.class);
            assertThatThrownBy(() -> ca.setStat("/testapp/alpha/views/all-orders.csv", new SftpClient.Attributes().modifyTime(0))).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_PERMISSION_DENIED));
            assertThatThrownBy(() -> names(cb, "/testapp/alpha")).isInstanceOf(java.io.UncheckedIOException.class).hasCauseInstanceOf(SftpException.class);
            assertThatThrownBy(() -> ca.mkdir("/testapp/alpha/new")).isInstanceOf(SftpException.class);
            assertThatThrownBy(() -> ca.remove("/testapp/alpha/views/all-orders.csv")).isInstanceOf(SftpException.class);
            assertThatThrownBy(() -> ca.rename("/testapp/alpha/views/all-orders.csv", "/testapp/alpha/views/new.csv")).isInstanceOf(SftpException.class);
            assertThat(harness.runtime.getInvoker().findOperation(HttpMethod.GET, "/metaData")).isPresent();
            assertThat(harness.runtime.getInvoker().findOperation(HttpMethod.POST, "/table/{tableName}/query")).isPresent();
            assertThat(harness.runtime.getInvoker().getClientChannel()).isEqualTo("sftp");
         }
         assertThat(harness.calls).isNotEmpty().allSatisfy(call ->
         {
            String template = call.path().substring("/qqq/v1".length()).replaceAll("/table/[^/]+/query", "/table/{tableName}/query");
            assertThat(harness.runtime.getInvoker().findOperation(HttpMethod.valueOf(call.method()), template)).isPresent();
            assertThat(call.channel()).startsWith("sftp/");
            assertThat(call.invokedBy()).isEqualTo("file-gateway");
         });
         harness.runtime.stop();
         assertThat(UiApiInProcessSessions.size()).isEqualTo(tokens);
         var connections = harness.audit.query("file_gateway_connection_log", new QQueryFilter());
         assertThat(connections).hasSize(2).allSatisfy(row ->
         {
            assertThat(row.getValueString("clientChannel")).isEqualTo("sftp");
            assertThat(row.getValueString("outcome")).isEqualTo("CONNECTED");
            assertThat(row.getValue("disconnectedAt")).isNotNull();
         });
         var transfers = harness.audit.query("file_gateway_transfer_log", new QQueryFilter());
         assertThat(transfers).anySatisfy(row ->
         {
            assertThat(row.getValueString("operation")).isEqualTo("LIST");
            assertThat(row.getValueString("outcome")).isEqualTo("OK");
         }).anySatisfy(row -> assertThat(row.getValueString("outcome")).isEqualTo("DENIED"))
            .allSatisfy(row -> assertThat(row.getValueString("clientChannel")).startsWith("sftp/"));
      }
   }



   /*******************************************************************************
    ** Refuses invalid credentials with audit.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"unknown", "revoked", "expiredKey", "disabled", "expiredAccount", "ip", "permission"})
   void refusesInvalidCredentialsWithAudit(String scenario) throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var key = pair();
         var account = h.account("alice", scenario.equals("permission") ? "NONE" : "A");
         var stored = h.key(account, key);
         String outcome = "AUTH_FAILED";
         switch(scenario)
         {
            case "revoked" -> h.audit.update("file_gateway_ssh_key", new QRecord().withValue("id", stored.getValue("id")).withValue("revokedAt", Instant.now()));
            case "expiredKey" ->
            {
               h.audit.update("file_gateway_ssh_key", new QRecord().withValue("id", stored.getValue("id")).withValue("expiresAt", Instant.now().minusSeconds(1)));
               outcome = "EXPIRED";
            }
            case "disabled" ->
            {
               h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("status", "DISABLED"));
               outcome = "DISABLED";
            }
            case "expiredAccount" ->
            {
               h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("expiresAt", Instant.now().minusSeconds(1)));
               outcome = "EXPIRED";
            }
            case "ip" ->
            {
               h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("allowedCidrs", "192.0.2.0/24"));
               outcome = "IP_DENIED";
            }
         }
         KeyPair offered = scenario.equals("unknown") ? pair() : key;
         assertThatThrownBy(() -> h.login("alice", offered)).isInstanceOf(Exception.class);
         assertThat(h.audit.query("file_gateway_connection_log", new QQueryFilter()).getFirst().getValueString("outcome")).isEqualTo(outcome);
      }
   }



   /*******************************************************************************
    ** Lockout and session limit.
    *******************************************************************************/
   @Test
   void lockoutAndSessionLimit() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData.ofOrWithNew(h.instance)
            .withSink(new com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference(RecordingAlerts.class));
         RecordingAlerts.signals.clear();
         var account = h.account("alice", "A");
         var good = pair();
         h.key(account, good);
         var bad = pair();
         for(int i = 0; i < 5; i++)
         {
            assertThatThrownBy(() -> h.login("alice", bad)).isInstanceOf(Exception.class);
         }
         assertThatThrownBy(() -> h.login("alice", good)).isInstanceOf(Exception.class);
         assertThat(RecordingAlerts.signals).containsExactly("filegateway.lockout");
         var rows = h.audit.query("file_gateway_connection_log", new QQueryFilter());
         assertThat(rows).anySatisfy(row -> assertThat(row.getValueString("outcome")).isEqualTo("LOCKED_OUT"));
         assertThat(h.audit.query("file_gateway_account", new QQueryFilter()).getFirst().getValue("lockedUntil")).isNotNull();
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("lockedUntil", null));
         try(var unlocked = h.login("alice", good))
         {
            assertThat(unlocked.isAuthenticated()).isTrue();
         }
      }
      try(var h = new FileGatewayTestServer())
      {
         var account = h.account("alice", "A");
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("maxConcurrentSessions", 1));
         var good = pair();
         h.key(account, good);
         try(var session = h.login("alice", good))
         {
            assertThatThrownBy(() -> h.login("alice", good)).isInstanceOf(Exception.class);
            assertThat(h.audit.query("file_gateway_connection_log", new QQueryFilter())).anySatisfy(row -> assertThat(row.getValueString("outcome")).isEqualTo("LIMIT"));
         }
      }
   }



   /*******************************************************************************
    ** Public key only and live revocation.
    *******************************************************************************/
   @Test
   void publicKeyOnlyAndLiveRevocation() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var account = h.account("alice", "A");
         var pair = pair();
         var key = h.key(account, pair);
         var server = h.runtime.getProtocol().getServer();
         assertThat(server.getPasswordAuthenticator()).isNull();
         assertThat(server.getKeyboardInteractiveAuthenticator()).isNull();
         assertThat(server.getUserAuthFactories()).extracting(factory -> factory.getName()).containsExactly("publickey");
         assertThat(server.getCipherFactories()).noneSatisfy(factory -> assertThat(factory.getName()).contains("cbc"));
         assertThat(server.getMacFactories()).allSatisfy(factory -> assertThat(factory.getName()).contains("sha2"));
         assertThat(server.getSignatureFactories()).noneSatisfy(factory -> assertThat(factory.getName()).isEqualTo("ssh-rsa"));
         try(var password = h.client.connect("alice", "127.0.0.1", h.runtime.getProtocol().getPort()).verify(Duration.ofSeconds(5)).getSession())
         {
            password.addPasswordIdentity("not-a-password");
            assertThatThrownBy(() -> password.auth().verify(Duration.ofSeconds(5))).isInstanceOf(Exception.class);
         }
         try(var session = h.login("alice", pair))
         {
            h.audit.update("file_gateway_ssh_key", new QRecord().withValue("id", key.getValue("id")).withValue("revokedAt", Instant.now()));
            h.runtime.getProtocol().getAuthenticator().poll();
            session.waitFor(java.util.Set.of(org.apache.sshd.client.session.ClientSession.ClientSessionEvent.CLOSED), Duration.ofSeconds(5));
            assertThat(session.isClosed()).isTrue();
         }
      }
      assertThat(CidrMatcher.matches("::1", "::1/128")).isTrue();
      assertThat(CidrMatcher.matches("127.0.0.1", "::1/128")).isFalse();
      assertThat(CidrMatcher.matches("127.0.0.1", "localhost/32")).isFalse();
   }



   /*******************************************************************************
    ** Exercise the core alert facade without adding any dependency on qbit-alerts.
    *******************************************************************************/
   public static class RecordingAlerts extends com.kingsrook.qqq.backend.core.alerts.LoggingAlertSink
   {
      static final List<String> signals = new java.util.concurrent.CopyOnWriteArrayList<>();

      /*******************************************************************************
       ** Record alert type names only, without account or key material.
       *******************************************************************************/
      @Override
      public void raise(com.kingsrook.qqq.backend.core.alerts.AlertSignal signal)
      {
         signals.add(signal.getAlertTypeName());
      }
   }
}
