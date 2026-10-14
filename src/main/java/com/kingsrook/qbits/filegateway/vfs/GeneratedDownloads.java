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

package com.kingsrook.qbits.filegateway.vfs;


import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qbits.filegateway.storage.GeneratedReadChannel;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiStreamResponse;
import com.kingsrook.qqq.openapi.model.HttpMethod;


/*******************************************************************************
 ** Generate user-authorized exports and reports through the UI API, with durable quotas.
 *******************************************************************************/
public final class GeneratedDownloads
{
   private final GatewayUiApiClient client;
   private final GatewaySession session;
   private final AuditWriter audit;
   private final FileGatewayQBitConfig config;
   private final Clock clock;
   private final boolean reportsAvailable;
   private final ObjectMapper mapper = new ObjectMapper();


   /*******************************************************************************
    ** Inspect only host configuration for report storage; visibility still uses user metadata.
    *******************************************************************************/
   public GeneratedDownloads(GatewayUiApiClient client, GatewaySession session, AuditWriter audit, FileGatewayQBitConfig config, Clock clock, QInstance instance)
   {
      this.client = client;
      this.session = session;
      this.audit = audit;
      this.config = config;
      this.clock = clock;
      var process = instance.getProcess("renderSavedReport");
      reportsAvailable = process != null && process.getInputFields().stream().anyMatch(field -> "storageTableName".equals(field.getName())
         && field.getDefaultValue() != null && instance.getTable(field.getDefaultValue().toString()) != null);
   }


   /*******************************************************************************
    ** A render process without its configured storage table cannot provide downloads.
    *******************************************************************************/
   public boolean reportsAvailable()
   {
      return (reportsAvailable);
   }


   /*******************************************************************************
    ** Start generation at open, owning the HTTP stream until the SFTP handle closes.
    *******************************************************************************/
   public SeekableByteChannel open(VirtualTree.Node node) throws IOException
   {
      Transfer transfer = new Transfer(node);
      try
      {
         if(!session.operationAllowed())
         {
            transfer.limit = true;
            throw (new IOException("Operation limit exceeded"));
         }
         int window = Math.toIntExact(config.getDownloadReadAheadMb() * 1024L * 1024L);
         if(window < 1)
         {
            throw (new IOException("Read-ahead window must be positive"));
         }
         String prefix = node.path().substring(0, node.path().lastIndexOf('/'));
         prefix = prefix.substring(0, prefix.lastIndexOf('/'));
         String table = node.job() == null ? prefix.substring(prefix.lastIndexOf('/') + 1) : node.job().getValueString("tableName");
         JsonNode record = null;
         for(JsonNode row : client.savedObjects("VIEW".equals(node.kind()) ? "savedView" : "savedReport", table))
         {
            if(node.objectId().equals(row.path("values").path("id").asText()))
            {
               record = row.path("values");
            }
         }
         if(record == null)
         {
            throw (new NoSuchFileException(node.path()));
         }
         UiApiStreamResponse response = "VIEW".equals(node.kind()) ? export(table, record, transfer.format) : report(node.objectId(), transfer.format);
         return (new GeneratedReadChannel(response.getInputStream(), window, transfer));
      }
      catch(IOException | RuntimeException e)
      {
         IOException failure = e instanceof IOException io ? io : new IOException("Unable to generate download", e);
         transfer.finish(failure);
         throw (failure);
      }
   }


   /*******************************************************************************
    ** Use the Next/Material saved-view schema, retaining filter ordering and visible columns.
    *******************************************************************************/
   private UiApiStreamResponse export(String table, JsonNode record, String format) throws IOException
   {
      JsonNode view = mapper.readTree(record.path("viewJson").asText());
      if(view == null || !view.isObject())
      {
         throw (new IOException("Saved view has invalid viewJson"));
      }
      ObjectNode body = mapper.createObjectNode().put("format", format).put("includeHeaderRow", true);
      JsonNode filter = view.has("queryFilter") ? view.path("queryFilter") : view.path("filter");
      if(filter.isObject())
      {
         ObjectNode query = filter.deepCopy();
         query.remove(List.of("skip", "limit"));
         body.set("filter", query);
      }
      JsonNode columns = view.path("queryColumns").path("columns");
      if(columns.isArray())
      {
         var names = body.putArray("fieldNames");
         for(JsonNode column : columns)
         {
            String name = column.path("name").asText();
            if(column.path("isVisible").asBoolean(true) && !name.isEmpty() && !name.equals("__check__"))
            {
               names.add(name);
            }
         }
         if(names.isEmpty())
         {
            throw (new IOException("Saved view has no visible export columns"));
         }
      }
      else if(view.path("fieldNames").isArray())
      {
         body.set("fieldNames", view.path("fieldNames"));
      }
      return (client.stream(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/table/{tableName}/export")
         .withPathParam("tableName", table).withJsonBody(body)));
   }


