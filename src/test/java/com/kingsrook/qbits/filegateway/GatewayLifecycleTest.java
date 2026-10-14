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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qbits.filegateway.vfs.GatewayFileSystem;
import com.kingsrook.qbits.filegateway.vfs.VirtualTree;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Time, multiple gateway listeners, runtime configuration and NIO boundaries.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class GatewayLifecycleTest
{
   /*******************************************************************************
    ** Advance time deterministically without changing the machine clock.
    *******************************************************************************/
   static class MutableClock extends Clock
   {
      private Instant now = Instant.now();
      /*******************************************************************************
       ** Get zone.
       *******************************************************************************/
      public ZoneId getZone()
      {
         return (ZoneOffset.UTC);
      }



      /*******************************************************************************
       ** With zone.
       *******************************************************************************/
      public Clock withZone(ZoneId zone)
      {
         return (this);
      }



      /*******************************************************************************
       ** Instant.
       *******************************************************************************/
      public Instant instant()
      {
         return (now);
      }



      /*******************************************************************************
       ** Advance.
       *******************************************************************************/
      void advance(Duration duration)
      {
         now = now.plus(duration);
      }
   }



   /*******************************************************************************
    ** Server-side session for assertions without exposing credentials in logs.
    *******************************************************************************/
   private GatewaySession state(FileGatewayTestServer h)
   {
      return (h.runtime.getProtocol().getServer().getActiveSessions().iterator().next().getAttribute(GatewaySession.ATTRIBUTE));
   }



   /*******************************************************************************
    ** Read names while retaining the actual opendir protocol path.
    *******************************************************************************/
   private List<String> names(SftpClient client, String path) throws Exception
   {
      var names = new ArrayList<String>();
      for(var entry : client.readDir(path))
      {
         names.add(entry.getFilename());
      }
      return (names);
   }



   /*******************************************************************************
    ** Re-mint at five minutes, revoke the prior token, and enforce changed grants.
    *******************************************************************************/
   @Test
   void refreshPermissionsAndCaches() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var session = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(session))
         {
            assertThat(names(sftp, "/testapp")).contains("alpha", "beta");
            int count = h.calls.size();
            names(sftp, "/testapp");
            assertThat(h.calls).hasSize(count);
            String token = state(h).token();
            assertThat(UiApiInProcessSessions.resolve(token)).isPresent();
            FileGatewayTestServer.GRANTS.put("A", Set.of("file_gateway.connect", "beta.read"));
            clock.advance(Duration.ofMinutes(5));
            assertThat(names(sftp, "/testapp")).doesNotContain("alpha").contains("beta");
            assertThat(state(h).token().equals(token)).isFalse();
            assertThat(UiApiInProcessSessions.resolve(token)).isEmpty();
            FileGatewayTestServer.GRANTS.put("A", Set.of());
            clock.advance(Duration.ofMinutes(5));
            assertThatThrownBy(() -> names(sftp, "/")).isInstanceOf(Exception.class);
         }
      }
   }



   /*******************************************************************************
    ** Each saved-object list is cached for thirty seconds and paged past 250 rows.
    *******************************************************************************/
   @Test
   void savedObjectsPaginationAndSlugCollisions() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         h.saved("savedView", "alpha", "Duplicate!");
         h.saved("savedView", "alpha", "Duplicate?");
         try(var session = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(session))
         {
            assertThat(names(sftp, "/testapp/alpha/views")).contains("duplicate-1.csv", "duplicate-2.xlsx");
            h.saved("savedView", "alpha", "Fresh");
            assertThat(names(sftp, "/testapp/alpha/views")).doesNotContain("fresh.csv");
            clock.advance(Duration.ofSeconds(31));
            assertThat(names(sftp, "/testapp/alpha/views")).contains("fresh.csv");
            var rows = new ArrayList<QRecord>();
            for(int i = 0; i < 251; i++)
            {
               rows.add(new QRecord().withValue("tableName", "alpha").withValue("label", "Paged " + i).withValue("userId", "A").withValue("viewJson", "{}"));
            }
            h.audit.system(() -> new com.kingsrook.qqq.backend.core.actions.tables.InsertAction().execute(
               new com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput("savedView").withRecords(rows)));
            clock.advance(Duration.ofSeconds(31));
            assertThat(names(sftp, "/testapp/alpha/views")).contains("paged-250.csv").hasSize(510);
         }
      }
      assertThat(VirtualTree.slug(" !!! ")).isEqualTo("item");
      assertThat(VirtualTree.slug("A".repeat(100))).hasSize(80);
   }



   /*******************************************************************************
    ** Revocation covers accounts, keys and explicit session termination, with heartbeats.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"disabled", "expired", "keyExpired", "end"})
   void pollEndsRevokedSessions(String reason) throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         var pair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         var key = h.key(account, pair);
         try(var session = h.login("alice", pair))
         {
            var state = state(h);
            String token = state.token();
            clock.advance(Duration.ofSeconds(60));
            h.runtime.getProtocol().getAuthenticator().poll();
            var connection = h.audit.query(FileGatewayTables.CONNECTION_LOG, new QQueryFilter()).getFirst();
            assertThat(connection.getValueInstant("lastSeenAt")).isAfter(connection.getValueInstant("connectedAt"));
            switch(reason)
            {
               case "disabled" -> h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("status", "DISABLED"));
               case "expired" -> h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("expiresAt", clock.instant().minusSeconds(1)));
               case "keyExpired" -> h.audit.update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", key.getValue("id")).withValue("expiresAt", clock.instant().minusSeconds(1)));
               case "end" ->
               {
                  com.kingsrook.qqq.backend.core.context.QContext.init(h.instance,
                     new com.kingsrook.qqq.backend.core.model.session.QSession().withPermissions(Set.of("file_gateway.admin")));
                  new com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses.Apply().runOnePage(
                     new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput()
                        .withProcessName(com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses.END_SESSION).withRecords(List.of(connection)),
                     new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput());
               }
            }
            h.runtime.getProtocol().getAuthenticator().poll();
            session.waitFor(Set.of(ClientSession.ClientSessionEvent.CLOSED), Duration.ofSeconds(5));
            assertThat(session.isClosed()).isTrue();
            assertThat(UiApiInProcessSessions.resolve(token)).isEmpty();
            assertThatThrownBy(state::token).isInstanceOf(com.kingsrook.qqq.backend.core.exceptions.QException.class);
         }
      }
   }



   /*******************************************************************************
    ** Competing listeners share the account row lock, never exceeding the database limit.
    *******************************************************************************/
   @Test
   void concurrentListenersAndRestartPreserveLockout() throws Exception
   {
      try(var h = new FileGatewayTestServer(); var second = FileGatewayRuntime.startForTest(h.instance, h.http,
         h.config, KeyUtils.generateKeyPair("ecdsa-sha2-nistp256", 256), Clock.systemUTC(), Duration.ofHours(1));
         var executor = Executors.newFixedThreadPool(2))
      {
         var pair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.key(account, pair);
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("maxConcurrentSessions", 1));
         var start = new CountDownLatch(1);
         var tasks = new ArrayList<java.util.concurrent.Future<ClientSession>>();
         for(var runtime : List.of(h.runtime, second))
         {
            tasks.add(executor.submit(() ->
            {
               start.await();
               var session = h.client.connect("alice", "127.0.0.1", runtime.getProtocol().getPort()).verify(Duration.ofSeconds(5)).getSession();
               session.addPublicKeyIdentity(pair);
               try
               {
                  session.auth().verify(Duration.ofSeconds(5));
                  return (session);
               }
               catch(Exception e)
               {
                  session.close(true).await();
                  return (null);
               }
            }));
         }
         start.countDown();
         var sessions = new ArrayList<ClientSession>();
         for(var task : tasks)
         {
            var session = task.get();
            if(session != null)
            {
               sessions.add(session);
            }
         }
         try
         {
            assertThat(sessions).hasSize(1);
         }
         finally
         {
            for(var session : sessions)
            {
               session.close(true).await();
            }
         }
         assertThat(h.audit.query(FileGatewayTables.CONNECTION_LOG, new QQueryFilter())).anySatisfy(r -> assertThat(r.getValueString("outcome")).isEqualTo("LIMIT"));
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("lockedUntil", Instant.now().plusSeconds(900)));
         h.runtime.stop();
         try(var session = h.client.connect("alice", "127.0.0.1", second.getProtocol().getPort()).verify(Duration.ofSeconds(5)).getSession())
         {
            session.addPublicKeyIdentity(pair);
            assertThatThrownBy(() -> session.auth().verify(Duration.ofSeconds(5))).isInstanceOf(Exception.class);
         }
         assertThat(h.audit.query(FileGatewayTables.CONNECTION_LOG, new QQueryFilter())).anySatisfy(r -> assertThat(r.getValueString("outcome")).isEqualTo("LOCKED_OUT"));
      }
   }



   /*******************************************************************************
    ** Connection limits apply before auth; operation limits reset on the next minute.
    *******************************************************************************/
   @Test
   void rateAndOperationLimits() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         var pair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.key(account, pair);
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("maxOperationsPerMinute", 1));
         try(var session = h.login("alice", pair); var sftp = SftpClientFactory.instance().createSftpClient(session))
         {
            names(sftp, "/");
            try(var other = h.login("alice", pair); var otherSftp = SftpClientFactory.instance().createSftpClient(other))
            {
               assertThatThrownBy(() -> names(otherSftp, "/")).isInstanceOf(Exception.class);
            }
            assertThatThrownBy(() -> names(sftp, "/")).isInstanceOf(Exception.class);
            clock.advance(Duration.ofSeconds(61));
            assertThat(names(sftp, "/")).contains("testapp");
         }
         h.config.withMaxConnectionsPerIpPerMinute(1);
         try(var first = h.login("alice", pair))
         {
            assertThatThrownBy(() -> h.login("alice", pair)).isInstanceOf(Exception.class);
         }
         assertThat(h.audit.query(FileGatewayTables.TRANSFER_LOG, new QQueryFilter())).anySatisfy(r -> assertThat(r.getValueString("outcome")).isEqualTo("LIMIT"));
      }
   }



   /*******************************************************************************
    ** Rotating usernames cannot evade the per-IP lock, which expires independently.
    *******************************************************************************/
   @Test
   void ipLockoutAcrossUsernames() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         h.config.withLockoutThreshold(3);
         var good = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), good);
         var bad = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         for(String username : List.of("unknown-one", "unknown-two", "unknown-three"))
         {
            assertThatThrownBy(() -> h.login(username, bad)).isInstanceOf(Exception.class);
         }
         assertThatThrownBy(() -> h.login("alice", good)).isInstanceOf(Exception.class);
         assertThat(h.audit.query(FileGatewayTables.CONNECTION_LOG, new QQueryFilter())).anySatisfy(r -> assertThat(r.getValueString("outcome")).isEqualTo("LOCKED_OUT"));
         clock.advance(Duration.ofMinutes(31));
         try(var session = h.login("alice", good))
         {
            assertThat(session.isAuthenticated()).isTrue();
         }
      }
   }



   /*******************************************************************************
    ** Host secrets are parsed from memory and missing secrets fail startup.
    *******************************************************************************/
   @Test
   void inMemoryHostKeys() throws Exception
   {
      var config = new FileGatewayQBitConfig().withHostKeyEnvVarName("TEST_HOST_KEY");
      assertThatThrownBy(() -> FileGatewayRuntime.hostKey(config, name -> null)).isInstanceOf(IllegalStateException.class);
      assertThat(FileGatewayRuntime.hostKey(config.withAllowGeneratedHostKey(true), name -> null)).isNotNull();
      var pair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
      var output = new ByteArrayOutputStream();
      OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(pair, "test", null, output);
      String pem = output.toString(StandardCharsets.UTF_8);
      var parsed = FileGatewayRuntime.hostKey(config, name -> name.equals("TEST_HOST_KEY") ? pem : null);
      assertThat(KeyUtils.compareKeyPairs(pair, parsed)).isTrue();
      assertThatThrownBy(() -> FileGatewayRuntime.hostKey(config, name -> "invalid")).isInstanceOf(Exception.class);
      try(var h = new FileGatewayTestServer())
      {
         h.runtime.stop();
         h.config.withHostKeyEnvVarName("TEST_HOST_KEY");
         try(var restarted = FileGatewayRuntime.start(h.instance, h.http, name -> name.equals("TEST_HOST_KEY") ? pem : "test-pod"))
         {
            assertThat(restarted.getProtocol().getPort()).isPositive();
            assertThat(restarted.getProtocol().activeSessions()).isZero();
         }
      }
   }



   /*******************************************************************************
    ** NIO paths never escape to native paths; phase-a mutations are always denied.
    *******************************************************************************/
   @Test
   void virtualNioContract() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var pair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), pair);
         h.saved("savedView", "alpha", "Orders");
         try(var session = h.login("alice", pair))
         {
            var state = state(h);
            try(var fs = (GatewayFileSystem) h.runtime.getProtocol().getServer().getFileSystemFactory().createFileSystem(state.ssh))
            {
               var path = fs.getPath("/testapp/alpha/views/orders.csv");
               assertThat(path.getFileName().toString()).isEqualTo("orders.csv");
               assertThat(path.getNameCount()).isEqualTo(4);
               assertThat(path.getName(1).toString()).isEqualTo("alpha");
               assertThat(path.subpath(1, 3).toString()).isEqualTo("alpha/views");
               assertThat(path.startsWith("/testapp")).isTrue();
               assertThat(path.endsWith("views/orders.csv")).isTrue();
               assertThat(fs.getPath("/testapp").relativize(path).toString()).isEqualTo("alpha/views/orders.csv");
               assertThat(fs.getPath("../../testapp").toAbsolutePath().normalize().toString()).isEqualTo("/testapp");
               assertThat(fs.getPath("/testapp", "alpha").resolve("views/orders.csv")).isEqualTo(path);
               assertThat(path.toRealPath()).isEqualTo(path);
               assertThat(path.hashCode()).isEqualTo(fs.getPath(path.toString()).hashCode());
               assertThat(path.toUri().getScheme()).isEqualTo("gateway");
               assertThat(path.iterator()).toIterable().hasSize(4);
               assertThatThrownBy(path::toFile).isInstanceOf(UnsupportedOperationException.class);
               assertThat(Files.readAttributes(path, BasicFileAttributes.class).isRegularFile()).isTrue();
               assertThat(Files.readAttributes(fs.getPath("/"), BasicFileAttributes.class).isDirectory()).isTrue();
               assertThatThrownBy(() -> Files.setAttribute(path, "basic:lastModifiedTime", java.nio.file.attribute.FileTime.from(Instant.EPOCH))).isInstanceOf(AccessDeniedException.class);
               assertThatThrownBy(() -> Files.createSymbolicLink(fs.getPath("/link"), path)).isInstanceOf(AccessDeniedException.class);
               assertThatThrownBy(() -> Files.createLink(fs.getPath("/link"), path)).isInstanceOf(AccessDeniedException.class);
               assertThatThrownBy(() -> Files.copy(path, fs.getPath("/copy"))).isInstanceOf(AccessDeniedException.class);
               assertThat(fs.getFileStores()).isEmpty();
               assertThat(fs.getRootDirectories()).containsExactly(fs.getPath("/"));
               assertThat(fs.isReadOnly()).isFalse();
               assertThat(fs.getSeparator()).isEqualTo("/");
               assertThatThrownBy(fs::newWatchService).isInstanceOf(UnsupportedOperationException.class);
               assertThatThrownBy(fs::getUserPrincipalLookupService).isInstanceOf(UnsupportedOperationException.class);
               assertThatThrownBy(() -> fs.getPathMatcher("glob:*")).isInstanceOf(UnsupportedOperationException.class);
            }
         }
      }
   }
}
