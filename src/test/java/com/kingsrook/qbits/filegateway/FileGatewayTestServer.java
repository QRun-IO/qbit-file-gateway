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


import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.TablesPossibleValueSourceMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedbulkloadprofiles.SavedBulkLoadProfileMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedreports.SavedReportsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedviews.SavedViewsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;


/*******************************************************************************
 ** Real strict UI API server, in-memory H2 and real MINA clients for gateway acceptance tests.
 *******************************************************************************/
public class FileGatewayTestServer implements AutoCloseable
{
   public final MemoryGatewayObjectStore store = new MemoryGatewayObjectStore();
   public final QInstance instance = new QInstance();
   public final FileGatewayQBitConfig config = new FileGatewayQBitConfig().withSftpEnabled(true).withSftpPort(0);
   public final AuditWriter audit = new AuditWriter(instance);
   public final QApplicationJavalinServer http;
   public final FileGatewayRuntime runtime;
   public final SshClient client;
   public final java.util.List<ApiCall> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
   public volatile int exportFailureStatus;

   /*******************************************************************************
    ** Non-secret request evidence for the invoker contract assertions.
    *******************************************************************************/
   public record ApiCall(String method, String path, String channel, String invokedBy)
   {
   }
   private final Connection keeper;
   public static final Map<String, Set<String>> GRANTS = new HashMap<>();



   /*******************************************************************************
    ** Start the complete in-memory test stack.
    *******************************************************************************/
   public FileGatewayTestServer() throws Exception
   {
      this(Clock.systemUTC());
   }



   /*******************************************************************************
    ** Controllable time for expiry, session refresh and metadata caching.
    *******************************************************************************/
   public FileGatewayTestServer(Clock clock) throws Exception
   {
      this(clock, instance ->
      {
         // Most acceptance tests use the default application metadata.
      });
   }


   /*******************************************************************************
    ** Customize application metadata before validation and server startup.
    *******************************************************************************/
   public FileGatewayTestServer(Clock clock, java.util.function.Consumer<QInstance> customize) throws Exception
   {
      this(clock, customize, 0, false, true);
   }



