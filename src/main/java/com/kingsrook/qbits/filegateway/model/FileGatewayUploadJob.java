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
 ** Record entity for upload jobs.
 *******************************************************************************/
public class FileGatewayUploadJob extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_upload_job";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private Integer accountId;

   @QField
   private Integer aliasId;

   @QField
   private Integer connectionLogId;

   @QField
   private String remoteAddress;

   @QField
   private String tableName;

   @QField
   private Integer savedBulkLoadProfileId;

   @QField
   private String consumerName;

   @QField
   private String consumerReference;

   @QField
   private String path;

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
   private Integer duplicateOfJobId;

   @QField
   private String processUuid;

   @QField
   private Integer insertedCount;

   @QField
   private Integer errorCount;

   @QField
   private String resultSummary;

   @QField
   private String resultsStorageKey;

   @QField
   private Long resultsSizeBytes;

   @QField
   private String errorMessage;

   @QField
   private Integer attempts;

   @QField
   private Instant nextAttemptAt;

   @QField
   private String claimedBy;

   @QField
   private Instant claimedAt;

   @QField
   private Instant queuedAt;

   @QField
   private Instant startedAt;

   @QField
   private Instant finishedAt;

   @QField
   private Instant acknowledgedAt;



   @QField
   private String plaintextStorageKey;

   @QField
   private String plaintextSha256;

   @QField
   private Long plaintextSizeBytes;


   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayUploadJob()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayUploadJob(QRecord record) throws QException
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
   public FileGatewayUploadJob withId(Integer id)
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
   public FileGatewayUploadJob withCreateDate(Instant createDate)
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
   public FileGatewayUploadJob withModifyDate(Instant modifyDate)
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
   public FileGatewayUploadJob withAccountId(Integer accountId)
   {
      this.accountId = accountId;
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
   public FileGatewayUploadJob withAliasId(Integer aliasId)
   {
      this.aliasId = aliasId;
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
   public FileGatewayUploadJob withConnectionLogId(Integer connectionLogId)
   {
      this.connectionLogId = connectionLogId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for remoteAddress.
    *******************************************************************************/
   public String getRemoteAddress()
   {
      return (remoteAddress);
   }



   /*******************************************************************************
    ** Fluent setter for remoteAddress.
    *******************************************************************************/
   public FileGatewayUploadJob withRemoteAddress(String remoteAddress)
   {
      this.remoteAddress = remoteAddress;
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
   public FileGatewayUploadJob withTableName(String tableName)
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
   public FileGatewayUploadJob withSavedBulkLoadProfileId(Integer savedBulkLoadProfileId)
   {
      this.savedBulkLoadProfileId = savedBulkLoadProfileId;
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
   public FileGatewayUploadJob withConsumerName(String consumerName)
   {
      this.consumerName = consumerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for consumerReference.
    *******************************************************************************/
   public String getConsumerReference()
   {
      return (consumerReference);
   }



   /*******************************************************************************
    ** Fluent setter for consumerReference.
    *******************************************************************************/
   public FileGatewayUploadJob withConsumerReference(String consumerReference)
   {
      this.consumerReference = consumerReference;
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
   public FileGatewayUploadJob withPath(String path)
   {
      this.path = path;
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
   public FileGatewayUploadJob withFileName(String fileName)
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
   public FileGatewayUploadJob withSizeBytes(Long sizeBytes)
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
   public FileGatewayUploadJob withSha256(String sha256)
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
   public FileGatewayUploadJob withStorageKey(String storageKey)
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
   public FileGatewayUploadJob withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for duplicateOfJobId.
    *******************************************************************************/
   public Integer getDuplicateOfJobId()
   {
      return (duplicateOfJobId);
   }



   /*******************************************************************************
    ** Fluent setter for duplicateOfJobId.
    *******************************************************************************/
   public FileGatewayUploadJob withDuplicateOfJobId(Integer duplicateOfJobId)
   {
      this.duplicateOfJobId = duplicateOfJobId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for processUuid.
    *******************************************************************************/
   public String getProcessUuid()
   {
      return (processUuid);
   }



   /*******************************************************************************
    ** Fluent setter for processUuid.
    *******************************************************************************/
   public FileGatewayUploadJob withProcessUuid(String processUuid)
   {
      this.processUuid = processUuid;
      return (this);
   }



   /*******************************************************************************
    ** Getter for insertedCount.
    *******************************************************************************/
   public Integer getInsertedCount()
   {
      return (insertedCount);
   }



   /*******************************************************************************
    ** Fluent setter for insertedCount.
    *******************************************************************************/
   public FileGatewayUploadJob withInsertedCount(Integer insertedCount)
   {
      this.insertedCount = insertedCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorCount.
    *******************************************************************************/
   public Integer getErrorCount()
   {
      return (errorCount);
   }



   /*******************************************************************************
    ** Fluent setter for errorCount.
    *******************************************************************************/
   public FileGatewayUploadJob withErrorCount(Integer errorCount)
   {
      this.errorCount = errorCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resultSummary.
    *******************************************************************************/
   public String getResultSummary()
   {
      return (resultSummary);
   }



   /*******************************************************************************
    ** Fluent setter for resultSummary.
    *******************************************************************************/
   public FileGatewayUploadJob withResultSummary(String resultSummary)
   {
      this.resultSummary = resultSummary;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resultsStorageKey.
    *******************************************************************************/
   public String getResultsStorageKey()
   {
      return (resultsStorageKey);
   }



   /*******************************************************************************
    ** Fluent setter for resultsStorageKey.
    *******************************************************************************/
   public FileGatewayUploadJob withResultsStorageKey(String resultsStorageKey)
   {
      this.resultsStorageKey = resultsStorageKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorMessage.
    *******************************************************************************/
   public String getErrorMessage()
   {
      return (errorMessage);
   }



   /*******************************************************************************
    ** Fluent setter for errorMessage.
    *******************************************************************************/
   public FileGatewayUploadJob withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
      return (this);
   }



   /*******************************************************************************
    ** Getter for attempts.
    *******************************************************************************/
   public Integer getAttempts()
   {
      return (attempts);
   }



   /*******************************************************************************
    ** Fluent setter for attempts.
    *******************************************************************************/
   public FileGatewayUploadJob withAttempts(Integer attempts)
   {
      this.attempts = attempts;
      return (this);
   }



   /*******************************************************************************
    ** Getter for nextAttemptAt.
    *******************************************************************************/
   public Instant getNextAttemptAt()
   {
      return (nextAttemptAt);
   }



   /*******************************************************************************
    ** Fluent setter for nextAttemptAt.
    *******************************************************************************/
   public FileGatewayUploadJob withNextAttemptAt(Instant nextAttemptAt)
   {
      this.nextAttemptAt = nextAttemptAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for claimedBy.
    *******************************************************************************/
   public String getClaimedBy()
   {
      return (claimedBy);
   }



   /*******************************************************************************
    ** Fluent setter for claimedBy.
    *******************************************************************************/
   public FileGatewayUploadJob withClaimedBy(String claimedBy)
   {
      this.claimedBy = claimedBy;
      return (this);
   }



   /*******************************************************************************
    ** Getter for claimedAt.
    *******************************************************************************/
   public Instant getClaimedAt()
   {
      return (claimedAt);
   }



   /*******************************************************************************
    ** Fluent setter for claimedAt.
    *******************************************************************************/
   public FileGatewayUploadJob withClaimedAt(Instant claimedAt)
   {
      this.claimedAt = claimedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for queuedAt.
    *******************************************************************************/
   public Instant getQueuedAt()
   {
      return (queuedAt);
   }



   /*******************************************************************************
    ** Fluent setter for queuedAt.
    *******************************************************************************/
   public FileGatewayUploadJob withQueuedAt(Instant queuedAt)
   {
      this.queuedAt = queuedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startedAt.
    *******************************************************************************/
   public Instant getStartedAt()
   {
      return (startedAt);
   }



   /*******************************************************************************
    ** Fluent setter for startedAt.
    *******************************************************************************/
   public FileGatewayUploadJob withStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for finishedAt.
    *******************************************************************************/
   public Instant getFinishedAt()
   {
      return (finishedAt);
   }



   /*******************************************************************************
    ** Fluent setter for finishedAt.
    *******************************************************************************/
   public FileGatewayUploadJob withFinishedAt(Instant finishedAt)
   {
      this.finishedAt = finishedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for acknowledgedAt.
    *******************************************************************************/
   public Instant getAcknowledgedAt()
   {
      return (acknowledgedAt);
   }



   /*******************************************************************************
    ** Fluent setter for acknowledgedAt.
    *******************************************************************************/
   public FileGatewayUploadJob withAcknowledgedAt(Instant acknowledgedAt)
   {
      this.acknowledgedAt = acknowledgedAt;
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
   public void setAliasId(Integer aliasId)
   {
      this.aliasId = aliasId;
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
   public void setRemoteAddress(String remoteAddress)
   {
      this.remoteAddress = remoteAddress;
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
   public void setConsumerName(String consumerName)
   {
      this.consumerName = consumerName;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setConsumerReference(String consumerReference)
   {
      this.consumerReference = consumerReference;
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
   public void setDuplicateOfJobId(Integer duplicateOfJobId)
   {
      this.duplicateOfJobId = duplicateOfJobId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setProcessUuid(String processUuid)
   {
      this.processUuid = processUuid;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setInsertedCount(Integer insertedCount)
   {
      this.insertedCount = insertedCount;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setErrorCount(Integer errorCount)
   {
      this.errorCount = errorCount;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setResultSummary(String resultSummary)
   {
      this.resultSummary = resultSummary;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setResultsStorageKey(String resultsStorageKey)
   {
      this.resultsStorageKey = resultsStorageKey;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setAttempts(Integer attempts)
   {
      this.attempts = attempts;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setNextAttemptAt(Instant nextAttemptAt)
   {
      this.nextAttemptAt = nextAttemptAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setClaimedBy(String claimedBy)
   {
      this.claimedBy = claimedBy;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setClaimedAt(Instant claimedAt)
   {
      this.claimedAt = claimedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setQueuedAt(Instant queuedAt)
   {
      this.queuedAt = queuedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFinishedAt(Instant finishedAt)
   {
      this.finishedAt = finishedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setAcknowledgedAt(Instant acknowledgedAt)
   {
      this.acknowledgedAt = acknowledgedAt;
   }

   /*******************************************************************************
    ** Stored result artifact length.
    *******************************************************************************/
   public Long getResultsSizeBytes()
   {
      return (resultsSizeBytes);
   }

   /*******************************************************************************
    ** Populate result artifact length.
    *******************************************************************************/
   public void setResultsSizeBytes(Long value)
   {
      resultsSizeBytes = value;
   }

   /*******************************************************************************
    ** Fluent result artifact length.
    *******************************************************************************/
   public FileGatewayUploadJob withResultsSizeBytes(Long value)
   {
      resultsSizeBytes = value;
      return (this);
   }




   /*******************************************************************************
    ** Getter for plaintextStorageKey.
    *******************************************************************************/
   public String getPlaintextStorageKey()
   {
      return (plaintextStorageKey);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPlaintextStorageKey(String plaintextStorageKey)
   {
      this.plaintextStorageKey = plaintextStorageKey;
   }



   /*******************************************************************************
    ** Fluent setter for plaintextStorageKey.
    *******************************************************************************/
   public FileGatewayUploadJob withPlaintextStorageKey(String plaintextStorageKey)
   {
      this.plaintextStorageKey = plaintextStorageKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for plaintextSha256.
    *******************************************************************************/
   public String getPlaintextSha256()
   {
      return (plaintextSha256);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPlaintextSha256(String plaintextSha256)
   {
      this.plaintextSha256 = plaintextSha256;
   }



   /*******************************************************************************
    ** Fluent setter for plaintextSha256.
    *******************************************************************************/
   public FileGatewayUploadJob withPlaintextSha256(String plaintextSha256)
   {
      this.plaintextSha256 = plaintextSha256;
      return (this);
   }



   /*******************************************************************************
    ** Getter for plaintextSizeBytes.
    *******************************************************************************/
   public Long getPlaintextSizeBytes()
   {
      return (plaintextSizeBytes);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPlaintextSizeBytes(Long plaintextSizeBytes)
   {
      this.plaintextSizeBytes = plaintextSizeBytes;
   }



   /*******************************************************************************
    ** Fluent setter for plaintextSizeBytes.
    *******************************************************************************/
   public FileGatewayUploadJob withPlaintextSizeBytes(Long plaintextSizeBytes)
   {
      this.plaintextSizeBytes = plaintextSizeBytes;
      return (this);
   }
}
