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

package com.kingsrook.qbits.filegateway.maintenance;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.FileGatewayQBitProducer;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.outbox.OutboxService;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qbits.filegateway.storage.ObjectStorageGatewayStore;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;

/*******************************************************************************
 ** Idempotent daily cleanup and at-least-once outbox callback retries.
 *******************************************************************************/
public class GatewayMaintenance implements BackendStep
{
   public static final String NAME = "file_gateway_retention";



   /*******************************************************************************
    ** Hidden host-scheduled process; no navigation or user action is registered.
    *******************************************************************************/
   public static QProcessMetaData metadata(FileGatewayQBitConfig config)
   {
      QProcessMetaData process = new QProcessMetaData().withName(NAME).withLabel("File gateway retention").withIsHidden(true)
         .withPermissionRules(FileGatewayTables.permissionRules(NAME))
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(GatewayMaintenance.class)));
      if(config.getSchedulerName() != null)
      {
         process.withSchedule(new QScheduleMetaData().withSchedulerName(config.getSchedulerName()).withRepeatSeconds(86400).withInitialDelaySeconds(60));
      }
      return (process);
   }



   /*******************************************************************************
    ** Resolve registered host configuration without starting the protocol runtime.
    *******************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      QInstance instance = QContext.getQInstance();
      FileGatewayQBitConfig config = (FileGatewayQBitConfig) instance.getQBits().get(FileGatewayQBitProducer.GROUP_ID + ":" + FileGatewayQBitProducer.ARTIFACT_ID).getConfig();
      maintain(instance, config, new ObjectStorageGatewayStore(instance, config.getFileGatewayBucketAlias()), Clock.systemUTC());
   }



   /*******************************************************************************
    ** Cursor batches remain correct while rows are deleted, and bound memory use.
    *******************************************************************************/
   public static void maintain(QInstance instance, FileGatewayQBitConfig config, GatewayObjectStore store, Clock clock) throws QException
   {
      AuditWriter audit = new AuditWriter(instance);
      OutboxService outbox = new OutboxService(instance, store, config, clock);
      for(String table : List.of(FileGatewayTables.UPLOAD_JOB, FileGatewayTables.OUTBOX_FILE, FileGatewayTables.TRANSFER_LOG, FileGatewayTables.CONNECTION_LOG, FileGatewayTables.SERVER))
      {
         int last = 0;
         while(true)
         {
            var rows = audit.query(table, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.GREATER_THAN, last))
               .withOrderBy(new QFilterOrderBy("id", true)).withLimit(250));
            if(rows.isEmpty())
            {
               break;
            }
            for(QRecord row : rows)
            {
               last = row.getValueInteger("id");
               try
               {
                  if(FileGatewayTables.UPLOAD_JOB.equals(table))
                  {
                     audit.withAccountLock("id", row.getValue("accountId"), () ->
                     {
                        try
                        {
                           cleanUpload(audit, store, audit.query(table, UploadJobs.equal("id", row.getValue("id"))).getFirst(), config, clock.instant());
                        }
                        catch(IOException e)
                        {
                           throw (new QException("Upload retention storage failure", e));
                        }
                        return (null);
                     });
                  }
                  else if(FileGatewayTables.OUTBOX_FILE.equals(table))
                  {
                     outbox.notifyDownloaded(row);
                     if("REMOVED".equals(row.getValueString("status")) || older(row, "createDate", config.getOutboxRetention(), clock.instant())
                        || (row.getValueInstant("expiresAt") != null && !row.getValueInstant("expiresAt").isAfter(clock.instant())))
                     {
                        deleteObject(store, row, "storageKey");
                        audit.update(table, new QRecord().withValue("id", row.getValue("id")).withValue("status", "EXPIRED").withValue("storageKey", null));
                     }
                  }
                  else if(FileGatewayTables.SERVER.equals(table))
                  {
                     if(older(row, "lastSeenAt", config.getServerRetention(), clock.instant()))
                     {
                        audit.delete(table, row.getValue("id"));
                     }
                  }
                  else if(older(row, "createDate", config.getLogRetention(), clock.instant())
                     && (!FileGatewayTables.CONNECTION_LOG.equals(table) || older(row, "lastSeenAt", Duration.ofMinutes(3), clock.instant())))
                  {
                     audit.delete(table, row.getValue("id"));
                  }
               }
               catch(IOException e)
               {
                  throw (new QException("File gateway retention storage failure", e));
               }
            }
         }
      }
   }



   /*******************************************************************************
    ** Never delete an object owned by an active worker; retain rows for the audit trail.
    *******************************************************************************/
   private static void cleanUpload(AuditWriter audit, GatewayObjectStore store, QRecord row, FileGatewayQBitConfig config, Instant now) throws IOException, QException
   {
      String status = row.getValueString("status");
      QRecord changes = new QRecord().withValue("id", row.getValue("id"));
      if("RECEIVING".equals(status) && older(row, "createDate", config.getReceivingStaleAfter(), now))
      {
         deleteObject(store, row, "storageKey");
         changes.withValue("status", "ABORTED").withValue("storageKey", null).withValue("finishedAt", now).withValue("errorMessage", "Incomplete upload expired");
      }
      else if(!List.of("RECEIVING", "RUNNING", "QUEUED").contains(status))
      {
         if(older(row, "createDate", config.getArchiveRetention(), now))
         {
            deleteObject(store, row, "storageKey");
            deleteObject(store, row, "plaintextStorageKey");
            changes.withValue("storageKey", null).withValue("plaintextStorageKey", null);
         }
         if(older(row, "createDate", config.getResultsRetention(), now))
         {
            deleteObject(store, row, "resultsStorageKey");
            changes.withValue("resultsStorageKey", null).withValue("resultsSizeBytes", null);
         }
      }
      if(changes.getValues().size() > 1)
      {
         audit.update(FileGatewayTables.UPLOAD_JOB, changes);
      }
   }



   /*******************************************************************************
    ** Storage deletes precede database clearing so transient errors can be retried.
    *******************************************************************************/
   private static void deleteObject(GatewayObjectStore store, QRecord row, String field) throws IOException
   {
      if(row.getValueString(field) != null)
      {
         store.delete(row.getValueString(field));
      }
   }



   /*******************************************************************************
    ** Null timestamps are not evidence that a record is old enough to remove.
    *******************************************************************************/
   private static boolean older(QRecord row, String field, Duration age, Instant now)
   {
      return (row.getValueInstant(field) != null && !row.getValueInstant(field).plus(age).isAfter(now));
   }
}
