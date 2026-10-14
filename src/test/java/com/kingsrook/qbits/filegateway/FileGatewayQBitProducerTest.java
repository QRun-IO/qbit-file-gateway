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

import java.util.List;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/*******************************************************************************
 ** Registration, channel walls and contingent endpoint pickers.
 *******************************************************************************/
class FileGatewayQBitProducerTest
{
   /*******************************************************************************
    ** All stable metadata belongs to one configured qbit.
    *******************************************************************************/
   @Test
   void registersTablesAppPickersAndWalls() throws Exception
   {
      var instance = new QInstance();
      var config = new FileGatewayQBitConfig().withBackendName("host-db").withTableMetaDataCustomizer((i, t) -> t.withLabel("Custom " + t.getLabel()));
      var producer = new FileGatewayQBitProducer().withQBitConfig(config).withNamespace("test");
      producer.produce(instance).addSelfToInstance(instance);
      assertThat(instance.getTables().keySet()).containsExactlyInAnyOrderElementsOf(FileGatewayTables.ALL);
      assertThat(instance.getQBits().get("com.kingsrook.qbits:qbit-file-gateway:test").getConfig()).isSameAs(config);
      assertThat(producer.getQBitConfig()).isSameAs(config);
      assertThat(producer.getNamespace()).isEqualTo("test");
      assertThat(instance.getApps()).containsOnlyKeys("file_gateway");
      assertThat(instance.getApp("file_gateway").getSections().stream().flatMap(s -> s.getTables().stream()).toList())
         .containsExactlyElementsOf(FileGatewayTables.MAIN);
      assertThat(instance.getJoins()).hasSize(6);
      assertThat(instance.getWidgets()).hasSize(16);
      assertThat(instance.getPossibleValueSources()).hasSize(20);
      instance.getTables().values().forEach(t ->
      {
         assertThat(t.getBackendName()).isEqualTo("host-db");
         assertThat(t.getLabel()).startsWith("Custom ");
         assertThat(t.getField("createDate").getBackendName()).isEqualTo("create_date");
      });
      var channels = ClientChannelsMetaData.ofOrWithNew(instance);
      assertThat(channels.getChannels()).containsKey("sftp");
      for(String name : List.of("file_gateway_account", "authentik_user", "audit", "api_key", "credential", "secret", "password", "permission", "role", "user_session", "webhooks_endpoint"))
      {
         assertThat(channels.isDenied("sftp", name, null)).as(name).isTrue();
      }
      assertThat(channels.isDenied("assistant", "file_gateway_ssh_key", null)).isTrue();
      assertThat(channels.isDenied("sftp", "orders", null)).isFalse();
      assertThat(instance.getTable(FileGatewayTables.ALIAS).getField("tableName").getPossibleValueSourceName()).isEqualTo("tables");
   }



   /*******************************************************************************
    ** Package scanning never enables an unconfigured producer.
    *******************************************************************************/
   @Test
   void disabledWithoutConfig() throws Exception
   {
      var instance = new QInstance();
      var producer = new FileGatewayQBitProducer();
      assertThat(producer.isEnabled()).isFalse();
      producer.produce(instance).addSelfToInstance(instance);
      assertThat(instance.getTables()).isEmpty();
      assertThat(instance.getQBits()).isEmpty();
      assertThat(FileGatewayRuntime.start(instance, null)).isNull();
      new FileGatewayQBitProducer().withQBitConfig(new FileGatewayQBitConfig()).produce(instance);
      assertThat(FileGatewayRuntime.start(instance, null, name -> null)).isNull();
   }



   /*******************************************************************************
    ** Each dependent endpoint field has exactly the specified input criterion.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"savedBulkLoadProfile", "savedView", "savedReport"})
   void contingentPickerMetadata(String source) throws Exception
   {
      var instance = new QInstance();
      new FileGatewayQBitProducer().withQBitConfig(new FileGatewayQBitConfig()).produce(instance);
      var field = instance.getTable(FileGatewayTables.ALIAS).getField(source + "Id");
      assertThat(field.getPossibleValueSourceName()).isEqualTo(source);
      var filter = field.getPossibleValueSourceFilter();
      assertThat(filter.getCriteria()).singleElement().satisfies(c ->
      {
         assertThat(c.getFieldName()).isEqualTo("tableName");
         assertThat(c.getOperator().name()).isEqualTo("EQUALS");
         assertThat(c.getValues()).containsExactly("${input.tableName}");
      });
      assertThat(filter.getSubFilters()).isNullOrEmpty();
   }
}