   /*******************************************************************************
    ** Select one record, submit the format input, then follow asynchronous render status.
    *******************************************************************************/
   private UiApiStreamResponse report(String id, String format) throws IOException
   {
      String values = mapper.writeValueAsString(Map.of("reportFormat", format));
      JsonNode response = client.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/processes/{processName}/init")
         .withPathParam("processName", "renderSavedReport").withFormParam("recordsParam", "recordIds").withFormParam("recordIds", id)
         .withFormParam("values", values));
      String uuid = response.path("processUUID").asText();
      String job = response.path("jobUUID").asText();
      for(int poll = 0; poll < 3600; poll++)
      {
         String type = response.path("type").asText();
         if("ERROR".equals(type))
         {
            throw (new IOException(response.path("userFacingError").asText("Report generation failed")));
         }
         if("JOB_STARTED".equals(type) || "RUNNING".equals(type))
         {
            if(response.hasNonNull("jobUUID"))
            {
               job = response.path("jobUUID").asText();
            }
            try
            {
               Thread.sleep(100);
            }
            catch(InterruptedException e)
            {
               Thread.currentThread().interrupt();
               throw (new IOException("Report generation interrupted", e));
            }
            response = client.invoke(new UiApiRequest().withMethod(HttpMethod.GET).withPath("/processes/{processName}/{processUUID}/status/{jobUUID}")
               .withPathParam("processName", "renderSavedReport").withPathParam("processUUID", uuid).withPathParam("jobUUID", job));
            continue;
         }
         JsonNode output = response.path("values");
         if(output.hasNonNull("storageReference") && output.hasNonNull("downloadFileName") && output.hasNonNull("storageTableName"))
         {
            return (client.stream(new UiApiRequest().withMethod(HttpMethod.GET).withPath("/download/{file}")
               .withPathParam("file", output.path("downloadFileName").asText())
               .withQueryParam("storageTableName", output.path("storageTableName").asText())
               .withQueryParam("storageReference", output.path("storageReference").asText())));
         }
         if(!"input".equals(response.path("nextStep").asText()))
         {
            throw (new IOException("Unexpected report step: " + response.path("nextStep").asText()));
         }
         response = client.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/processes/{processName}/{processUUID}/step/{stepName}")
            .withPathParam("processName", "renderSavedReport").withPathParam("processUUID", uuid).withPathParam("stepName", "input")
            .withFormParam("values", values));
      }
      throw (new IOException("Report generation timed out"));
   }


   /*******************************************************************************
    ** One durable row per handle, updated under the account lock as bytes flow.
    *******************************************************************************/
   private final class Transfer implements GeneratedReadChannel.Audit
   {
      private final long began = System.nanoTime();
      private final String format;
      private final QRecord row;
      private long bytes;
      private boolean limit;

      /*******************************************************************************
       ** Default unfinished transfers to ERROR, including a process terminated mid-read.
       *******************************************************************************/
      private Transfer(VirtualTree.Node node) throws IOException
      {
         format = node.job() == null ? node.path().substring(node.path().lastIndexOf('.') + 1) : node.job().getValueString("format");
         try
         {
            row = audit.insert(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("accountId", session.account.getValue("id"))
               .withValue("connectionLogId", session.connectionId).withValue("operation", "DOWNLOAD").withValue("path", node.path())
               .withValue("objectKind", node.kind()).withValue("objectId", node.objectId()).withValue("format", format)
               .withValue("bytes", 0L).withValue("outcome", "ERROR").withValue("clientChannel", "sftp/" + session.id));
         }
         catch(QException e)
         {
            throw (new IOException("Unable to persist download audit", e));
         }
      }


      /*******************************************************************************
       ** Filter by account, operation and UTC day; include in-progress and failed transfers.
       *******************************************************************************/
      @Override
      public void charge(int count) throws IOException
      {
         try
         {
            boolean allowed = audit.withAccountLock("id", session.account.getValue("id"), () ->
            {
               long maximum = config.getMaxGeneratedFileMb() * 1024L * 1024L;
               Long dailyMb = session.account.getValueLong("maxDownloadMbPerDay");
               long total = 0;
               if(dailyMb != null)
               {
                  var start = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                  for(int skip = 0; ; skip += 250)
                  {
                     var rows = audit.query(FileGatewayTables.TRANSFER_LOG, new QQueryFilter()
                        .withCriteria(new QFilterCriteria("accountId", QCriteriaOperator.EQUALS, session.account.getValue("id")))
                        .withCriteria(new QFilterCriteria("operation", QCriteriaOperator.EQUALS, "DOWNLOAD"))
                        .withCriteria(new QFilterCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, start))
                        .withOrderBy(new QFilterOrderBy("id", true)).withSkip(skip).withLimit(250));
                     for(QRecord record : rows)
                     {
                        Long value = record.getValueLong("bytes");
                        total += value == null ? 0 : value;
                     }
                     if(rows.size() < 250)
                     {
                        break;
                     }
                  }
               }
               if(bytes + count > maximum || (dailyMb != null && total + count > dailyMb * 1024L * 1024L))
               {
                  return (false);
               }
               audit.update(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("id", row.getValue("id")).withValue("bytes", bytes + count));
               return (true);
            });
            if(!allowed)
            {
               limit = true;
               throw (new IOException("Generated file or daily download limit exceeded"));
            }
            bytes += count;
         }
         catch(QException e)
         {
            throw (new IOException("Unable to enforce download limits", e));
         }
      }


      /*******************************************************************************
       ** Preserve API denial and limit outcomes instead of collapsing them to generic errors.
       *******************************************************************************/
      @Override
      public void finish(IOException failure) throws IOException
      {
         try
         {
            audit.update(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("id", row.getValue("id"))
               .withValue("durationMs", (System.nanoTime() - began) / 1_000_000)
               .withValue("outcome", limit ? "LIMIT" : failure instanceof AccessDeniedException ? "DENIED" : failure == null ? "OK" : "ERROR")
               .withValue("message", failure == null ? null : failure.getMessage()));
         }
         catch(QException e)
         {
            throw (new IOException("Unable to finalize download audit", e));
         }
      }
   }
}
