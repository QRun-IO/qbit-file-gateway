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
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Permission-filtered virtual app and table tree with per-session in-memory caches.
 *******************************************************************************/
public class VirtualTree
{
   /*******************************************************************************
    ** A virtual directory or generated file.
    *******************************************************************************/
   public record Node(String path, boolean directory, String kind, String objectId)
   {
   }

   private final GatewayUiApiClient client;
   private final GatewaySession session;
   private final AuditWriter audit;
   private final Clock clock;
   private JsonNode metadata;
   private Instant metadataAt;
   private final Map<String, JsonNode> saved = new HashMap<>();
   private final Map<String, Instant> savedAt = new HashMap<>();



   /*******************************************************************************
    ** Create a session-private tree and caches.
    *******************************************************************************/
   public VirtualTree(GatewayUiApiClient client, GatewaySession session, AuditWriter audit, Clock clock)
   {
      this.client = client;
      this.session = session;
      this.audit = audit;
      this.clock = clock;
   }



   /*******************************************************************************
    ** Cache user metadata for sixty seconds.
    *******************************************************************************/
   private JsonNode metadata() throws IOException
   {
      if(metadataAt == null || !metadataAt.plusSeconds(60).isAfter(clock.instant()))
      {
         metadata = client.metadata();
         metadataAt = clock.instant();
      }
      return (metadata);
   }



   /*******************************************************************************
    ** Cache saved objects for thirty seconds.
    *******************************************************************************/
   private JsonNode saved(String source, String table) throws IOException
   {
      String key = source + "/" + table;
      if(!savedAt.containsKey(key) || !savedAt.get(key).plusSeconds(30).isAfter(clock.instant()))
      {
         saved.put(key, client.savedObjects(source, table));
         savedAt.put(key, clock.instant());
      }
      return (saved.get(key));
   }



   /*******************************************************************************
    ** Resolve only through visible parents; hidden paths are indistinguishable from missing ones.
    *******************************************************************************/
   public synchronized Node stat(String path) throws IOException
   {
      sessionToken();
      if("/".equals(path))
      {
         return (new Node("/", true, "APP", null));
      }
      int split = path.lastIndexOf('/');
      String parent = split == 0 ? "/" : path.substring(0, split);
      return (children(parent).stream().filter(node -> node.path().equals(path)).findFirst()
         .orElseThrow(() -> new NoSuchFileException(path)));
   }



   /*******************************************************************************
    ** Refresh permissions even when a listing uses cached metadata.
    *******************************************************************************/
   private void sessionToken() throws IOException
   {
      try
      {
         session.token();
      }
      catch(Exception e)
      {
         throw (new AccessDeniedException("User session unavailable"));
      }
   }



   /*******************************************************************************
    ** Audit each directory open once.
    *******************************************************************************/
   public synchronized List<Node> list(String path) throws IOException
   {
      long started = System.nanoTime();
      try
      {
         sessionToken();
         if(!session.operationAllowed())
         {
            log(path, "LIST", "LIMIT", null, started);
            throw (new IOException("Operation limit exceeded"));
         }
         List<Node> nodes = children(path);
         log(path, "LIST", "OK", null, started);
         return (nodes);
      }
      catch(NoSuchFileException e)
      {
         log(path, "LIST", "NOT_FOUND", null, started);
         throw (e);
      }
      catch(AccessDeniedException e)
      {
         log(path, "LIST", "DENIED", null, started);
         throw (e);
      }
   }



