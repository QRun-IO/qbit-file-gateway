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

package com.kingsrook.qbits.filegateway.sftp;


import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;


/*******************************************************************************
 ** Database-backed SSH key authentication, account limits, IP windows and live revocation.
 *******************************************************************************/
public class SshKeyAuthenticator implements PublickeyAuthenticator, FileGatewayAuthenticatorInterface
{
   private static final QLogger LOG = QLogger.getLogger(SshKeyAuthenticator.class);
   private final QInstance instance;
   private final FileGatewayQBitConfig config;
   private final AuditWriter audit;
   private final Clock clock;
   private final String podName;
   private final Map<String, GatewaySession> sessions = new ConcurrentHashMap<>();
   private final Map<String, Deque<Instant>> failures = new HashMap<>();
   private final Map<String, Deque<Instant>> connections = new HashMap<>();
   private final Map<String, Instant> ipLocks = new HashMap<>();
   private final Map<String, String> ipLockAccounts = new HashMap<>();
   private final Map<String, String> failureAccounts = new HashMap<>();



   /*******************************************************************************
    ** Bind authentication and audit dependencies.
    *******************************************************************************/
   public SshKeyAuthenticator(QInstance instance, FileGatewayQBitConfig config, AuditWriter audit, Clock clock, String podName)
   {
      this.instance = instance;
      this.config = config;
      this.audit = audit;
      this.clock = clock;
      this.podName = podName;
   }



   /*******************************************************************************
    ** Track TCP accept and enforce the pre-authentication connection rate. The
    ** connection-log row is written at the first sign-in attempt (or a refusal),
    ** so TCP health probes and port scans that never authenticate leave no row.
    *******************************************************************************/
   public synchronized void accepted(ServerSession ssh) throws QException
   {
      GatewaySession state = new GatewaySession(instance, ssh, clock);
      ssh.setAttribute(GatewaySession.ATTRIBUTE, state);
      state.connectedAt = clock.instant();
      sessions.put(state.id, state);
      prune(connections, clock.instant().minusSeconds(60));
      Deque<Instant> window = connections.computeIfAbsent(state.remoteIp, ignored -> new ArrayDeque<>());
      window.addLast(clock.instant());
      if(window.size() > config.getMaxConnectionsPerIpPerMinute())
      {
         outcome(state, "LIMIT", "Connection rate limit exceeded");
         ssh.close(true);
      }
   }



   /*******************************************************************************
    ** Validate the account and offered public key, then mint the user session.
    *******************************************************************************/
   @Override
   public synchronized boolean authenticate(String username, PublicKey publicKey, ServerSession ssh)
   {
      GatewaySession state = ssh.getAttribute(GatewaySession.ATTRIBUTE);
      if(state == null || ssh.isClosing())
      {
         return (false);
      }
      try
      {
         return (audit.withAccountLock(username, () -> authenticateLocked(username, publicKey, ssh, state)));
      }
      catch(Exception e)
      {
         LOG.warn("SFTP authentication service failed", e);
         state.revoke();
         state.disconnectReason = "Authentication service unavailable";
         ssh.close(true);
         return (false);
      }
   }



