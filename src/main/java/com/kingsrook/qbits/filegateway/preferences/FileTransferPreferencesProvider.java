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

package com.kingsrook.qbits.filegateway.preferences;


import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceActionMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceActionRequest;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceActionResult;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceActionScope;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceListMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceProviderInterface;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRequestContext;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRow;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRowChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceSectionMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceValues;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;


/*******************************************************************************
 ** Self-service SSH keys. Core supplies the trusted scope and retains the user
 ** and client channel; this provider never switches to a system session.
 *******************************************************************************/
public class FileTransferPreferencesProvider implements UserPreferenceProviderInterface
{
   public static final String SECTION_NAME = "file-transfer";
   private static final List<String> KEY_FIELDS = List.of("label", "fingerprint", "keyType", "lastUsedAt", "expiresAt");



   /*******************************************************************************
    ** Register without an app scope: connect permission is sufficient.
    *******************************************************************************/
   public static void register(QInstance instance, FileGatewayQBitConfig config)
   {
      UserPreferenceSectionMetaData section = new UserPreferenceSectionMetaData()
         .withName(SECTION_NAME).withLabel("File transfer").withIcon(new QIcon("swap_vert")).withOrder(60)
         .withDescription("Sign in to the SFTP file gateway with your SSH keys.")
         .withPermissionName(FileGatewayPermissions.CONNECT).withProvider(new QCodeReference(FileTransferPreferencesProvider.class))
         .withField(new QFieldMetaData("username", QFieldType.STRING).withLabel("Username").withIsEditable(false)
            .withHelpContent(new QHelpContent("Adding your first key creates your SFTP account and username.")))
         .withField(new QFieldMetaData("port", QFieldType.INTEGER).withLabel("Port").withIsEditable(false))
         .withField(new QFieldMetaData("hostKeyFingerprint", QFieldType.STRING).withLabel("Host key fingerprint").withIsEditable(false))
         .withList(new UserPreferenceListMetaData().withName("keys").withLabel("SSH keys").withRowLabel("Key")
            .withDescription("Adding your first key creates your SFTP account.")
            .withField(new QFieldMetaData("label", QFieldType.STRING).withLabel("Label").withIsRequired(true).withMaxLength(1024))
            .withField(new QFieldMetaData("fingerprint", QFieldType.STRING).withLabel("Fingerprint").withIsEditable(false))
            .withField(new QFieldMetaData("keyType", QFieldType.STRING).withLabel("Key type").withIsEditable(false))
            .withField(new QFieldMetaData("lastUsedAt", QFieldType.DATE_TIME).withLabel("Last used").withIsEditable(false))
            .withField(new QFieldMetaData("expiresAt", QFieldType.DATE_TIME).withLabel("Expires at")))
         .withAction(new UserPreferenceActionMetaData().withName("addKey").withLabel("Add key")
            .withScope(UserPreferenceActionScope.LIST).withListName("keys").withOrder(10)
            .withInputField(new QFieldMetaData("publicKey", QFieldType.TEXT).withLabel("Public key").withIsRequired(true))
            .withInputField(new QFieldMetaData("label", QFieldType.STRING).withLabel("Label").withIsRequired(true).withMaxLength(1024))
            .withInputField(new QFieldMetaData("expiresAt", QFieldType.DATE_TIME).withLabel("Expires at")))
         .withAction(new UserPreferenceActionMetaData().withName("revokeKey").withLabel("Revoke")
            .withScope(UserPreferenceActionScope.ROW).withListName("keys").withOrder(20).withIsDestructive(true)
            .withConfirmationText("Revoke this SSH key? It will no longer be able to sign in."));
      if(config.getPublicHostName() != null && !config.getPublicHostName().isBlank())
      {
         section.withField(new QFieldMetaData("host", QFieldType.STRING).withLabel("Host").withIsEditable(false).withDefaultValue(config.getPublicHostName()));
      }
      UserPreferencesMetaData.ofOrWithNew(instance).withSection(section);
   }



   /*******************************************************************************
    ** Publish only the user's active key metadata, never the public-key text.
    *******************************************************************************/
   @Override
   public UserPreferenceValues load(UserPreferenceRequestContext context) throws QException
   {
      QRecord account = account(context, null);
      UserPreferenceValues result = new UserPreferenceValues();
      result.getValues().put("username", account == null ? "" : account.getValue("username"));
      List<QRecord> servers = query(FileGatewayTables.SERVER, new QQueryFilter()
         .withOrderBy(new QFilterOrderBy("lastSeenAt", false)).withOrderBy(new QFilterOrderBy("id", false)).withLimit(1), null);
      if(!servers.isEmpty())
      {
         result.getValues().put("port", servers.getFirst().getValue("port"));
         result.getValues().put("hostKeyFingerprint", servers.getFirst().getValue("hostKeyFingerprint"));
      }
      List<UserPreferenceRow> rows = new ArrayList<>();
      if(account != null)
      {
         for(QRecord key : query(FileGatewayTables.SSH_KEY, keysFilter(account), null))
         {
            Map<String, Serializable> values = new LinkedHashMap<>();
            KEY_FIELDS.forEach(field -> values.put(field, key.getValue(field)));
            rows.add(new UserPreferenceRow().withKey(key.getValueString("id")).withLabel(key.getValueString("label")).withValues(values));
         }
      }
      result.getLists().put("keys", rows);
      return (result);
   }



