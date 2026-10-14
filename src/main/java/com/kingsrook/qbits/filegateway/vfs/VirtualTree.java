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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.sftp.GatewaySession;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qbits.filegateway.storage.ObjectReadChannel;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Permission-filtered virtual app and table tree with per-session in-memory caches.
 *******************************************************************************/
public class VirtualTree
{
   /*******************************************************************************
    ** A virtual directory or generated file.
    *******************************************************************************/
   public record Node(String path, boolean directory, String kind, String objectId, long size, QRecord job)
   {
      /*******************************************************************************
       ** Metadata-backed nodes have no stored size or job.
       *******************************************************************************/
      public Node(String path, boolean directory, String kind, String objectId)
      {
         this(path, directory, kind, objectId, 0, null);
      }
   }

   private final GatewayUiApiClient client;
   private final GatewaySession session;
   private final AuditWriter audit;
   private final Clock clock;
   private UploadJobs uploads;
   private GatewayObjectStore store;
   private GeneratedDownloads downloads;
   private com.kingsrook.qbits.filegateway.outbox.OutboxService outbox;
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
    ** Attach the runtime's upload pipeline; endpoint targets will reuse this service.
    *******************************************************************************/
   public VirtualTree withUploads(UploadJobs uploads, GatewayObjectStore store)
   {
      this.uploads = uploads;
      this.store = store;
      return (this);
   }


   /*******************************************************************************
    ** Attach generated downloads and the host's report-storage availability.
    *******************************************************************************/
   public VirtualTree withDownloads(GeneratedDownloads downloads)
   {
      this.downloads = downloads;
      return (this);
   }



   /*******************************************************************************
    ** Use the same provider in the web deployment and the virtual outbox folders.
    *******************************************************************************/
   public VirtualTree withOutbox(com.kingsrook.qbits.filegateway.outbox.OutboxService outbox)
   {
      this.outbox = outbox;
      return (this);
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
    ** Resolve permitted app and table folders from UI API metadata.
    *******************************************************************************/
   private List<Node> children(String path) throws IOException
   {
      List<Node> nodes = rawChildren(path);
      if(Boolean.TRUE.equals(session.account.getValueBoolean("pgpEncryptDownloads")))
      {
         return (nodes.stream().map(node -> node.directory() || "TEMPORARY".equals(node.kind()) ? node
            : new Node(node.path() + ".pgp", false, node.kind(), node.objectId(), 0, node.job())).toList());
      }
      return (nodes);
   }



   /*******************************************************************************
    ** Resolve canonical plaintext paths before applying download encryption names.
    *******************************************************************************/
   private List<Node> rawChildren(String path) throws IOException
   {
      JsonNode meta = metadata();
      List<Node> result = new ArrayList<>();
      List<Node> endpoints = endpointChildren(path, meta);
      if(!endpoints.isEmpty() || endpointMount(path, meta) != null)
      {
         result.addAll(endpoints);
         if(!"/".equals(path))
         {
            return (result);
         }
      }
      if("ENDPOINTS_ONLY".equals(session.account.getValueString("rootMode")))
      {
         if("/".equals(path))
         {
            return (result);
         }
         throw (new NoSuchFileException(path));
      }
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
      boolean profiles = meta.path("tables").path(table).path("insertPermission").asBoolean(false) && meta.path("tables").has("savedBulkLoadProfile") && meta.path("processes").has(table + ".bulkInsert");
      if(profiles && !saved("savedBulkLoadProfile", table).isEmpty())
      {
         folders.add("upload");
      }
      if(meta.path("tables").has("savedView"))
      {
         folders.add("views");
      }
      if(downloads != null && downloads.reportsAvailable() && meta.path("tables").has("savedReport") && meta.path("processes").has("renderSavedReport"))
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
         return (artifacts(prefix, table, path, folder));
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
         return (artifacts(prefix, table, path, "upload"));
      }
      throw (new NoSuchFileException(path));
   }



