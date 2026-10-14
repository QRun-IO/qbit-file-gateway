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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.api.FileAcceptance;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumersMetaData;
import com.kingsrook.qbits.filegateway.api.FileGatewayScannerInterface;
import com.kingsrook.qbits.filegateway.api.InboundFile;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;

/*******************************************************************************
 ** Bounded, at-least-once upload delivery with fenced claims and deterministic polling.
 *******************************************************************************/
public class UploadJobWorker implements AutoCloseable
{
   private static final QLogger LOG = QLogger.getLogger(UploadJobWorker.class);
   private static final long[] RETRY_SECONDS = {60, 300, 900, 3600, 21600};
   private final QInstance instance;
   private final UiApiInvoker invoker;
   private final FileGatewayQBitConfig config;
   private final GatewayObjectStore store;
   private final AuditWriter audit;
   private final Clock clock;
   private final String pod;
   private volatile boolean stopped;
   private ScheduledExecutorService pool;

   /*******************************************************************************
    ** No threads are started until the enabled runtime explicitly starts this worker.
    *******************************************************************************/
   public UploadJobWorker(QInstance instance, UiApiInvoker invoker, FileGatewayQBitConfig config, GatewayObjectStore store, Clock clock, String pod)
   {
      this.instance = instance;
      this.invoker = invoker;
      this.config = config;
      this.store = store;
      this.clock = clock;
      this.pod = pod == null ? java.util.UUID.randomUUID().toString() : pod;
      audit = new AuditWriter(instance);
   }

