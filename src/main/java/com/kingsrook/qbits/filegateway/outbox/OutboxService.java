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

package com.kingsrook.qbits.filegateway.outbox;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumersMetaData;
import com.kingsrook.qbits.filegateway.api.FileGatewayOutboxProvider;
import com.kingsrook.qbits.filegateway.api.OutboxFileRef;
import com.kingsrook.qbits.filegateway.metadata.AliasCustomizer;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;

/*******************************************************************************
 ** Database and object-storage outbox usable in web pods without an SFTP listener.
 *******************************************************************************/
public class OutboxService implements FileGatewayOutboxProvider
{
   private static final QLogger LOG = QLogger.getLogger(OutboxService.class);
   private final QInstance instance;
   private final AuditWriter audit;
   private final GatewayObjectStore store;
   private final FileGatewayQBitConfig config;
   private final Clock clock;



   /*******************************************************************************
    ** No runtime, threads or protocol credentials are required.
    *******************************************************************************/
   public OutboxService(QInstance instance, GatewayObjectStore store, FileGatewayQBitConfig config, Clock clock)
   {
      this.instance = instance;
      this.audit = new AuditWriter(instance);
      this.store = store;
      this.config = config;
      this.clock = clock;
   }



   /*******************************************************************************
    ** Publish only after the object is complete and its caller-supplied size matches.
    *******************************************************************************/
   @Override
   public OutboxFileRef put(String endpointName, String fileName, InputStream content, long sizeBytes) throws QException
   {
      if(!AliasCustomizer.validFileName(fileName) || sizeBytes < 0)
      {
         throw (new QException("Invalid outbox filename or size"));
      }
      QRecord alias = endpoint(endpointName);
      String key = "file-gateway/outbox/" + alias.getValue("id") + "/" + UUID.randomUUID() + "/" + fileName;
      try
      {
         GatewayObjectStore.Receipt receipt = store.put(key, content);
         if(receipt.size() != sizeBytes)
         {
            throw (new QException("Outbox stream size does not match sizeBytes"));
         }
         return (audit.withRowLock(FileGatewayTables.ALIAS, "id", alias.getValue("id"), () ->
         {
            QRecord current = endpoint(endpointName);
            for(QRecord existing : list(current.getValueInteger("id")))
            {
               if(fileName.equals(existing.getValueString("fileName")))
               {
                  throw (new QException("An active outbox file already has this filename"));
               }
            }
            QRecord row = audit.insert(FileGatewayTables.OUTBOX_FILE, new QRecord().withValue("aliasId", current.getValue("id"))
               .withValue("fileName", fileName).withValue("storageKey", key).withValue("sizeBytes", receipt.size()).withValue("sha256", receipt.sha256())
               .withValue("status", "READY").withValue("downloadCount", 0).withValue("onDownloadedPending", false)
               .withValue("createdByConsumer", current.getValue("consumerName")).withValue("createDate", clock.instant())
               .withValue("expiresAt", clock.instant().plus(config.getOutboxRetention())));
            return (reference(row, current));
         }));
      }
      catch(Exception e)
      {
         try
         {
            store.delete(key);
         }
         catch(IOException cleanup)
         {
            e.addSuppressed(cleanup);
         }
         throw (new QException("Unable to publish outbox file: " + (e instanceof QException ? e.getMessage() : "storage failed"), e));
      }
   }