   /*******************************************************************************
    ** Endpoint targets are resolved from the same user-scoped saved-object queries.
    *******************************************************************************/
   private List<QRecord> visibleEndpoints(JsonNode meta) throws IOException
   {
      try
      {
         List<QRecord> visible = new ArrayList<>();
         for(QRecord alias : audit.query(FileGatewayTables.ALIAS, UploadJobs.equal("isActive", true)))
         {
            if(alias.getValue("accountId") != null && !java.util.Objects.equals(alias.getValue("accountId"), session.account.getValue("id")))
            {
               continue;
            }
            String kind = alias.getValueString("kind");
            if(List.of("INBOX", "OUTBOX").contains(kind))
            {
               visible.add(alias);
               continue;
            }
            String table = alias.getValueString("tableName");
            if(table == null || !meta.path("tables").has(table))
            {
               continue;
            }
            String source = "BULK_LOAD".equals(kind) ? "savedBulkLoadProfile" : "VIEW".equals(kind) ? "savedView" : "savedReport";
            if(!meta.path("tables").has(source)
               || ("BULK_LOAD".equals(kind) && (!meta.path("tables").path(table).path("insertPermission").asBoolean(false) || !meta.path("processes").has(table + ".bulkInsert")))
               || ("REPORT".equals(kind) && (downloads == null || !downloads.reportsAvailable() || !meta.path("processes").has("renderSavedReport"))))
            {
               continue;
            }
            for(JsonNode row : saved(source, table))
            {
               if(row.path("values").path("id").asText().equals(alias.getValueString(source + "Id")))
               {
                  alias.withValue("_label", row.path("values").path("label").asText());
                  visible.add(alias);
                  break;
               }
            }
         }
         return (visible);
      }
      catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
      {
         throw (new IOException("Unable to resolve endpoints", e));
      }
   }



   /*******************************************************************************
    ** An exact visible mount, including an empty upload or outbox folder.
    *******************************************************************************/
   private QRecord endpointMount(String path, JsonNode meta) throws IOException
   {
      return (visibleEndpoints(meta).stream().filter(alias -> path.equals(alias.getValueString("path"))).findFirst().orElse(null));
   }



   /*******************************************************************************
    ** Mount ancestors reveal only the next segment, preserving endpoint chroots.
    *******************************************************************************/
   private List<Node> endpointChildren(String path, JsonNode meta) throws IOException
   {
      List<Node> nodes = new ArrayList<>();
      String prefix = "/".equals(path) ? "/" : path + "/";
      for(QRecord alias : visibleEndpoints(meta))
      {
         String mount = alias.getValueString("path");
         if(mount.startsWith(prefix))
         {
            String suffix = mount.substring(prefix.length()).split("/")[0];
            String child = prefix + suffix;
            if(nodes.stream().noneMatch(node -> child.equals(node.path())))
            {
               nodes.add(new Node(child, true, "ENDPOINT", alias.getValueString("id"), 0, mount.equals(child) ? alias : null));
            }
         }
         if(!mount.equals(path))
         {
            continue;
         }
         String kind = alias.getValueString("kind");
         if(List.of("BULK_LOAD", "INBOX").contains(kind))
         {
            nodes.addAll(artifacts("", alias.getValueString("tableName"), path, "upload"));
         }
         else if("OUTBOX".equals(kind) && outbox != null)
         {
            try
            {
               for(QRecord row : outbox.list(alias.getValueInteger("id")))
               {
                  nodes.add(new Node(prefix + row.getValueString("fileName"), false, "OUTBOX", row.getValueString("id"), row.getValueLong("sizeBytes"), row));
               }
            }
            catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
            {
               throw (new IOException("Unable to list outbox", e));
            }
         }
         else if(List.of("VIEW", "REPORT").contains(kind))
         {
            String format = alias.getValueString("format");
            String formats = session.account.getValueString("formats");
            if(format != null && List.of((formats == null ? "csv,xlsx" : formats).split("[,\\s]+")).contains(format))
            {
               String name = alias.getValueString("fileName");
               if(name == null || name.isBlank())
               {
                  name = slug(alias.getValueString("_label")) + "." + format;
               }
               nodes.add(new Node(prefix + name, false, kind, alias.getValueString("VIEW".equals(kind) ? "savedViewId" : "savedReportId"), 0, alias));
            }
         }
      }
      return (nodes);
   }



