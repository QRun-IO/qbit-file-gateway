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
 ** Record entity for transfers.
 *******************************************************************************/
public class FileGatewayTransferLog extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_transfer_log";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private Integer accountId;

   @QField
   private Integer connectionLogId;

   @QField
   private String operation;

   @QField
   private String path;

   @QField
   private String objectKind;

   @QField
   private String objectId;

   @QField
   private String format;

   @QField
   private Long bytes;

   @QField
   private String sha256;

   @QField
   private Long durationMs;

   @QField
   private String outcome;

   @QField
   private String message;

   @QField
   private String clientChannel;

   @QField
   private Integer uploadJobId;



   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayTransferLog()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayTransferLog(QRecord record) throws QException
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
   public FileGatewayTransferLog withId(Integer id)
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
   public FileGatewayTransferLog withCreateDate(Instant createDate)
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
   public FileGatewayTransferLog withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
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
   public FileGatewayTransferLog withAccountId(Integer accountId)
   {
      this.accountId = accountId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for connectionLogId.
    *******************************************************************************/
   public Integer getConnectionLogId()
   {
      return (connectionLogId);
   }



   /*******************************************************************************
    ** Fluent setter for connectionLogId.
    *******************************************************************************/
   public FileGatewayTransferLog withConnectionLogId(Integer connectionLogId)
   {
      this.connectionLogId = connectionLogId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for operation.
    *******************************************************************************/
   public String getOperation()
   {
      return (operation);
   }



   /*******************************************************************************
    ** Fluent setter for operation.
    *******************************************************************************/
   public FileGatewayTransferLog withOperation(String operation)
   {
      this.operation = operation;
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
   public FileGatewayTransferLog withPath(String path)
   {
      this.path = path;
      return (this);
   }



   /*******************************************************************************
    ** Getter for objectKind.
    *******************************************************************************/
   public String getObjectKind()
   {
      return (objectKind);
   }



   /*******************************************************************************
    ** Fluent setter for objectKind.
    *******************************************************************************/
   public FileGatewayTransferLog withObjectKind(String objectKind)
   {
      this.objectKind = objectKind;
      return (this);
   }



   /*******************************************************************************
    ** Getter for objectId.
    *******************************************************************************/
   public String getObjectId()
   {
      return (objectId);
   }



   /*******************************************************************************
    ** Fluent setter for objectId.
    *******************************************************************************/
   public FileGatewayTransferLog withObjectId(String objectId)
   {
      this.objectId = objectId;
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
   public FileGatewayTransferLog withFormat(String format)
   {
      this.format = format;
      return (this);
   }



   /*******************************************************************************
    ** Getter for bytes.
    *******************************************************************************/
   public Long getBytes()
   {
      return (bytes);
   }



   /*******************************************************************************
    ** Fluent setter for bytes.
    *******************************************************************************/
   public FileGatewayTransferLog withBytes(Long bytes)
   {
      this.bytes = bytes;
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
   public FileGatewayTransferLog withSha256(String sha256)
   {
      this.sha256 = sha256;
      return (this);
   }



   /*******************************************************************************
    ** Getter for durationMs.
    *******************************************************************************/
   public Long getDurationMs()
   {
      return (durationMs);
   }



   /*******************************************************************************
    ** Fluent setter for durationMs.
    *******************************************************************************/
   public FileGatewayTransferLog withDurationMs(Long durationMs)
   {
      this.durationMs = durationMs;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outcome.
    *******************************************************************************/
   public String getOutcome()
   {
      return (outcome);
   }



   /*******************************************************************************
    ** Fluent setter for outcome.
    *******************************************************************************/
   public FileGatewayTransferLog withOutcome(String outcome)
   {
      this.outcome = outcome;
      return (this);
   }



   /*******************************************************************************
    ** Getter for message.
    *******************************************************************************/
   public String getMessage()
   {
      return (message);
   }



   /*******************************************************************************
    ** Fluent setter for message.
    *******************************************************************************/
   public FileGatewayTransferLog withMessage(String message)
   {
      this.message = message;
      return (this);
   }



   /*******************************************************************************
    ** Getter for clientChannel.
    *******************************************************************************/
   public String getClientChannel()
   {
      return (clientChannel);
   }



   /*******************************************************************************
    ** Fluent setter for clientChannel.
    *******************************************************************************/
   public FileGatewayTransferLog withClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for uploadJobId.
    *******************************************************************************/
   public Integer getUploadJobId()
   {
      return (uploadJobId);
   }



   /*******************************************************************************
    ** Fluent setter for uploadJobId.
    *******************************************************************************/
   public FileGatewayTransferLog withUploadJobId(Integer uploadJobId)
   {
      this.uploadJobId = uploadJobId;
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
   public void setAccountId(Integer accountId)
   {
      this.accountId = accountId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setConnectionLogId(Integer connectionLogId)
   {
      this.connectionLogId = connectionLogId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOperation(String operation)
   {
      this.operation = operation;
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
   public void setObjectKind(String objectKind)
   {
      this.objectKind = objectKind;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setObjectId(String objectId)
   {
      this.objectId = objectId;
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
   public void setBytes(Long bytes)
   {
      this.bytes = bytes;
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
   public void setDurationMs(Long durationMs)
   {
      this.durationMs = durationMs;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOutcome(String outcome)
   {
      this.outcome = outcome;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMessage(String message)
   {
      this.message = message;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setUploadJobId(Integer uploadJobId)
   {
      this.uploadJobId = uploadJobId;
   }
}
