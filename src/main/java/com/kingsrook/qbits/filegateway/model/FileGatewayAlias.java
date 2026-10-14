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

package com.kingsrook.qbits.filegateway.model;


import java.time.Instant;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;


/*******************************************************************************
 ** Record entity for endpoints.
 *******************************************************************************/
public class FileGatewayAlias extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_alias";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private String name;

   @QField
   private String path;

   @QField
   private String kind;

   @QField
   private Integer accountId;

   @QField
   private String tableName;

   @QField
   private Integer savedBulkLoadProfileId;

   @QField
   private Integer savedViewId;

   @QField
   private Integer savedReportId;

   @QField
   private String format;

   @QField
   private String fileName;

   @QField
   private String consumerName;

   @QField
   private Boolean isActive;

   @QField
   private String description;



   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayAlias()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayAlias(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id.
    *******************************************************************************/
   public Integer getId()
   {
      return (id);
   }



   /*******************************************************************************
    ** Fluent setter for id.
    *******************************************************************************/
   public FileGatewayAlias withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate.
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (createDate);
   }



   /*******************************************************************************
    ** Fluent setter for createDate.
    *******************************************************************************/
   public FileGatewayAlias withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate.
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (modifyDate);
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate.
    *******************************************************************************/
   public FileGatewayAlias withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name.
    *******************************************************************************/
   public String getName()
   {
      return (name);
   }



   /*******************************************************************************
    ** Fluent setter for name.
    *******************************************************************************/
   public FileGatewayAlias withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for path.
    *******************************************************************************/
   public String getPath()
   {
      return (path);
   }



   /*******************************************************************************
    ** Fluent setter for path.
    *******************************************************************************/
   public FileGatewayAlias withPath(String path)
   {
      this.path = path;
      return (this);
   }



   /*******************************************************************************
    ** Getter for kind.
    *******************************************************************************/
   public String getKind()
   {
      return (kind);
   }



   /*******************************************************************************
    ** Fluent setter for kind.
    *******************************************************************************/
   public FileGatewayAlias withKind(String kind)
   {
      this.kind = kind;
      return (this);
   }



   /*******************************************************************************
    ** Getter for accountId.
    *******************************************************************************/
   public Integer getAccountId()
   {
      return (accountId);
   }



   /*******************************************************************************
    ** Fluent setter for accountId.
    *******************************************************************************/
   public FileGatewayAlias withAccountId(Integer accountId)
   {
      this.accountId = accountId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableName.
    *******************************************************************************/
   public String getTableName()
   {
      return (tableName);
   }



   /*******************************************************************************
    ** Fluent setter for tableName.
    *******************************************************************************/
   public FileGatewayAlias withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for savedBulkLoadProfileId.
    *******************************************************************************/
   public Integer getSavedBulkLoadProfileId()
   {
      return (savedBulkLoadProfileId);
   }



   /*******************************************************************************
    ** Fluent setter for savedBulkLoadProfileId.
    *******************************************************************************/
   public FileGatewayAlias withSavedBulkLoadProfileId(Integer savedBulkLoadProfileId)
   {
      this.savedBulkLoadProfileId = savedBulkLoadProfileId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for savedViewId.
    *******************************************************************************/
   public Integer getSavedViewId()
   {
      return (savedViewId);
   }



   /*******************************************************************************
    ** Fluent setter for savedViewId.
    *******************************************************************************/
   public FileGatewayAlias withSavedViewId(Integer savedViewId)
   {
      this.savedViewId = savedViewId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for savedReportId.
    *******************************************************************************/
   public Integer getSavedReportId()
   {
      return (savedReportId);
   }



   /*******************************************************************************
    ** Fluent setter for savedReportId.
    *******************************************************************************/
   public FileGatewayAlias withSavedReportId(Integer savedReportId)
   {
      this.savedReportId = savedReportId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for format.
    *******************************************************************************/
   public String getFormat()
   {
      return (format);
   }



   /*******************************************************************************
    ** Fluent setter for format.
    *******************************************************************************/
   public FileGatewayAlias withFormat(String format)
   {
      this.format = format;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fileName.
    *******************************************************************************/
   public String getFileName()
   {
      return (fileName);
   }



   /*******************************************************************************
    ** Fluent setter for fileName.
    *******************************************************************************/
   public FileGatewayAlias withFileName(String fileName)
   {
      this.fileName = fileName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for consumerName.
    *******************************************************************************/
   public String getConsumerName()
   {
      return (consumerName);
   }



   /*******************************************************************************
    ** Fluent setter for consumerName.
    *******************************************************************************/
   public FileGatewayAlias withConsumerName(String consumerName)
   {
      this.consumerName = consumerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for isActive.
    *******************************************************************************/
   public Boolean getIsActive()
   {
      return (isActive);
   }



   /*******************************************************************************
    ** Fluent setter for isActive.
    *******************************************************************************/
   public FileGatewayAlias withIsActive(Boolean isActive)
   {
      this.isActive = isActive;
      return (this);
   }



   /*******************************************************************************
    ** Getter for description.
    *******************************************************************************/
   public String getDescription()
   {
      return (description);
   }



   /*******************************************************************************
    ** Fluent setter for description.
    *******************************************************************************/
   public FileGatewayAlias withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPath(String path)
   {
      this.path = path;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setKind(String kind)
   {
      this.kind = kind;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setAccountId(Integer accountId)
   {
      this.accountId = accountId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setSavedBulkLoadProfileId(Integer savedBulkLoadProfileId)
   {
      this.savedBulkLoadProfileId = savedBulkLoadProfileId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setSavedViewId(Integer savedViewId)
   {
      this.savedViewId = savedViewId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setSavedReportId(Integer savedReportId)
   {
      this.savedReportId = savedReportId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFormat(String format)
   {
      this.format = format;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFileName(String fileName)
   {
      this.fileName = fileName;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setConsumerName(String consumerName)
   {
      this.consumerName = consumerName;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setIsActive(Boolean isActive)
   {
      this.isActive = isActive;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }
}
