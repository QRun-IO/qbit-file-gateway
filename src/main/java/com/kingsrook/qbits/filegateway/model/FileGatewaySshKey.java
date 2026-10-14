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
 ** Record entity for ssh keys.
 *******************************************************************************/
public class FileGatewaySshKey extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_ssh_key";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private Integer accountId;

   @QField
   private String label;

   @QField
   private String publicKey;

   @QField
   private String fingerprint;

   @QField
   private String keyType;

   @QField
   private Integer keyBits;

   @QField
   private Instant expiresAt;

   @QField
   private Instant revokedAt;

   @QField
   private Instant lastUsedAt;

   @QField
   private String lastUsedIp;



   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewaySshKey()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewaySshKey(QRecord record) throws QException
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
   public FileGatewaySshKey withId(Integer id)
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
   public FileGatewaySshKey withCreateDate(Instant createDate)
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
   public FileGatewaySshKey withModifyDate(Instant modifyDate)
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
   public FileGatewaySshKey withAccountId(Integer accountId)
   {
      this.accountId = accountId;
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
   public FileGatewaySshKey withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for publicKey.
    *******************************************************************************/
   public String getPublicKey()
   {
      return (publicKey);
   }



   /*******************************************************************************
    ** Fluent setter for publicKey.
    *******************************************************************************/
   public FileGatewaySshKey withPublicKey(String publicKey)
   {
      this.publicKey = publicKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fingerprint.
    *******************************************************************************/
   public String getFingerprint()
   {
      return (fingerprint);
   }



   /*******************************************************************************
    ** Fluent setter for fingerprint.
    *******************************************************************************/
   public FileGatewaySshKey withFingerprint(String fingerprint)
   {
      this.fingerprint = fingerprint;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyType.
    *******************************************************************************/
   public String getKeyType()
   {
      return (keyType);
   }



   /*******************************************************************************
    ** Fluent setter for keyType.
    *******************************************************************************/
   public FileGatewaySshKey withKeyType(String keyType)
   {
      this.keyType = keyType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyBits.
    *******************************************************************************/
   public Integer getKeyBits()
   {
      return (keyBits);
   }



   /*******************************************************************************
    ** Fluent setter for keyBits.
    *******************************************************************************/
   public FileGatewaySshKey withKeyBits(Integer keyBits)
   {
      this.keyBits = keyBits;
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
   public FileGatewaySshKey withExpiresAt(Instant expiresAt)
   {
      this.expiresAt = expiresAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for revokedAt.
    *******************************************************************************/
   public Instant getRevokedAt()
   {
      return (revokedAt);
   }



   /*******************************************************************************
    ** Fluent setter for revokedAt.
    *******************************************************************************/
   public FileGatewaySshKey withRevokedAt(Instant revokedAt)
   {
      this.revokedAt = revokedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastUsedAt.
    *******************************************************************************/
   public Instant getLastUsedAt()
   {
      return (lastUsedAt);
   }



   /*******************************************************************************
    ** Fluent setter for lastUsedAt.
    *******************************************************************************/
   public FileGatewaySshKey withLastUsedAt(Instant lastUsedAt)
   {
      this.lastUsedAt = lastUsedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastUsedIp.
    *******************************************************************************/
   public String getLastUsedIp()
   {
      return (lastUsedIp);
   }



   /*******************************************************************************
    ** Fluent setter for lastUsedIp.
    *******************************************************************************/
   public FileGatewaySshKey withLastUsedIp(String lastUsedIp)
   {
      this.lastUsedIp = lastUsedIp;
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
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPublicKey(String publicKey)
   {
      this.publicKey = publicKey;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setFingerprint(String fingerprint)
   {
      this.fingerprint = fingerprint;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setKeyType(String keyType)
   {
      this.keyType = keyType;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setKeyBits(Integer keyBits)
   {
      this.keyBits = keyBits;
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
   public void setRevokedAt(Instant revokedAt)
   {
      this.revokedAt = revokedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLastUsedAt(Instant lastUsedAt)
   {
      this.lastUsedAt = lastUsedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLastUsedIp(String lastUsedIp)
   {
      this.lastUsedIp = lastUsedIp;
   }
}
