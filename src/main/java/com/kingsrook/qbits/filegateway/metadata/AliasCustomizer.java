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

import java.util.List;
import java.util.Optional;
import com.kingsrook.qbits.filegateway.sftp.AuditWriter;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;

/*******************************************************************************
 ** Endpoints are canonical mounts, never a way to shadow application paths.
 *******************************************************************************/
public class AliasCustomizer implements TableCustomizerInterface
{
   /*******************************************************************************
    ** Validate the merged row so sparse updates cannot bypass mount validation.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean preview, Optional<List<QRecord>> oldRecords) throws QException
   {
      AuditWriter audit = new AuditWriter(QContext.getQInstance());
      for(QRecord record : records)
      {
         QRecord merged = new QRecord();
         oldRecords.orElse(List.of()).stream().filter(old -> java.util.Objects.equals(old.getValue("id"), record.getValue("id")))
            .findFirst().ifPresent(old -> merged.getValues().putAll(old.getValues()));
         merged.getValues().putAll(record.getValues());
         String path = merged.getValueString("path");
         String error = null;
         if(path == null || !path.matches("/[a-z0-9._/-]+") || path.contains("..") || path.contains("//") || path.endsWith("/") || List.of(path.split("/")).contains("."))
         {
            error = "Endpoint path must be absolute, use lowercase letters, digits, dots, underscores, hyphens and slashes, and contain no dot segments or empty segments";
         }
         else
         {
            boolean fullTree = merged.getValue("accountId") == null;
            if(!fullTree)
            {
               var accounts = audit.query(FileGatewayTables.ACCOUNT, UploadJobs.equal("id", merged.getValue("accountId")));
               fullTree = accounts.isEmpty() || !"ENDPOINTS_ONLY".equals(accounts.getFirst().getValueString("rootMode"));
            }
            if(fullTree && QContext.getQInstance().getApps().containsKey(path.substring(1).split("/")[0]))
            {
               error = "Endpoint path collides with an application name for a FULL_TREE account";
            }
            for(QRecord other : audit.query(FileGatewayTables.ALIAS, new QQueryFilter()))
            {
               String mount = other.getValueString("path");
               if(!java.util.Objects.equals(other.getValue("id"), merged.getValue("id")) && mount != null
                  && (path.equals(mount) || path.startsWith(mount + "/") || mount.startsWith(path + "/")))
               {
                  error = "Endpoint paths must be unique and must not overlap another endpoint mount";
               }
            }
         }
         String filename = merged.getValueString("fileName");
         if(filename != null && !validFileName(filename))
         {
            error = "Endpoint file name must be a single safe filename";
         }
         if(error != null)
         {
            record.addError(new BadInputStatusMessage(error));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** Object names cannot escape a mount or inject control characters.
    *******************************************************************************/
   public static boolean validFileName(String name)
   {
      return (name != null && !name.isBlank() && !name.equals(".") && !name.equals("..") && !name.contains("/")
         && !name.contains("\\") && !name.contains(":") && name.chars().noneMatch(Character::isISOControl));
   }
}
