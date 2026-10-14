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

package com.kingsrook.qbits.filegateway.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.ai.AiFieldMetaData;
import com.kingsrook.qqq.backend.core.actions.ai.DataClassification;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;


/*******************************************************************************
 ** The seven gateway tables, their stable columns, pickers and child lists.
 *******************************************************************************/
public final class FileGatewayTables
{
   public static final String ACCOUNT = "file_gateway_account";
   public static final String SSH_KEY = "file_gateway_ssh_key";
   public static final String ALIAS = "file_gateway_alias";
   public static final String UPLOAD_JOB = "file_gateway_upload_job";
   public static final String OUTBOX_FILE = "file_gateway_outbox_file";
   public static final String TRANSFER_LOG = "file_gateway_transfer_log";
   public static final String CONNECTION_LOG = "file_gateway_connection_log";
   public static final String SERVER = "file_gateway_server";
   public static final List<String> ALL = List.of(ACCOUNT, SSH_KEY, ALIAS, UPLOAD_JOB, OUTBOX_FILE, TRANSFER_LOG, CONNECTION_LOG, SERVER);
   public static final List<String> MAIN = List.of(ACCOUNT, ALIAS, UPLOAD_JOB, TRANSFER_LOG, CONNECTION_LOG);
   public static final List<String> SYSTEM_WRITTEN = List.of(UPLOAD_JOB, OUTBOX_FILE, TRANSFER_LOG, CONNECTION_LOG, SERVER);



   /*******************************************************************************
    ** Metadata factories only.
    *******************************************************************************/
   private FileGatewayTables()
   {
   }



