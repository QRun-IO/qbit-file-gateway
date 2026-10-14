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
 ** Record entity for outbox files.
 *******************************************************************************/
public class FileGatewayOutboxFile extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_outbox_file";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private Integer aliasId;

   @QField
   private String fileName;

   @QField
   private Long sizeBytes;

   @QField
   private String sha256;

   @QField
   private String storageKey;

   @QField
   private String status;

   @QField
   private Instant firstDownloadedAt;

   @QField
   private Integer downloadCount;

   @QField
   private Instant removedAt;

   @QField
   private Instant expiresAt;

   @QField
   private String createdByConsumer;



   @QField
   private Boolean onDownloadedPending;


   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayOutboxFile()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayOutboxFile(QRecord record) throws QException
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
   public FileGatewayOutboxFile withId(Integer id)
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
   public FileGatewayOutboxFile withCreateDate(Instant createDate)
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
   public FileGatewayOutboxFile withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for aliasId.
    *******************************************************************************/
   public Integer getAliasId()
   {
      return (aliasId);
   }



   /*******************************************************************************
    ** Fluent setter for aliasId.
    *******************************************************************************/
   public FileGatewayOutboxFile withAliasId(Integer aliasId)
   {
      this.aliasId = aliasId;
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
   public FileGatewayOutboxFile withFileName(String fileName)
   {
      this.fileName = fileName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sizeBytes.
    *******************************************************************************/
   public Long getSizeBytes()
   {
      return (sizeBytes);
   }



   /*******************************************************************************
    ** Fluent setter for sizeBytes.
    *******************************************************************************/
   public FileGatewayOutboxFile withSizeBytes(Long sizeBytes)
   {
      this.sizeBytes = sizeBytes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sha256.
    *******************************************************************************/
   public String getSha256()
   {
      return (sha256);
   }



   /*******************************************************************************
    ** Fluent setter for sha256.
    *******************************************************************************/
   public FileGatewayOutboxFile withSha256(String sha256)
   {
      this.sha256 = sha256;
      return (this);
   }



   /*******************************************************************************
    ** Getter for storageKey.
    *******************************************************************************/
   public String getStorageKey()
   {
      return (storageKey);
   }



   /*******************************************************************************
    ** Fluent setter for storageKey.
    *******************************************************************************/
   public FileGatewayOutboxFile withStorageKey(String storageKey)
   {
      this.storageKey = storageKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status.
    *******************************************************************************/
   public String getStatus()
   {
      return (status);
   }



   /*******************************************************************************
    ** Fluent setter for status.
    *******************************************************************************/
   public FileGatewayOutboxFile withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for firstDownloadedAt.
    *******************************************************************************/
   public Instant getFirstDownloadedAt()
   {
      return (firstDownloadedAt);
   }



   /*******************************************************************************
    ** Fluent setter for firstDownloadedAt.
    *******************************************************************************/
   public FileGatewayOutboxFile withFirstDownloadedAt(Instant firstDownloadedAt)
   {
      this.firstDownloadedAt = firstDownloadedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for downloadCount.
    *******************************************************************************/
   public Integer getDownloadCount()
   {
      return (downloadCount);
   }



   /*******************************************************************************
    ** Fluent setter for downloadCount.
    *******************************************************************************/
   public FileGatewayOutboxFile withDownloadCount(Integer downloadCount)
   {
      this.downloadCount = downloadCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for removedAt.
    *******************************************************************************/
   public Instant getRemovedAt()
   {
      return (removedAt);
   }



   /*******************************************************************************
    ** Fluent setter for removedAt.
    *******************************************************************************/
   public FileGatewayOutboxFile withRemovedAt(Instant removedAt)
   {
      this.removedAt = removedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for expiresAt.
    *******************************************************************************/
   public Instant getExpiresAt()
   {
      return (expiresAt);
   }



   /*******************************************************************************
    ** Fluent setter for expiresAt.
    *******************************************************************************/
   public FileGatewayOutboxFile withExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createdByConsumer.
    *******************************************************************************/
   public String getCreatedByConsumer()
   {
      return (createdByConsumer);
   }



   /*******************************************************************************
    ** Fluent setter for createdByConsumer.
    *******************************************************************************/
   public FileGatewayOutboxFile withCreatedByConsumer(String createdByConsumer)
   {
      this.createdByConsumer = createdByConsumer;
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
   public void setAliasId(Integer aliasId)
   {
      this.aliasId = aliasId;
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
   public void setSizeBytes(Long sizeBytes)
   {
      this.sizeBytes = sizeBytes;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setSha256(String sha256)
   {
      this.sha256 = sha256;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setStorageKey(String storageKey)
   {
      this.storageKey = storageKey;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFirstDownloadedAt(Instant firstDownloadedAt)
   {
      this.firstDownloadedAt = firstDownloadedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setDownloadCount(Integer downloadCount)
   {
      this.downloadCount = downloadCount;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setRemovedAt(Instant removedAt)
   {
      this.removedAt = removedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setCreatedByConsumer(String createdByConsumer)
   {
      this.createdByConsumer = createdByConsumer;
   }



   /*******************************************************************************
    ** Getter for onDownloadedPending.
    *******************************************************************************/
   public Boolean getOnDownloadedPending()
   {
      return (onDownloadedPending);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOnDownloadedPending(Boolean onDownloadedPending)
   {
      this.onDownloadedPending = onDownloadedPending;
   }



   /*******************************************************************************
    ** Fluent setter for onDownloadedPending.
    *******************************************************************************/
   public FileGatewayOutboxFile withOnDownloadedPending(Boolean onDownloadedPending)
   {
      this.onDownloadedPending = onDownloadedPending;
      return (this);
   }
}
