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

package com.kingsrook.qbits.filegateway;

import java.lang.reflect.Modifier;
import java.time.Instant;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/*******************************************************************************
 ** The stable schema and record entities must round-trip every persisted field.
 *******************************************************************************/
class EntityContractTest
{
   /*******************************************************************************
    ** Verify annotations, metadata completeness and fluent accessors against QRecord conversion.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"Account", "SshKey", "Alias", "UploadJob", "OutboxFile", "TransferLog", "ConnectionLog", "Server"})
   void entityRoundTrip(String suffix) throws Exception
   {
      Class<?> type = Class.forName("com.kingsrook.qbits.filegateway.model.FileGateway" + suffix);
      var entity = (QRecordEntity) type.getConstructor().newInstance();
      String tableName = (String) type.getField("TABLE_NAME").get(null);
      var table = FileGatewayTables.tables(new FileGatewayQBitConfig()).stream().filter(t -> t.getName().equals(tableName)).findFirst().orElseThrow();
      for(var field : type.getDeclaredFields())
      {
         if(Modifier.isStatic(field.getModifiers()))
         {
            continue;
         }
         assertThat(field.getAnnotation(QField.class)).isNotNull();
         assertThat(table.getField(field.getName())).as(field.getName()).isNotNull();
         Object value = field.getType() == String.class ? "value-" + field.getName() : field.getType() == Instant.class ? Instant.parse("2026-10-13T12:34:56Z")
            : field.getType() == Boolean.class ? true : field.getType() == Long.class ? (Object) 12345678901L : 17;
         String capitalized = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
         assertThat(type.getMethod("with" + capitalized, field.getType()).invoke(entity, value)).isSameAs(entity);
         assertThat(type.getMethod("get" + capitalized).invoke(entity)).isEqualTo(value);
      }
      QRecord record = entity.toQRecord();
      var restored = (QRecordEntity) type.getConstructor(QRecord.class).newInstance(record);
      assertThat(restored.toQRecord().getValues()).containsAllEntriesOf(record.getValues());
      assertThat(record.getValues().keySet()).containsExactlyInAnyOrderElementsOf(table.getFields().keySet());
   }



   /*******************************************************************************
    ** All documented defaults are available before any runtime has started.
    *******************************************************************************/
   @Test
   void configurationDefaultsAndFluentOptions() throws Exception
   {
      var config = new FileGatewayQBitConfig();
      assertThat(config.getSftpEnabled()).isFalse();
      assertThat(config.getSftpPort()).isEqualTo(2222);
      assertThat(config.getAllowGeneratedHostKey()).isFalse();
      assertThat(config.getHostKeyEnvVarName()).isEqualTo("FILE_GATEWAY_SSH_HOST_KEY");
      assertThat(config.getDefaultMaxConcurrentSessions()).isEqualTo(4);
      assertThat(config.getDefaultMaxOperationsPerMinute()).isEqualTo(600);
      assertThat(config.getLockoutThreshold()).isEqualTo(5);
      assertThat(config.getTemporaryUploadSuffixes()).containsExactly(".filepart", ".tmp", ".part", ".partial");
      for(var method : FileGatewayQBitConfig.class.getDeclaredMethods())
      {
         if(!method.getName().startsWith("with"))
         {
            continue;
         }
         var getter = FileGatewayQBitConfig.class.getMethod("get" + method.getName().substring(4));
         Object value = getter.invoke(config);
         assertThat(method.invoke(config, value)).isSameAs(config);
         assertThat(getter.invoke(config)).isEqualTo(value);
      }
   }
}
