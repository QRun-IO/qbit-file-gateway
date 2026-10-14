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

package com.kingsrook.qbits.filegateway.upload;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;

/*******************************************************************************
 ** Durable upload lifecycle. Account row transactions serialize quotas and renames across pods.
 *******************************************************************************/
public class UploadJobs
{
   public static final long MIB = 1024L * 1024L;
   private final AuditWriter audit;
   private final GatewayObjectStore store;
   private final FileGatewayQBitConfig config;
   private final Clock clock;

   /*******************************************************************************
    ** Share storage and persistence between the virtual tree and workers.
    *******************************************************************************/
   public UploadJobs(AuditWriter audit, GatewayObjectStore store, FileGatewayQBitConfig config, Clock clock)
   {
      this.audit = audit;
      this.store = store;
      this.config = config;
      this.clock = clock;
   }

   /*******************************************************************************
    ** The target was resolved through the user's metadata; endpoint routing can reuse this seam.
    *******************************************************************************/
   public SeekableByteChannel receive(GatewaySession session, String path, String table, Integer profile) throws IOException
   {
      String name = path.substring(path.lastIndexOf('/') + 1);
      if(name.isBlank() || name.contains(":") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl))
      {
         throw (new IOException("Invalid upload filename"));
      }
      if(!session.operationAllowed())
      {
         throw (new IOException("Operation limit exceeded"));
      }
      try
      {
         String date = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC).format(clock.instant());
         String key = "file-gateway/uploads/" + session.account.getValue("id") + "/" + date + "/" + UUID.randomUUID() + "/" + name;
         QRecord job = audit.insert(FileGatewayTables.UPLOAD_JOB, new QRecord()
            .withValue("accountId", session.account.getValue("id")).withValue("connectionLogId", session.connectionId)
            .withValue("remoteAddress", session.remoteIp).withValue("tableName", table).withValue("savedBulkLoadProfileId", profile)
            .withValue("consumerName", "bulk-load").withValue("path", path).withValue("fileName", name).withValue("storageKey", key)
            .withValue("status", "RECEIVING").withValue("sizeBytes", 0L).withValue("attempts", 0).withValue("createDate", clock.instant()));
         long started = System.nanoTime();
         return (new UploadChannel(store, key, Math.toIntExact(config.getUploadPartSizeMb() * MIB), config.getUploadReorderWindowMb() * MIB, new UploadChannel.Lifecycle()
         {
            @Override
            public void reserve(long size) throws IOException
            {
               reserveBytes(job, size);
            }

            @Override
            public void complete(GatewayObjectStore.Receipt receipt) throws IOException
            {
               try
               {
                  if(session.ssh.isClosing() || !session.ssh.isOpen())
                  {
                     throw (new IOException("Connection ended before upload close"));
                  }
                  job.withValue("sizeBytes", receipt.size()).withValue("sha256", receipt.sha256());
                  audit.update(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("id", job.getValue("id"))
                     .withValue("sizeBytes", receipt.size()).withValue("sha256", receipt.sha256()));
                  if(!temporary(name))
                  {
                     queue(job, path, name);
                  }
                  log(session, job, "OK", null, started);
               }
               catch(QException e)
               {
                  throw (new IOException("Unable to finalize upload", e));
               }
            }

            @Override
            public void abort(String message, boolean limit) throws IOException
            {
               try
               {
                  QRecord outcome = new QRecord().withValue("id", job.getValue("id"))
                     .withValue("status", limit ? "REJECTED" : "ABORTED").withValue("errorMessage", message).withValue("finishedAt", clock.instant());
                  audit.update(FileGatewayTables.UPLOAD_JOB, outcome);
                  UploadNotifications.send(audit, job, outcome, session.account);
                  log(session, job, limit ? "LIMIT" : "ERROR", message, started);
               }
               catch(QException e)
               {
                  throw (new IOException("Unable to audit aborted upload", e));
               }
            }
         }));
      }
      catch(QException e)
      {
         throw (new IOException("Unable to create upload job", e));
      }
   }

   /*******************************************************************************
    ** Charge the high-water byte count under an account lock, including concurrent uploads.
    *******************************************************************************/
   private void reserveBytes(QRecord job, long size) throws IOException
   {
      try
      {
         String rejection = audit.withAccountLock("id", job.getValue("accountId"), () ->
         {
            QRecord account = audit.query(FileGatewayTables.ACCOUNT, equal("id", job.getValue("accountId"))).getFirst();
            Integer maximum = account.getValueInteger("maxFileSizeMb");
            if(size > (maximum == null ? config.getDefaultMaxFileSizeMb() : maximum) * MIB)
            {
               return ("File size limit exceeded");
            }
            Integer daily = account.getValueInteger("maxUploadMbPerDay");
            if(daily != null)
            {
               var midnight = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
               long used = 0;
               for(QRecord other : audit.query(FileGatewayTables.UPLOAD_JOB, equal("accountId", job.getValue("accountId"))
                  .withCriteria(new QFilterCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, midnight))))
               {
                  if(!Objects.equals(other.getValue("id"), job.getValue("id")))
                  {
                     used += other.getValueLong("sizeBytes") == null ? 0 : other.getValueLong("sizeBytes");
                  }
               }
               if(size > daily * MIB - used)
               {
                  return ("Daily upload quota exceeded");
               }
            }
            audit.update(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("id", job.getValue("id")).withValue("sizeBytes", size));
            return (null);
         });
         if(rejection != null)
         {
            throw (new UploadLimitException(rejection));
         }
         job.withValue("sizeBytes", size);
      }
      catch(QException e)
      {
         throw (new IOException("Unable to reserve upload quota", e));
      }
   }

   /*******************************************************************************
    ** Close and rename share one idempotent transition, with target-aware deduplication.
    *******************************************************************************/
   public void queue(QRecord job, String path, String name) throws QException
   {
      audit.withAccountLock("id", job.getValue("accountId"), () ->
      {
         QRecord current = audit.query(FileGatewayTables.UPLOAD_JOB, equal("id", job.getValue("id"))).getFirst();
         if(!"RECEIVING".equals(current.getValueString("status")) || current.getValueString("sha256") == null)
         {
            return (null);
         }
         QRecord update = new QRecord().withValue("id", job.getValue("id")).withValue("path", path).withValue("fileName", name);
         if(temporary(name))
         {
            audit.update(FileGatewayTables.UPLOAD_JOB, update);
            return (null);
         }
         update.withValue("status", "QUEUED").withValue("queuedAt", clock.instant());
         QRecord account = audit.query(FileGatewayTables.ACCOUNT, equal("id", job.getValue("accountId"))).getFirst();
         if(Boolean.TRUE.equals(account.getValueBoolean("dedupeUploads")))
         {
            for(QRecord candidate : audit.query(FileGatewayTables.UPLOAD_JOB, equal("accountId", job.getValue("accountId"))
               .withCriteria(new QFilterCriteria("sha256", QCriteriaOperator.EQUALS, current.getValue("sha256")))
               .withCriteria(new QFilterCriteria("status", QCriteriaOperator.EQUALS, "SUCCEEDED"))
               .withCriteria(new QFilterCriteria("finishedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, clock.instant().minus(config.getDedupeWindow())))))
            {
               if(Objects.equals(current.getValue("tableName"), candidate.getValue("tableName"))
                  && Objects.equals(current.getValue("savedBulkLoadProfileId"), candidate.getValue("savedBulkLoadProfileId"))
                  && Objects.equals(current.getValue("aliasId"), candidate.getValue("aliasId"))
                  && Objects.equals(current.getValue("consumerName"), candidate.getValue("consumerName")))
               {
                  update.withValue("status", "DUPLICATE").withValue("duplicateOfJobId", candidate.getValue("id"))
                     .withValue("finishedAt", clock.instant()).withValue("resultSummary", "Skipped: duplicate of upload " + candidate.getValue("id"));
                  break;
               }
            }
         }
         audit.update(FileGatewayTables.UPLOAD_JOB, update);
         return (null);
      });
   }

   /*******************************************************************************
    ** Closed temporary files can be renamed only within their original upload folder.
    *******************************************************************************/
   public void rename(Integer accountId, String source, String target) throws IOException
   {
      if(!source.substring(0, source.lastIndexOf('/')).equals(target.substring(0, target.lastIndexOf('/'))))
      {
         throw (new IOException("Rename must stay inside one upload folder"));
      }
      String name = target.substring(target.lastIndexOf('/') + 1);
      if(name.isBlank() || name.contains(":") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl))
      {
         throw (new IOException("Invalid upload filename"));
      }
      try
      {
         var rows = audit.query(FileGatewayTables.UPLOAD_JOB, equal("accountId", accountId)
            .withCriteria(new QFilterCriteria("path", QCriteriaOperator.EQUALS, source))
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.EQUALS, "RECEIVING")));
         if(rows.size() != 1 || rows.getFirst().getValueString("sha256") == null || !temporary(rows.getFirst().getValueString("fileName")))
         {
            throw (new IOException("Only a completed temporary upload can be renamed"));
         }
         queue(rows.getFirst(), target, target.substring(target.lastIndexOf('/') + 1));
      }
      catch(QException e)
      {
         throw (new IOException("Unable to rename upload", e));
      }
   }

   /*******************************************************************************
    ** Account-scoped records for a visible table.
    *******************************************************************************/
   public List<QRecord> forTable(Integer accountId, String table) throws IOException
   {
      try
      {
         return (audit.query(FileGatewayTables.UPLOAD_JOB, equal("accountId", accountId)
            .withCriteria(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, table))));
      }
      catch(QException e)
      {
         throw (new IOException("Unable to list upload jobs", e));
      }
   }

   /*******************************************************************************
    ** Acknowledging either artifact hides both results and the sidecar.
    *******************************************************************************/
   public void acknowledge(QRecord job) throws IOException
   {
      try
      {
         audit.update(FileGatewayTables.UPLOAD_JOB, new QRecord().withValue("id", job.getValue("id")).withValue("acknowledgedAt", clock.instant()));
      }
      catch(QException e)
      {
         throw (new IOException("Unable to acknowledge upload", e));
      }
   }

   /*******************************************************************************
    ** Configured temporary filename suffixes.
    *******************************************************************************/
   public boolean temporary(String name)
   {
      return (config.getTemporaryUploadSuffixes().stream().anyMatch(name::endsWith));
   }

   /*******************************************************************************
    ** Common equality filter for gateway bookkeeping only.
    *******************************************************************************/
   public static QQueryFilter equal(String field, java.io.Serializable value)
   {
      return (new QQueryFilter(new QFilterCriteria(field, QCriteriaOperator.EQUALS, value)));
   }

   /*******************************************************************************
    ** Upload audit includes measured bytes, digest and the original connection identity.
    *******************************************************************************/
   private void log(GatewaySession session, QRecord job, String outcome, String message, long started) throws QException
   {
      audit.insert(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("accountId", session.account.getValue("id"))
         .withValue("connectionLogId", session.connectionId).withValue("operation", "UPLOAD").withValue("path", job.getValue("path"))
         .withValue("objectKind", "PROFILE").withValue("objectId", job.getValueString("savedBulkLoadProfileId"))
         .withValue("uploadJobId", job.getValue("id")).withValue("bytes", job.getValue("sizeBytes")).withValue("sha256", job.getValue("sha256"))
         .withValue("durationMs", (System.nanoTime() - started) / 1_000_000).withValue("outcome", outcome).withValue("message", message)
         .withValue("clientChannel", "sftp/" + session.id));
   }
}
