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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.filegateway.api.FileAcceptance;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumersMetaData;
import com.kingsrook.qbits.filegateway.api.FileGatewayOutbox;
import com.kingsrook.qbits.filegateway.api.InboundFile;
import com.kingsrook.qbits.filegateway.api.OutboxFileRef;
import com.kingsrook.qbits.filegateway.maintenance.GatewayMaintenance;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayHome;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.outbox.OutboxService;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Phase E acceptance uses real MINA sessions, strict UI API and the adopter DDL.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class PhaseEIntegrationTest
{
   static InboundFile accepted;
   static byte[] acceptedBytes;
   static int downloaded;
   static boolean failCallback;

   public static class Consumer implements FileGatewayConsumer
   {
      @Override
      public String getName()
      {
         return ("partner");
      }

      @Override
      public FileAcceptance accept(InboundFile file) throws Exception
      {
         accepted = file;
         try(var stream = file.openStream())
         {
            acceptedBytes = stream.readAllBytes();
         }
         return (FileAcceptance.accepted("receipt"));
      }

      @Override
      public void onDownloaded(OutboxFileRef ref) throws Exception
      {
         if(failCallback)
         {
            throw (new Exception("retry test"));
         }
         downloaded++;
      }
   }

   static QRecord alias(FileGatewayTestServer h, String name, String path, String kind, QRecord account) throws Exception
   {
      return (h.audit.insert(FileGatewayTables.ALIAS, new QRecord().withValue("name", name).withValue("path", path).withValue("kind", kind)
         .withValue("accountId", account == null ? null : account.getValue("id")).withValue("consumerName", "partner")));
   }

   static List<String> names(SftpClient sftp, String path) throws Exception
   {
      List<String> names = new ArrayList<>();
      for(var item : sftp.readDir(path))
      {
         if(!List.of(".", "..").contains(item.getFilename()))
         {
            names.add(item.getFilename());
         }
      }
      return (names);
   }

   static byte[] read(SftpClient sftp, String path) throws Exception
   {
      try(var input = sftp.read(path))
      {
         return (input.readAllBytes());
      }
   }

   @Test
   void endpointsChrootVisibilityBulkAndInbox() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         FileGatewayConsumersMetaData.ofOrWithNew(h.instance).withConsumer(new QCodeReference(Consumer.class));
         var profile = UploadIntegrationTest.profile(h);
         var service = h.audit.insert(FileGatewayTables.ACCOUNT, new QRecord().withValue("username", "service").withValue("userId", "A").withValue("accountType", "SERVICE"));
         assertThat(service.getValueString("rootMode")).isEqualTo("ENDPOINTS_ONLY");
         var user = h.account("alice", "A");
         assertThat(user.getValueString("rootMode")).isEqualTo("FULL_TREE");
         var denied = h.account("bob", "B");
         var bulk = alias(h, "orders", "/acme/orders", "BULK_LOAD", null);
         h.audit.update(FileGatewayTables.ALIAS, new QRecord().withValue("id", bulk.getValue("id")).withValue("tableName", "alpha")
            .withValue("savedBulkLoadProfileId", profile.getValue("id")));
         alias(h, "inbox", "/acme/inbox", "INBOX", service);
         alias(h, "private", "/private", "INBOX", user);
         alias(h, "shared", "/shared", "OUTBOX", null);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(service, key);
         var userKey = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(user, userKey);
         var deniedKey = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(denied, deniedKey);
         try(var ssh = h.login("service", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh);
             var aliceSsh = h.login("alice", userKey); var alice = SftpClientFactory.instance().createSftpClient(aliceSsh);
             var bobSsh = h.login("bob", deniedKey); var bob = SftpClientFactory.instance().createSftpClient(bobSsh))
         {
            assertThat(names(sftp, "/")).containsExactlyInAnyOrder("acme", "shared");
            assertThat(names(alice, "/")).contains("testapp", "acme", "private", "shared");
            assertThat(names(bob, "/")).doesNotContain("acme", "private");
            assertThatThrownBy(() -> sftp.stat("/testapp")).isInstanceOf(Exception.class);
            try(var output = sftp.write("/acme/orders/order.csv"))
            {
               output.write(UploadIntegrationTest.CSV);
            }
            h.runtime.getWorker().runOnce();
            assertThat(UploadIntegrationTest.jobs(h).getFirst().getValueString("status")).isEqualTo("SUCCEEDED");
            try(var output = sftp.write("/acme/inbox/message.txt"))
            {
               output.write("hello partner".getBytes(StandardCharsets.UTF_8));
            }
            h.runtime.getWorker().runOnce();
            assertThat(accepted.endpointName()).isEqualTo("inbox");
            assertThat(accepted.remoteAddress()).isEqualTo("127.0.0.1");
            assertThat(acceptedBytes).isEqualTo("hello partner".getBytes(StandardCharsets.UTF_8));
         }
      }
   }

   @Test
   void endpointValidationAndView() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var user = h.account("alice", "A");
         for(String path : List.of("relative", "/../escape", "/UPPER", "/a//b", "/a/./b", "/a/", "/testapp/in"))
         {
            assertThatThrownBy(() -> alias(h, "invalid", path, "INBOX", user)).isInstanceOf(Exception.class);
         }
         var mount = alias(h, "good", "/good", "INBOX", user);
         assertThatThrownBy(() -> h.audit.update(FileGatewayTables.ALIAS, new QRecord().withValue("id", mount.getValue("id")).withValue("path", "/testapp"))).isInstanceOf(Exception.class);
         assertThatThrownBy(() -> alias(h, "duplicate", "/good", "INBOX", user)).isInstanceOf(Exception.class);
         h.seedRows(2);
         var saved = h.saved("savedView", "alpha", "Daily Rows");
         var view = alias(h, "view", "/download", "VIEW", user);
         h.audit.update(FileGatewayTables.ALIAS, new QRecord().withValue("id", view.getValue("id")).withValue("tableName", "alpha")
            .withValue("savedViewId", saved.getValue("id")).withValue("format", "csv"));
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(user, key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            assertThat(names(sftp, "/download")).containsExactly("daily-rows.csv");
            assertThat(read(sftp, "/download/daily-rows.csv")).isEqualTo(read(sftp, "/testapp/alpha/views/daily-rows.csv"));
         }
      }
   }

   @Test
   void outboxLifecycleAndRetry() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         downloaded = 0;
         failCallback = false;
         FileGatewayConsumersMetaData.ofOrWithNew(h.instance).withConsumer(new QCodeReference(Consumer.class));
         var account = h.account("alice", "A");
         alias(h, "out", "/out", "OUTBOX", account);
         alias(h, "in", "/in", "INBOX", account);
         assertThatThrownBy(() -> FileGatewayOutbox.put("missing", "x", new ByteArrayInputStream(new byte[0]), 0)).isInstanceOf(Exception.class);
         assertThatThrownBy(() -> FileGatewayOutbox.put("in", "x", new ByteArrayInputStream(new byte[0]), 0)).isInstanceOf(Exception.class);
         byte[] bytes = new byte[200000];
         new java.util.Random(7).nextBytes(bytes);
         var ref = FileGatewayOutbox.put("out", "reply.bin", new ByteArrayInputStream(bytes), bytes.length);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(account, key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            assertThat(names(sftp, "/out")).containsExactly("reply.bin");
            try(var handle = sftp.open("/out/reply.bin"))
            {
               byte[] partial = new byte[100];
               for(int i = 0; i < 5; i++)
               {
                  sftp.read(handle, 0, partial);
               }
            }
            assertThat(downloaded).isZero();
            assertThat(read(sftp, "/out/reply.bin")).isEqualTo(bytes);
            assertThat(downloaded).isEqualTo(1);
            var row = h.audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("id", ref.outboxFileId())).getFirst();
            assertThat(row.getValueString("status")).isEqualTo("DOWNLOADED");
            assertThat(row.getValue("firstDownloadedAt")).isNotNull();
            assertThat(row.getValueInteger("downloadCount")).isEqualTo(1);
            read(sftp, "/out/reply.bin");
            assertThat(downloaded).isEqualTo(1);
            sftp.remove("/out/reply.bin");
            assertThat(names(sftp, "/out")).isEmpty();
            assertThat(h.audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("id", ref.outboxFileId())).getFirst().getValueString("status")).isEqualTo("REMOVED");
            failCallback = true;
            FileGatewayOutbox.put("out", "retry.bin", new ByteArrayInputStream(bytes), bytes.length);
            read(sftp, "/out/retry.bin");
            assertThat(downloaded).isEqualTo(1);
            failCallback = false;
            GatewayMaintenance.maintain(h.instance, h.config, h.store, Clock.systemUTC());
            assertThat(downloaded).isEqualTo(2);
         }
      }
   }

   @Test
   void outboxWithoutRunningSftpAndHomeAndIntegrations() throws Exception
   {
      try(var h = new FileGatewayTestServer(Clock.systemUTC(), instance -> { }, 0, false, false))
      {
         assertThat(FileGatewayOutbox.isAvailable()).isTrue();
         FileGatewayOutbox.setProvider(new OutboxService(h.instance, h.store, h.config, Clock.systemUTC()));
         var account = h.account("partner", "A");
         alias(h, "out", "/out", "OUTBOX", account);
         FileGatewayOutbox.put("out", "file.txt", new ByteArrayInputStream(new byte[]{1}), 1);
         Instant now = Instant.now();
         h.seedTransfer(account, "UPLOAD", 2097152L, now);
         h.seedTransfer(account, "DOWNLOAD", 1048576L, now);
         h.audit.insert(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("accountId", account.getValue("id")).withValue("fileName", "orders.csv")
            .withValue("status", "FAILED").withValue("finishedAt", now).withValue("errorMessage", "Invalid quantity"));
         QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.view")));
         FileGatewayHome home = new FileGatewayHome();
         assertThat(((Number) ((StatisticsData) home.data("transfers", now)).getCount()).intValue()).isEqualTo(2);
         assertThat(((Number) ((StatisticsData) home.data("in", now)).getCount()).doubleValue()).isEqualTo(2);
         assertThat(((Number) ((StatisticsData) home.data("out", now)).getCount()).doubleValue()).isEqualTo(1);
         assertThat(((Number) ((StatisticsData) home.data("failures", now)).getCount()).intValue()).isEqualTo(1);
         assertThat(((TableData) home.data("recent", now)).getRows()).hasSize(1);
         assertThat(((TableData) home.data("partners", now)).getRows()).hasSize(1);
         for(String widget : FileGatewayHome.KEYS)
         {
            assertThat(home.data(widget, now).getType()).isIn("statistics", "table");
         }
         assertThat(h.instance.getWidgets().values()).allSatisfy(widget -> assertThat(widget.getType()).isNotEqualTo("html"));
         assertThat(FileGatewayIntegrations.esbTableNames()).containsExactly(FileGatewayTables.ACCOUNT, FileGatewayTables.ALIAS, FileGatewayTables.UPLOAD_JOB);
         assertThat(FileGatewayIntegrations.apiTableNames()).containsExactlyElementsOf(FileGatewayTables.ALL);
         assertThat(FileGatewayIntegrations.quickSearchTables()).hasSize(3);
      }
   }
}
