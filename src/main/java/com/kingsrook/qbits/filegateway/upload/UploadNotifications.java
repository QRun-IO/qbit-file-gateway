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

import java.util.List;
import java.util.Locale;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;

/*******************************************************************************
 ** Core notification signals for terminal upload outcomes, including transfer limits.
 *******************************************************************************/
public final class UploadNotifications
{
   /*******************************************************************************
    ** Static signal publisher.
    *******************************************************************************/
   private UploadNotifications()
   {
   }

   /*******************************************************************************
    ** No alerts producer is a no-op; service account messages go to their owner.
    *******************************************************************************/
   public static void send(AuditWriter audit, QRecord job, QRecord outcome, QRecord account) throws QException
   {
      String status = outcome.getValueString("status");
      if(!List.of("FAILED", "PARTIAL", "REJECTED").contains(status)
         && !("SUCCEEDED".equals(status) && Boolean.TRUE.equals(account.getValueBoolean("notifyOnSuccess"))))
      {
         return;
      }
      audit.system(() ->
      {
         var alerts = AlertsInstanceMetaData.of(QContext.getQInstance());
         if(alerts == null || alerts.getSink() == null)
         {
            return (null);
         }
         String recipient = "SERVICE".equals(account.getValueString("accountType")) ? account.getValueString("ownerUserId") : account.getValueString("userId");
         if(recipient == null || recipient.isBlank())
         {
            return (null);
         }
         QAlerts.notify(new NotificationSignal().withTitle("Upload " + status.toLowerCase(Locale.ROOT))
            .withBody(outcome.getValueString("resultSummary") == null ? outcome.getValueString("errorMessage") : outcome.getValueString("resultSummary"))
            .withSourceName("file-gateway").withSourceKey(job.getValueString("id"))
            .withSourceTableName(FileGatewayTables.UPLOAD_JOB).withSourceRecordId(job.getValueString("id"))
            .withAudience(NotificationAudience.user(recipient))
            .withLink(AlertLink.toRecord(FileGatewayTables.UPLOAD_JOB, job.getValue("id"), "Upload job")));
         return (null);
      });
   }
}
