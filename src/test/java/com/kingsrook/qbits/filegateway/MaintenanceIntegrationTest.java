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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.filegateway.maintenance.GatewayMaintenance;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Retention and administrative effects operate on durable shared database state.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class MaintenanceIntegrationTest
{
   @Test
   void everyRetentionRuleAndFreshRowsSurvive() throws Exception
   {
      Instant now = Instant.parse("2026-10-13T12:00:00Z");
      try(var h = new FileGatewayTestServer(Clock.fixed(now, ZoneOffset.UTC)))
      {
         var account = h.account("alice", "A");
         for(String status : List.of("SUCCEEDED", "RECEIVING", "RUNNING", "QUEUED"))
         {
            String key = "retention/" + status;
            h.store.put(key, new java.io.ByteArrayInputStream(new byte[]{1}));
            h.store.put(key + "-results", new java.io.ByteArrayInputStream(new byte[]{2}));
            h.store.put(key + "-plain", new java.io.ByteArrayInputStream(new byte[]{3}));
            var job = h.audit.insert(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("accountId", account.getValue("id"))
               .withValue("status", status).withValue("fileName", status)
               .withValue("storageKey", key).withValue("resultsStorageKey", key + "-results").withValue("plaintextStorageKey", key + "-plain"));
            backdate(h, FileGatewayTables.UPLOAD_JOB, job, now.minusSeconds(100 * 86400L));
         }
         for(String table : List.of(FileGatewayTables.TRANSFER_LOG, FileGatewayTables.CONNECTION_LOG))
         {
            backdate(h, table, h.audit.insert(table, new QRecord().withValue("lastSeenAt", now.minusSeconds(181 * 86400L))), now.minusSeconds(181 * 86400L));
            backdate(h, table, h.audit.insert(table, new QRecord().withValue("lastSeenAt", now)), now);
         }
         h.audit.insert(FileGatewayTables.SERVER, new QRecord().withValue("podName", "dead-pod").withValue("lastSeenAt", now.minusSeconds(86401)));
         var alias = PhaseEIntegrationTest.alias(h, "out", "/out", "OUTBOX", account);
         h.store.put("outbox/old", new java.io.ByteArrayInputStream(new byte[]{4}));
         var out = h.audit.insert(FileGatewayTables.OUTBOX_FILE, new QRecord().withValue("aliasId", alias.getValue("id")).withValue("status", "READY")
            .withValue("storageKey", "outbox/old"));
         backdate(h, FileGatewayTables.OUTBOX_FILE, out, now.minusSeconds(31 * 86400L));
         GatewayMaintenance.maintain(h.instance, h.config, h.store, Clock.fixed(now, ZoneOffset.UTC));
         var jobs = UploadIntegrationTest.jobs(h);
         assertThat(jobs).anySatisfy(row ->
         {
            assertThat(row.getValueString("status")).isEqualTo("ABORTED");
            assertThat(row.getValue("storageKey")).isNull();
         });
         assertThat(h.store.objects).doesNotContainKeys("retention/SUCCEEDED", "retention/SUCCEEDED-results", "retention/SUCCEEDED-plain", "retention/RECEIVING", "outbox/old")
            .containsKeys("retention/RUNNING", "retention/QUEUED");
         assertThat(h.audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("id", out.getValue("id"))).getFirst().getValueString("status")).isEqualTo("EXPIRED");
         assertThat(h.audit.query(FileGatewayTables.TRANSFER_LOG, new QQueryFilter())).hasSize(1);
         assertThat(h.audit.query(FileGatewayTables.CONNECTION_LOG, new QQueryFilter())).hasSize(1);
         assertThat(h.audit.query(FileGatewayTables.SERVER, new QQueryFilter())).hasSize(1);
         GatewayMaintenance.maintain(h.instance, h.config, h.store, Clock.fixed(now, ZoneOffset.UTC));
         assertThat(GatewayMaintenance.metadata(h.config.withSchedulerName("scheduler")).getSchedule().getRepeatSeconds()).isEqualTo(86400);
      }
   }

   /*******************************************************************************
    ** Inserts stamp createDate with the real time; move a row's createDate after.
    *******************************************************************************/
   private static void backdate(FileGatewayTestServer h, String table, QRecord row, Instant createDate) throws Exception
   {
      h.audit.update(table, new QRecord().withValue("id", row.getValue("id")).withValue("createDate", createDate));
      assertThat(h.audit.query(table, UploadJobs.equal("id", row.getValue("id"))).getFirst().getValueInstant("createDate")).isEqualTo(createDate);
   }



   @Test
   void reviewResultAndLiveRevocation() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var account = h.account("alice", "A");
         var keyPair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var key = h.key(account, keyPair);
         QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.admin")));
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("lockedUntil", Instant.now().plusSeconds(600)).withValue("failedLoginCount", 5));
         apply(h, FileGatewayProcesses.UNLOCK, account);
         var unlocked = h.audit.query(FileGatewayTables.ACCOUNT, UploadJobs.equal("id", account.getValue("id"))).getFirst();
         assertThat(unlocked.getValue("lockedUntil")).isNull();
         assertThat(unlocked.getValueInteger("failedLoginCount")).isZero();
         try(var ssh = h.login("alice", keyPair))
         {
            var row = h.audit.query(FileGatewayTables.CONNECTION_LOG, UploadJobs.equal("accountId", account.getValue("id"))).getFirst();
            apply(h, FileGatewayProcesses.END_SESSION, row);
            assertThat(h.audit.query(FileGatewayTables.CONNECTION_LOG, UploadJobs.equal("id", row.getValue("id"))).getFirst().getValue("disconnectedAt")).isNull();
            h.runtime.getProtocol().getAuthenticator().poll();
            ssh.waitFor(java.util.EnumSet.of(org.apache.sshd.client.session.ClientSession.ClientSessionEvent.CLOSED), java.time.Duration.ofSeconds(5));
            assertThat(ssh.isClosed()).isTrue();
         }
         try(var ssh = h.login("alice", keyPair))
         {
            apply(h, FileGatewayProcesses.DISABLE, account);
            h.runtime.getProtocol().getAuthenticator().poll();
            ssh.waitFor(java.util.EnumSet.of(org.apache.sshd.client.session.ClientSession.ClientSessionEvent.CLOSED), java.time.Duration.ofSeconds(5));
            assertThat(ssh.isClosed()).isTrue();
         }
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("status", "ACTIVE"));
         apply(h, FileGatewayProcesses.REVOKE, key);
         assertThatThrownBy(() -> h.login("alice", keyPair)).isInstanceOf(Exception.class);
      }
   }

   private void apply(FileGatewayTestServer h, String action, QRecord row) throws Exception
   {
      QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.admin")));
      var input = new RunBackendStepInput().withProcessName(action).withRecords(List.of(row));
      var review = new FileGatewayProcesses.Review();
      var preview = new RunBackendStepOutput();
      review.runOnePage(input, preview);
      assertThat(review.getProcessSummary(preview, false)).hasSize(1);
      assertThat(review.getProcessSummary(preview, true)).hasSize(1);
      var result = new RunBackendStepOutput();
      new FileGatewayProcesses.Apply().runOnePage(input.withRecords(preview.getRecords()), result);
      assertThat(result.getRecords()).hasSize(1);
   }
}
