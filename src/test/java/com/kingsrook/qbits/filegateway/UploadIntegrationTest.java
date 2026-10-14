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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.kingsrook.qbits.filegateway.api.FileAcceptance;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumersMetaData;
import com.kingsrook.qbits.filegateway.api.FileGatewayScannerInterface;
import com.kingsrook.qbits.filegateway.api.InboundFile;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses;
import com.kingsrook.qbits.filegateway.upload.UploadJobWorker;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
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

@ExtendWith(NoDiskExtension.class)
/*******************************************************************************
 ** Test fixture operation.
 *******************************************************************************/
class UploadIntegrationTest
{
   static final String FOLDER = "/testapp/alpha/upload/orders-in/";
   static final byte[] CSV = "name\nAlice\nBob\n".getBytes(StandardCharsets.UTF_8);

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   static QRecord profile(FileGatewayTestServer h) throws Exception
   {
      QRecord profile = h.saved("savedBulkLoadProfile", "alpha", "Orders In");
      h.audit.system(() -> new UpdateAction().execute(new UpdateInput("savedBulkLoadProfile").withRecords(List.of(new QRecord()
         .withValue("id", profile.getValue("id")).withValue("mappingJson", "{\"version\":\"v1\",\"layout\":\"FLAT\",\"hasHeaderRow\":true,\"fieldList\":[{\"fieldName\":\"name\",\"headerName\":\"name\",\"columnIndex\":null,\"defaultValue\":null}]}")))));
      return (profile);
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void bulkInsertAndArtifacts() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.key(account, key);
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("notifyOnSuccess", true));
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            try(var out = sftp.write(FOLDER + "orders.csv"))
            {
               out.write(CSV);
            }
            var jobs = h.audit.query("file_gateway_upload_job", new QQueryFilter());
            assertThat(jobs).hasSize(1);
            assertThat(jobs.getFirst().getValueString("status")).isEqualTo("QUEUED");
            assertThat(h.runtime.getWorker().runOnce()).isTrue();
            QRecord job = h.audit.query("file_gateway_upload_job", new QQueryFilter()).getFirst();
            assertThat(job.getValueString("status")).withFailMessage("Job values: %s", job.getValues()).isEqualTo("SUCCEEDED");
            var rows = h.audit.system(() -> new QueryAction().execute(new QueryInput("alpha")).getRecords());
            assertThat(rows).hasSize(2);
            assertThat(TestAlertSink.notifications).hasSize(1);
            assertThat(TestAlertSink.notifications.getFirst().getAudience().getValues()).containsExactly("A");
            var audits = h.audit.system(() -> new QueryAction().execute(new QueryInput("audit")).getRecords());
            assertThat(audits).anySatisfy(row -> assertThat(row.getValueString("clientChannel")).withFailMessage("%s", row.getValues()).startsWith("sftp/"));
            assertThat(job.getValueInteger("insertedCount")).isEqualTo(2);
            try(var in = sftp.read(FOLDER + "orders.csv.done"))
            {
               assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("2", "SUCCEEDED");
            }
            String date = job.getValueInstant("createDate").atZone(ZoneOffset.UTC).toLocalDate().toString();
            try(var in = sftp.read("/testapp/alpha/archive/" + date + "/" + job.getValue("id") + "-orders.csv"))
            {
               assertThat(in.readAllBytes()).isEqualTo(CSV);
            }
            try(var in = sftp.read("/testapp/alpha/results/" + job.getValue("id") + "-orders.csv.results.csv"))
            {
               assertThat(in.readAllBytes()).isEqualTo(h.store.objects.get(job.getValueString("resultsStorageKey")));
            }
         }
      }
   }

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   static List<QRecord> jobs(FileGatewayTestServer h) throws Exception
   {
      return (h.audit.query("file_gateway_upload_job", new QQueryFilter()));
   }

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   static void put(SftpClient sftp, String name, byte[] bytes) throws Exception
   {
      try(var out = sftp.write(FOLDER + name))
      {
         out.write(bytes);
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void temporaryRenameDedupeAndAcknowledgement() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            put(sftp, "orders.csv.filepart", CSV);
            assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("RECEIVING");
            assertThat(h.runtime.getWorker().runOnce()).isFalse();
            sftp.rename(FOLDER + "orders.csv.filepart", FOLDER + "orders.csv");
            assertThat(jobs(h)).hasSize(1);
            assertThat(h.runtime.getWorker().runOnce()).isTrue();
            assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("SUCCEEDED");
            put(sftp, "orders.csv", CSV);
            assertThat(jobs(h)).hasSize(2);
            assertThat(jobs(h).getLast().getValueString("status")).isEqualTo("DUPLICATE");
            assertThat(h.runtime.getWorker().runOnce()).isFalse();
            try(var in = sftp.read(FOLDER + "orders.csv.done"))
            {
               assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("Skipped");
            }
            assertThat(h.audit.system(() -> new QueryAction().execute(new QueryInput("alpha")).getRecords())).hasSize(2);
            sftp.remove(FOLDER + "orders.csv.done");
            assertThat(jobs(h).getLast().getValue("acknowledgedAt")).isNotNull();
            String result = "/testapp/alpha/results/" + jobs(h).getFirst().getValue("id") + "-orders.csv.results.csv";
            sftp.remove(result);
            assertThatThrownBy(() -> sftp.stat(result)).isInstanceOf(Exception.class);
         }
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void badRowAndMissingValueMappings() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var profile = profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            h.audit.system(() -> new UpdateAction().execute(new UpdateInput("savedBulkLoadProfile").withRecords(List.of(new QRecord()
               .withValue("id", profile.getValue("id")).withValue("mappingJson", "{\"version\":\"v1\",\"layout\":\"FLAT\",\"hasHeaderRow\":true,\"fieldList\":[{\"fieldName\":\"name\",\"headerName\":\"name\",\"columnIndex\":null,\"defaultValue\":null},{\"fieldName\":\"quantity\",\"headerName\":\"quantity\",\"columnIndex\":null,\"defaultValue\":null}]}")))));
            put(sftp, "bad.csv", "name,quantity\nAlice,1\nBob,bad\n".getBytes(StandardCharsets.UTF_8));
            h.runtime.getWorker().runOnce();
            var job = jobs(h).getFirst();
            assertThat(job.getValueString("status")).withFailMessage("%s", job.getValues()).isEqualTo("PARTIAL");
            assertThat(job.getValueInteger("insertedCount")).isEqualTo(1);
            assertThat(job.getValueInteger("errorCount")).isEqualTo(1);
            assertThat(new String(h.store.objects.get(job.getValueString("resultsStorageKey")), StandardCharsets.UTF_8)).contains("Quantity", "3");
            assertThat(TestAlertSink.notifications).hasSize(1);
            h.audit.system(() -> new UpdateAction().execute(new UpdateInput("savedBulkLoadProfile").withRecords(List.of(new QRecord()
               .withValue("id", profile.getValue("id")).withValue("mappingJson", "{\"version\":\"v1\",\"layout\":\"FLAT\",\"hasHeaderRow\":true,\"fieldList\":[{\"fieldName\":\"name\",\"doValueMapping\":true}]}")))));
            put(sftp, "mapping.csv", CSV);
            h.runtime.getWorker().runOnce();
            assertThat(jobs(h).getLast().getValueString("status")).isEqualTo("FAILED");
            assertThat(jobs(h).getLast().getValueString("errorMessage")).contains("this profile needs value mappings saved in it");
         }
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void fileAndDailyLimitsDeletePartialObjects() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.key(account, key);
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("maxFileSizeMb", 1));
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            assertThatThrownBy(() -> put(sftp, "large.csv", new byte[1024 * 1024 + 1])).isInstanceOf(Exception.class);
            var job = jobs(h).getFirst();
            assertThat(job.getValueString("status")).isEqualTo("REJECTED");
            assertThat(h.store.objects).doesNotContainKey(job.getValueString("storageKey"));
            assertThat(h.audit.query("file_gateway_transfer_log", new QQueryFilter())).anySatisfy(log -> assertThat(log.getValueString("outcome")).isEqualTo("LIMIT"));
            h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("maxUploadMbPerDay", 1));
            assertThatThrownBy(() -> put(sftp, "quota.csv", new byte[1024 * 1024])).isInstanceOf(Exception.class);
            assertThat(jobs(h).getLast().getValueString("errorMessage")).contains("Daily upload quota");
         }
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void permissionDenialCreatesNoJob() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         FileGatewayTestServer.GRANTS.put("A", Set.of("file_gateway.connect", "alpha.read"));
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            var names = new ArrayList<String>();
            for(var entry : sftp.readDir("/testapp/alpha"))
            {
               names.add(entry.getFilename());
            }
            assertThat(names).doesNotContain("upload");
            assertThatThrownBy(() -> put(sftp, "denied.csv", CSV)).isInstanceOf(Exception.class);
            assertThat(jobs(h)).isEmpty();
         }
      }
   }

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public static class Scanner implements FileGatewayScannerInterface
   {
      static Result next;
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public Result scan(InboundFile file) throws Exception
      {
         try(var in = file.openStream())
         {
            assertThat(in.readAllBytes()).isEqualTo(CSV);
         }
         return (next);
      }
   }

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public static class Consumer implements FileGatewayConsumer
   {
      static int count;
      static FileAcceptance next;
      static InboundFile received;
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public String getName()
      {
         return ("test-consumer");
      }
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public FileAcceptance accept(InboundFile file) throws Exception
      {
         count++;
         received = file;
         assertThat(QContext.getQSession().getClientChannel()).startsWith("sftp/");
         assertThat(QContext.getQSession().hasPermission("alpha.insert")).isTrue();
         try(var in = file.openStream())
         {
            assertThat(in.readAllBytes()).isEqualTo(CSV);
         }
         return (next);
      }
   }

   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public static class MutableClock extends Clock
   {
      Instant now = Instant.parse("2026-10-14T03:00:00Z");
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public Instant instant()
      {
         return (now);
      }
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public ZoneId getZone()
      {
         return (ZoneOffset.UTC);
      }
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      public Clock withZone(ZoneId zone)
      {
         return (this);
      }
      /*******************************************************************************
       ** Test fixture operation.
       *******************************************************************************/
      void advance(Duration by)
      {
         now = now.plus(by);
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void scanAndConsumerRetriesAfterDisconnect() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         profile(h);
         h.config.withScanner(new QCodeReference(Scanner.class));
         FileGatewayConsumersMetaData.ofOrWithNew(h.instance).withConsumer(new QCodeReference(Consumer.class));
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.key(account, key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            put(sftp, "in.csv", CSV);
         }
         QRecord job = jobs(h).getFirst();
         h.audit.update("file_gateway_upload_job", new QRecord().withValue("id", job.getValue("id")).withValue("consumerName", "test-consumer"));
         Scanner.next = FileGatewayScannerInterface.Result.ERROR;
         Consumer.count = 0;
         h.runtime.getWorker().runOnce();
         assertThat(jobs(h).getFirst().getValueInstant("nextAttemptAt")).isEqualTo(clock.instant().plusSeconds(60));
         assertThat(h.runtime.getWorker().runOnce()).isFalse();
         clock.advance(Duration.ofMinutes(1));
         Scanner.next = FileGatewayScannerInterface.Result.CLEAN;
         Consumer.next = FileAcceptance.retryLater("Busy", Duration.ofSeconds(2));
         h.runtime.getWorker().runOnce();
         assertThat(Consumer.count).isEqualTo(1);
         assertThat(Consumer.received.sha256()).isEqualTo(job.getValueString("sha256"));
         assertThat(Consumer.received.remoteAddress()).isEqualTo("127.0.0.1");
         assertThat(Consumer.received.username()).isEqualTo("alice");
         assertThat(Consumer.received.endpointName()).isNull();
         assertThat(jobs(h).getFirst().getValueInstant("nextAttemptAt")).isEqualTo(clock.instant().plusSeconds(300));
         clock.advance(Duration.ofMinutes(5));
         Consumer.next = FileAcceptance.accepted("accepted-123");
         h.runtime.getWorker().runOnce();
         assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("SUCCEEDED");
         assertThat(jobs(h).getFirst().getValueString("consumerReference")).isEqualTo("accepted-123");
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void quarantineUnknownConsumerAndRetryAction() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            put(sftp, "infected.csv", CSV);
            String original = jobs(h).getFirst().getValueString("storageKey");
            h.config.withScanner(new QCodeReference(Scanner.class));
            Scanner.next = FileGatewayScannerInterface.Result.INFECTED;
            h.runtime.getWorker().runOnce();
            var rejected = jobs(h).getFirst();
            assertThat(rejected.getValueString("status")).isEqualTo("REJECTED");
            assertThat(h.store.objects).doesNotContainKey(original);
            assertThat(h.store.objects.get(rejected.getValueString("storageKey"))).isEqualTo(CSV);
            assertThat(rejected.getValueString("storageKey")).startsWith("file-gateway/quarantine/");
            h.config.withScanner(null);
            put(sftp, "unknown.csv", CSV);
            var job = jobs(h).getLast();
            h.audit.update("file_gateway_upload_job", new QRecord().withValue("id", job.getValue("id")).withValue("consumerName", "missing"));
            h.runtime.getWorker().runOnce();
            job = jobs(h).getLast();
            assertThat(job.getValueString("status")).isEqualTo("REJECTED");
            assertThat(job.getValueString("errorMessage")).contains("Unknown consumer");
            QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.admin")));
            var input = new RunBackendStepInput().withProcessName(FileGatewayProcesses.RETRY_UPLOAD).withRecords(List.of(job));
            var review = new FileGatewayProcesses.Review();
            var output = new RunBackendStepOutput();
            review.runOnePage(input, output);
            assertThat(review.getProcessSummary(output, false)).hasSize(1);
            new FileGatewayProcesses.Apply().runOnePage(input, new RunBackendStepOutput());
            assertThat(jobs(h).getLast().getValueString("status")).isEqualTo("QUEUED");
            assertThat(jobs(h).getLast().getValueInteger("attempts")).isZero();
         }
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void exclusiveClaimsStaleRecoveryAndStop() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            put(sftp, "claim.csv", CSV);
         }
         var job = jobs(h).getFirst();
         var first = new UploadJobWorker(h.instance, h.runtime.getInvoker(), h.config, h.store, clock, "pod-a");
         var second = new UploadJobWorker(h.instance, h.runtime.getInvoker(), h.config, h.store, clock, "pod-b");
         var a = CompletableFuture.supplyAsync(() ->
         {
            try
            {
               return (first.claim(job));
            }
            catch(Exception e)
            {
               throw new RuntimeException(e);
            }
         }
         );
         var b = CompletableFuture.supplyAsync(() ->
         {
            try
            {
               return (second.claim(job));
            }
            catch(Exception e)
            {
               throw new RuntimeException(e);
            }
         }
         );
         assertThat((a.get() == null ? 0 : 1) + (b.get() == null ? 0 : 1)).isEqualTo(1);
         clock.advance(Duration.ofMinutes(31));
         second.requeueStale();
         assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("QUEUED");
         assertThat(first.claim(jobs(h).getFirst())).isNotNull();
         first.close();
         assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("QUEUED");
         assertThat(first.runOnce()).isFalse();
         second.close();
      }
   }


   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void reorderedWritesRangesAndMissingBlocks() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var ssh = h.login("alice", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            try(var handle = sftp.open(FOLDER + "reordered.csv", SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate))
            {
               sftp.write(handle, 5, CSV, 5, CSV.length - 5);
               sftp.write(handle, 0, CSV, 0, 5);
            }
            assertThat(h.store.objects.get(jobs(h).getFirst().getValueString("storageKey"))).isEqualTo(CSV);
            h.runtime.getWorker().runOnce();
            var job = jobs(h).getFirst();
            String date = job.getValueInstant("createDate").atZone(ZoneOffset.UTC).toLocalDate().toString();
            String path = "/testapp/alpha/archive/" + date + "/" + job.getValue("id") + "-reordered.csv";
            assertThat(sftp.stat(path).getSize()).isEqualTo(CSV.length);
            byte[] part = new byte[4];
            try(var handle = sftp.open(path, SftpClient.OpenMode.Read))
            {
               assertThat(sftp.read(handle, 6, part)).isEqualTo(4);
               assertThat(part).isEqualTo(java.util.Arrays.copyOfRange(CSV, 6, 10));
            }
            assertThat(h.audit.query("file_gateway_transfer_log", new QQueryFilter())).anySatisfy(log ->
            {
               assertThat(log.getValueString("operation")).isEqualTo("DOWNLOAD");
               assertThat(log.getValueLong("bytes")).isEqualTo(4);
            });
            var hole = sftp.open(FOLDER + "hole.csv", SftpClient.OpenMode.Write, SftpClient.OpenMode.Create);
            sftp.write(hole, 5, CSV, 5, CSV.length - 5);
            assertThatThrownBy(hole::close).isInstanceOf(Exception.class);
            assertThat(jobs(h).getLast().getValueString("status")).isEqualTo("ABORTED");
            assertThat(h.store.objects).doesNotContainKey(jobs(h).getLast().getValueString("storageKey"));
            h.config.withUploadReorderWindowMb(1);
            try(var far = sftp.open(FOLDER + "far.csv", SftpClient.OpenMode.Write, SftpClient.OpenMode.Create))
            {
               assertThatThrownBy(() -> sftp.write(far, 2 * 1024 * 1024, CSV, 0, 1)).isInstanceOf(Exception.class);
            }
            assertThat(jobs(h).getLast().getValueString("status")).isEqualTo("ABORTED");
         }
      }
   }

   /*******************************************************************************
    ** Verify the upload contract through the real SFTP stack.
    *******************************************************************************/
   @Test
   void retriesExhaustAndServiceOwnerIsNotified() throws Exception
   {
      var clock = new MutableClock();
      try(var h = new FileGatewayTestServer(clock))
      {
         profile(h);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("service", "A");
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id"))
            .withValue("accountType", "SERVICE").withValue("ownerUserId", "OWNER").withValue("rootMode", "FULL_TREE"));
         h.key(account, key);
         try(var ssh = h.login("service", key); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            put(sftp, "retry.csv", CSV);
         }
         FileGatewayConsumersMetaData.ofOrWithNew(h.instance).withConsumer(new QCodeReference(Consumer.class));
         Consumer.next = FileAcceptance.retryLater("Busy", null);
         Consumer.count = 0;
         h.audit.update("file_gateway_upload_job", new QRecord().withValue("id", jobs(h).getFirst().getValue("id")).withValue("consumerName", "test-consumer"));
         long[] delays = {60, 300, 900, 3600};
         for(int attempt = 0; attempt < 5; attempt++)
         {
            assertThat(h.runtime.getWorker().runOnce()).isTrue();
            if(attempt < 4)
            {
               assertThat(jobs(h).getFirst().getValueInstant("nextAttemptAt")).isEqualTo(clock.instant().plusSeconds(delays[attempt]));
               clock.advance(Duration.ofSeconds(delays[attempt]));
            }
         }
         assertThat(jobs(h).getFirst().getValueString("status")).isEqualTo("FAILED");
         assertThat(Consumer.count).isEqualTo(5);
         assertThat(TestAlertSink.notifications).hasSize(1);
         assertThat(TestAlertSink.notifications.getFirst().getAudience().getValues()).containsExactly("OWNER");
         assertThat(TestAlertSink.notifications.getFirst().getLink().getTableName()).isEqualTo("file_gateway_upload_job");
         assertThat(h.runtime.getWorker().runOnce()).isFalse();
      }
   }

}
