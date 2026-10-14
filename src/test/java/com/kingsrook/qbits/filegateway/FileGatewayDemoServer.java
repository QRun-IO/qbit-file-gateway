/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2026.  Kingsrook, LLC
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.apache.sshd.common.config.keys.KeyUtils;


/*******************************************************************************
 ** Demo server for screenshots: the full test instance with the Next UI on
 ** port 8099 (or the first argument), MOCK auth granting every permission to
 ** browser sessions, and realistic gateway data. Test scope only.
 *******************************************************************************/
public final class FileGatewayDemoServer
{
   private static final List<String> FILES = List.of("orders-2026-10-13.csv", "inventory.xlsx", "returns.csv", "price-update.csv", "partners.csv", "shipments-week41.csv");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private FileGatewayDemoServer()
   {
   }



   /*******************************************************************************
    ** Start, seed and keep running until killed.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
      FileGatewayTestServer.TestAuthentication.grantAllToUiSessions = true;
      FileGatewayTestServer server = start(port);
      System.out.println("File gateway demo: http://localhost:" + port + "/app/file_gateway");
      Thread.currentThread().join();
      server.close();
   }



   /*******************************************************************************
    ** Start the server and seed demo data.
    *******************************************************************************/
   static FileGatewayTestServer start(int port) throws Exception
   {
      FileGatewayTestServer server = new FileGatewayTestServer(Clock.systemUTC(), instance ->
      {
      }, port, true, true);
      seed(server);
      return (server);
   }



   /*******************************************************************************
    ** Seed accounts, keys, endpoints, jobs, transfers, connections and outbox files.
    *******************************************************************************/
   static void seed(FileGatewayTestServer h) throws Exception
   {
      Random  random = new Random(42);
      Instant now    = Instant.now();

      QRecord acme    = h.audit.insert(FileGatewayTables.ACCOUNT, new QRecord().withValue("username", "acme-orders").withValue("label", "Acme Corp (orders feed)")
         .withValue("accountType", "SERVICE").withValue("userId", "acme-svc").withValue("ownerUserId", "jamie").withValue("allowedCidrs", "203.0.113.0/24"));
      QRecord globex  = h.audit.insert(FileGatewayTables.ACCOUNT, new QRecord().withValue("username", "globex").withValue("label", "Globex Logistics")
         .withValue("accountType", "SERVICE").withValue("userId", "globex-svc").withValue("ownerUserId", "jamie"));
      QRecord jamie   = h.audit.insert(FileGatewayTables.ACCOUNT, new QRecord().withValue("username", "jamie").withValue("label", "Jamie Rivera")
         .withValue("accountType", "USER").withValue("userId", "jamie"));
      h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", globex.getValue("id")).withValue("failedLoginCount", 5)
         .withValue("lockedUntil", now.plus(25, ChronoUnit.MINUTES)));
      List<QRecord> accounts = List.of(acme, globex, jamie);

      for(QRecord account : accounts)
      {
         h.key(account, KeyUtils.generateKeyPair("ssh-ed25519", 256));
      }

      QRecord inbox  = h.audit.insert(FileGatewayTables.ALIAS, new QRecord().withValue("name", "acme-orders-in").withValue("path", "/acme/orders-in")
         .withValue("kind", "INBOX").withValue("consumerName", "bulk-load").withValue("accountId", acme.getValue("id")).withValue("description", "Acme drops nightly order files here"));
      QRecord outbox = h.audit.insert(FileGatewayTables.ALIAS, new QRecord().withValue("name", "globex-acks").withValue("path", "/globex/acks")
         .withValue("kind", "OUTBOX").withValue("accountId", globex.getValue("id")).withValue("description", "Acknowledgements Globex picks up"));
      h.audit.insert(FileGatewayTables.ALIAS, new QRecord().withValue("name", "alpha-daily").withValue("path", "/reports/alpha-daily")
         .withValue("kind", "VIEW").withValue("tableName", "alpha").withValue("format", "csv").withValue("fileName", "alpha-daily.csv"));

