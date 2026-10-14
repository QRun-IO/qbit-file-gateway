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
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;

/*******************************************************************************
 ** Reject account names that cannot be used as stable SSH login names.
 *******************************************************************************/
public class AccountCustomizer implements TableCustomizerInterface
{
   /*******************************************************************************
    ** Partial updates preserve the current username; supplied names must be valid.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean preview, Optional<List<QRecord>> oldRecords)
   {
      for(QRecord record : records)
      {
         if(record.getValues().containsKey("username") && (record.getValueString("username") == null || !record.getValueString("username").matches("[a-z0-9._-]{3,64}")))
         {
            record.addError(new BadInputStatusMessage("Username must be 3 to 64 lowercase letters, digits, dots, underscores or hyphens"));
         }
         for(String limit : List.of("maxConcurrentSessions", "maxOperationsPerMinute", "maxFileSizeMb", "maxUploadMbPerDay", "maxDownloadMbPerDay"))
         {
            if(record.getValueInteger(limit) != null && record.getValueInteger(limit) < 1)
            {
               record.addError(new BadInputStatusMessage(limit + " must be positive, or blank for an unlimited daily quota"));
            }
         }
      }
      return (records);
   }
}