   /*******************************************************************************
    ** Core validates types; reject blank labels as well as missing ones.
    *******************************************************************************/
   @Override
   public List<String> validate(UserPreferenceRequestContext context, UserPreferenceChange change)
   {
      List<String> errors = new ArrayList<>();
      for(UserPreferenceRowChange row : change.getLists().getOrDefault("keys", List.of()))
      {
         if(row.getValues().containsKey("label") && (row.getValues().get("label") == null || row.getValues().get("label").toString().isBlank()))
         {
            errors.add("Label: is required");
         }
      }
      return (errors);
   }



   /*******************************************************************************
    ** Apply partial changes atomically, checking ownership again under row locks.
    *******************************************************************************/
   @Override
   public void store(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      try(var tx = QBackendTransaction.openFor(new QueryInput(FileGatewayTables.ACCOUNT)))
      {
         try
         {
            QRecord account = lockedAccount(context, tx);
            for(UserPreferenceRowChange row : change.getLists().getOrDefault("keys", List.of()))
            {
               QRecord key = lockedKey(account, row.getKey(), tx);
               QRecord update = new QRecord().withValue("id", key.getValue("id"));
               for(String field : List.of("label", "expiresAt"))
               {
                  if(row.getValues().containsKey(field))
                  {
                     update.setValue(field, row.getValues().get(field));
                  }
               }
               update(FileGatewayTables.SSH_KEY, update, tx);
            }
            tx.commit();
         }
         catch(QException | RuntimeException e)
         {
            tx.rollback();
            throw e;
         }
      }
   }



   /*******************************************************************************
    ** Add through the normal key customizer, or revoke an owned, unrevoked row.
    *******************************************************************************/
   @Override
   public UserPreferenceActionResult runAction(UserPreferenceRequestContext context, UserPreferenceActionRequest request) throws QException
   {
      if(!List.of("addKey", "revokeKey").contains(request.getActionName()))
      {
         throw (new QBadRequestException("This action isn't available."));
      }
      if("addKey".equals(request.getActionName()))
      {
         List<String> errors = new ArrayList<>();
         for(String field : List.of("publicKey", "label"))
         {
            if(request.getValues().get(field) == null || request.getValues().get(field).toString().isBlank())
            {
               errors.add(("publicKey".equals(field) ? "Public key" : "Label") + ": is required");
            }
         }
         if(!errors.isEmpty())
         {
            throw (new QBadRequestException(String.join("\n", errors)));
         }
      }
      try(var tx = QBackendTransaction.openFor(new QueryInput(FileGatewayTables.ACCOUNT)))
      {
         try
         {
            QRecord account = lockedAccount(context, tx);
            UserPreferenceActionResult result;
            if("addKey".equals(request.getActionName()))
            {
               if(account == null)
               {
                  account = insert(FileGatewayTables.ACCOUNT, new QRecord().withValue("username", availableUsername(context.getUserId(), tx))
                     .withValue("userId", context.getUserId()).withValue("accountType", "USER").withValue("rootMode", "FULL_TREE"), tx);
               }
               QRecord key = insert(FileGatewayTables.SSH_KEY, new QRecord().withValue("accountId", account.getValue("id"))
                  .withValue("publicKey", request.getValues().get("publicKey")).withValue("label", request.getValues().get("label"))
                  .withValue("expiresAt", request.getValues().get("expiresAt")), tx);
               result = new UserPreferenceActionResult().withMessage("Added key " + key.getValueString("fingerprint") + ".").withFocusRowKey(key.getValueString("id"));
            }
            else
            {
               QRecord key = lockedKey(account, request.getRowKey(), tx);
               update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", key.getValue("id")).withValue("revokedAt", Instant.now()), tx);
               result = new UserPreferenceActionResult().withMessage("Key revoked.");
            }
            tx.commit();
            return (result);
         }
         catch(QException | RuntimeException e)
         {
            tx.rollback();
            throw e;
         }
      }
   }



