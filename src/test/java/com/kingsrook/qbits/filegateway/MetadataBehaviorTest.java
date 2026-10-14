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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Real database customizers and strict UI API dependent pickers.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class MetadataBehaviorTest
{
   /*******************************************************************************
    ** Every contingent picker excludes records for the other table via the UI API.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"savedBulkLoadProfile", "savedView", "savedReport"})
   void contingentPickerBehavior(String source) throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var own = h.saved(source, "alpha", "Alpha choice");
         var other = h.saved(source, "beta", "Beta choice");
         assertThat(own.getErrors()).isEmpty();
         assertThat(other.getErrors()).isEmpty();
         var session = new QSession().withPermissions(Set.of("file_gateway.admin"));
         String token = UiApiInProcessSessions.register(session, java.time.Duration.ofMinutes(1));
         try
         {
            var response = UiApiInvoker.forServer(h.http, "next-ui").invoke(new UiApiRequest().withMethod(HttpMethod.POST)
               .withPath("/table/{tableName}/possibleValues/{fieldName}").withPathParam("tableName", FileGatewayTables.ALIAS)
               .withPathParam("fieldName", source + "Id").withCredentials(UiApiCredentials.ofInProcessSession(token))
               .withJsonBody(Map.of("values", Map.of("tableName", "alpha"))));
            assertThat(response.getStatus()).as(response.getJson().toString()).isEqualTo(200);
            assertThat(response.getJson().toString()).contains("Alpha choice").doesNotContain("Beta choice");
         }
         finally
         {
            UiApiInProcessSessions.revoke(token);
         }
      }
   }



   /*******************************************************************************
    ** Validation runs on insert and partial update, computes metadata, and enforces uniqueness.
    *******************************************************************************/
   @Test
   void publicKeyValidation() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var account = h.account("alice", "A");
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var stored = h.key(account, key);
         assertThat(stored.getValueString("fingerprint")).isEqualTo(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key.getPublic()));
         assertThat(stored.getValueString("keyType")).isEqualTo("ssh-ed25519");
         assertThat(stored.getValueInteger("keyBits")).isEqualTo(256);
         assertThatThrownBy(() -> h.key(account, key)).isInstanceOf(QException.class);
         for(String type : List.of("ssh-rsa", "ssh-dss"))
         {
            var weak = KeyUtils.generateKeyPair(type, type.equals("ssh-rsa") ? 2048 : 1024);
            assertThatThrownBy(() -> h.key(account, weak)).isInstanceOf(QException.class);
         }
         assertThat(h.key(account, KeyUtils.generateKeyPair("ssh-rsa", 3072)).getValueInteger("keyBits")).isEqualTo(3072);
         assertThat(h.key(account, KeyUtils.generateKeyPair("ecdsa-sha2-nistp384", 384)).getValueInteger("keyBits")).isEqualTo(384);
         for(String text : List.of("not a key", "ssh-unknown invalid", ""))
         {
            assertThatThrownBy(() -> h.audit.insert(FileGatewayTables.SSH_KEY, new QRecord().withValue("accountId", account.getValue("id")).withValue("publicKey", text))).isInstanceOf(QException.class);
         }
         h.audit.update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", stored.getValue("id")).withValue("label", "Renamed").withValue("fingerprint", "forged"));
         var read = h.audit.query(FileGatewayTables.SSH_KEY, new QQueryFilter()).getFirst();
         assertThat(read.getValueString("fingerprint")).isEqualTo(stored.getValueString("fingerprint"));
         var weak = KeyUtils.generateKeyPair("ssh-rsa", 2048);
         assertThatThrownBy(() -> h.audit.update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", stored.getValue("id")).withValue("publicKey", PublicKeyEntry.toString(weak.getPublic())))).isInstanceOf(QException.class);
         assertThatThrownBy(() -> h.account("Bad name", "A")).isInstanceOf(QException.class);
         assertThatThrownBy(() -> h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("maxConcurrentSessions", 0))).isInstanceOf(QException.class);
      }
   }



   /*******************************************************************************
    ** People can read logs but cannot write them, including administrators.
    *******************************************************************************/
   @Test
   void permissionsAndAdministrativeActions() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var account = h.account("alice", "A");
         var key = h.key(account, KeyUtils.generateKeyPair("ssh-ed25519", 256));
         var checker = new FileGatewayPermissionChecker();
         QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.view")));
         checker.checkPermissionsThrowing(new QueryInput(FileGatewayTables.ACCOUNT), h.instance.getTable(FileGatewayTables.ACCOUNT));
         assertThatThrownBy(() -> checker.checkPermissionsThrowing(new UpdateInput(FileGatewayTables.ACCOUNT), h.instance.getTable(FileGatewayTables.ACCOUNT))).isInstanceOf(QException.class);
         QContext.init(h.instance, new QSession().withPermissions(Set.of("file_gateway.admin")));
         checker.checkPermissionsThrowing(new InsertInput(FileGatewayTables.ACCOUNT), h.instance.getTable(FileGatewayTables.ACCOUNT));
         for(String table : FileGatewayTables.SYSTEM_WRITTEN)
         {
            assertThatThrownBy(() -> checker.checkPermissionsThrowing(new UpdateInput(table), h.instance.getTable(table))).isInstanceOf(QException.class);
         }
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("lockedUntil", Instant.now().plusSeconds(900)).withValue("failedLoginCount", 5));
         var input = new RunBackendStepInput().withProcessName(FileGatewayProcesses.UNLOCK).withRecords(List.of(account));
         var review = new FileGatewayProcesses.Review();
         var preview = new RunBackendStepOutput();
         review.runOnePage(input, preview);
         assertThat(review.getProcessSummary(preview, false)).hasSize(1);
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter()).getFirst().getValue("lockedUntil")).isNotNull();
         new FileGatewayProcesses.Apply().runOnePage(input, new RunBackendStepOutput());
         var unlocked = h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter()).getFirst();
         assertThat(unlocked.getValue("lockedUntil")).isNull();
         assertThat(unlocked.getValueInteger("failedLoginCount")).isZero();
         new FileGatewayProcesses.Apply().runOnePage(input.withProcessName(FileGatewayProcesses.DISABLE), new RunBackendStepOutput());
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter()).getFirst().getValueString("status")).isEqualTo("DISABLED");
         new FileGatewayProcesses.Apply().runOnePage(input.withProcessName(FileGatewayProcesses.REVOKE).withRecords(List.of(key)), new RunBackendStepOutput());
         assertThat(h.audit.query(FileGatewayTables.SSH_KEY, new QQueryFilter()).getFirst().getValue("revokedAt")).isNotNull();
         QContext.init(h.instance, new QSession());
         assertThatThrownBy(() -> new FileGatewayProcesses.Apply().runOnePage(input, new RunBackendStepOutput())).isInstanceOf(QException.class);
         assertThatThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(FileGatewayTables.ACCOUNT), h.instance.getTable(FileGatewayTables.ACCOUNT))).isInstanceOf(QException.class);
         assertThatThrownBy(() -> h.audit.query("alpha", new QQueryFilter())).isInstanceOf(IllegalArgumentException.class);
      }
   }
}
