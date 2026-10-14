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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.security.FieldSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 ** Real SFTP downloads against strict user-authorized UI exports and report processes.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class DownloadIntegrationTest
{
   private static final String VIEW = """
      {"queryFilter":{"criteria":[{"fieldName":"quantity","operator":"GREATER_THAN","values":[1]}],
      "orderBys":[{"fieldName":"quantity","isAscending":false}],"skip":30,"limit":1},
      "queryColumns":{"columns":[{"name":"__check__","isVisible":true},{"name":"quantity","isVisible":true},
      {"name":"name","isVisible":true},{"name":"id","isVisible":false}]}}
      """;


   /*******************************************************************************
    ** Store the same JSON used by Next UI, including selection and hidden columns.
    *******************************************************************************/
   private QRecord view(FileGatewayTestServer h, String json) throws Exception
   {
      return (h.audit.system(() -> new InsertAction().execute(new InsertInput("savedView").withRecord(new QRecord()
         .withValue("tableName", "alpha").withValue("label", "Selected rows").withValue("userId", "A").withValue("viewJson", json))).getRecords().getFirst()));
   }


   /*******************************************************************************
    ** Read until EOF regardless of the generated file's zero stat size.
    *******************************************************************************/
   private byte[] read(SftpClient client, String path) throws Exception
   {
      try(var input = client.read(path))
      {
         return (input.readAllBytes());
      }
   }


   /*******************************************************************************
    ** Filter, column order, ordering, three formats, hidden tables and transfer identity.
    *******************************************************************************/
   @Test
   void viewFormatsPermissionsAndAudit() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         h.seedRows(3);
         QRecord saved = view(h, VIEW);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("formats", "csv,xlsx,json"));
         h.key(account, key);
         var bob = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("bob", "B"), bob);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session);
             var deniedSession = h.login("bob", bob); var denied = SftpClientFactory.instance().createSftpClient(deniedSession))
         {
            String path = "/testapp/alpha/views/selected-rows.";
            assertThat(client.stat(path + "csv").getSize()).isZero();
            String csv = new String(read(client, path + "csv"), StandardCharsets.UTF_8);
            assertThat(csv.replace("\r\n", "\n")).isEqualTo("\"Quantity\",\"Name\"\n\"3\",\"Row 3 " + "x".repeat(50) + "\"\n\"2\",\"Row 2 " + "x".repeat(50) + "\"\n");
            try(var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(read(client, path + "xlsx"))))
            {
               assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Quantity");
               assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue()).isEqualTo(3);
            }
            var json = new ObjectMapper().readTree(read(client, path + "json"));
            assertThat(json.toString()).contains("Row 3", "Row 2").doesNotContain("Row 1");
            assertThatThrownBy(() -> denied.stat(path + "csv")).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE));
            assertThatThrownBy(() -> read(denied, path + "csv")).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE));
         }
         var downloads = h.audit.query("file_gateway_transfer_log", new QQueryFilter()).stream()
            .filter(r -> "DOWNLOAD".equals(r.getValueString("operation"))).toList();
         assertThat(downloads).hasSize(3).allSatisfy(row ->
         {
            assertThat(row.getValueString("outcome")).isEqualTo("OK");
            assertThat(row.getValueString("objectKind")).isEqualTo("VIEW");
            assertThat(row.getValueString("objectId")).isEqualTo(saved.getValueString("id"));
            assertThat(row.getValueLong("bytes")).isPositive();
            assertThat(row.getValueLong("durationMs")).isNotNegative();
            assertThat(row.getValueString("format")).isIn("csv", "xlsx", "json");
         });
         assertThat(h.calls).allSatisfy(call ->
         {
            assertThat(call.channel()).startsWith("sftp/");
            assertThat(call.invokedBy()).isEqualTo("file-gateway");
         });
      }
   }


   /*******************************************************************************
    ** FieldSecurityLock DENY removes the protected value exactly as a direct user export.
    *******************************************************************************/
   @Test
   void fieldSecurityLockMatchesDirectUserExport() throws Exception
   {
      try(var h = new FileGatewayTestServer(Clock.systemUTC(), instance ->
      {
         instance.setEnforcePermissionsInCoreActions(true);
         instance.addSecurityKeyType(new QSecurityKeyType().withName("privateField").withAllAccessKeyName("privateFieldAll"));
         instance.getTable("alpha").getField("name").setFieldSecurityLock(new FieldSecurityLock()
            .withSecurityKeyType("privateField").withDefaultBehavior(FieldSecurityLock.Behavior.DENY).withOverrideValues(List.of("reader")));
      }))
      {
         h.seedRows(3);
         view(h, "{\"queryFilter\":{}}");
         h.audit.system(() -> new InsertAction().execute(new InsertInput("savedView").withRecord(new QRecord()
            .withValue("tableName", "alpha").withValue("label", "Denied field").withValue("userId", "A").withValue("viewJson", VIEW))));
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         var user = h.audit.system(() -> new QAuthenticationModuleDispatcher().getQModule(h.instance.getAuthentication()).createAutomatedSessionForUser(h.instance, "A"));
         String token = UiApiInProcessSessions.register(user, Duration.ofMinutes(5));
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            var direct = h.runtime.getInvoker().invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/table/{tableName}/export")
               .withPathParam("tableName", "alpha").withCredentials(UiApiCredentials.ofInProcessSession(token)).withClientDetail("direct-comparison")
               .withJsonBody(Map.of("format", "csv", "filter", Map.of(), "includeHeaderRow", true)));
            assertThat(direct.isSuccess()).as(direct.getBody()).isTrue();
            byte[] actual = read(client, "/testapp/alpha/views/selected-rows.csv");
            assertThat(new String(actual, StandardCharsets.UTF_8)).doesNotContain("Row ");
            assertThat(actual).isEqualTo(direct.getBody().getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> read(client, "/testapp/alpha/views/denied-field.csv")).isInstanceOf(SftpException.class);
         }
         finally
         {
            UiApiInProcessSessions.revoke(token);
         }
      }
   }


   /*******************************************************************************
    ** The daily quota excludes prior UTC days, other accounts and non-download operations.
    *******************************************************************************/
   @Test
   void dailyQuotaUsesFilteredUtcHistory() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         h.seedRows(3);
         view(h, "{}");
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("maxDownloadMbPerDay", 1));
         h.seedTransfer(account, "DOWNLOAD", 5_000_000L, Instant.now().minusSeconds(86400));
         h.seedTransfer(account, "UPLOAD", 5_000_000L, Instant.now());
         h.seedTransfer(h.account("other", "A"), "DOWNLOAD", 5_000_000L, Instant.now());
         h.key(account, key);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            assertThat(read(client, "/testapp/alpha/views/selected-rows.csv")).isNotEmpty();
            assertThatThrownBy(() -> read(client, "/testapp/alpha/views/selected-rows.json")).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE));
         }
      }
   }


   /*******************************************************************************
    ** Non-success stream responses preserve their text and distinguish denied transfers.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(ints = {403, 500})
   void apiErrorsAreAudited(int status) throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         view(h, "{}");
         h.exportFailureStatus = status;
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            assertThatThrownBy(() -> read(client, "/testapp/alpha/views/selected-rows.csv"))
               .isInstanceOf(SftpException.class).hasMessageContaining("Export failure test");
         }
         assertThat(h.audit.query("file_gateway_transfer_log", new QQueryFilter())).anySatisfy(row ->
         {
            assertThat(row.getValueString("operation")).isEqualTo("DOWNLOAD");
            assertThat(row.getValueString("outcome")).isEqualTo(status == 403 ? "DENIED" : "ERROR");
            assertThat(row.getValueString("message")).contains("Export failure test");
            assertThat(row.getValueLong("bytes")).isZero();
         });
      }
   }


   /*******************************************************************************
    ** Real renderSavedReport input/execute/output and storage download as the SSH user.
    *******************************************************************************/
   @Test
   void savedReportDownload() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         h.seedRows(3);
         QRecord report = h.audit.system(() -> new InsertAction().execute(new InsertInput("savedReport").withRecord(new QRecord()
            .withValue("tableName", "alpha").withValue("label", "Daily").withValue("userId", "A")
            .withValue("columnsJson", "{\"columns\":[{\"name\":\"quantity\"}]}")
            .withValue("queryFilterJson", "{\"criteria\":[{\"fieldName\":\"quantity\",\"operator\":\"EQUALS\",\"values\":[2]}]}"))).getRecords().getFirst());
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            String csv = new String(read(client, "/testapp/alpha/reports/daily.csv"), StandardCharsets.UTF_8);
            assertThat(csv.replace("\r\n", "\n")).isEqualTo("\"Quantity\"\n\"2\"\n");
         }
         assertThat(h.calls).anySatisfy(call -> assertThat(call.path()).contains("/processes/renderSavedReport/init"))
            .anySatisfy(call -> assertThat(call.path()).contains("/download/"))
            .allSatisfy(call -> assertThat(call.channel()).startsWith("sftp/"));
         assertThat(h.audit.query("file_gateway_transfer_log", new QQueryFilter())).anySatisfy(row ->
         {
            assertThat(row.getValueString("objectKind")).isEqualTo("REPORT");
            assertThat(row.getValueString("objectId")).isEqualTo(report.getValueString("id"));
            assertThat(row.getValueString("outcome")).isEqualTo("OK");
         });
      }
   }


   /*******************************************************************************
    ** Missing configured storage hides the report folder, including guessed paths.
    *******************************************************************************/
   @Test
   void missingReportStorageHidesFolder() throws Exception
   {
      try(var h = new FileGatewayTestServer(Clock.systemUTC(), instance -> instance.getTables().remove("reportStorage")))
      {
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(h.account("alice", "A"), key);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            List<String> names = new ArrayList<>();
            client.readDir("/testapp/alpha").forEach(entry -> names.add(entry.getFilename()));
            assertThat(names).doesNotContain("reports");
            assertThatThrownBy(() -> client.stat("/testapp/alpha/reports")).isInstanceOfSatisfying(SftpException.class,
               e -> assertThat(e.getStatus()).isEqualTo(SftpConstants.SSH_FX_NO_SUCH_FILE));
         }
      }
   }


   /*******************************************************************************
    ** Large exports are consumed with fixed buffers; caps fail during read and audit LIMIT.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"large", "generated", "daily"})
   void streamingAndLimits(String scenario) throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         h.seedRows(50000);
         view(h, "{\"fieldNames\":[\"name\"],\"queryFilter\":{\"orderBys\":[{\"fieldName\":\"id\",\"isAscending\":true}]}}");
         h.config.withDownloadReadAheadMb(1).withMaxGeneratedFileMb(scenario.equals("generated") ? 1 : 20);
         var key = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         var account = h.account("alice", "A");
         if(scenario.equals("daily"))
         {
            h.audit.update("file_gateway_account", new QRecord().withValue("id", account.getValue("id")).withValue("maxDownloadMbPerDay", 1));
            h.audit.insert("file_gateway_transfer_log", new QRecord().withValue("accountId", account.getValue("id"))
               .withValue("operation", "DOWNLOAD").withValue("bytes", 900000L).withValue("outcome", "OK")
               .withValue("path", "/earlier.csv").withValue("clientChannel", "sftp/earlier"));
         }
         h.key(account, key);
         try(var session = h.login("alice", key); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            if(scenario.equals("large"))
            {
               try(var input = client.read("/testapp/alpha/views/selected-rows.csv"))
               {
                  byte[] buffer = new byte[32768];
                  long bytes = 0;
                  int lines = 0;
                  for(int count; (count = input.read(buffer)) >= 0; )
                  {
                     bytes += count;
                     for(int i = 0; i < count; i++)
                     {
                        if(buffer[i] == '\n')
                        {
                           lines++;
                        }
                     }
                  }
                  assertThat(bytes).isGreaterThan(3_000_000);
                  assertThat(lines).isEqualTo(50001);
               }
            }
            else
            {
               assertThatThrownBy(() -> read(client, "/testapp/alpha/views/selected-rows.csv")).isInstanceOf(java.io.IOException.class)
                  .hasMessageContaining("limit");
            }
         }
         assertThat(h.audit.query("file_gateway_transfer_log", new QQueryFilter())).anySatisfy(row ->
         {
            assertThat(row.getValueString("objectKind")).isEqualTo("VIEW");
            assertThat(row.getValueString("outcome")).isEqualTo(scenario.equals("large") ? "OK" : "LIMIT");
            assertThat(row.getValueLong("bytes")).isPositive().isLessThanOrEqualTo(scenario.equals("large") ? 4_000_000L : 1_048_576L);
         });
      }
   }
}