   /*******************************************************************************
    ** Account checks and reservations share one database transaction across pods.
    *******************************************************************************/
   private boolean authenticateLocked(String username, PublicKey publicKey, ServerSession ssh, GatewaySession state) throws QException
   {
      if(state.account != null)
      {
         return (username.equals(state.account.getValueString("username")) && state.key != null
            && KeyUtils.getFingerPrint(BuiltinDigests.sha256, publicKey).equals(state.key.getValueString("fingerprint")));
      }
      QRecord account = first(FileGatewayTables.ACCOUNT, "username", username);
      String fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, publicKey);
      ensureConnectionRow(state);
      audit.update(FileGatewayTables.CONNECTION_LOG, new QRecord().withValue("id", state.connectionId)
         .withValue("username", username).withValue("keyFingerprint", fingerprint).withValue("clientVersion", ssh.getClientVersion())
         .withValue("accountId", account == null ? null : account.getValue("id")));
      Instant now = clock.instant();
      if(account != null && account.getValue("lockedUntil") == null && username.equals(ipLockAccounts.get(state.remoteIp)))
      {
         ipLocks.remove(state.remoteIp);
         ipLockAccounts.remove(state.remoteIp);
         failures.remove(state.remoteIp);
         failureAccounts.remove(state.remoteIp);
      }
      ipLocks.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
      ipLockAccounts.keySet().retainAll(ipLocks.keySet());
      if(ipLocks.containsKey(state.remoteIp) || (account != null && (future(account, "lockedUntil") || "LOCKED".equals(account.getValueString("status")))))
      {
         return (outcome(state, "LOCKED_OUT", "Authentication locked"));
      }
      if(account == null)
      {
         return (failed(state, null, "AUTH_FAILED", "Unknown account or key"));
      }
      if(!"ACTIVE".equals(account.getValueString("status")))
      {
         return (failed(state, account, "DISABLED", "Account disabled"));
      }
      if(expired(account, "expiresAt"))
      {
         return (failed(state, account, "EXPIRED", "Account expired"));
      }
      if(!CidrMatcher.matches(state.remoteIp, account.getValueString("allowedCidrs")))
      {
         return (failed(state, account, "IP_DENIED", "Address outside allowlist"));
      }
      QRecord key = first(FileGatewayTables.SSH_KEY, "fingerprint", fingerprint);
      if(key == null || !account.getValue("id").equals(key.getValue("accountId")) || key.getValue("revokedAt") != null)
      {
         return (failed(state, account, "AUTH_FAILED", "Unknown or revoked key"));
      }
      if(expired(key, "expiresAt"))
      {
         return (failed(state, account, "EXPIRED", "Key expired"));
      }
      int limit = account.getValueInteger("maxConcurrentSessions") == null ? config.getDefaultMaxConcurrentSessions() : account.getValueInteger("maxConcurrentSessions");
      ////////////////////////////////////////////////////////////////////////
      // only live rows: connected, not disconnected, seen in the last 3 min //
      ////////////////////////////////////////////////////////////////////////
      QQueryFilter liveFilter = filter("accountId", account.getValue("id"))
         .withCriteria(new QFilterCriteria("outcome", QCriteriaOperator.EQUALS, "CONNECTED"))
         .withCriteria(new QFilterCriteria("disconnectedAt", QCriteriaOperator.IS_BLANK))
         .withCriteria(new QFilterCriteria("lastSeenAt", QCriteriaOperator.GREATER_THAN, now.minusSeconds(180)));
      long active = audit.query(FileGatewayTables.CONNECTION_LOG, liveFilter).size();
      if(active >= limit)
      {
         return (outcome(state, "LIMIT", "Concurrent session limit exceeded"));
      }
      state.account = account;
      state.key = key;
      try
      {
         state.token();
      }
      catch(QException e)
      {
         state.account = null;
         state.key = null;
         return (failed(state, account, "AUTH_FAILED", "no file_gateway.connect"));
      }
      outcome(state, "CONNECTED", null);
      return (true);
   }



   /*******************************************************************************
    ** MINA has verified the signature, not just accepted an unsigned key probe.
    *******************************************************************************/
   public synchronized void authenticated(ServerSession ssh) throws QException
   {
      GatewaySession state = ssh.getAttribute(GatewaySession.ATTRIBUTE);
      if(state == null || state.account == null)
      {
         throw (new QException("Missing authenticated gateway identity"));
      }
      QRecord account = state.account;
      QRecord key = state.key;
      Instant now = clock.instant();
      audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id"))
         .withValue("failedLoginCount", 0).withValue("failedLoginWindowAt", null)
         .withValue("lastLoginAt", now).withValue("lastLoginIp", state.remoteIp));
      audit.update(FileGatewayTables.SSH_KEY, new QRecord().withValue("id", key.getValue("id"))
         .withValue("lastUsedAt", now).withValue("lastUsedIp", state.remoteIp));
   }



   /*******************************************************************************
    ** Persist account failure windows and apply the per-IP lockout.
    *******************************************************************************/
   private boolean failed(GatewaySession state, QRecord account, String result, String reason) throws QException
   {
      Instant now = clock.instant();
      prune(failures, now.minus(config.getLockoutWindow()));
      failureAccounts.keySet().retainAll(failures.keySet());
      Deque<Instant> window = failures.computeIfAbsent(state.remoteIp, ignored -> new ArrayDeque<>());
      String username = account == null ? "" : account.getValueString("username");
      if(window.isEmpty())
      {
         failureAccounts.put(state.remoteIp, username);
      }
      else if(!username.equals(failureAccounts.get(state.remoteIp)))
      {
         failureAccounts.put(state.remoteIp, "");
      }
      window.addLast(now);
      boolean lock = window.size() >= config.getLockoutThreshold();
      if(lock)
      {
         ipLocks.put(state.remoteIp, now.plus(config.getLockoutDuration()));
         ipLockAccounts.put(state.remoteIp, failureAccounts.get(state.remoteIp));
      }
      if(account != null)
      {
         Instant start = account.getValueInstant("failedLoginWindowAt");
         Integer oldCount = account.getValueInteger("failedLoginCount");
         int count = start == null || !start.plus(config.getLockoutWindow()).isAfter(now) ? 1 : (oldCount == null ? 0 : oldCount) + 1;
         QRecord update = new QRecord().withValue("id", account.getValue("id")).withValue("failedLoginCount", count)
            .withValue("failedLoginWindowAt", count == 1 ? now : start);
         if(count >= config.getLockoutThreshold())
         {
            update.setValue("lockedUntil", now.plus(config.getLockoutDuration()));
            lock = true;
         }
         audit.update(FileGatewayTables.ACCOUNT, update);
      }
      if(lock)
      {
         audit.system(() ->
         {
            QAlerts.raise(new AlertSignal("filegateway.lockout").withDedupKey(account == null ? state.remoteIp : account.getValueString("id"))
               .withSummary("File gateway authentication locked"));
            return (null);
         });
      }
      return (outcome(state, result, reason));
   }



   /*******************************************************************************
    ** Write the connection-log row on first need (an auth attempt or a refusal).
    ** Its outcome stays AUTH_FAILED until the sign-in succeeds.
    *******************************************************************************/
   private void ensureConnectionRow(GatewaySession state) throws QException
   {
      if(state.connectionId == null)
      {
         Instant at = state.connectedAt == null ? clock.instant() : state.connectedAt;
         state.connectionId = audit.insert(FileGatewayTables.CONNECTION_LOG, new QRecord()
            .withValue("sessionId", state.id).withValue("remoteIp", state.remoteIp).withValue("podName", podName)
            .withValue("connectedAt", at).withValue("lastSeenAt", clock.instant())
            .withValue("clientChannel", "sftp").withValue("outcome", "AUTH_FAILED")).getValueInteger("id");
      }
   }



   /*******************************************************************************
    ** Record the authentication outcome without credentials.
    *******************************************************************************/
   private boolean outcome(GatewaySession state, String outcome, String reason) throws QException
   {
      ensureConnectionRow(state);
      audit.update(FileGatewayTables.CONNECTION_LOG, new QRecord().withValue("id", state.connectionId)
         .withValue("outcome", outcome).withValue("disconnectReason", reason).withValue("lastSeenAt", clock.instant()));
      if(reason != null)
      {
         state.disconnectReason = reason;
      }
      return (false);
   }



   /*******************************************************************************
    ** Revoke credentials and finalize connection accounting.
    *******************************************************************************/
   public synchronized void closed(ServerSession ssh) throws QException
   {
      GatewaySession state = ssh.removeAttribute(GatewaySession.ATTRIBUTE);
      if(state == null)
      {
         return;
      }
      state.revoke();
      sessions.remove(state.id);
      if(state.account != null && !ssh.isAuthenticated())
      {
         outcome(state, "AUTH_FAILED", "Public key signature was not verified");
      }
      if(state.connectionId != null)
      {
         audit.update(FileGatewayTables.CONNECTION_LOG, new QRecord().withValue("id", state.connectionId)
            .withValue("disconnectedAt", clock.instant()).withValue("disconnectReason", state.disconnectReason)
            .withValue("bytesIn", 0L).withValue("bytesOut", 0L)
            .withValue("operationCount", state.operations));
      }
   }



   /*******************************************************************************
    ** Finish any callbacks still queued when MINA has stopped accepting work.
    *******************************************************************************/
   public synchronized void shutdown() throws QException
   {
      for(GatewaySession state : List.copyOf(sessions.values()))
      {
         closed(state.ssh);
      }
   }



   /*******************************************************************************
    ** Re-read live accounts and keys and heartbeat open connection rows.
    *******************************************************************************/
   public synchronized void poll() throws QException
   {
      for(GatewaySession state : List.copyOf(sessions.values()))
      {
         if(state.account == null)
         {
            continue;
         }
         QRecord account = first(FileGatewayTables.ACCOUNT, "id", state.account.getValue("id"));
         QRecord key = first(FileGatewayTables.SSH_KEY, "id", state.key.getValue("id"));
         QRecord connection = first(FileGatewayTables.CONNECTION_LOG, "id", state.connectionId);
         if(account == null || key == null || !"ACTIVE".equals(account.getValueString("status"))
            || expired(account, "expiresAt") || expired(key, "expiresAt") || key.getValue("revokedAt") != null
            || connection == null || connection.getValue("disconnectedAt") != null || connection.getValue("endRequestedAt") != null)
         {
            state.disconnectReason = connection != null && connection.getValueString("disconnectReason") != null
               ? connection.getValueString("disconnectReason") : "Account or key revoked";
            state.revoke();
            state.ssh.close(true);
         }
         else
         {
            state.account = account;
            audit.update(FileGatewayTables.CONNECTION_LOG, new QRecord().withValue("id", state.connectionId)
               .withValue("lastSeenAt", clock.instant()).withValue("operationCount", state.operations));
         }
      }
   }



   /*******************************************************************************
    ** Find gateway-owned state.
    *******************************************************************************/
   private QRecord first(String table, String field, java.io.Serializable value) throws QException
   {
      return (audit.query(table, filter(field, value)).stream().findFirst().orElse(null));
   }



   /*******************************************************************************
    ** Equality query for owned tables.
    *******************************************************************************/
   private QQueryFilter filter(String field, java.io.Serializable value)
   {
      return (new QQueryFilter(new QFilterCriteria(field, QCriteriaOperator.EQUALS, value)));
   }



   /*******************************************************************************
    ** Whether a timestamp lies in the future.
    *******************************************************************************/
   private boolean future(QRecord row, String field)
   {
      Instant value = row.getValueInstant(field);
      return (value != null && value.isAfter(clock.instant()));
   }



   /*******************************************************************************
    ** Whether an optional expiry has passed.
    *******************************************************************************/
   private boolean expired(QRecord row, String field)
   {
      Instant value = row.getValueInstant(field);
      return (value != null && !value.isAfter(clock.instant()));
   }



   /*******************************************************************************
    ** Bound in-memory IP windows to their active interval.
    *******************************************************************************/
   private void prune(Map<String, Deque<Instant>> windows, Instant cutoff)
   {
      windows.values().forEach(window -> window.removeIf(instant -> !instant.isAfter(cutoff)));
      windows.entrySet().removeIf(entry -> entry.getValue().isEmpty());
   }
}