   /*******************************************************************************
    ** Demo and web-only tests can serve Next without starting an SFTP listener.
    *******************************************************************************/
   public FileGatewayTestServer(Clock clock, java.util.function.Consumer<QInstance> customize, int port, boolean frontend, boolean sftp) throws Exception
   {
      config.withSftpEnabled(sftp);
      ConnectionManager.resetConnectionProviders();
      GRANTS.clear();
      GRANTS.put("A", Set.of("file_gateway.connect", "alpha.read", "alpha.insert", "beta.read", "beta.insert"));
      GRANTS.put("B", Set.of("file_gateway.connect", "beta.read", "beta.insert"));
      GRANTS.put("NONE", Set.of("beta.read"));
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("test").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(TestAuthentication.class)));
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData().withName("rdbms").withVendor("h2").withHostName("mem")
         .withDatabaseName("fg" + UUID.randomUUID().toString().replace("-", "")).withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE");
      instance.addBackend(backend);
      keeper = new ConnectionManager().getConnection(backend);
      for(String sql : Files.readString(Path.of("docs/ddl/file_gateway.sql")).split(";"))
      {
         if(!sql.isBlank())
         {
            keeper.createStatement().execute(sql);
         }
      }
      for(String name : List.of("alpha", "beta"))
      {
         instance.addTable(new QTableMetaData().withName(name).withLabel(name).withBackendName("rdbms")
            .withPrimaryKeyField("id").withField(new QFieldMetaData("id", QFieldType.INTEGER))
            .withField(new QFieldMetaData("name", QFieldType.STRING).withIsRequired(true))
            .withField(new QFieldMetaData("quantity", QFieldType.INTEGER))
            .withSection(SectionFactory.defaultT1("id", "name", "quantity"))
            .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS).withPermissionBaseName(name)));
      }
      instance.addApp(new QAppMetaData().withName("testapp").withLabel("Test App")
         .withChild(instance.getTable("alpha")).withChild(instance.getTable("beta"))
         .withSection(new QAppSection().withName("data").withLabel("Data").withTables(List.of("alpha", "beta"))));
      instance.addPossibleValueSource(TablesPossibleValueSourceMetaDataProvider.defineTablesPossibleValueSource(instance));
      new SavedBulkLoadProfileMetaDataProvider().defineAll(instance, "rdbms", null);
      new SavedViewsMetaDataProvider().defineAll(instance, "rdbms", null);
      new SavedReportsMetaDataProvider().defineAll(instance, "rdbms", "rdbms", null);
      new FileGatewayQBitProducer().withQBitConfig(config).produce(instance);
      new com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider().withClientChannelField(true).defineAll(instance, "rdbms", null);
      instance.getTable("alpha").withAuditRules(new com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules()
         .withAuditLevel(com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel.RECORD));
      TestAlertSink.notifications.clear();
      com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData.ofOrWithNew(instance).withSink(new QCodeReference(TestAlertSink.class));
      for(QTableMetaData table : instance.getTables().values())
      {
         if(!table.getName().startsWith("file_gateway_"))
         {
            table.withBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
            for(QFieldMetaData field : table.getFields().values())
            {
               field.setBackendName(QInstanceEnricher.inferBackendName(field.getName()));
            }
            createTable(table);
         }
      }
      instance.addBackend(new com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData()
         .withName("memory").withBackendType(com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule.class));
      instance.getTable("reportStorage").setBackendName("memory");
      instance.addTable(new QTableMetaData().withName("testUploadArchive").withBackendName("memory").withPrimaryKeyField("reference")
         .withField(new QFieldMetaData("reference", QFieldType.STRING)).withField(new QFieldMetaData("contents", QFieldType.BLOB)));
      instance.withSupplementalMetaData(new com.kingsrook.qqq.middleware.javalin.QJavalinMetaData().withUploadedFileArchiveTableName("testUploadArchive"));
      customize.accept(instance);
      new QInstanceEnricher(instance).enrich();
      new QInstanceValidator().validate(instance);
      QContext.init(instance, new QSystemUserSession(instance));
      http = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         /*******************************************************************************
          ** Define q instance.
          *******************************************************************************/
         @Override
         public QInstance defineQInstance()
         {
            return (instance);
         }
      }).withPort(port).withServeFrontendMaterialDashboard(false).withServeFrontendNext(frontend)
         .withJavalinConfigCustomizer(j -> j.routes.before(ctx ->
         {
            calls.add(new ApiCall(ctx.method().name(), ctx.path(), ctx.header("X-QQQ-Client"), ctx.header("X-QQQ-Invoked-By")));
            if(exportFailureStatus != 0 && ctx.path().endsWith("/export"))
            {
               throw (new io.javalin.http.HttpResponseException(exportFailureStatus, "Export failure test"));
            }
         }))
         .withStrictUiApiRoutes(true).withMiddlewareVersionList(List.of(new MiddlewareVersionV1()));
      http.start();
      runtime = sftp ? FileGatewayRuntime.startForTest(instance, http, config, KeyUtils.generateKeyPair("ecdsa-sha2-nistp256", 256), clock, Duration.ofHours(1), store) : null;
      if(!sftp)
      {
         com.kingsrook.qbits.filegateway.api.FileGatewayOutbox.setProvider(new com.kingsrook.qbits.filegateway.outbox.OutboxService(instance, store, config, clock));
      }
      client = SshClient.setUpDefaultClient();
      client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
      client.start();
   }


   /*******************************************************************************
    ** Seed many export rows without generating unrelated insert audits.
    *******************************************************************************/
   public void seedRows(int count) throws Exception
   {
      try(var statement = keeper.prepareStatement("INSERT INTO alpha (id, name, quantity) VALUES (?, ?, ?)"))
      {
         for(int i = 1; i <= count; i++)
         {
            statement.setInt(1, i);
            statement.setString(2, "Row " + i + " " + "x".repeat(50));
            statement.setInt(3, i);
            statement.addBatch();
            if(i % 1000 == 0)
            {
               statement.executeBatch();
            }
         }
         statement.executeBatch();
      }
   }


   /*******************************************************************************
    ** Seed quota history with an exact UTC timestamp rather than the insertion clock.
    *******************************************************************************/
   public void seedTransfer(QRecord account, String operation, long bytes, java.time.Instant when) throws Exception
   {
      QRecord row = audit.insert("file_gateway_transfer_log", new QRecord().withValue("accountId", account.getValue("id"))
         .withValue("operation", operation).withValue("bytes", bytes).withValue("outcome", "OK")
         .withValue("path", "/history.csv").withValue("clientChannel", "sftp/history"));
      try(var statement = keeper.prepareStatement("UPDATE file_gateway_transfer_log SET create_date = ? WHERE id = ?"))
      {
         statement.setObject(1, when.atOffset(java.time.ZoneOffset.UTC).toLocalDateTime());
         statement.setInt(2, row.getValueInteger("id"));
         statement.executeUpdate();
      }
   }



   /*******************************************************************************
    ** Create core test tables using their actual metadata.
    *******************************************************************************/
   private void createTable(QTableMetaData table) throws Exception
   {
      StringBuilder sql = new StringBuilder("CREATE TABLE ").append(table.getName()).append(" (");
      boolean first = true;
      for(QFieldMetaData field : table.getFields().values())
      {
         if(!first)
         {
            sql.append(", ");
         }
         first = false;
         String type = switch(field.getType())
         {
            case INTEGER -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL -> "DECIMAL";
            case BOOLEAN -> "BOOLEAN";
            case DATE_TIME -> "TIMESTAMP";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case BLOB -> "BINARY LARGE OBJECT";
            default -> "VARCHAR(65535)";
         };
         sql.append(field.getBackendName()).append(" ").append(type);
         if(field.getName().equals(table.getPrimaryKeyField()) && (field.getType() == QFieldType.INTEGER || field.getType() == QFieldType.LONG))
         {
            sql.append(" GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
         }
      }
      keeper.createStatement().execute(sql.append(")").toString());
   }



   /*******************************************************************************
    ** Insert an active account with schema defaults.
    *******************************************************************************/
   public QRecord account(String username, String userId) throws Exception
   {
      return (audit.insert("file_gateway_account", new QRecord().withValue("username", username).withValue("label", username).withValue("userId", userId)));
   }



   /*******************************************************************************
    ** Insert a generated key through the real validation customizer.
    *******************************************************************************/
   public QRecord key(QRecord account, KeyPair pair) throws Exception
   {
      return (audit.insert("file_gateway_ssh_key", new QRecord().withValue("accountId", account.getValue("id"))
         .withValue("label", "Test key").withValue("publicKey", PublicKeyEntry.toString(pair.getPublic()))));
   }



   /*******************************************************************************
    ** Authenticate a real MINA SSH client.
    *******************************************************************************/
   public ClientSession login(String username, KeyPair key) throws Exception
   {
      ClientSession session = client.connect(username, "127.0.0.1", runtime.getProtocol().getPort()).verify(Duration.ofSeconds(10)).getSession();
      session.addPublicKeyIdentity(key);
      try
      {
         session.auth().verify(Duration.ofSeconds(10));
         return (session);
      }
      catch(Exception e)
      {
         session.close(true).await(Duration.ofSeconds(5));
         throw (e);
      }
   }



   /*******************************************************************************
    ** Seed visible core saved-object rows under their owner.
    *******************************************************************************/
   public QRecord saved(String source, String table, String label) throws Exception
   {
      return (audit.system(() -> new InsertAction().execute(new InsertInput(source).withRecord(new QRecord()
         .withValue("tableName", table).withValue("label", label).withValue("userId", "A").withValue("viewJson", "{}")
         .withValue("mappingJson", "{}").withValue("columnsJson", "{\"columns\":[{\"name\":\"id\"}]}"))).getRecords().get(0)));
   }



   /*******************************************************************************
    ** Close sessions and all services.
    *******************************************************************************/
   @Override
   public void close() throws Exception
   {
      client.stop();
      if(runtime != null)
      {
         runtime.stop();
      }
      http.stop();
      keeper.close();
      QContext.clear();
   }



   /*******************************************************************************
    ** MOCK automation permissions differ by user id.
    *******************************************************************************/
   public static class TestAuthentication implements QAuthenticationModuleCustomizerInterface
   {
      /** Demo only: browser (MOCK) sessions get every permission in the instance. */
      public static volatile boolean grantAllToUiSessions = false;



      /*******************************************************************************
       ** Browser sessions: nothing, unless the demo server switches it on.
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
      {
         if(grantAllToUiSessions)
         {
            java.util.Set<String> all = new java.util.HashSet<>(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.getAllAvailablePermissionNames(instance));
            all.addAll(com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions.ALL);
            session.setPermissions(all);
         }
      }



      /*******************************************************************************
       ** Customize automated session for user.
       *******************************************************************************/
      @Override
      public void customizeAutomatedSessionForUser(QInstance instance, QSession session, Serializable userId)
      {
         session.getUser().setFullName("Test user " + userId);
         session.setPermissions(GRANTS.getOrDefault(userId.toString(), Set.of()));
      }
   }
}
