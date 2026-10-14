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


import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.preferences.FileTransferPreferencesProvider;
import com.kingsrook.qqq.backend.core.actions.preferences.UserPreferencesAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceActionResult;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiCredentials;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInProcessSessions;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Self-service acceptance through core, strict HTTP routes and real SSH clients.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class FileTransferPreferencesTest
{
   private static final String SECTION = FileTransferPreferencesProvider.SECTION_NAME;
   private final UserPreferencesAction preferences = new UserPreferencesAction();



   /*******************************************************************************
    ** Connect permission alone reveals the section, independently of the admin app.
    *******************************************************************************/
   @Test
   void visibilityAndEmptyAccount() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         user(h, "A");
         assertThat(preferences.listSections()).hasSize(1);
         var section = preferences.getSection(SECTION);
         assertThat(section.getSection().getPermissionName()).isEqualTo("file_gateway.connect");
         assertThat(section.getSection().getAppName()).isNull();
         assertThat(section.getSection().getOrder()).isEqualTo(60);
         assertThat(section.getValues().getValues().get("username")).isEqualTo("");
         assertThat(section.getValues().getLists().get("keys")).isEmpty();
         assertThat(section.getSection().getLists().getFirst().getDescription()).contains("first key creates");
         assertThat(section.getSection().getFields()).noneMatch(field -> "host".equals(field.getName()));
         assertThat(section.getValues().getValues().get("port")).isEqualTo(h.runtime.getProtocol().getPort());
         assertThat(section.getValues().getValues().get("hostKeyFingerprint")).isNotNull();
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter())).isEmpty();
         user(h, "NONE");
         assertThat(preferences.listSections()).isEmpty();
         assertThatThrownBy(() -> preferences.getSection(SECTION)).isInstanceOf(QNotFoundException.class);
         assertThatThrownBy(() -> preferences.runAction(SECTION, "addKey", Map.of())).isInstanceOf(QNotFoundException.class);
         assertThatThrownBy(() -> preferences.patchSection(SECTION, Map.of())).isInstanceOf(QNotFoundException.class);
      }
   }



   /*******************************************************************************
    ** Host is optional; heartbeat data comes from the most recent server.
    *******************************************************************************/
   @Test
   void publicHostAndLatestHeartbeat() throws Exception
   {
      try(var h = new FileGatewayTestServer(Clock.systemUTC(), instance -> { }, 0, false, false))
      {
         assertThat(h.config.getPublicHostName()).isNull();
         UserPreferencesMetaData.of(h.instance).getSections().clear();
         FileTransferPreferencesProvider.register(h.instance, h.config.withPublicHostName("sftp.example.test"));
         user(h, "A");
         assertThat(preferences.getSection(SECTION).getValues().getValues()).containsEntry("host", "sftp.example.test").containsEntry("port", null);
         h.audit.insert(FileGatewayTables.SERVER, new QRecord().withValue("podName", "new").withValue("port", 2223)
            .withValue("lastSeenAt", Instant.now()).withValue("hostKeyFingerprint", "SHA256:new"));
         h.audit.insert(FileGatewayTables.SERVER, new QRecord().withValue("podName", "old").withValue("port", 2222)
            .withValue("lastSeenAt", Instant.now().minusSeconds(60)).withValue("hostKeyFingerprint", "SHA256:old"));
         assertThat(preferences.getSection(SECTION).getValues().getValues()).containsEntry("port", 2223).containsEntry("hostKeyFingerprint", "SHA256:new");
      }
   }



   /*******************************************************************************
    ** Creation uses schema defaults, reuses the account and preserves audit identity.
    *******************************************************************************/
   @Test
   void createsAndReusesAccountWithAudits() throws Exception
   {
      try(var h = new FileGatewayTestServer(Clock.systemUTC(), instance ->
      {
         for(String table : List.of(FileGatewayTables.ACCOUNT, FileGatewayTables.SSH_KEY))
         {
            instance.getTable(table).withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.RECORD));
         }
         instance.getTable(FileGatewayTables.ACCOUNT).getField("maxFileSizeMb").setDefaultValue(321);
         instance.setEnforcePermissionsInCoreActions(true);
      }))
      {
         user(h, "A");
         var key = pair();
         var first = add(key, "Laptop");
         assertThat(first.getMessage()).contains(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key.getPublic()));
         assertThat(first.getSection().getValues().getValues().get("username")).isEqualTo("user-a");
         assertThat(first.getSection().getValues().getLists().get("keys").getFirst().getValues()).containsEntry("keyType", "ssh-ed25519").doesNotContainKey("publicKey");
         add(pair(), "Backup");
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter())).singleElement().satisfies(account ->
         {
            assertThat(account.getValueString("userId")).isEqualTo("A");
            assertThat(account.getValueString("accountType")).isEqualTo("USER");
            assertThat(account.getValueString("rootMode")).isEqualTo("FULL_TREE");
            assertThat(account.getValueString("status")).isEqualTo("ACTIVE");
            assertThat(account.getValueInteger("maxFileSizeMb")).isEqualTo(321);
            assertThat(account.getValueInteger("maxConcurrentSessions")).isEqualTo(4);
         });
         assertThat(preferences.getSection(SECTION).getValues().getLists().get("keys")).hasSize(2);
         var audits = h.audit.system(() -> new QueryAction().execute(new QueryInput("audit")).getRecords());
         var auditTables = h.audit.system(() -> new QueryAction().execute(new QueryInput("auditTable")).getRecords());
         var auditUsers = h.audit.system(() -> new QueryAction().execute(new QueryInput("auditUser")).getRecords());
         Object tableId = auditTables.stream().filter(row -> FileGatewayTables.SSH_KEY.equals(row.getValueString("name"))).findFirst().orElseThrow().getValue("id");
         Object userId = auditUsers.stream().filter(row -> "Test user A".equals(row.getValueString("name"))).findFirst().orElseThrow().getValue("id");
         assertThat(audits).anySatisfy(audit ->
         {
            assertThat(audit.getValue("auditTableId")).isEqualTo(tableId);
            assertThat(audit.getValue("auditUserId")).isEqualTo(userId);
            assertThat(audit.getValueString("clientChannel")).isEqualTo("next-ui/preferences-test");
         });
         assertThat(QContext.getQSession().getUser().getIdReference()).isEqualTo("A");
      }
   }



   /*******************************************************************************
    ** Invalid first keys roll back the account; duplicates retain table messages.
    *******************************************************************************/
   @Test
   void rejectsWeakDuplicateAndMissingKeys() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         user(h, "A");
         assertThatThrownBy(() -> add(KeyUtils.generateKeyPair("ssh-rsa", 2048), "Weak"))
            .isInstanceOf(QBadRequestException.class).hasMessageContaining("at least 3072 bits");
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter())).isEmpty();
         assertThatThrownBy(() -> preferences.runAction(SECTION, "addKey", Map.of()))
            .isInstanceOf(QBadRequestException.class).hasMessageContaining("Public key: is required").hasMessageContaining("Label: is required");
         var key = pair();
         add(key, "First");
         assertThatThrownBy(() -> add(key, "Duplicate")).isInstanceOf(QBadRequestException.class).hasMessageContaining("Fingerprint");
         user(h, "B");
         assertThatThrownBy(() -> add(key, "Other user duplicate")).isInstanceOf(QBadRequestException.class).hasMessageContaining("Fingerprint");
         assertThat(h.audit.query(FileGatewayTables.ACCOUNT, new QQueryFilter())).hasSize(1);
         assertThat(h.audit.query(FileGatewayTables.SSH_KEY, new QQueryFilter())).hasSize(1);
      }
   }



   /*******************************************************************************
    ** Normalization, truncation and suffixes obey the account validator.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"Alice@EXAMPLE.COM", "A", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
   void uniquifiesUsername(String id) throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         user(h, id);
         String base = id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
         base = base.length() < 3 ? "user-" + base : base.substring(0, Math.min(64, base.length()));
         h.account(base, "B");
         String second = base.substring(0, Math.min(62, base.length())) + "-2";
         h.account(second, "B");
         var added = add(pair(), "Key");
         assertThat(added.getSection().getValues().getValues().get("username")).isEqualTo(base.substring(0, Math.min(62, base.length())) + "-3");
      }
   }



   /*******************************************************************************
    ** Lists and mutations exclude other users, revoked keys and service accounts.
    *******************************************************************************/
   @Test
   void isolatesKeysAndRejectsIdentityInjection() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         var other = h.key(h.account("bob", "B"), pair());
         var account = h.account("alice", "A");
         var revoked = h.key(account, pair());
         h.audit.update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", revoked.getValue("id")).withValue("revokedAt", Instant.now()));
         var service = h.account("service", "A");
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", service.getValue("id")).withValue("accountType", "SERVICE"));
         var serviceKey = h.key(service, pair());
         user(h, "A");
         var own = add(pair(), "Own");
         assertThat(preferences.getSection(SECTION).getValues().getLists().get("keys")).extracting(row -> row.getKey()).containsExactly(own.getFocusRowKey());
         for(QRecord excluded : List.of(other, revoked, serviceKey))
         {
            assertThatThrownBy(() -> preferences.runAction(SECTION, "revokeKey", Map.of("rowKey", excluded.getValueString("id"))))
               .isInstanceOf(QBadRequestException.class);
            assertThatThrownBy(() -> patch(excluded.getValueString("id"), Map.of("label", "Stolen")))
               .isInstanceOf(QBadRequestException.class).hasMessageContaining("isn't a setting here");
         }
         assertThatThrownBy(() -> preferences.runAction(SECTION, "addKey", Map.of("userId", "B"))).isInstanceOf(QBadRequestException.class);
         assertThatThrownBy(() -> preferences.patchSection(SECTION, Map.of("userId", "B"))).isInstanceOf(QBadRequestException.class);
         assertThatThrownBy(() -> patch(own.getFocusRowKey(), Map.of("accountId", other.getValue("accountId"))))
            .isInstanceOf(QBadRequestException.class);
         assertThat(h.audit.query(FileGatewayTables.SSH_KEY, new QQueryFilter())).noneMatch(row -> "Stolen".equals(row.getValueString("label")));
      }
   }



   /*******************************************************************************
    ** PATCH changes only label/expiry, supports clearing expiry and rejects readonly fields.
    *******************************************************************************/
   @Test
   void editsOnlyMutableFields() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         user(h, "A");
         String key = add(pair(), "Laptop").getFocusRowKey();
         Instant expires = Instant.parse("2030-01-01T00:00:00Z");
         patch(key, Map.of("label", "Work laptop", "expiresAt", expires.toString()));
         var values = preferences.getSection(SECTION).getValues().getLists().get("keys").getFirst().getValues();
         assertThat(values).containsEntry("label", "Work laptop").containsEntry("expiresAt", expires);
         Map<String, Object> clear = new HashMap<>();
         clear.put("expiresAt", null);
         patch(key, clear);
         assertThat(preferences.getSection(SECTION).getValues().getLists().get("keys").getFirst().getValues()).containsEntry("expiresAt", null);
         for(String field : List.of("fingerprint", "keyType", "lastUsedAt"))
         {
            assertThatThrownBy(() -> patch(key, Map.of(field, "2030-01-01T00:00:00Z"))).isInstanceOf(QBadRequestException.class).hasMessageContaining("can't be changed");
         }
         for(String field : List.of("username", "port", "hostKeyFingerprint"))
         {
            assertThatThrownBy(() -> preferences.patchSection(SECTION, Map.of("values", Map.of(field, "22"))))
               .isInstanceOf(QBadRequestException.class).hasMessageContaining("can't be changed");
         }
         assertThatThrownBy(() -> patch(key, Map.of("label", " "))).isInstanceOf(QBadRequestException.class).hasMessageContaining("Label: is required");
         assertThatThrownBy(() -> patch(key, Map.of("publicKey", "replacement"))).isInstanceOf(QBadRequestException.class);
      }
   }



   /*******************************************************************************
    ** A self-service key signs in and lists SFTP; revocation closes and refuses it.
    *******************************************************************************/
   @Test
   void addedKeyLogsInAndRevokedKeyCannot() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         user(h, "A");
         var pair = pair();
         var added = add(pair, "Laptop");
         String username = added.getSection().getValues().getValues().get("username").toString();
         try(var session = h.login(username, pair); var client = SftpClientFactory.instance().createSftpClient(session))
         {
            assertThat(client.readDir("/")).anyMatch(entry -> "testapp".equals(entry.getFilename()));
            assertThat(preferences.getSection(SECTION).getValues().getLists().get("keys").getFirst().getValues().get("lastUsedAt")).isNotNull();
            assertThat(preferences.runAction(SECTION, "revokeKey", Map.of("rowKey", added.getFocusRowKey())).getMessage()).isEqualTo("Key revoked.");
            h.runtime.getProtocol().getAuthenticator().poll();
            session.waitFor(Set.of(org.apache.sshd.client.session.ClientSession.ClientSessionEvent.CLOSED), Duration.ofSeconds(5));
            assertThat(session.isClosed()).isTrue();
         }
         assertThat(h.audit.query(FileGatewayTables.SSH_KEY, new QQueryFilter()).getFirst().getValue("revokedAt")).isNotNull();
         assertThat(preferences.getSection(SECTION).getValues().getLists().get("keys")).isEmpty();
         assertThatThrownBy(() -> h.login(username, pair)).isInstanceOf(Exception.class);
      }
   }



   /*******************************************************************************
    ** The strict UI route exposes actions and translates domain failures to 400.
    *******************************************************************************/
   @Test
   void preferencesThroughStrictUiApi() throws Exception
   {
      try(var h = new FileGatewayTestServer())
      {
         QSession session = user(h, "A");
         String token = UiApiInProcessSessions.register(session, Duration.ofMinutes(1));
         try
         {
            var invoker = UiApiInvoker.forServer(h.http, "next-ui");
            var get = invoker.invoke(new UiApiRequest().withMethod(HttpMethod.GET).withPath("/preferences")
               .withCredentials(UiApiCredentials.ofInProcessSession(token)));
            assertThat(get.getStatus()).isEqualTo(200);
            assertThat(get.getJson().toString()).contains("file-transfer", "addKey", "revokeKey");
            var added = invoker.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/preferences/{sectionName}/actions/{actionName}")
               .withPathParam("sectionName", SECTION).withPathParam("actionName", "addKey").withCredentials(UiApiCredentials.ofInProcessSession(token))
               .withJsonBody(Map.of("values", Map.of("publicKey", PublicKeyEntry.toString(pair().getPublic()), "label", "HTTP key"))));
            assertThat(added.getStatus()).as(added.getBody()).isEqualTo(200);
            assertThat(added.getJson().toString()).contains("SHA256:", "HTTP key");
            var invalid = invoker.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/preferences/{sectionName}/actions/{actionName}")
               .withPathParam("sectionName", SECTION).withPathParam("actionName", "addKey").withCredentials(UiApiCredentials.ofInProcessSession(token))
               .withJsonBody(Map.of("values", Map.of("publicKey", "invalid", "label", "Invalid"))));
            assertThat(invalid.getStatus()).isEqualTo(400);
            assertThat(invalid.getBody()).contains("at least 3072 bits");
         }
         finally
         {
            UiApiInProcessSessions.revoke(token);
         }
      }
   }



   /*******************************************************************************
    ** Set a real non-system session with only the user's normal permissions.
    *******************************************************************************/
   private QSession user(FileGatewayTestServer h, String id) throws Exception
   {
      QSession session = h.audit.system(() -> new QAuthenticationModuleDispatcher().getQModule(h.instance.getAuthentication()).createAutomatedSessionForUser(h.instance, id));
      session.setPermissions(FileGatewayTestServer.GRANTS.getOrDefault(id, Set.of("file_gateway.connect")));
      session.setClientChannel("next-ui/preferences-test");
      QContext.init(h.instance, session);
      return (session);
   }



   /*******************************************************************************
    ** Generate a supported public key entirely in memory.
    *******************************************************************************/
   private KeyPair pair() throws Exception
   {
      return (KeyUtils.generateKeyPair("ssh-ed25519", 256));
   }



   /*******************************************************************************
    ** Exercise registered action input validation rather than calling the provider.
    *******************************************************************************/
   private UserPreferenceActionResult add(KeyPair key, String label) throws Exception
   {
      return (preferences.runAction(SECTION, "addKey", Map.of("values", Map.of("publicKey", PublicKeyEntry.toString(key.getPublic()), "label", label))));
   }



   /*******************************************************************************
    ** Send the same partial row body as a preferences client.
    *******************************************************************************/
   private void patch(String key, Map<String, ?> values) throws Exception
   {
      preferences.patchSection(SECTION, Map.of("lists", Map.of("keys", List.of(Map.of("key", key, "values", values)))));
   }
}