   /*******************************************************************************
    ** Use only the session identity; service accounts stay in the admin tables.
    *******************************************************************************/
   private QRecord account(UserPreferenceRequestContext context, QBackendTransaction tx) throws QException
   {
      List<QRecord> rows = query(FileGatewayTables.ACCOUNT, new QQueryFilter()
         .withCriteria("userId", QCriteriaOperator.EQUALS, context.getUserId())
         .withCriteria("accountType", QCriteriaOperator.EQUALS, "USER").withOrderBy(new QFilterOrderBy("id", true)).withLimit(1), tx);
      return (rows.isEmpty() ? null : rows.getFirst());
   }



   /*******************************************************************************
    ** A transactional timestamp update acquires the same lock used by gateway auth.
    ** Re-read after the lock in case an administrator changed ownership meanwhile.
    *******************************************************************************/
   private QRecord lockedAccount(UserPreferenceRequestContext context, QBackendTransaction tx) throws QException
   {
      QRecord account = account(context, tx);
      if(account != null)
      {
         update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("modifyDate", Instant.now()), tx);
         QRecord current = account(context, tx);
         if(current == null || !Objects.equals(current.getValue("id"), account.getValue("id")))
         {
            throw (new QBadRequestException("Account: isn't a setting here"));
         }
         return (current);
      }
      return (null);
   }



   /*******************************************************************************
    ** Scope every key lookup to the caller's account and unrevoked rows.
    *******************************************************************************/
   private QQueryFilter keysFilter(QRecord account)
   {
      return (new QQueryFilter().withCriteria("accountId", QCriteriaOperator.EQUALS, account.getValue("id"))
         .withCriteria("revokedAt", QCriteriaOperator.IS_BLANK).withOrderBy(new QFilterOrderBy("id", true)));
   }



   /*******************************************************************************
    ** Check ownership both before and after locking the key against reassignment.
    *******************************************************************************/
   private QRecord lockedKey(QRecord account, String rowKey, QBackendTransaction tx) throws QException
   {
      QRecord key = ownKey(account, rowKey, tx);
      update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", key.getValue("id")).withValue("modifyDate", Instant.now()), tx);
      return (ownKey(account, rowKey, tx));
   }



   /*******************************************************************************
    ** Do not interpret an arbitrary caller-supplied key as an unrestricted ID.
    *******************************************************************************/
   private QRecord ownKey(QRecord account, String rowKey, QBackendTransaction tx) throws QException
   {
      if(account != null)
      {
         for(QRecord key : query(FileGatewayTables.SSH_KEY, keysFilter(account), tx))
         {
            if(key.getValueString("id").equals(rowKey))
            {
               return (key);
            }
         }
      }
      throw (new QBadRequestException("Key: isn't a setting here"));
   }



   /*******************************************************************************
    ** Normalize opaque user IDs to the account validator's 3-64 character syntax.
    ** Truncate before suffixing, so even a 64-character collision stays valid.
    *******************************************************************************/
   private String availableUsername(String userId, QBackendTransaction tx) throws QException
   {
      String base = userId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
      if(base.length() < 3)
      {
         base = "user-" + base;
      }
      base = base.substring(0, Math.min(64, base.length()));
      String username = base;
      int suffix = 2;
      while(!query(FileGatewayTables.ACCOUNT, new QQueryFilter().withCriteria("username", QCriteriaOperator.EQUALS, username).withLimit(1), tx).isEmpty())
      {
         String ending = "-" + suffix++;
         username = base.substring(0, Math.min(base.length(), 64 - ending.length())) + ending;
      }
      return (username);
   }



   /*******************************************************************************
    ** Standard actions retain customizers, configured audits and the caller's session.
    *******************************************************************************/
   private List<QRecord> query(String table, QQueryFilter filter, QBackendTransaction tx) throws QException
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(filter).withTransaction(tx)).getRecords());
   }



   /*******************************************************************************
    ** Preserve domain validation messages while withholding internal database errors.
    *******************************************************************************/
   private QRecord checked(QRecord record) throws QException
   {
      if(record.getErrors() != null && !record.getErrors().isEmpty())
      {
         List<String> messages = record.getErrors().stream().map(error -> error instanceof BadInputStatusMessage ? error.getMessage() : "Could not save this setting.").toList();
         throw (new QBadRequestException(String.join("\n", messages)));
      }
      return (record);
   }



   /*******************************************************************************
    ** Insert via ordinary table validation, including fingerprint uniqueness.
    *******************************************************************************/
   private QRecord insert(String table, QRecord record, QBackendTransaction tx) throws QException
   {
      return (checked(new InsertAction().execute(new InsertInput(table).withRecord(record).withTransaction(tx)).getRecords().getFirst()));
   }



   /*******************************************************************************
    ** Persist only supplied fields, with the original user's audit attribution.
    *******************************************************************************/
   private void update(String table, QRecord record, QBackendTransaction tx) throws QException
   {
      checked(new UpdateAction().execute(new UpdateInput(table).withRecords(List.of(record)).withTransaction(tx)).getRecords().getFirst());
   }
}