   /*******************************************************************************
    ** Resolve app hierarchy and phase-a table folders from UI API metadata.
    *******************************************************************************/
   private List<Node> children(String path) throws IOException
   {
      JsonNode meta = metadata();
      List<Node> result = new ArrayList<>();
      if("/".equals(path))
      {
         addAppChildren(result, "", meta.path("appTree"), meta);
         return (result);
      }
      String[] segments = path.substring(1).split("/");
      JsonNode children = meta.path("appTree");
      String prefix = "";
      String table = null;
      int index = 0;
      for(; index < segments.length; index++)
      {
         JsonNode found = null;
         for(JsonNode child : children)
         {
            if(segments[index].equals(child.path("name").asText()))
            {
               found = child;
               break;
            }
         }
         if(found == null)
         {
            throw (new NoSuchFileException(path));
         }
         prefix += "/" + segments[index];
         if("TABLE".equalsIgnoreCase(found.path("type").asText()) && meta.path("tables").has(segments[index]))
         {
            table = segments[index++];
            break;
         }
         if(!"APP".equalsIgnoreCase(found.path("type").asText()))
         {
            throw (new NoSuchFileException(path));
         }
         children = found.path("children");
      }
      if(table == null)
      {
         addAppChildren(result, prefix, children, meta);
         return (result);
      }
      List<String> folders = new ArrayList<>(List.of("results", "archive"));
      boolean profiles = meta.path("tables").has("savedBulkLoadProfile") && meta.path("processes").has(table + ".bulkInsert");
      if(profiles && !saved("savedBulkLoadProfile", table).isEmpty())
      {
         folders.add("upload");
      }
      if(meta.path("tables").has("savedView"))
      {
         folders.add("views");
      }
      if(meta.path("tables").has("savedReport") && meta.path("processes").has("renderSavedReport"))
      {
         folders.add("reports");
      }
      if(index == segments.length)
      {
         for(String folder : folders)
         {
            result.add(new Node(prefix + "/" + folder, true, folderKind(folder), null));
         }
         return (result);
      }
      String folder = segments[index++];
      if(!folders.contains(folder))
      {
         throw (new NoSuchFileException(path));
      }
      if("results".equals(folder) || "archive".equals(folder))
      {
         if(index != segments.length)
         {
            throw (new NoSuchFileException(path));
         }
         return (result);
      }
      String source = "upload".equals(folder) ? "savedBulkLoadProfile" : "views".equals(folder) ? "savedView" : "savedReport";
      JsonNode records = saved(source, table);
      Map<String, Integer> counts = new HashMap<>();
      for(JsonNode row : records)
      {
         counts.merge(slug(row.path("values").path("label").asText()), 1, Integer::sum);
      }
      for(JsonNode row : records)
      {
         String id = row.path("values").path("id").asText();
         String name = slug(row.path("values").path("label").asText());
         if(counts.get(name) > 1)
         {
            name += "-" + id;
         }
         String item = prefix + "/" + folder + "/" + name;
         if("upload".equals(folder))
         {
            result.add(new Node(item, true, "PROFILE", id));
         }
         else
         {
            String formats = session.account.getValueString("formats");
            for(String format : (formats == null ? "csv,xlsx" : formats).split("[,\\s]+"))
            {
               if(List.of("csv", "xlsx", "json").contains(format))
               {
                  result.add(new Node(item + "." + format, false, folderKind(folder), id));
               }
            }
         }
      }
      if(index == segments.length)
      {
         return (result);
      }
      if(index + 1 == segments.length && "upload".equals(folder) && result.stream().anyMatch(node -> node.path().equals(path)))
      {
         return (List.of());
      }
      throw (new NoSuchFileException(path));
   }



   /*******************************************************************************
    ** Expose only app and readable table nodes.
    *******************************************************************************/
   private void addAppChildren(List<Node> result, String prefix, JsonNode children, JsonNode meta)
   {
      for(JsonNode child : children)
      {
         String type = child.path("type").asText().toUpperCase(Locale.ROOT);
         String name = child.path("name").asText();
         if(("APP".equals(type) || (!prefix.isEmpty() && "TABLE".equals(type) && meta.path("tables").has(name)))
            && !name.isEmpty() && !name.contains("/") && !name.equals(".") && !name.equals(".."))
         {
            result.add(new Node(prefix + "/" + name, true, type, name));
         }
      }
   }



   /*******************************************************************************
    ** Map virtual folders to persisted audit kinds.
    *******************************************************************************/
   private String folderKind(String folder)
   {
      return (switch(folder)
      {
         case "upload" -> "PROFILE";
         case "views" -> "VIEW";
         case "reports" -> "REPORT";
         case "results" -> "RESULT";
         default -> "ARCHIVE";
      });
   }



   /*******************************************************************************
    ** Stable lowercase label slug, at most eighty characters before a collision suffix.
    *******************************************************************************/
   public static String slug(String label)
   {
      String value = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
      if(value.length() > 80)
      {
         value = value.substring(0, 80);
      }
      return (value.isEmpty() ? "item" : value);
   }



   /*******************************************************************************
    ** Audit and deny unsupported operations.
    *******************************************************************************/
   public void deny(String path, String operation, String message) throws IOException
   {
      session.operationAllowed();
      log(path, operation, "DENIED", message, System.nanoTime());
      throw (new AccessDeniedException(path, null, message));
   }



   /*******************************************************************************
    ** Persist the operation through the system audit writer.
    *******************************************************************************/
   private void log(String path, String operation, String outcome, String message, long started) throws IOException
   {
      String kind = "TABLE";
      String id = null;
      try
      {
         Node node = stat(path);
         kind = node.kind();
         id = node.objectId();
      }
      catch(IOException ignored)
      {
         // Missing targets have no object id.
      }
      try
      {
         audit.insert(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("accountId", session.account.getValue("id"))
            .withValue("connectionLogId", session.connectionId).withValue("operation", operation).withValue("path", path)
            .withValue("objectKind", kind).withValue("objectId", id).withValue("bytes", 0L)
            .withValue("durationMs", (System.nanoTime() - started) / 1_000_000).withValue("outcome", outcome)
            .withValue("message", message).withValue("clientChannel", "sftp/" + session.id));
      }
      catch(Exception e)
      {
         throw (new IOException("Unable to persist operation audit", e));
      }
   }
}
