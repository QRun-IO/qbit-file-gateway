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

package com.kingsrook.qbits.filegateway.sftp;


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.permissions.CorePermissionEnforcer;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;


/*******************************************************************************
 ** System-session access limited to the gateway own tables; application data uses the UI API.
 *******************************************************************************/
public class AuditWriter
{
   private final QInstance instance;
   private final ThreadLocal<QBackendTransaction> transaction = new ThreadLocal<>();



   /*******************************************************************************
    ** Bind the gateway database instance.
    *******************************************************************************/
   public AuditWriter(QInstance instance)
   {
      this.instance = instance;
   }


   /*******************************************************************************
    ** Work within a short system-session scope.
    *******************************************************************************/
   @FunctionalInterface
   public interface Work<T>
   {
      /*******************************************************************************
       ** Execute the database operation.
       *******************************************************************************/
      T run() throws QException;
   }



   /*******************************************************************************
    ** Preserve caller context while writing gateway-owned state.
    *******************************************************************************/
   public <T> T system(Work<T> work) throws QException
   {
      var previous = QContext.capture();
      try
      {
         QContext.init(instance, new QSystemUserSession(instance));
         try(var ignored = CorePermissionEnforcer.openAuthorizedScope())
         {
            return (work.run());
         }
      }
      finally
      {
         QContext.init(previous);
      }
   }



   /*******************************************************************************
    ** Serialize account authentication across pods with a transactional row update.
    ** Re-reading after acquiring the write lock sees committed counters and sessions.
    *******************************************************************************/
   public <T> T withAccountLock(String username, Work<T> work) throws QException
   {
      return (withAccountLock("username", username, work));
   }



   /*******************************************************************************
    ** Lock one account row without changing any user-managed fields.
    *******************************************************************************/
   private <T> T withAccountLock(String field, Serializable value, Work<T> work) throws QException
   {
      return (system(() ->
      {
         try(var tx = QBackendTransaction.openFor(new QueryInput(FileGatewayTables.ACCOUNT)))
         {
            transaction.set(tx);
            try
            {
               var rows = query(FileGatewayTables.ACCOUNT, new QQueryFilter(new QFilterCriteria(field, QCriteriaOperator.EQUALS, value)));
               if(!rows.isEmpty())
               {
                  update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", rows.getFirst().getValue("id")).withValue("modifyDate", Instant.now()));
               }
               T result = work.run();
               tx.commit();
               return (result);
            }
            catch(Exception e)
            {
               tx.rollback();
               throw (new QException("Gateway account transaction failed", e));
            }
            finally
            {
               transaction.remove();
            }
         }
      }));
   }



   /*******************************************************************************
    ** Charge an account-wide minute window atomically, including other gateway pods.
    *******************************************************************************/
   public boolean operationAllowed(Integer accountId, Instant now, int maximum) throws QException
   {
      return (withAccountLock("id", accountId, () ->
      {
         var rows = query(FileGatewayTables.ACCOUNT, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, accountId)));
         if(rows.isEmpty())
         {
            throw (new QException("Gateway account no longer exists"));
         }
         QRecord account = rows.getFirst();
         Instant window = account.getValueInstant("operationWindowAt");
         Integer previous = account.getValueInteger("operationWindowCount");
         boolean reset = window == null || !window.plusSeconds(60).isAfter(now);
         int count = reset ? 1 : (previous == null ? 0 : previous) + 1;
         update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", accountId)
            .withValue("operationWindowAt", reset ? now : window).withValue("operationWindowCount", count));
         return (count <= maximum);
      }));
   }



   /*******************************************************************************
    ** Reject accidental use on application data.
    *******************************************************************************/
   private void checkTable(String table)
   {
      if(!FileGatewayTables.ALL.contains(table))
      {
         throw (new IllegalArgumentException("System access is limited to gateway tables"));
      }
   }



   /*******************************************************************************
    ** Read gateway state.
    *******************************************************************************/
   public List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      checkTable(table);
      return (system(() -> new QueryAction().execute(new QueryInput(table).withFilter(filter).withTransaction(transaction.get())).getRecords()));
   }



   /*******************************************************************************
    ** Insert and require successful persistence.
    *******************************************************************************/
   public QRecord insert(String table, QRecord record) throws QException
   {
      checkTable(table);
      QRecord result = system(() -> new InsertAction().execute(new InsertInput(table).withRecord(record).withTransaction(transaction.get())).getRecords().get(0));
      checkErrors(result);
      return (result);
   }



   /*******************************************************************************
    ** Update gateway state.
    *******************************************************************************/
   public void update(String table, QRecord record) throws QException
   {
      checkTable(table);
      QRecord result = system(() -> new UpdateAction().execute(new UpdateInput(table).withRecords(List.of(record)).withTransaction(transaction.get())).getRecords().get(0));
      checkErrors(result);
   }



   /*******************************************************************************
    ** Fail closed on bookkeeping errors without printing records.
    *******************************************************************************/
   private void checkErrors(QRecord record) throws QException
   {
      if(record.getErrors() != null && !record.getErrors().isEmpty())
      {
         throw (new QException("Unable to persist gateway state"));
      }
   }
}