   /*******************************************************************************
    ** Each scheduled lane handles one job at a time, without an unbounded work queue.
    *******************************************************************************/
   public void start(Duration interval)
   {
      if(!config.getSftpEnabled())
      {
         return;
      }
      pool = Executors.newScheduledThreadPool(config.getUploadWorkerThreads(), runnable ->
      {
         Thread thread = new Thread(runnable, "file-gateway-upload");
         thread.setDaemon(true);
         return (thread);
      });
      for(int lane = 0; lane < config.getUploadWorkerThreads(); lane++)
      {
         pool.scheduleWithFixedDelay(() ->
         {
            try
            {
               runOnce();
            }
            catch(Exception e)
            {
               LOG.warn("Upload worker could not persist job state");
            }
         }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
      }
   }

   /*******************************************************************************
    ** Reconcile stale leases and synchronously deliver at most one eligible job.
    *******************************************************************************/
   public boolean runOnce() throws QException
   {
      if(stopped)
      {
         return (false);
      }
      requeueStale();
      for(QRecord candidate : audit.query(FileGatewayTables.UPLOAD_JOB, UploadJobs.equal("status", "QUEUED")))
      {
         if(candidate.getValueInstant("nextAttemptAt") != null && candidate.getValueInstant("nextAttemptAt").isAfter(clock.instant()))
         {
            continue;
         }
         QRecord claimed = claim(candidate);
         if(claimed != null)
         {
            deliver(claimed);
            return (true);
         }
      }
      return (false);
   }

   /*******************************************************************************
    ** The expected status is checked inside the account transaction; competing pods cannot both win.
    *******************************************************************************/
   public QRecord claim(QRecord candidate) throws QException
   {
      return (audit.withAccountLock("id", candidate.getValue("accountId"), () ->
      {
         QRecord current = find(candidate);
         if(stopped || !"QUEUED".equals(current.getValueString("status"))
            || (current.getValueInstant("nextAttemptAt") != null && current.getValueInstant("nextAttemptAt").isAfter(clock.instant())))
         {
            return (null);
         }
         QRecord changes = new QRecord().withValue("id", current.getValue("id")).withValue("status", "RUNNING")
            .withValue("claimedBy", pod).withValue("claimedAt", clock.instant()).withValue("startedAt", clock.instant())
            .withValue("attempts", attempts(current) + 1);
         return (audit.transitionUpload(changes, "QUEUED") ? find(current) : null);
      }));
   }

   /*******************************************************************************
    ** Requeue abandoned leases up to three starts, fencing late completions by claim time.
    *******************************************************************************/
   public void requeueStale() throws QException
   {
      for(QRecord candidate : audit.query(FileGatewayTables.UPLOAD_JOB, UploadJobs.equal("status", "RUNNING")))
      {
         audit.withAccountLock("id", candidate.getValue("accountId"), () ->
         {
            QRecord job = find(candidate);
            if("RUNNING".equals(job.getValueString("status")) && job.getValueInstant("claimedAt") != null
               && !job.getValueInstant("claimedAt").plus(config.getJobStaleAfter()).isAfter(clock.instant()))
            {
               QRecord changes = new QRecord().withValue("id", job.getValue("id"))
                  .withValue("status", attempts(job) >= 3 ? "FAILED" : "QUEUED").withValue("claimedBy", null).withValue("claimedAt", null)
                  .withValue("errorMessage", "Upload worker lease expired").withValue("nextAttemptAt", clock.instant());
               if(attempts(job) >= 3)
               {
                  changes.withValue("finishedAt", clock.instant());
               }
               audit.update(FileGatewayTables.UPLOAD_JOB, changes);
               QRecord account = audit.query(FileGatewayTables.ACCOUNT, UploadJobs.equal("id", job.getValue("accountId"))).getFirst();
               UploadNotifications.send(audit, job, changes, account);
            }
            return (null);
         });
      }
   }

   /*******************************************************************************
    ** Fresh user context is scoped to each delivery and always restored and revoked.
    *******************************************************************************/
   private void deliver(QRecord job) throws QException
   {
      var previous = QContext.capture();
      try
      {
         QRecord account = audit.query(FileGatewayTables.ACCOUNT, UploadJobs.equal("id", job.getValue("accountId"))).getFirst();
         if(!"ACTIVE".equals(account.getValueString("status"))
            || (account.getValueInstant("expiresAt") != null && !account.getValueInstant("expiresAt").isAfter(clock.instant())))
         {
            finish(job, new QRecord().withValue("status", "REJECTED").withValue("errorMessage", "Gateway account is unavailable"), account);
            return;
         }
         try(var user = new WorkerUserSession(instance, invoker, account.getValueString("userId"), "upload-" + job.getValue("id"), clock))
         {
            InboundFile file = inbound(job, account);
            if(config.getScanner() != null)
            {
               var scan = QCodeLoader.getAdHoc(FileGatewayScannerInterface.class, config.getScanner()).scan(file);
               if(scan == FileGatewayScannerInterface.Result.INFECTED)
               {
                  String quarantine = "file-gateway/quarantine/" + job.getValue("id") + "/" + job.getValueString("fileName");
                  store.copy(file.storageKey(), quarantine);
                  store.delete(file.storageKey());
                  finish(job, new QRecord().withValue("status", "REJECTED").withValue("storageKey", quarantine)
                     .withValue("errorMessage", "Scanner rejected infected file"), account);
                  return;
               }
               if(scan != FileGatewayScannerInterface.Result.CLEAN)
               {
                  retry(job, "Scanner unavailable", null);
                  return;
               }
            }
            FileGatewayConsumer consumer = consumer(job, user);
            if(consumer == null)
            {
               finish(job, new QRecord().withValue("status", "REJECTED").withValue("errorMessage", "Unknown consumer: " + job.getValueString("consumerName")), account);
               return;
            }
            FileAcceptance acceptance = consumer.accept(file);
            if(acceptance.getStatus() == FileAcceptance.Status.RETRY_LATER)
            {
               retry(job, acceptance.getReason(), acceptance.getRetryAfter());
               return;
            }
            QRecord outcome = consumer instanceof BulkLoadConsumer bulk ? bulk.outcome() : new QRecord()
               .withValue("status", acceptance.getStatus() == FileAcceptance.Status.ACCEPTED ? "SUCCEEDED" : "REJECTED")
               .withValue("errorMessage", acceptance.getReason());
            outcome.withValue("consumerReference", acceptance.getConsumerReference()).withValue("processUuid", job.getValue("processUuid"));
            finish(job, outcome, account);
         }
      }
      catch(Exception e)
      {
         retry(job, "Consumer delivery failed", null);
      }
      finally
      {
         QContext.init(previous);
      }
   }

   /*******************************************************************************
    ** Registry lookup is by declared name, with the built-in bulk-load consumer reserved.
    *******************************************************************************/
   private FileGatewayConsumer consumer(QRecord job, WorkerUserSession user)
   {
      String name = job.getValueString("consumerName");
      if("bulk-load".equals(name))
      {
         return (new BulkLoadConsumer(user, job));
      }
      for(var reference : FileGatewayConsumersMetaData.ofOrWithNew(instance).getConsumers())
      {
         FileGatewayConsumer consumer = QCodeLoader.getAdHoc(FileGatewayConsumer.class, reference);
         if(name.equals(consumer.getName()))
         {
            return (consumer);
         }
      }
      return (null);
   }

   /*******************************************************************************
    ** Immutable delivery envelope; each openStream gets an independent object stream.
    *******************************************************************************/
   private InboundFile inbound(QRecord job, QRecord account)
   {
      return (new InboundFile(job.getValueInteger("id"), null, account.getValueString("username"), account.getValueString("userId"),
         job.getValueString("remoteAddress"), job.getValueString("path"), job.getValueString("fileName"), job.getValueLong("sizeBytes"),
         job.getValueString("sha256"), job.getValueInstant("createDate"), config.getFileGatewayBucketAlias(), job.getValueString("storageKey"), () ->
         {
            try
            {
               return (store.open(job.getValueString("storageKey")));
            }
            catch(IOException e)
            {
               throw (new UncheckedIOException(e));
            }
         }));
   }

   /*******************************************************************************
    ** Retry delays have a five-attempt cap; a consumer can request a longer delay.
    *******************************************************************************/
   private void retry(QRecord job, String message, Duration requested) throws QException
   {
      if(stopped)
      {
         return;
      }
      if(attempts(job) >= 5)
      {
         QRecord account = audit.query(FileGatewayTables.ACCOUNT, UploadJobs.equal("id", job.getValue("accountId"))).getFirst();
         try
         {
            finish(job, new QRecord().withValue("status", "FAILED").withValue("errorMessage", message), account);
         }
         catch(IOException e)
         {
            throw (new QException("Unable to finish failed upload", e));
         }
         return;
      }
      long delay = RETRY_SECONDS[Math.min(Math.max(0, attempts(job) - 1), RETRY_SECONDS.length - 1)];
      if(requested != null)
      {
         delay = Math.max(delay, requested.toSeconds());
      }
      persistOwned(job, new QRecord().withValue("status", "QUEUED").withValue("errorMessage", message)
         .withValue("nextAttemptAt", clock.instant().plusSeconds(delay)).withValue("claimedBy", null).withValue("claimedAt", null));
   }

   /*******************************************************************************
    ** Results are bounded by the process summary, never by the original upload size.
    *******************************************************************************/
   private void finish(QRecord job, QRecord outcome, QRecord account) throws QException, IOException
   {
      String summary = outcome.getValueString("resultSummary");
      if(summary == null)
      {
         summary = outcome.getValueString("errorMessage");
      }
      if(summary == null)
      {
         summary = outcome.getValueString("status");
      }
      String key = "file-gateway/results/" + job.getValue("accountId") + "/" + job.getValue("id") + "/" + java.util.UUID.randomUUID() + ".csv";
      byte[] csv = ("row,field,message\r\n,,\"" + summary.replace("\"", "\"\"") + "\"\r\n").getBytes(StandardCharsets.UTF_8);
      String detailed = outcome.getValueString("_resultsCsv");
      outcome.getValues().remove("_resultsCsv");
      if(detailed != null)
      {
         csv = detailed.getBytes(StandardCharsets.UTF_8);
      }
      store.put(key, new ByteArrayInputStream(csv));
      outcome.withValue("resultSummary", summary).withValue("resultsStorageKey", key).withValue("resultsSizeBytes", (long) csv.length)
         .withValue("finishedAt", clock.instant()).withValue("nextAttemptAt", null).withValue("claimedBy", null).withValue("claimedAt", null);
      if(persistOwned(job, outcome))
      {
         UploadNotifications.send(audit, job, outcome, account);
      }
      else
      {
         store.delete(key);
      }
   }

   /*******************************************************************************
    ** Late workers cannot overwrite a requeued or newly claimed job.
    *******************************************************************************/
   private boolean persistOwned(QRecord job, QRecord changes) throws QException
   {
      return (audit.withAccountLock("id", job.getValue("accountId"), () ->
      {
         QRecord current = find(job);
         if(!"RUNNING".equals(current.getValueString("status")) || !Objects.equals(pod, current.getValueString("claimedBy"))
            || !Objects.equals(job.getValueInstant("claimedAt"), current.getValueInstant("claimedAt")))
         {
            return (false);
         }
         return (audit.transitionUpload(changes.withValue("id", job.getValue("id")), "RUNNING"));
      }));
   }

   /*******************************************************************************
    ** Reload a gateway job inside a short bookkeeping transaction.
    *******************************************************************************/
   private QRecord find(QRecord job) throws QException
   {
      return (audit.query(FileGatewayTables.UPLOAD_JOB, UploadJobs.equal("id", job.getValue("id"))).getFirst());
   }

   /*******************************************************************************
    ** Null attempts on older rows count as zero.
    *******************************************************************************/
   private int attempts(QRecord job)
   {
      return (job.getValueInteger("attempts") == null ? 0 : job.getValueInteger("attempts"));
   }

   /*******************************************************************************
    ** Interrupt in-flight delivery and release only this pod's leases.
    *******************************************************************************/
   @Override
   public void close() throws QException
   {
      stopped = true;
      if(pool != null)
      {
         pool.shutdownNow();
      }
      for(QRecord job : audit.query(FileGatewayTables.UPLOAD_JOB, UploadJobs.equal("claimedBy", pod)))
      {
         persistOwned(job, new QRecord().withValue("status", "QUEUED").withValue("claimedBy", null).withValue("claimedAt", null));
      }
   }
}