   /*******************************************************************************
    ** Sidecars and results expire after seven days; archive objects remain until retention.
    *******************************************************************************/
   private List<Node> artifacts(String prefix, String table, String path, String folder) throws IOException
   {
      List<Node> nodes = new ArrayList<>();
      if("results".equals(folder) && !path.equals(prefix + "/results"))
      {
         throw (new NoSuchFileException(path));
      }
      if(uploads == null)
      {
         return (nodes);
      }
      for(QRecord job : uploads.forTable(session.account.getValueInteger("id"), table))
      {
         String status = job.getValueString("status");
         boolean active = List.of("RECEIVING", "QUEUED", "RUNNING").contains(status);
         boolean visible = job.getValueInstant("acknowledgedAt") == null
            && job.getValueInstant("createDate").plusSeconds(7 * 86400).isAfter(clock.instant());
         String name = job.getValueString("fileName");
         String id = job.getValueString("id");
         if("upload".equals(folder) && visible && job.getValueString("path").startsWith(path + "/"))
         {
            String suffix = active ? ".processing" : List.of("SUCCEEDED", "DUPLICATE").contains(status) ? ".done" : ".error";
            String item = path + "/" + name + suffix;
            nodes.removeIf(node -> node.path().equals(item));
            nodes.add(new Node(item, false, "SIDECAR", id, summary(job).length, job));
            if("RECEIVING".equals(status) && uploads.temporary(name))
            {
               nodes.add(new Node(path + "/" + name, false, "TEMPORARY", id, job.getValueLong("sizeBytes"), job));
            }
         }
         if("results".equals(folder) && visible && job.getValueString("resultsStorageKey") != null)
         {
            nodes.add(new Node(prefix + "/results/" + id + "-" + name + ".results.csv", false, "RESULT", id,
               job.getValueLong("resultsSizeBytes") == null ? 0 : job.getValueLong("resultsSizeBytes"), job));
         }
         if("archive".equals(folder) && !active && job.getValueString("sha256") != null
            && job.getValueString("storageKey") != null && !job.getValueString("storageKey").startsWith("file-gateway/quarantine/"))
         {
            String date = job.getValueInstant("createDate").atZone(ZoneOffset.UTC).toLocalDate().toString();
            String directory = prefix + "/archive/" + date;
            if(path.equals(prefix + "/archive"))
            {
               if(nodes.stream().noneMatch(node -> node.path().equals(directory)))
               {
                  nodes.add(new Node(directory, true, "ARCHIVE", null));
               }
            }
            else if(path.equals(directory))
            {
               nodes.add(new Node(directory + "/" + id + "-" + name, false, "ARCHIVE", id, job.getValueLong("sizeBytes"), job));
            }
         }
      }
      return (nodes);
   }