   /*******************************************************************************
    ** A missing, inactive or incorrectly typed endpoint cannot accept output.
    *******************************************************************************/
   private QRecord endpoint(String name) throws QException
   {
      var rows = audit.query(FileGatewayTables.ALIAS, UploadJobs.equal("name", name));
      if(rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().getValueBoolean("isActive")) || !"OUTBOX".equals(rows.getFirst().getValueString("kind")))
      {
         throw (new QException("Outbox endpoint is missing, inactive or not OUTBOX"));
      }
      return (rows.getFirst());
   }



   /*******************************************************************************
    ** Removed and expired files disappear immediately from the virtual folder.
    *******************************************************************************/
   public List<QRecord> list(Integer aliasId) throws QException
   {
      return (audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("aliasId", aliasId)).stream()
         .filter(row -> List.of("READY", "DOWNLOADED").contains(row.getValueString("status"))).toList());
   }



   /*******************************************************************************
    ** Only a complete read schedules the durable, at-least-once notification.
    *******************************************************************************/
   public void downloaded(QRecord row) throws QException
   {
      audit.withRowLock(FileGatewayTables.OUTBOX_FILE, "id", row.getValue("id"), () ->
      {
         QRecord current = audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("id", row.getValue("id"))).getFirst();
         if(List.of("READY", "DOWNLOADED").contains(current.getValueString("status")))
         {
            QRecord changes = new QRecord().withValue("id", current.getValue("id")).withValue("status", "DOWNLOADED")
               .withValue("downloadCount", current.getValueInteger("downloadCount") + 1);
            if(current.getValue("firstDownloadedAt") == null)
            {
               changes.withValue("firstDownloadedAt", clock.instant()).withValue("onDownloadedPending", true);
            }
            audit.update(FileGatewayTables.OUTBOX_FILE, changes);
         }
         return (null);
      });
      notifyDownloaded(row);
   }



   /*******************************************************************************
    ** Holding the row lock prevents duplicate concurrent delivery; crash retries remain possible.
    *******************************************************************************/
   public void notifyDownloaded(QRecord row) throws QException
   {
      audit.withRowLock(FileGatewayTables.OUTBOX_FILE, "id", row.getValue("id"), () ->
      {
         QRecord current = audit.query(FileGatewayTables.OUTBOX_FILE, UploadJobs.equal("id", row.getValue("id"))).getFirst();
         if(!Boolean.TRUE.equals(current.getValueBoolean("onDownloadedPending")))
         {
            return (null);
         }
         try
         {
            QRecord alias = audit.query(FileGatewayTables.ALIAS, UploadJobs.equal("id", current.getValue("aliasId"))).getFirst();
            String name = alias.getValueString("consumerName");
            if(name != null && !name.isBlank())
            {
               FileGatewayConsumer selected = null;
               for(var code : FileGatewayConsumersMetaData.ofOrWithNew(instance).getConsumers())
               {
                  FileGatewayConsumer consumer = QCodeLoader.getAdHoc(FileGatewayConsumer.class, code);
                  if(name.equals(consumer.getName()))
                  {
                     selected = consumer;
                  }
               }
               if(selected == null)
               {
                  throw (new QException("Outbox consumer is not registered"));
               }
               selected.onDownloaded(reference(current, alias));
            }
            audit.update(FileGatewayTables.OUTBOX_FILE, new QRecord().withValue("id", row.getValue("id")).withValue("onDownloadedPending", false));
         }
         catch(Exception e)
         {
            LOG.warn("Outbox download notification failed; maintenance will retry");
         }
         return (null);
      });
   }



   /*******************************************************************************
    ** Acknowledgment retains audit state until object retention runs.
    *******************************************************************************/
   public void remove(QRecord row) throws QException
   {
      audit.withRowLock(FileGatewayTables.OUTBOX_FILE, "id", row.getValue("id"), () ->
      {
         audit.update(FileGatewayTables.OUTBOX_FILE, new QRecord().withValue("id", row.getValue("id"))
            .withValue("status", "REMOVED").withValue("removedAt", clock.instant()));
         return (null);
      });
   }



   /*******************************************************************************
    ** Consumer contract contains no implementation types or storage credentials.
    *******************************************************************************/
   private OutboxFileRef reference(QRecord row, QRecord alias)
   {
      return (new OutboxFileRef(row.getValueInteger("id"), alias.getValueString("name"), alias.getValueString("path") + "/" + row.getValueString("fileName"),
         row.getValueString("fileName"), row.getValueLong("sizeBytes"), row.getValueString("sha256"), row.getValueInstant("createDate")));
   }
}
