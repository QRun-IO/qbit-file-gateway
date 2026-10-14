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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextBlockData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextValues;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;

/*******************************************************************************
 ** Typed home widgets over real gateway data, queried as the viewing user.
 *******************************************************************************/
public class FileGatewayHome extends AbstractWidgetRenderer
{
   /** Readable UTC timestamps in home tables (the widget title already names the table). */
   private static final java.time.format.DateTimeFormatter WHEN = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

   public static final List<String> KEYS = List.of("transfers", "failures", "sessions", "in", "out", "locked", "recent", "partners", "active", "servers");
   private static final List<String> LABELS = List.of("Transfers today", "Failed jobs (24 hours)", "Active sessions", "MB in today", "MB out today", "Locked accounts",
      "Recent failures", "Top partners (7 days)", "Active sessions", "Server");



   /*******************************************************************************
    ** Six small statistics followed by full-width responsive tables, without HTML.
    *******************************************************************************/
   public static List<QWidgetMetaData> widgets()
   {
      List<QWidgetMetaData> widgets = new ArrayList<>();
      for(int i = 0; i < KEYS.size(); i++)
      {
         widgets.add(new QWidgetMetaData().withName("file_gateway_home_" + KEYS.get(i)).withLabel(LABELS.get(i))
            .withType((i < 6 ? WidgetType.STATISTICS : WidgetType.TABLE).getType()).withGridColumns(i < 6 ? 2 : 12)
            .withIsCard(true).withShowReloadButton(true).withCodeReference(new QCodeReference(FileGatewayHome.class))
            .withPermissionRules(FileGatewayTables.permissionRules("file_gateway")));
      }
      return (widgets);
   }