      List<String> statuses = List.of("SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "PARTIAL", "FAILED", "REJECTED", "DUPLICATE", "QUEUED", "RUNNING");
      for(int i = 0; i < 40; i++)
      {
         QRecord account  = accounts.get(i % accounts.size());
         String  status   = statuses.get(random.nextInt(statuses.size()));
         String  file     = FILES.get(random.nextInt(FILES.size()));
         Instant finished = now.minus(random.nextInt(7 * 24 * 60), ChronoUnit.MINUTES);
         int     inserted = "FAILED".equals(status) || "REJECTED".equals(status) ? 0 : 50 + random.nextInt(900);
         int     errors   = "PARTIAL".equals(status) ? 1 + random.nextInt(12) : 0;
         h.audit.insert(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("accountId", account.getValue("id"))
            .withValue("aliasId", account == acme ? inbox.getValue("id") : null).withValue("tableName", "alpha").withValue("consumerName", "bulk-load")
            .withValue("path", "/testapp/alpha/upload/orders/" + file).withValue("fileName", file).withValue("sizeBytes", 20_000L + random.nextInt(4_000_000))
            .withValue("sha256", Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong())).withValue("status", status)
            .withValue("insertedCount", inserted).withValue("errorCount", errors).withValue("remoteAddress", "203.0.113." + (10 + random.nextInt(40)))
            .withValue("errorMessage", "FAILED".equals(status) ? "this profile needs value mappings saved in it" : "REJECTED".equals(status) ? "Virus scan: infected" : null)
            .withValue("queuedAt", finished.minusSeconds(40)).withValue("startedAt", finished.minusSeconds(30))
            .withValue("finishedAt", List.of("QUEUED", "RUNNING").contains(status) ? null : finished).withValue("attempts", 1));
      }

      for(int i = 0; i < 60; i++)
      {
         QRecord account = accounts.get(random.nextInt(accounts.size()));
         String  op      = List.of("LIST", "UPLOAD", "DOWNLOAD", "LIST", "DOWNLOAD").get(random.nextInt(5));
         h.seedTransfer(account, op, "LIST".equals(op) ? 0 : 10_000L + random.nextInt(8_000_000), now.minus(random.nextInt(6 * 24 * 60), ChronoUnit.MINUTES));
      }

      for(int i = 0; i < 12; i++)
      {
         QRecord account = accounts.get(i % accounts.size());
         boolean live    = i < 2;
         Instant start   = now.minus(live ? 5 + i * 7 : 60 + random.nextInt(5000), ChronoUnit.MINUTES);
         h.audit.insert(FileGatewayTables.CONNECTION_LOG, new QRecord().withValue("accountId", account.getValue("id")).withValue("username", account.getValueString("username"))
            .withValue("remoteIp", "203.0.113." + (10 + i)).withValue("clientVersion", "SSH-2.0-OpenSSH_9.8").withValue("outcome", i == 5 ? "AUTH_FAILED" : "CONNECTED")
            .withValue("sessionId", "demo-" + i).withValue("podName", "file-gateway-" + (i % 2 == 0 ? "7f9c4-abcde" : "7f9c4-fghij")).withValue("connectedAt", start)
            .withValue("lastSeenAt", live ? now : start.plus(4, ChronoUnit.MINUTES)).withValue("disconnectedAt", live ? null : start.plus(4, ChronoUnit.MINUTES))
            .withValue("bytesIn", (long) random.nextInt(9_000_000)).withValue("bytesOut", (long) random.nextInt(2_000_000)).withValue("operationCount", (long) (3 + random.nextInt(40)))
            .withValue("clientChannel", "sftp"));
      }

      for(String name : List.of("ack-1041.csv", "ack-1042.csv", "ack-1043.csv"))
      {
         h.store.put("file-gateway/outbox/" + outbox.getValue("id") + "/" + name, new java.io.ByteArrayInputStream("order,status\n1041,received\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
         h.audit.insert(FileGatewayTables.OUTBOX_FILE, new QRecord().withValue("aliasId", outbox.getValue("id")).withValue("fileName", name).withValue("sizeBytes", 26L)
            .withValue("storageKey", "file-gateway/outbox/" + outbox.getValue("id") + "/" + name).withValue("status", name.endsWith("3.csv") ? "READY" : "DOWNLOADED"));
      }
   }
}
