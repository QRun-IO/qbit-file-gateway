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

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;

/*******************************************************************************
 ** Administrative record actions use the standard streamed Review and Result flow.
 *******************************************************************************/
public final class FileGatewayProcesses
{
   public static final String RETRY_UPLOAD = "file_gateway_retry_upload";
   public static final String UNLOCK = "file_gateway_unlock_account";
   public static final String DISABLE = "file_gateway_disable_account";
   public static final String REVOKE = "file_gateway_revoke_key";
   public static final String END_SESSION = "file_gateway_end_session";

   /*******************************************************************************
    ** Metadata factory only.
    *******************************************************************************/
   private FileGatewayProcesses()
   {
   }



   /*******************************************************************************
    ** Administrative actions bound to their owning tables.
    *******************************************************************************/
   public static List<QProcessMetaData> processes()
   {
      return (List.of(action(UNLOCK, "Unlock account", FileGatewayTables.ACCOUNT),
         action(DISABLE, "Disable account", FileGatewayTables.ACCOUNT),
         action(REVOKE, "Revoke key", FileGatewayTables.SSH_KEY),
         action(END_SESSION, "End session", FileGatewayTables.CONNECTION_LOG),
         action(RETRY_UPLOAD, "Retry upload", FileGatewayTables.UPLOAD_JOB)));
   }



   /*******************************************************************************
    ** A table-bound action, never a navigation entry.
    *******************************************************************************/
   private static QProcessMetaData action(String name, String label, String table)
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, table);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, table);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, label + " for the selected records.");
      return (StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, Review.class, Apply.class, values)
         .withName(name).withLabel(label).withTableName(table).withPermissionRules(FileGatewayTables.permissionRules(name)));
   }



   /*******************************************************************************
    ** Direct step invocation must require the same permission as the process route.
    *******************************************************************************/
   private static void requireAdmin() throws QPermissionDeniedException
   {
      if(QContext.getQSession() == null || !QContext.getQSession().hasPermission(FileGatewayPermissions.ADMIN))
      {
         throw (new QPermissionDeniedException("Missing file_gateway.admin"));
      }
   }



   /*******************************************************************************
    ** Preview selections without changing records.
    *******************************************************************************/
   public static class Review extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine summary = new ProcessSummaryLine(Status.OK, 0, null);
      private final ProcessSummaryLine skipped = new ProcessSummaryLine(Status.INFO, 0, "uploads are not eligible for retry");

      /*******************************************************************************
       ** Review uses the framework's future and past tense summary lines.
       *******************************************************************************/
      public Review()
      {
         summary.setSingularFutureMessage("record will be updated");
         summary.setPluralFutureMessage("records will be updated");
         summary.setSingularPastMessage("record was updated");
         summary.setPluralPastMessage("records were updated");
      }



      /*******************************************************************************
       ** Pass only authorized selections to the load phase.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         requireAdmin();
         for(QRecord record : input.getRecords())
         {
            if(RETRY_UPLOAD.equals(input.getProcessName()) && (!List.of("FAILED", "REJECTED").contains(record.getValueString("status"))
               || record.getValueString("storageKey") == null || record.getValueString("sha256") == null))
            {
               skipped.incrementCountAndAddPrimaryKey(record.getValue("id"));
               continue;
            }
            summary.incrementCountAndAddPrimaryKey(record.getValue("id"));
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Standard Review and Result summary.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean result)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>(List.of(summary));
         skipped.addSelfToListIfAnyCount(lines);
         return (lines);
      }
   }



   /*******************************************************************************
    ** Apply the approved action to the gateway's own bookkeeping tables.
    *******************************************************************************/
   public static class Apply extends AbstractLoadStep
   {
      /*******************************************************************************
       ** Whitelist mutations, using sparse updates so concurrent account changes survive.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         requireAdmin();
         AuditWriter audit = new AuditWriter(QContext.getQInstance());
         for(QRecord record : input.getRecords())
         {
            if(RETRY_UPLOAD.equals(input.getProcessName()))
            {
               audit.withAccountLock("id", record.getValue("accountId"), () ->
               {
                  QRecord current = audit.query(FileGatewayTables.UPLOAD_JOB, UploadJobs.equal("id", record.getValue("id"))).getFirst();
                  if(!List.of("FAILED", "REJECTED").contains(current.getValueString("status"))
                     || current.getValueString("storageKey") == null || current.getValueString("sha256") == null)
                  {
                     throw (new QException("Only failed or rejected stored uploads can be retried"));
                  }
                  QRecord retry = new QRecord().withValue("id", record.getValue("id"))
                     .withValue("status", "QUEUED").withValue("attempts", 0).withValue("nextAttemptAt", null)
                     .withValue("claimedBy", null).withValue("claimedAt", null).withValue("finishedAt", null)
                     .withValue("acknowledgedAt", null).withValue("errorMessage", null).withValue("resultSummary", null)
                     .withValue("resultsStorageKey", null).withValue("resultsSizeBytes", null).withValue("processUuid", null)
                     .withValue("insertedCount", null).withValue("errorCount", null).withValue("queuedAt", Instant.now());
                  audit.update(FileGatewayTables.UPLOAD_JOB, retry);
                  output.getRecords().add(retry);
                  return (null);
               });
               continue;
            }
            QRecord update = new QRecord().withValue("id", record.getValue("id"));
            String table;
            switch(input.getProcessName())
            {
               case UNLOCK ->
               {
                  table = FileGatewayTables.ACCOUNT;
                  update.withValue("lockedUntil", null).withValue("failedLoginCount", 0).withValue("failedLoginWindowAt", null);
                  if("LOCKED".equals(record.getValueString("status")))
                  {
                     update.withValue("status", "ACTIVE");
                  }
               }
               case DISABLE ->
               {
                  table = FileGatewayTables.ACCOUNT;
                  update.withValue("status", "DISABLED");
               }
               case REVOKE ->
               {
                  table = FileGatewayTables.SSH_KEY;
                  update.withValue("revokedAt", Instant.now());
               }
               case END_SESSION ->
               {
                  table = FileGatewayTables.CONNECTION_LOG;
                  update.withValue("endRequestedAt", Instant.now()).withValue("disconnectReason", "Ended by administrator");
               }
               default -> throw (new QException("Unsupported gateway action"));
            }
            audit.update(table, update);
            output.getRecords().add(update);
         }
      }
   }
}
