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


import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.digest.BuiltinDigests;


/*******************************************************************************
 ** Validates stored public keys and computes authoritative key metadata.
 *******************************************************************************/
public class SshKeyCustomizer implements TableCustomizerInterface
{



   /*******************************************************************************
    ** Parse and validate inserts and partial updates without logging key material.
    *******************************************************************************/
   @Override
   public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecords)
   {
      for(QRecord record : records)
      {
         QRecord old = oldRecords.orElse(List.of()).stream().filter(candidate -> Objects.equals(candidate.getValue("id"), record.getValue("id"))).findFirst().orElse(null);
         String text = record.getValues().containsKey("publicKey") ? record.getValueString("publicKey") : old == null ? null : old.getValueString("publicKey");
         try
         {
            PublicKey key = PublicKeyEntry.parsePublicKeyEntry(text).resolvePublicKey(null, Map.of(), null);
            String type = KeyUtils.getKeyType(key);
            int bits = KeyUtils.getKeySize(key);
            if(!("ssh-ed25519".equals(type) || type.startsWith("ecdsa-sha2-nistp") || ("ssh-rsa".equals(type) && bits >= 3072)))
            {
               throw (new IllegalArgumentException("Unsupported key type or size"));
            }
            record.setValue("fingerprint", KeyUtils.getFingerPrint(BuiltinDigests.sha256, key));
            record.setValue("keyType", type);
            record.setValue("keyBits", bits);
         }
         catch(Exception e)
         {
            record.addError(new BadInputStatusMessage("Use an OpenSSH Ed25519, ECDSA, or RSA key of at least 3072 bits"));
         }
      }
      return (records);
   }
}
