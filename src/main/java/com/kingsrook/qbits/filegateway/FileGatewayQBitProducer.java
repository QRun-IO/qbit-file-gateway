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
import com.kingsrook.qbits.filegateway.metadata.FileGatewayProcesses;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.permissions.FileGatewayPermissions;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Entry point for the file-gateway metadata; runtime startup belongs to the host.
 *******************************************************************************/
public class FileGatewayQBitProducer implements QBitMetaDataProducer<FileGatewayQBitConfig>
{
   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "qbit-file-gateway";
   public static final String VERSION     = "0.1.0";

   private FileGatewayQBitConfig qBitConfig;
   private String namespace;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public FileGatewayQBitProducer withQBitConfig(FileGatewayQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the config.
    *******************************************************************************/
   @Override
   public FileGatewayQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the namespace.
    *******************************************************************************/
   public FileGatewayQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the namespace.
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (namespace);
   }



   /*******************************************************************************
    ** Identity and configuration of this qbit.
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData().withGroupId(GROUP_ID).withArtifactId(ARTIFACT_ID).withVersion(VERSION).withNamespace(namespace).withConfig(qBitConfig));
   }



   /*******************************************************************************
    ** Package-scanned producers have no config: skip them.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Register tables, child lists, permissions, channel walls and the application.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(!isEnabled())
      {
         return (new MetaDataProducerMultiOutput());
      }
      QBitMetaData qBit = getQBitMetaData();
      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput().with(qBit);
      FileGatewayTables.possibleValueSources().forEach(output::add);
      QAppMetaData app = new QAppMetaData().withName("file_gateway").withLabel("File Gateway")
         .withIcon(new QIcon("folder_shared")).withPermissionRules(FileGatewayTables.permissionRules("file_gateway"));
      for(QTableMetaData table : FileGatewayTables.tables(qBitConfig))
      {
         if(qBitConfig.getTableMetaDataCustomizer() != null)
         {
            table = qBitConfig.getTableMetaDataCustomizer().customizeMetaData(qInstance, table);
         }
         table.setSourceQBitName(qBit.getName());
         output.add(table);
         if(FileGatewayTables.MAIN.contains(table.getName()))
         {
            app.withChild(table);
         }
      }
      app.withSection(new QAppSection().withName("configuration").withLabel("Configuration")
         .withTables(List.of(FileGatewayTables.ACCOUNT, FileGatewayTables.ALIAS)));
      app.withSection(new QAppSection().withName("activity").withLabel("Activity")
         .withTables(List.of(FileGatewayTables.UPLOAD_JOB, FileGatewayTables.TRANSFER_LOG, FileGatewayTables.CONNECTION_LOG)));
      FileGatewayTables.joins().forEach(output::add);
      FileGatewayTables.childWidgets().forEach(output::add);
      var home = com.kingsrook.qbits.filegateway.metadata.FileGatewayHome.widgets();
      home.forEach(output::add);
      app.withWidgets(home.stream().map(widget -> widget.getName()).toList());
      output.add(app);
      FileGatewayProcesses.processes().forEach(output::add);
      output.add(com.kingsrook.qbits.filegateway.maintenance.GatewayMaintenance.metadata(qBitConfig));
      output.add(new QAlertTypeMetaData().withName("filegateway.lockout").withLabel("File Gateway Lockout")
         .withSourceQBitName(qBit.getName()).withSubjectTableName(FileGatewayTables.ACCOUNT)
         .withVisibilityPermission(FileGatewayPermissions.VIEW));
      output.addSelfToInstance(qInstance);
      ClientChannelsMetaData channels = ClientChannelsMetaData.ofOrWithNew(qInstance)
         .withChannel("sftp", "SFTP file gateway").withDeniedPattern("sftp", "file_gateway_*")
         .withDeniedPattern("assistant", "file_gateway_*");
      for(String pattern : ClientChannelsMetaData.ASSISTANT_DEFAULT_DENIED_PATTERNS)
      {
         if(!"assistant_*".equals(pattern))
         {
            channels.withDeniedPattern("sftp", pattern);
         }
      }
      com.kingsrook.qbits.filegateway.api.FileGatewayOutbox.setProvider(new com.kingsrook.qbits.filegateway.outbox.OutboxService(qInstance,
         new com.kingsrook.qbits.filegateway.storage.ObjectStorageGatewayStore(qInstance, qBitConfig.getFileGatewayBucketAlias()), qBitConfig, java.time.Clock.systemUTC()));
      return (new MetaDataProducerMultiOutput());
   }
}