   /*******************************************************************************
    ** Sidecar text is generated solely from durable job state.
    *******************************************************************************/
   private byte[] summary(QRecord job)
   {
      String text = job.getValueString("status") + "\n";
      if(job.getValueString("resultSummary") != null)
      {
         text += job.getValueString("resultSummary") + "\n";
      }
      else if(job.getValueString("errorMessage") != null)
      {
         text += job.getValueString("errorMessage") + "\n";
      }
      return (text.getBytes(StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** Writes require a visible profile, never a guessed table or profile identifier.
    *******************************************************************************/
   public SeekableByteChannel write(String path) throws IOException
   {
      String parent = path.substring(0, path.lastIndexOf('/'));
      Node target = stat(parent);
      if(uploads != null && target.job() != null && "ENDPOINT".equals(target.kind())
         && List.of("BULK_LOAD", "INBOX").contains(target.job().getValueString("kind")))
      {
         QRecord alias = target.job();
         return (uploads.receive(session, path, alias.getValueString("tableName"), alias.getValueInteger("savedBulkLoadProfileId"), alias));
      }
      if(uploads == null || !target.directory() || !"PROFILE".equals(target.kind()) || target.objectId() == null)
      {
         deny(path, "UPLOAD", "Uploads require a bulk load profile folder");
      }
      String table = parent.substring(0, parent.lastIndexOf("/upload/"));
      table = table.substring(table.lastIndexOf('/') + 1);
      return (uploads.receive(session, path, table, Integer.valueOf(target.objectId())));
   }



   /*******************************************************************************
    ** Open generated views/reports or stored artifacts without using the filesystem.
    *******************************************************************************/
   public SeekableByteChannel read(String path) throws IOException
   {
      Node node = stat(path);
      if(Boolean.TRUE.equals(session.account.getValueBoolean("pgpEncryptDownloads")) && !node.directory() && path.endsWith(".pgp"))
      {
         String plainPath = path.substring(0, path.length() - 4);
         Node plain = rawChildren(plainPath.substring(0, plainPath.lastIndexOf('/'))).stream()
            .filter(candidate -> candidate.path().equals(plainPath)).findFirst().orElseThrow(() -> new NoSuchFileException(path));
         return (encrypted(plain));
      }
      return (readNode(node));
   }



   /*******************************************************************************
    ** Encrypt lazily into the existing fixed rolling window, without a producer thread.
    *******************************************************************************/
   private SeekableByteChannel encrypted(Node node) throws IOException
   {
      SeekableByteChannel source = readNode(node);
      try
      {
         var encrypted = com.kingsrook.qbits.filegateway.pgp.PgpStreams.encrypt(java.nio.channels.Channels.newInputStream(source),
            session.account.getValueString("pgpPublicKey"), node.path().substring(node.path().lastIndexOf('/') + 1));
         boolean[] complete = {false};
         var tracked = new java.io.FilterInputStream(encrypted)
         {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException
            {
               int count = in.read(bytes, offset, length);
               if(count < 0)
               {
                  complete[0] = true;
               }
               return (count);
            }
         };
         return (new com.kingsrook.qbits.filegateway.storage.GeneratedReadChannel(tracked, 65536, new com.kingsrook.qbits.filegateway.storage.GeneratedReadChannel.Audit()
         {
            @Override
            public void charge(int bytes)
            {
               // The underlying transfer accounts for plaintext bytes and applies its limits.
            }

            @Override
            public void finish(IOException failure) throws IOException
            {
               if(complete[0] && failure == null && "OUTBOX".equals(node.kind()))
               {
                  try
                  {
                     outbox.downloaded(node.job());
                  }
                  catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
                  {
                     throw (new IOException("Unable to acknowledge encrypted outbox", e));
                  }
               }
            }
         }));
      }
      catch(IOException | RuntimeException e)
      {
         source.close();
         throw (e);
      }
   }



   /*******************************************************************************
    ** Open a resolved plaintext node through its original authorized delivery path.
    *******************************************************************************/
   private SeekableByteChannel readNode(Node node) throws IOException
   {
      String path = node.path();
      if(downloads != null && !node.directory() && List.of("VIEW", "REPORT").contains(node.kind()))
      {
         return (downloads.open(node));
      }
      if(node.job() == null || node.directory() || "TEMPORARY".equals(node.kind()))
      {
         deny(path, "DOWNLOAD", "This file is not downloadable");
      }
      if(!session.operationAllowed())
      {
         throw (new IOException("Operation limit exceeded"));
      }
      long began = System.nanoTime();
      ObjectReadChannel.Completion completion = (bytes, failed) -> downloadLog(node, bytes, failed, began);
      if("SIDECAR".equals(node.kind()))
      {
         byte[] content = summary(node.job());
         return (new ObjectReadChannel(content.length, (start, end) -> new ByteArrayInputStream(content, (int) start, (int) (end - start + 1)), completion));
      }
      String key = node.job().getValueString("RESULT".equals(node.kind()) ? "resultsStorageKey" : "storageKey");
      ObjectReadChannel channel = new ObjectReadChannel(node.size(), (start, end) -> store.openRange(key, start, end), completion);
      if("OUTBOX".equals(node.kind()) && !Boolean.TRUE.equals(session.account.getValueBoolean("pgpEncryptDownloads")))
      {
         channel.withFullDownload((bytes, failed) ->
         {
            try
            {
               outbox.downloaded(node.job());
            }
            catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
            {
               throw (new IOException("Unable to acknowledge outbox download", e));
            }
         });
      }
      return (channel);
   }



   /*******************************************************************************
    ** Record actual object-download bytes, including ranged reads, once per handle.
    *******************************************************************************/
   private void downloadLog(Node node, long bytes, boolean failed, long began) throws IOException
   {
      try
      {
         audit.insert(FileGatewayTables.TRANSFER_LOG, new QRecord().withValue("accountId", session.account.getValue("id"))
            .withValue("connectionLogId", session.connectionId).withValue("operation", "DOWNLOAD").withValue("path", node.path())
            .withValue("objectKind", "SIDECAR".equals(node.kind()) ? "RESULT" : node.kind()).withValue("objectId", node.objectId())
            .withValue("uploadJobId", "OUTBOX".equals(node.kind()) ? null : node.job().getValue("id")).withValue("bytes", bytes)
            .withValue("sha256", "ARCHIVE".equals(node.kind()) ? node.job().getValue("sha256") : null)
            .withValue("durationMs", (System.nanoTime() - began) / 1_000_000).withValue("outcome", failed ? "ERROR" : "OK")
            .withValue("clientChannel", "sftp/" + session.id));
      }
      catch(Exception e)
      {
         throw (new IOException("Unable to persist download audit", e));
      }
   }



   /*******************************************************************************
    ** Acknowledge visible job artifacts; archive removal remains denied.
    *******************************************************************************/
   public void remove(String path) throws IOException
   {
      Node node = stat(path);
      if("OUTBOX".equals(node.kind()) && outbox != null)
      {
         try
         {
            outbox.remove(node.job());
            log(path, "REMOVE", "OK", null, System.nanoTime());
            return;
         }
         catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
         {
            throw (new IOException("Unable to remove outbox file", e));
         }
      }
      if(node.job() == null || !List.of("SIDECAR", "RESULT").contains(node.kind()))
      {
         deny(path, "REMOVE", "Only sidecars and results can be acknowledged");
      }
      uploads.acknowledge(node.job());
   }



   /*******************************************************************************
    ** Only completed temporary uploads may be renamed within the same visible folder.
    *******************************************************************************/
   public void rename(String source, String target) throws IOException
   {
      Node node = stat(source);
      if(!"TEMPORARY".equals(node.kind()))
      {
         deny(source, "RENAME", "Only temporary uploads can be renamed");
      }
      stat(target.substring(0, target.lastIndexOf('/')));
      uploads.rename(session.account.getValueInteger("id"), source, target);
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
         kind = List.of("SIDECAR", "TEMPORARY").contains(node.kind()) ? "RESULT" : node.kind();
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
