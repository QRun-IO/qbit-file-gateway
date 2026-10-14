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
 ** Record entity for accounts.
 *******************************************************************************/
public class FileGatewayAccount extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_account";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private String username;

   @QField
   private String label;

   @QField
   private String accountType;

   @QField
   private String userId;

   @QField
   private String ownerUserId;

   @QField
   private String status;

   @QField
   private String rootMode;

   @QField
   private String allowedCidrs;

   @QField
   private Instant expiresAt;

   @QField
   private String formats;

   @QField
   private Integer maxConcurrentSessions;

   @QField
   private Integer maxOperationsPerMinute;

   @QField
   private Integer maxFileSizeMb;

   @QField
   private Integer maxUploadMbPerDay;

   @QField
   private Integer maxDownloadMbPerDay;

   @QField
   private Boolean dedupeUploads;

   @QField
   private Boolean notifyOnSuccess;

   @QField
   private String pgpPublicKey;

   @QField
   private Boolean pgpDecryptUploads;

   @QField
   private Boolean pgpEncryptDownloads;

   @QField
   private Instant operationWindowAt;

   @QField
   private Integer operationWindowCount;

   @QField
   private Integer failedLoginCount;

   @QField
   private Instant failedLoginWindowAt;

   @QField
   private Instant lockedUntil;

   @QField
   private Instant lastLoginAt;

   @QField
   private String lastLoginIp;



   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayAccount()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayAccount(QRecord record) throws QException
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
   public FileGatewayAccount withId(Integer id)
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
   public FileGatewayAccount withCreateDate(Instant createDate)
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
   public FileGatewayAccount withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for username.
    *******************************************************************************/
   public String getUsername()
   {
      return (username);
   }



   /*******************************************************************************
    ** Fluent setter for username.
    *******************************************************************************/
   public FileGatewayAccount withUsername(String username)
   {
      this.username = username;
      return (this);
   }



   /*******************************************************************************
    ** Getter for label.
    *******************************************************************************/
   public String getLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** Fluent setter for label.
    *******************************************************************************/
   public FileGatewayAccount withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for accountType.
    *******************************************************************************/
   public String getAccountType()
   {
      return (accountType);
   }



   /*******************************************************************************
    ** Fluent setter for accountType.
    *******************************************************************************/
   public FileGatewayAccount withAccountType(String accountType)
   {
      this.accountType = accountType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userId.
    *******************************************************************************/
   public String getUserId()
   {
      return (userId);
   }



   /*******************************************************************************
    ** Fluent setter for userId.
    *******************************************************************************/
   public FileGatewayAccount withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for ownerUserId.
    *******************************************************************************/
   public String getOwnerUserId()
   {
      return (ownerUserId);
   }



   /*******************************************************************************
    ** Fluent setter for ownerUserId.
    *******************************************************************************/
   public FileGatewayAccount withOwnerUserId(String ownerUserId)
   {
      this.ownerUserId = ownerUserId;
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
   public FileGatewayAccount withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rootMode.
    *******************************************************************************/
   public String getRootMode()
   {
      return (rootMode);
   }



   /*******************************************************************************
    ** Fluent setter for rootMode.
    *******************************************************************************/
   public FileGatewayAccount withRootMode(String rootMode)
   {
      this.rootMode = rootMode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowedCidrs.
    *******************************************************************************/
   public String getAllowedCidrs()
   {
      return (allowedCidrs);
   }



   /*******************************************************************************
    ** Fluent setter for allowedCidrs.
    *******************************************************************************/
   public FileGatewayAccount withAllowedCidrs(String allowedCidrs)
   {
      this.allowedCidrs = allowedCidrs;
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
   public FileGatewayAccount withExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for formats.
    *******************************************************************************/
   public String getFormats()
   {
      return (formats);
   }



   /*******************************************************************************
    ** Fluent setter for formats.
    *******************************************************************************/
   public FileGatewayAccount withFormats(String formats)
   {
      this.formats = formats;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxConcurrentSessions.
    *******************************************************************************/
   public Integer getMaxConcurrentSessions()
   {
      return (maxConcurrentSessions);
   }



   /*******************************************************************************
    ** Fluent setter for maxConcurrentSessions.
    *******************************************************************************/
   public FileGatewayAccount withMaxConcurrentSessions(Integer maxConcurrentSessions)
   {
      this.maxConcurrentSessions = maxConcurrentSessions;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxOperationsPerMinute.
    *******************************************************************************/
   public Integer getMaxOperationsPerMinute()
   {
      return (maxOperationsPerMinute);
   }



   /*******************************************************************************
    ** Fluent setter for maxOperationsPerMinute.
    *******************************************************************************/
   public FileGatewayAccount withMaxOperationsPerMinute(Integer maxOperationsPerMinute)
   {
      this.maxOperationsPerMinute = maxOperationsPerMinute;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxFileSizeMb.
    *******************************************************************************/
   public Integer getMaxFileSizeMb()
   {
      return (maxFileSizeMb);
   }



   /*******************************************************************************
    ** Fluent setter for maxFileSizeMb.
    *******************************************************************************/
   public FileGatewayAccount withMaxFileSizeMb(Integer maxFileSizeMb)
   {
      this.maxFileSizeMb = maxFileSizeMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxUploadMbPerDay.
    *******************************************************************************/
   public Integer getMaxUploadMbPerDay()
   {
      return (maxUploadMbPerDay);
   }



   /*******************************************************************************
    ** Fluent setter for maxUploadMbPerDay.
    *******************************************************************************/
   public FileGatewayAccount withMaxUploadMbPerDay(Integer maxUploadMbPerDay)
   {
      this.maxUploadMbPerDay = maxUploadMbPerDay;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxDownloadMbPerDay.
    *******************************************************************************/
   public Integer getMaxDownloadMbPerDay()
   {
      return (maxDownloadMbPerDay);
   }



   /*******************************************************************************
    ** Fluent setter for maxDownloadMbPerDay.
    *******************************************************************************/
   public FileGatewayAccount withMaxDownloadMbPerDay(Integer maxDownloadMbPerDay)
   {
      this.maxDownloadMbPerDay = maxDownloadMbPerDay;
      return (this);
   }



   /*******************************************************************************
    ** Getter for dedupeUploads.
    *******************************************************************************/
   public Boolean getDedupeUploads()
   {
      return (dedupeUploads);
   }



   /*******************************************************************************
    ** Fluent setter for dedupeUploads.
    *******************************************************************************/
   public FileGatewayAccount withDedupeUploads(Boolean dedupeUploads)
   {
      this.dedupeUploads = dedupeUploads;
      return (this);
   }



   /*******************************************************************************
    ** Getter for notifyOnSuccess.
    *******************************************************************************/
   public Boolean getNotifyOnSuccess()
   {
      return (notifyOnSuccess);
   }



   /*******************************************************************************
    ** Fluent setter for notifyOnSuccess.
    *******************************************************************************/
   public FileGatewayAccount withNotifyOnSuccess(Boolean notifyOnSuccess)
   {
      this.notifyOnSuccess = notifyOnSuccess;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pgpPublicKey.
    *******************************************************************************/
   public String getPgpPublicKey()
   {
      return (pgpPublicKey);
   }



   /*******************************************************************************
    ** Fluent setter for pgpPublicKey.
    *******************************************************************************/
   public FileGatewayAccount withPgpPublicKey(String pgpPublicKey)
   {
      this.pgpPublicKey = pgpPublicKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pgpDecryptUploads.
    *******************************************************************************/
   public Boolean getPgpDecryptUploads()
   {
      return (pgpDecryptUploads);
   }



   /*******************************************************************************
    ** Fluent setter for pgpDecryptUploads.
    *******************************************************************************/
   public FileGatewayAccount withPgpDecryptUploads(Boolean pgpDecryptUploads)
   {
      this.pgpDecryptUploads = pgpDecryptUploads;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pgpEncryptDownloads.
    *******************************************************************************/
   public Boolean getPgpEncryptDownloads()
   {
      return (pgpEncryptDownloads);
   }



   /*******************************************************************************
    ** Fluent setter for pgpEncryptDownloads.
    *******************************************************************************/
   public FileGatewayAccount withPgpEncryptDownloads(Boolean pgpEncryptDownloads)
   {
      this.pgpEncryptDownloads = pgpEncryptDownloads;
      return (this);
   }



   /*******************************************************************************
    ** Getter for failedLoginCount.
    *******************************************************************************/
   public Integer getFailedLoginCount()
   {
      return (failedLoginCount);
   }



   /*******************************************************************************
    ** Fluent setter for failedLoginCount.
    *******************************************************************************/
   public FileGatewayAccount withFailedLoginCount(Integer failedLoginCount)
   {
      this.failedLoginCount = failedLoginCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for failedLoginWindowAt.
    *******************************************************************************/
   public Instant getFailedLoginWindowAt()
   {
      return (failedLoginWindowAt);
   }



   /*******************************************************************************
    ** Fluent setter for failedLoginWindowAt.
    *******************************************************************************/
   public FileGatewayAccount withFailedLoginWindowAt(Instant failedLoginWindowAt)
   {
      this.failedLoginWindowAt = failedLoginWindowAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lockedUntil.
    *******************************************************************************/
   public Instant getLockedUntil()
   {
      return (lockedUntil);
   }



   /*******************************************************************************
    ** Fluent setter for lockedUntil.
    *******************************************************************************/
   public FileGatewayAccount withLockedUntil(Instant lockedUntil)
   {
      this.lockedUntil = lockedUntil;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastLoginAt.
    *******************************************************************************/
   public Instant getLastLoginAt()
   {
      return (lastLoginAt);
   }



   /*******************************************************************************
    ** Fluent setter for lastLoginAt.
    *******************************************************************************/
   public FileGatewayAccount withLastLoginAt(Instant lastLoginAt)
   {
      this.lastLoginAt = lastLoginAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastLoginIp.
    *******************************************************************************/
   public String getLastLoginIp()
   {
      return (lastLoginIp);
   }



   /*******************************************************************************
    ** Fluent setter for lastLoginIp.
    *******************************************************************************/
   public FileGatewayAccount withLastLoginIp(String lastLoginIp)
   {
      this.lastLoginIp = lastLoginIp;
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
   public void setUsername(String username)
   {
      this.username = username;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setAccountType(String accountType)
   {
      this.accountType = accountType;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setUserId(String userId)
   {
      this.userId = userId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOwnerUserId(String ownerUserId)
   {
      this.ownerUserId = ownerUserId;
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
   public void setRootMode(String rootMode)
   {
      this.rootMode = rootMode;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setAllowedCidrs(String allowedCidrs)
   {
      this.allowedCidrs = allowedCidrs;
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
   public void setFormats(String formats)
   {
      this.formats = formats;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMaxConcurrentSessions(Integer maxConcurrentSessions)
   {
      this.maxConcurrentSessions = maxConcurrentSessions;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMaxOperationsPerMinute(Integer maxOperationsPerMinute)
   {
      this.maxOperationsPerMinute = maxOperationsPerMinute;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMaxFileSizeMb(Integer maxFileSizeMb)
   {
      this.maxFileSizeMb = maxFileSizeMb;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMaxUploadMbPerDay(Integer maxUploadMbPerDay)
   {
      this.maxUploadMbPerDay = maxUploadMbPerDay;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setMaxDownloadMbPerDay(Integer maxDownloadMbPerDay)
   {
      this.maxDownloadMbPerDay = maxDownloadMbPerDay;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setDedupeUploads(Boolean dedupeUploads)
   {
      this.dedupeUploads = dedupeUploads;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setNotifyOnSuccess(Boolean notifyOnSuccess)
   {
      this.notifyOnSuccess = notifyOnSuccess;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPgpPublicKey(String pgpPublicKey)
   {
      this.pgpPublicKey = pgpPublicKey;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPgpDecryptUploads(Boolean pgpDecryptUploads)
   {
      this.pgpDecryptUploads = pgpDecryptUploads;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPgpEncryptDownloads(Boolean pgpEncryptDownloads)
   {
      this.pgpEncryptDownloads = pgpEncryptDownloads;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFailedLoginCount(Integer failedLoginCount)
   {
      this.failedLoginCount = failedLoginCount;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFailedLoginWindowAt(Instant failedLoginWindowAt)
   {
      this.failedLoginWindowAt = failedLoginWindowAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLockedUntil(Instant lockedUntil)
   {
      this.lockedUntil = lockedUntil;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLastLoginAt(Instant lastLoginAt)
   {
      this.lastLoginAt = lastLoginAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLastLoginIp(String lastLoginIp)
   {
      this.lastLoginIp = lastLoginIp;
   }



   /*******************************************************************************
    ** Getter for operationWindowAt.
    *******************************************************************************/
   public Instant getOperationWindowAt()
   {
      return (operationWindowAt);
   }



   /*******************************************************************************
    ** Fluent setter for operationWindowAt.
    *******************************************************************************/
   public FileGatewayAccount withOperationWindowAt(Instant operationWindowAt)
   {
      this.operationWindowAt = operationWindowAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for operationWindowCount.
    *******************************************************************************/
   public Integer getOperationWindowCount()
   {
      return (operationWindowCount);
   }



   /*******************************************************************************
    ** Fluent setter for operationWindowCount.
    *******************************************************************************/
   public FileGatewayAccount withOperationWindowCount(Integer operationWindowCount)
   {
      this.operationWindowCount = operationWindowCount;
      return (this);
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOperationWindowAt(Instant operationWindowAt)
   {
      this.operationWindowAt = operationWindowAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOperationWindowCount(Integer operationWindowCount)
   {
      this.operationWindowCount = operationWindowCount;
   }
}
