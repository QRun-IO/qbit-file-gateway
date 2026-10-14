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
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.filegateway.api.FileAcceptance;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.InboundFile;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiRequest;
import com.kingsrook.qqq.openapi.model.HttpMethod;

/*******************************************************************************
 ** Drives the real bulk-insert UI process as the user; never inserts application rows directly.
 *******************************************************************************/
public final class BulkLoadConsumer implements FileGatewayConsumer
{
   private final WorkerUserSession session;
   private final QRecord job;
   private QRecord outcome;

   /*******************************************************************************
    ** One consumer instance per delivery, holding only that process's outcome.
    *******************************************************************************/
   public BulkLoadConsumer(WorkerUserSession session, QRecord job)
   {
      this.session = session;
      this.job = job;
   }

   /*******************************************************************************
    ** Stable built-in registry name.
    *******************************************************************************/
   @Override
   public String getName()
   {
      return ("bulk-load");
   }

   /*******************************************************************************
    ** Follow returned frontend steps; the API resumes backend steps after each one.
    *******************************************************************************/
   @Override
   public FileAcceptance accept(InboundFile file) throws Exception
   {
      String process = job.getValueString("tableName") + ".bulkInsert";
      JsonNode profiles = session.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/table/{tableName}/query")
         .withPathParam("tableName", "savedBulkLoadProfile").withJsonBody(Map.of("filter", UploadJobs.equal("id", job.getValue("savedBulkLoadProfileId"))))).path("records");
      if(profiles.size() != 1 || !job.getValueString("tableName").equals(profiles.get(0).path("values").path("tableName").asText()))
      {
         return (failed("Saved bulk load profile is unavailable"));
      }
      JsonNode mapping = new ObjectMapper().readTree(profiles.get(0).path("values").path("mappingJson").asText());
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("savedBulkLoadProfileId", job.getValue("savedBulkLoadProfileId"));
      values.put("version", mapping.path("version").asText());
      values.put("layout", mapping.path("layout").asText());
      values.put("hasHeaderRow", mapping.path("hasHeaderRow").asBoolean());
      values.put("fieldListJSON", mapping.path("fieldList").toString());
      for(JsonNode field : mapping.path("fieldList"))
      {
         if(field.path("doValueMapping").asBoolean() && field.path("valueMappings").isEmpty())
         {
            return (failed("this profile needs value mappings saved in it"));
         }
      }
      JsonNode response = session.invoke(new UiApiRequest().withMethod(HttpMethod.POST).withPath("/processes/{processName}/init")
         .withPathParam("processName", process).withFormParam("values", new ObjectMapper().writeValueAsString(Map.of("tableName", job.getValueString("tableName"))))
         .withMultipartFile("theFile", file.fileName(), "application/octet-stream", file.streamSupplier()));
      String uuid = response.path("processUUID").asText();
      job.withValue("processUuid", uuid);
      for(int steps = 0; steps < 20; steps++)
      {
         response = await(process, uuid, response);
         if("ERROR".equals(response.path("type").asText()))
         {
            return (failed(response.path("userFacingError").asText("Bulk load process failed")));
         }
         String next = response.path("nextStep").asText();
         if(next.equals("valueMapping"))
         {
            return (failed("this profile needs value mappings saved in it"));
         }
         if(response.path("values").has("processResults"))
         {
            summarize(response.path("values").path("processResults"));
            return (FileAcceptance.accepted(uuid));
         }
         if(!java.util.List.of("upload", "fileMapping", "review").contains(next))
         {
            return (failed("Unexpected bulk load step: " + next));
         }
         Map<String, Object> stepValues = next.equals("fileMapping") ? values : Map.of();
         UiApiRequest step = new UiApiRequest().withMethod(HttpMethod.POST).withPath("/processes/{processName}/{processUUID}/step/{stepName}")
            .withPathParam("processName", process).withPathParam("processUUID", uuid).withPathParam("stepName", next);
         if(!stepValues.isEmpty())
         {
            // the invoker sends form fields as multipart for this multipart-only operation (qqq#1170)
            step.withFormParam("values", new ObjectMapper().writeValueAsString(stepValues));
         }
         response = session.invoke(step);
      }
      return (failed("Bulk load exceeded its step limit"));
   }

   /*******************************************************************************
    ** Asynchronous UI API steps retain their job UUID across RUNNING responses.
    *******************************************************************************/
   private JsonNode await(String process, String uuid, JsonNode response) throws Exception
   {
      String async = response.path("jobUUID").asText();
      for(int poll = 0; poll < 3600 && ("JOB_STARTED".equals(response.path("type").asText()) || "RUNNING".equals(response.path("type").asText())); poll++)
      {
         Thread.sleep(100);
         response = session.invoke(new UiApiRequest().withMethod(HttpMethod.GET).withPath("/processes/{processName}/{processUUID}/status/{jobUUID}")
            .withPathParam("processName", process).withPathParam("processUUID", uuid).withPathParam("jobUUID", async));
      }
      if("RUNNING".equals(response.path("type").asText()))
      {
         throw (new IOException("Bulk load status timed out"));
      }
      return (response);
   }

   /*******************************************************************************
    ** Preserve the API's detailed summary (including row examples) for the result artifact.
    *******************************************************************************/
   private void summarize(JsonNode lines)
   {
      int inserted = 0;
      int errors = 0;
      StringBuilder summary = new StringBuilder();
      StringBuilder csv = new StringBuilder("row,field,message\r\n");
      for(JsonNode line : lines)
      {
         String status = line.path("status").asText();
         int count = line.path("count").asInt();
         String message = line.path("message").asText();
         if("OK".equals(status))
         {
            inserted += count;
         }
         if("ERROR".equals(status))
         {
            errors += count;
         }
         summary.append(count).append(" ").append(message).append("\n");
         csv.append(",,").append(csvCell(count + " " + message)).append("\r\n");
         for(JsonNode bullet : line.path("bulletsOfText"))
         {
            String detail = bullet.asText();
            summary.append(detail).append("\n");
            var row = java.util.regex.Pattern.compile("(?i)(?:row[^0-9]*)?([0-9]+)").matcher(detail);
            csv.append(row.find() ? row.group(1) : "").append(",,").append(csvCell(message + " " + detail)).append("\r\n");
         }
      }
      outcome = new QRecord().withValue("status", errors == 0 ? "SUCCEEDED" : inserted == 0 ? "FAILED" : "PARTIAL")
         .withValue("insertedCount", inserted).withValue("errorCount", errors).withValue("resultSummary", summary.toString()).withValue("_resultsCsv", csv.toString());
   }

   /*******************************************************************************
    ** Quote CSV cells, including embedded commas, newlines and quotes.
    *******************************************************************************/
   private String csvCell(String value)
   {
      return ("\"" + value.replace("\"", "\"\"") + "\"");
   }



   /*******************************************************************************
    ** Process errors are terminal rather than silently replaying an executed import.
    *******************************************************************************/
   private FileAcceptance failed(String message)
   {
      outcome = new QRecord().withValue("status", "FAILED").withValue("errorMessage", message).withValue("resultSummary", message);
      return (FileAcceptance.accepted(job.getValueString("processUuid")));
   }

   /*******************************************************************************
    ** Worker persists counts and status along with the consumer reference.
    *******************************************************************************/
   public QRecord outcome()
   {
      return (outcome);
   }
}