   /*******************************************************************************
    ** The same permission is required for direct renderer calls and widget routes.
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      return (new RenderWidgetOutput(data(input.getWidgetMetaData().getName().replace("file_gateway_home_", ""), Instant.now())));
   }



   /*******************************************************************************
    ** Accept an explicit time for deterministic seeded-data assertions.
    *******************************************************************************/
   public QWidgetData data(String key, Instant now) throws QException
   {
      if(QContext.getQSession() == null || !(QContext.getQSession().hasPermission(FileGatewayPermissions.VIEW) || QContext.getQSession().hasPermission(FileGatewayPermissions.ADMIN)))
      {
         throw (new QPermissionDeniedException("Missing file_gateway.view"));
      }
      Instant today = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
      if(List.of("transfers", "in", "out").contains(key))
      {
         var rows = query(FileGatewayTables.TRANSFER_LOG, since("createDate", today));
         long value = "transfers".equals(key) ? rows.size() : rows.stream()
            .filter(row -> ("in".equals(key) ? "UPLOAD" : "DOWNLOAD").equals(row.getValueString("operation")))
            .mapToLong(row -> number(row, "bytes")).sum();
         return (new StatisticsData("transfers".equals(key) ? value : Math.round(value / 10485.76) / 100.0, null, null));
      }
      if("locked".equals(key))
      {
         long count = query(FileGatewayTables.ACCOUNT, new QQueryFilter()).stream().filter(row -> "LOCKED".equals(row.getValueString("status"))
            || (row.getValueInstant("lockedUntil") != null && row.getValueInstant("lockedUntil").isAfter(now))).count();
         return (new StatisticsData(count, null, null));
      }
      if(List.of("failures", "recent").contains(key))
      {
         var rows = query(FileGatewayTables.UPLOAD_JOB, since("finishedAt", now.minusSeconds("failures".equals(key) ? 86400 : 7 * 86400))
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, "FAILED", "PARTIAL", "REJECTED")));
         if("failures".equals(key))
         {
            return (new StatisticsData(rows.size(), null, null));
         }
         rows.sort(java.util.Comparator.comparing((QRecord row) -> row.getValueInstant("finishedAt")).reversed());
         return (table("Recent failures", FileGatewayTables.UPLOAD_JOB, rows.stream().limit(20).toList(),
            List.of("fileName", "status", "errorMessage", "finishedAt"), List.of("File", "Status", "Message", "Finished")));
      }
      if(List.of("sessions", "active").contains(key))
      {
         var rows = query(FileGatewayTables.CONNECTION_LOG, since("lastSeenAt", now.minusSeconds(180))
            .withCriteria(new QFilterCriteria("disconnectedAt", QCriteriaOperator.IS_BLANK))
            .withCriteria(new QFilterCriteria("outcome", QCriteriaOperator.EQUALS, "CONNECTED")));
         return ("sessions".equals(key) ? new StatisticsData(rows.size(), null, null) : table("Active sessions", FileGatewayTables.CONNECTION_LOG, rows,
            List.of("username", "remoteIp", "connectedAt", "operationCount", "podName"), List.of("Username", "IP", "Connected", "Operations", "Pod")));
      }
      if("servers".equals(key))
      {
         return (table("Server", FileGatewayTables.SERVER, query(FileGatewayTables.SERVER, since("lastSeenAt", now.minusSeconds(180))),
            List.of("podName", "hostKeyFingerprint", "port", "lastSeenAt"), List.of("Pod", "Host key fingerprint", "Port", "Last seen")));
      }
      Map<Integer, QRecord> partners = new LinkedHashMap<>();
      for(QRecord account : query(FileGatewayTables.ACCOUNT, new QQueryFilter()))
      {
         partners.put(account.getValueInteger("id"), account.withValue("transfers", 0L).withValue("mb", 0.0));
      }
      for(QRecord transfer : query(FileGatewayTables.TRANSFER_LOG, since("createDate", now.minusSeconds(7 * 86400))))
      {
         QRecord partner = partners.get(transfer.getValueInteger("accountId"));
         if(partner != null)
         {
            partner.withValue("transfers", number(partner, "transfers") + 1)
               .withValue("mb", ((Number) partner.getValue("mb")).doubleValue() + number(transfer, "bytes") / 1048576.0);
         }
      }
      List<QRecord> rows = partners.values().stream().filter(row -> number(row, "transfers") > 0)
         .sorted(java.util.Comparator.comparingLong((QRecord row) -> number(row, "transfers")).reversed()).limit(10).toList();
      return (table("Top partners (7 days)", FileGatewayTables.ACCOUNT, rows, List.of("username", "transfers", "mb"), List.of("Partner", "Transfers", "MB")));
   }



   /*******************************************************************************
    ** Typed text blocks carry record links; cells never contain markup.
    *******************************************************************************/
   private TableData table(String label, String table, List<QRecord> records, List<String> fields, List<String> labels)
   {
      List<TableData.Column> columns = new ArrayList<>();
      for(int i = 0; i < fields.size(); i++)
      {
         columns.add(new TableData.Column(i == 0 ? "block" : "default", labels.get(i), fields.get(i), "1fr", null));
      }
      List<Map<String, Object>> rows = new ArrayList<>();
      for(QRecord record : records)
      {
         Map<String, Object> row = new LinkedHashMap<>();
         for(String field : fields)
         {
            Object value = record.getValue(field);
            row.put(field, value == null ? "" : value instanceof Instant instant ? WHEN.format(instant) : value);
         }
         row.put(fields.getFirst(), new TextBlockData().withValues(new TextValues(record.getValueString(fields.getFirst())))
            .withLink("/record/" + table + "/" + record.getValue("id")));
         rows.add(row);
      }
      return (new TableData(null, columns, rows).withRowsPerPage(10).withHidePaginationDropdown(true));
   }



   /*******************************************************************************
    ** Queries preserve viewer security and use bounded database pages.
    *******************************************************************************/
   private List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      List<QRecord> rows = new ArrayList<>();
      for(int offset = 0; ; offset += 250)
      {
         var page = new QueryAction().execute(new QueryInput(table).withFilter(filter.withSkip(offset).withLimit(250).withOrderBy(new QFilterOrderBy("id", true)))).getRecords();
         rows.addAll(page);
         if(page.size() < 250)
         {
            return (rows);
         }
      }
   }



   /*******************************************************************************
    ** UTC time windows agree with quota and transfer bookkeeping.
    *******************************************************************************/
   private QQueryFilter since(String field, Instant from)
   {
      return (new QQueryFilter(new QFilterCriteria(field, QCriteriaOperator.GREATER_THAN_OR_EQUALS, from)));
   }



   /*******************************************************************************
    ** Older audit rows may have no counters.
    *******************************************************************************/
   private long number(QRecord row, String field)
   {
      return (row.getValueLong(field) == null ? 0 : row.getValueLong(field));
   }
}