   /*******************************************************************************
    ** Permission names use the full table or app name.
    *******************************************************************************/
   public static QPermissionRules permissionRules(String name)
   {
      return (new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS).withPermissionBaseName(name)
         .withCustomPermissionChecker(new QCodeReference(FileGatewayPermissionChecker.class)));
   }



   /*******************************************************************************
    ** Enums and gateway record pickers.
    *******************************************************************************/
   public static List<QPossibleValueSource> possibleValueSources()
   {
      List<QPossibleValueSource> sources = new ArrayList<>();
      sources.add(QPossibleValueSource.newForEnum("file_gateway_account_type", FileGatewayEnums.AccountType.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_account_status", FileGatewayEnums.AccountStatus.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_root_mode", FileGatewayEnums.RootMode.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_alias_kind", FileGatewayEnums.AliasKind.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_upload_status", FileGatewayEnums.UploadStatus.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_outbox_status", FileGatewayEnums.OutboxStatus.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_operation", FileGatewayEnums.Operation.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_object_kind", FileGatewayEnums.ObjectKind.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_transfer_outcome", FileGatewayEnums.TransferOutcome.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_connection_outcome", FileGatewayEnums.ConnectionOutcome.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_format", FileGatewayEnums.Format.values()));
      sources.add(QPossibleValueSource.newForEnum("file_gateway_key_type", FileGatewayEnums.KeyType.values()));
      ALL.forEach(name -> sources.add(QPossibleValueSource.newForTable(name)));
      return (sources);
   }



   /** Plain labels for child lists and their record-screen tabs. */
   static final java.util.Map<String, String> CHILD_LABELS = java.util.Map.of(
      SSH_KEY, "SSH keys", UPLOAD_JOB, "Upload jobs", CONNECTION_LOG, "Connections",
      TRANSFER_LOG, "Transfers", OUTBOX_FILE, "Outbox files");

   /** Plain field labels where the name-derived label reads badly. */
   static final java.util.Map<String, String> FIELD_LABELS = java.util.Map.ofEntries(
      java.util.Map.entry("allowedCidrs", "Allowed CIDRs"), java.util.Map.entry("userId", "User"),
      java.util.Map.entry("ownerUserId", "Owner"), java.util.Map.entry("maxFileSizeMb", "Max file size (MB)"),
      java.util.Map.entry("maxUploadMbPerDay", "Max upload per day (MB)"), java.util.Map.entry("maxDownloadMbPerDay", "Max download per day (MB)"),
      java.util.Map.entry("lastLoginIp", "Last login IP"), java.util.Map.entry("lastUsedIp", "Last used IP"),
      java.util.Map.entry("pgpPublicKey", "PGP public key"), java.util.Map.entry("pgpDecryptUploads", "Decrypt PGP uploads"),
      java.util.Map.entry("pgpEncryptDownloads", "Encrypt downloads with PGP"), java.util.Map.entry("remoteIp", "Remote IP"),
      java.util.Map.entry("remoteAddress", "Remote IP"), java.util.Map.entry("sha256", "SHA-256"),
      java.util.Map.entry("processUuid", "Process UUID"), java.util.Map.entry("savedBulkLoadProfileId", "Bulk load profile"),
      java.util.Map.entry("savedViewId", "Saved view"), java.util.Map.entry("savedReportId", "Saved report"),
      java.util.Map.entry("sizeBytes", "Size (bytes)"), java.util.Map.entry("accountId", "Account"),
      java.util.Map.entry("aliasId", "Endpoint"), java.util.Map.entry("connectionLogId", "Connection"),
      java.util.Map.entry("hostKeyFingerprint", "Host key fingerprint"), java.util.Map.entry("keyFingerprint", "Key fingerprint"));



   /*******************************************************************************
    ** Build all seven tables.
    *******************************************************************************/
   public static List<QTableMetaData> tables(FileGatewayQBitConfig config)
   {
      List<QTableMetaData> tables = List.of(account(config), ssh_key(config), alias(config), upload_job(config), outbox_file(config), transfer_log(config), connection_log(config), server(config));
      for(QTableMetaData table : tables)
      {
         for(QFieldMetaData field : table.getFields().values())
         {
            if(FIELD_LABELS.containsKey(field.getName()))
            {
               field.setLabel(FIELD_LABELS.get(field.getName()));
            }
         }
      }
      return (tables);
   }



   /*******************************************************************************
    ** Common identity and timestamps.
    *******************************************************************************/
   private static QTableMetaData base(FileGatewayQBitConfig config, String name, String label)
   {
      return (new QTableMetaData().withName(name).withLabel(label).withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(permissionRules(name))
         .withField(field("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(field("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("modifyDate", QFieldType.DATE_TIME).withIsEditable(false)));
   }



   /*******************************************************************************
    ** Apply the host backend naming convention explicitly.
    *******************************************************************************/
   private static QFieldMetaData field(String name, QFieldType type)
   {
      return (new QFieldMetaData(name, type).withBackendName(QInstanceEnricher.inferBackendName(name)));
   }



   /*******************************************************************************
    ** Metadata for accounts.
    *******************************************************************************/
   private static QTableMetaData account(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, ACCOUNT, "Accounts")
         .withRecordLabelFormatAndFields("%s", "username")
         .withField(field("username", QFieldType.STRING).withIsRequired(true))
         .withField(field("label", QFieldType.STRING))
         .withField(field("accountType", QFieldType.STRING).withPossibleValueSourceName("file_gateway_account_type").withDefaultValue("USER").withIsRequired(true))
         .withField(field("userId", QFieldType.STRING).withIsRequired(true))
         .withField(field("ownerUserId", QFieldType.STRING))
         .withField(field("status", QFieldType.STRING).withPossibleValueSourceName("file_gateway_account_status").withDefaultValue("ACTIVE").withIsRequired(true))
         .withField(field("rootMode", QFieldType.STRING).withPossibleValueSourceName("file_gateway_root_mode"))
         .withField(field("allowedCidrs", QFieldType.TEXT))
         .withField(field("expiresAt", QFieldType.DATE_TIME))
         .withField(field("formats", QFieldType.STRING).withDefaultValue("csv,xlsx"))
         .withField(field("maxConcurrentSessions", QFieldType.INTEGER).withDefaultValue(config.getDefaultMaxConcurrentSessions()))
         .withField(field("maxOperationsPerMinute", QFieldType.INTEGER).withDefaultValue(config.getDefaultMaxOperationsPerMinute()))
         .withField(field("maxFileSizeMb", QFieldType.INTEGER).withDefaultValue(config.getDefaultMaxFileSizeMb()))
         .withField(field("maxUploadMbPerDay", QFieldType.INTEGER).withDefaultValue(config.getDefaultMaxUploadMbPerDay()))
         .withField(field("maxDownloadMbPerDay", QFieldType.INTEGER).withDefaultValue(config.getDefaultMaxDownloadMbPerDay()))
         .withField(field("dedupeUploads", QFieldType.BOOLEAN).withDefaultValue(true))
         .withField(field("notifyOnSuccess", QFieldType.BOOLEAN).withDefaultValue(false))
         .withField(field("pgpPublicKey", QFieldType.TEXT))
         .withField(field("pgpDecryptUploads", QFieldType.BOOLEAN).withDefaultValue(false))
         .withField(field("pgpEncryptDownloads", QFieldType.BOOLEAN).withDefaultValue(false))
         .withField(field("operationWindowAt", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("operationWindowCount", QFieldType.INTEGER).withDefaultValue(0).withIsEditable(false))
         .withField(field("failedLoginCount", QFieldType.INTEGER).withDefaultValue(0).withIsEditable(false))
         .withField(field("failedLoginWindowAt", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("lockedUntil", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("lastLoginAt", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("lastLoginIp", QFieldType.STRING).withIsEditable(false));
      table.withUniqueKey(new UniqueKey("username"));
      table.withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(AccountCustomizer.class));
      table.withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(AccountCustomizer.class));
      table.withSection(SectionFactory.defaultT1("id", "username", "label", "accountType", "userId", "ownerUserId"));
      table.withSection(SectionFactory.defaultT2("status", "rootMode", "allowedCidrs", "expiresAt", "formats", "maxConcurrentSessions", "maxOperationsPerMinute", "maxFileSizeMb", "maxUploadMbPerDay", "maxDownloadMbPerDay", "dedupeUploads", "notifyOnSuccess", "pgpPublicKey", "pgpDecryptUploads", "pgpEncryptDownloads", "operationWindowAt", "operationWindowCount", "failedLoginCount", "failedLoginWindowAt", "lockedUntil", "lastLoginAt", "lastLoginIp"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withSection(SectionFactory.customT2(SSH_KEY, new QIcon("list")).withLabel(CHILD_LABELS.get(SSH_KEY)).withWidgetName(ACCOUNT + "_children_" + SSH_KEY));
      table.withSection(SectionFactory.customT2(UPLOAD_JOB, new QIcon("list")).withLabel(CHILD_LABELS.get(UPLOAD_JOB)).withWidgetName(ACCOUNT + "_children_" + UPLOAD_JOB));
      table.withSection(SectionFactory.customT2(CONNECTION_LOG, new QIcon("list")).withLabel(CHILD_LABELS.get(CONNECTION_LOG)).withWidgetName(ACCOUNT + "_children_" + CONNECTION_LOG));
      return (table);
   }



   /*******************************************************************************
    ** Metadata for ssh keys.
    *******************************************************************************/
   private static QTableMetaData ssh_key(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, SSH_KEY, "SSH Keys")
         .withRecordLabelFormatAndFields("%s", "label")
         .withField(field("accountId", QFieldType.INTEGER).withPossibleValueSourceName(ACCOUNT).withIsRequired(true))
         .withField(field("label", QFieldType.STRING))
         .withField(field("publicKey", QFieldType.TEXT).withIsRequired(true))
         .withField(field("fingerprint", QFieldType.STRING).withIsEditable(false))
         .withField(field("keyType", QFieldType.STRING).withPossibleValueSourceName("file_gateway_key_type").withIsEditable(false))
         .withField(field("keyBits", QFieldType.INTEGER).withIsEditable(false))
         .withField(field("expiresAt", QFieldType.DATE_TIME))
         .withField(field("revokedAt", QFieldType.DATE_TIME))
         .withField(field("lastUsedAt", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(field("lastUsedIp", QFieldType.STRING).withIsEditable(false));
      table.withUniqueKey(new UniqueKey("fingerprint"));
      table.withSection(SectionFactory.defaultT1("id", "accountId", "label", "publicKey", "fingerprint", "keyType"));
      table.withSection(SectionFactory.defaultT2("keyBits", "expiresAt", "revokedAt", "lastUsedAt", "lastUsedIp"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(SshKeyCustomizer.class));
      table.withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(SshKeyCustomizer.class));
      table.getField("publicKey").withSupplementalMetaData(new AiFieldMetaData().withClassification(DataClassification.CONFIDENTIAL));
      return (table);
   }



   /*******************************************************************************
    ** Metadata for endpoints.
    *******************************************************************************/
   private static QTableMetaData alias(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, ALIAS, "Endpoints")
         .withRecordLabelFormatAndFields("%s", "name")
         .withField(field("name", QFieldType.STRING))
         .withField(field("path", QFieldType.STRING))
         .withField(field("kind", QFieldType.STRING).withPossibleValueSourceName("file_gateway_alias_kind"))
         .withField(field("accountId", QFieldType.INTEGER).withPossibleValueSourceName(ACCOUNT))
         .withField(field("tableName", QFieldType.STRING).withPossibleValueSourceName("tables"))
         .withField(field("savedBulkLoadProfileId", QFieldType.INTEGER).withPossibleValueSourceName("savedBulkLoadProfile").withPossibleValueSourceFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, "${input.tableName}"))))
         .withField(field("savedViewId", QFieldType.INTEGER).withPossibleValueSourceName("savedView").withPossibleValueSourceFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, "${input.tableName}"))))
         .withField(field("savedReportId", QFieldType.INTEGER).withPossibleValueSourceName("savedReport").withPossibleValueSourceFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, "${input.tableName}"))))
         .withField(field("format", QFieldType.STRING).withPossibleValueSourceName("file_gateway_format"))
         .withField(field("fileName", QFieldType.STRING))
         .withField(field("consumerName", QFieldType.STRING))
         .withField(field("isActive", QFieldType.BOOLEAN).withDefaultValue(true))
         .withField(field("description", QFieldType.TEXT));
      table.withUniqueKey(new UniqueKey("name"));
      table.withUniqueKey(new UniqueKey("path"));
      table.withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(AliasCustomizer.class));
      table.withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(AliasCustomizer.class));
      table.withSection(SectionFactory.defaultT1("id", "name", "path", "kind", "accountId", "tableName"));
      table.withSection(SectionFactory.defaultT2("savedBulkLoadProfileId", "savedViewId", "savedReportId", "format", "fileName", "consumerName", "isActive", "description"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withSection(SectionFactory.customT2(UPLOAD_JOB, new QIcon("list")).withLabel(CHILD_LABELS.get(UPLOAD_JOB)).withWidgetName(ALIAS + "_children_" + UPLOAD_JOB));
      table.withSection(SectionFactory.customT2(OUTBOX_FILE, new QIcon("list")).withLabel(CHILD_LABELS.get(OUTBOX_FILE)).withWidgetName(ALIAS + "_children_" + OUTBOX_FILE));
      return (table);
   }



   /*******************************************************************************
    ** Metadata for upload jobs.
    *******************************************************************************/
   private static QTableMetaData upload_job(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, UPLOAD_JOB, "Upload Jobs").withRecordVersionField("status")
         .withRecordLabelFormatAndFields("%s", "id")
         .withField(field("accountId", QFieldType.INTEGER).withPossibleValueSourceName(ACCOUNT))
         .withField(field("aliasId", QFieldType.INTEGER).withPossibleValueSourceName(ALIAS))
         .withField(field("connectionLogId", QFieldType.INTEGER).withPossibleValueSourceName(CONNECTION_LOG))
         .withField(field("remoteAddress", QFieldType.STRING))
         .withField(field("tableName", QFieldType.STRING).withPossibleValueSourceName("tables"))
         .withField(field("savedBulkLoadProfileId", QFieldType.INTEGER).withPossibleValueSourceName("savedBulkLoadProfile"))
         .withField(field("consumerName", QFieldType.STRING))
         .withField(field("consumerReference", QFieldType.STRING))
         .withField(field("path", QFieldType.STRING))
         .withField(field("fileName", QFieldType.STRING))
         .withField(field("sizeBytes", QFieldType.LONG))
         .withField(field("sha256", QFieldType.STRING))
         .withField(field("storageKey", QFieldType.STRING))
         .withField(field("status", QFieldType.STRING).withPossibleValueSourceName("file_gateway_upload_status"))
         .withField(field("duplicateOfJobId", QFieldType.INTEGER).withPossibleValueSourceName(UPLOAD_JOB))
         .withField(field("processUuid", QFieldType.STRING))
         .withField(field("insertedCount", QFieldType.INTEGER))
         .withField(field("errorCount", QFieldType.INTEGER))
         .withField(field("resultSummary", QFieldType.TEXT))
         .withField(field("resultsStorageKey", QFieldType.STRING))
         .withField(field("resultsSizeBytes", QFieldType.LONG))
         .withField(field("plaintextStorageKey", QFieldType.STRING))
         .withField(field("plaintextSha256", QFieldType.STRING))
         .withField(field("plaintextSizeBytes", QFieldType.LONG))
         .withField(field("errorMessage", QFieldType.TEXT))
         .withField(field("attempts", QFieldType.INTEGER))
         .withField(field("nextAttemptAt", QFieldType.DATE_TIME))
         .withField(field("claimedBy", QFieldType.STRING))
         .withField(field("claimedAt", QFieldType.DATE_TIME))
         .withField(field("queuedAt", QFieldType.DATE_TIME))
         .withField(field("startedAt", QFieldType.DATE_TIME))
         .withField(field("finishedAt", QFieldType.DATE_TIME))
         .withField(field("acknowledgedAt", QFieldType.DATE_TIME));
      table.withSection(SectionFactory.defaultT1("id", "accountId", "aliasId", "connectionLogId", "remoteAddress", "tableName"));
      table.withSection(SectionFactory.defaultT2("savedBulkLoadProfileId", "consumerName", "consumerReference", "path", "fileName", "sizeBytes", "sha256", "storageKey", "status", "duplicateOfJobId", "processUuid", "insertedCount", "errorCount", "resultSummary", "resultsStorageKey", "resultsSizeBytes", "plaintextStorageKey", "plaintextSha256", "plaintextSizeBytes", "errorMessage", "attempts", "nextAttemptAt", "claimedBy", "claimedAt", "queuedAt", "startedAt", "finishedAt", "acknowledgedAt"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withSection(SectionFactory.customT2(TRANSFER_LOG, new QIcon("list")).withLabel(CHILD_LABELS.get(TRANSFER_LOG)).withWidgetName(UPLOAD_JOB + "_children_" + TRANSFER_LOG));
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      return (table);
   }



   /*******************************************************************************
    ** Metadata for outbox files.
    *******************************************************************************/
   private static QTableMetaData outbox_file(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, OUTBOX_FILE, "Outbox Files")
         .withRecordLabelFormatAndFields("%s", "id")
         .withField(field("aliasId", QFieldType.INTEGER).withPossibleValueSourceName(ALIAS))
         .withField(field("fileName", QFieldType.STRING))
         .withField(field("sizeBytes", QFieldType.LONG))
         .withField(field("sha256", QFieldType.STRING))
         .withField(field("storageKey", QFieldType.STRING))
         .withField(field("status", QFieldType.STRING).withPossibleValueSourceName("file_gateway_outbox_status"))
         .withField(field("firstDownloadedAt", QFieldType.DATE_TIME))
         .withField(field("downloadCount", QFieldType.INTEGER))
         .withField(field("onDownloadedPending", QFieldType.BOOLEAN).withDefaultValue(false))
         .withField(field("removedAt", QFieldType.DATE_TIME))
         .withField(field("expiresAt", QFieldType.DATE_TIME))
         .withField(field("createdByConsumer", QFieldType.STRING));
      table.withSection(SectionFactory.defaultT1("id", "aliasId", "fileName", "sizeBytes", "sha256", "storageKey"));
      table.withSection(SectionFactory.defaultT2("status", "firstDownloadedAt", "downloadCount", "onDownloadedPending", "removedAt", "expiresAt", "createdByConsumer"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      return (table);
   }



   /*******************************************************************************
    ** Metadata for transfers.
    *******************************************************************************/
   private static QTableMetaData transfer_log(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, TRANSFER_LOG, "Transfers")
         .withRecordLabelFormatAndFields("%s", "id")
         .withField(field("accountId", QFieldType.INTEGER).withPossibleValueSourceName(ACCOUNT))
         .withField(field("connectionLogId", QFieldType.INTEGER).withPossibleValueSourceName(CONNECTION_LOG))
         .withField(field("operation", QFieldType.STRING).withPossibleValueSourceName("file_gateway_operation"))
         .withField(field("path", QFieldType.STRING))
         .withField(field("objectKind", QFieldType.STRING).withPossibleValueSourceName("file_gateway_object_kind"))
         .withField(field("objectId", QFieldType.STRING))
         .withField(field("format", QFieldType.STRING).withPossibleValueSourceName("file_gateway_format"))
         .withField(field("bytes", QFieldType.LONG))
         .withField(field("sha256", QFieldType.STRING))
         .withField(field("durationMs", QFieldType.LONG))
         .withField(field("outcome", QFieldType.STRING).withPossibleValueSourceName("file_gateway_transfer_outcome"))
         .withField(field("message", QFieldType.TEXT))
         .withField(field("clientChannel", QFieldType.STRING))
         .withField(field("uploadJobId", QFieldType.INTEGER).withPossibleValueSourceName(UPLOAD_JOB));
      table.withSection(SectionFactory.defaultT1("id", "accountId", "connectionLogId", "operation", "path", "objectKind"));
      table.withSection(SectionFactory.defaultT2("objectId", "format", "bytes", "sha256", "durationMs", "outcome", "message", "clientChannel", "uploadJobId"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      return (table);
   }



   /*******************************************************************************
    ** Metadata for connections.
    *******************************************************************************/
   private static QTableMetaData connection_log(FileGatewayQBitConfig config)
   {
      QTableMetaData table = base(config, CONNECTION_LOG, "Connections")
         .withRecordLabelFormatAndFields("%s", "id")
         .withField(field("accountId", QFieldType.INTEGER).withPossibleValueSourceName(ACCOUNT))
         .withField(field("username", QFieldType.STRING))
         .withField(field("remoteIp", QFieldType.STRING))
         .withField(field("clientVersion", QFieldType.STRING))
         .withField(field("keyFingerprint", QFieldType.STRING))
         .withField(field("outcome", QFieldType.STRING).withPossibleValueSourceName("file_gateway_connection_outcome"))
         .withField(field("sessionId", QFieldType.STRING))
         .withField(field("podName", QFieldType.STRING))
         .withField(field("connectedAt", QFieldType.DATE_TIME))
         .withField(field("lastSeenAt", QFieldType.DATE_TIME))
         .withField(field("disconnectedAt", QFieldType.DATE_TIME))
         .withField(field("endRequestedAt", QFieldType.DATE_TIME))
         .withField(field("disconnectReason", QFieldType.TEXT))
         .withField(field("bytesIn", QFieldType.LONG))
         .withField(field("bytesOut", QFieldType.LONG))
         .withField(field("operationCount", QFieldType.LONG))
         .withField(field("clientChannel", QFieldType.STRING));
      table.withSection(SectionFactory.defaultT1("id", "accountId", "username", "remoteIp", "clientVersion", "keyFingerprint"));
      table.withSection(SectionFactory.defaultT2("outcome", "sessionId", "podName", "connectedAt", "lastSeenAt", "disconnectedAt", "endRequestedAt", "disconnectReason", "bytesIn", "bytesOut", "operationCount", "clientChannel"));
      table.withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      return (table);
   }



   /*******************************************************************************
    ** Gateway heartbeat shared with the main web deployment, never a navigation table.
    *******************************************************************************/
   private static QTableMetaData server(FileGatewayQBitConfig config)
   {
      return (base(config, SERVER, "Servers").withRecordLabelFormatAndFields("%s", "podName")
         .withField(field("podName", QFieldType.STRING).withIsRequired(true))
         .withField(field("hostKeyFingerprint", QFieldType.STRING))
         .withField(field("port", QFieldType.INTEGER))
         .withField(field("startedAt", QFieldType.DATE_TIME))
         .withField(field("lastSeenAt", QFieldType.DATE_TIME))
         .withUniqueKey(new UniqueKey("podName"))
         .withSection(SectionFactory.defaultT1("id", "podName", "hostKeyFingerprint", "port", "startedAt", "lastSeenAt"))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
         .withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE));
   }



   /*******************************************************************************
    ** Parent and child relationships.
    *******************************************************************************/
   public static List<QJoinMetaData> joins()
   {
      return (List.of(
         new QJoinMetaData().withName(ACCOUNT + "_join_" + SSH_KEY).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(ACCOUNT).withRightTable(SSH_KEY).withJoinOn(new JoinOn("id", "accountId")),
         new QJoinMetaData().withName(ACCOUNT + "_join_" + UPLOAD_JOB).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(ACCOUNT).withRightTable(UPLOAD_JOB).withJoinOn(new JoinOn("id", "accountId")),
         new QJoinMetaData().withName(ACCOUNT + "_join_" + CONNECTION_LOG).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(ACCOUNT).withRightTable(CONNECTION_LOG).withJoinOn(new JoinOn("id", "accountId")),
         new QJoinMetaData().withName(ALIAS + "_join_" + UPLOAD_JOB).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(ALIAS).withRightTable(UPLOAD_JOB).withJoinOn(new JoinOn("id", "aliasId")),
         new QJoinMetaData().withName(ALIAS + "_join_" + OUTBOX_FILE).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(ALIAS).withRightTable(OUTBOX_FILE).withJoinOn(new JoinOn("id", "aliasId")),
         new QJoinMetaData().withName(UPLOAD_JOB + "_join_" + TRANSFER_LOG).withType(JoinType.ONE_TO_MANY)
            .withLeftTable(UPLOAD_JOB).withRightTable(TRANSFER_LOG).withJoinOn(new JoinOn("id", "uploadJobId"))));
   }



   /*******************************************************************************
    ** Typed child-record widgets; no home or HTML widget.
    *******************************************************************************/
   public static List<QWidgetMetaData> childWidgets()
   {
      List<QWidgetMetaData> widgets = new ArrayList<>();
      for(QJoinMetaData join : joins())
      {
         widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(join)
            .withName(join.getLeftTable() + "_children_" + join.getRightTable()).withLabel(CHILD_LABELS.getOrDefault(join.getRightTable(), join.getRightTable()))
            .withCanAddChildRecord(SSH_KEY.equals(join.getRightTable())).withMaxRows(100)
            .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules(join.getRightTable())));
      }
      return (widgets);
   }
}
