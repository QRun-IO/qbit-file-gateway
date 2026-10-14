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
 ** Record entity for connections.
 *******************************************************************************/
public class FileGatewayConnectionLog extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_connection_log";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private Integer accountId;

   @QField
   private String username;

   @QField
   private String remoteIp;

   @QField
   private String clientVersion;

   @QField
   private String keyFingerprint;

   @QField
   private String outcome;

   @QField
   private String sessionId;

   @QField
   private String podName;

   @QField
   private Instant connectedAt;

   @QField
   private Instant lastSeenAt;

   @QField
   private Instant disconnectedAt;

   @QField
   private String disconnectReason;

   @QField
   private Long bytesIn;

   @QField
   private Long bytesOut;

   @QField
   private Long operationCount;

   @QField
   private String clientChannel;



   /*******************************************************************************
    ** Create an empty entity.
    *******************************************************************************/
   public FileGatewayConnectionLog()
   {
   }



   /*******************************************************************************
    ** Populate from a database record.
    *******************************************************************************/
   public FileGatewayConnectionLog(QRecord record) throws QException
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
   public FileGatewayConnectionLog withId(Integer id)
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
   public FileGatewayConnectionLog withCreateDate(Instant createDate)
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
   public FileGatewayConnectionLog withModifyDate(Instant modifyDate)
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
   public FileGatewayConnectionLog withAccountId(Integer accountId)
   {
      this.accountId = accountId;
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
   public FileGatewayConnectionLog withUsername(String username)
   {
      this.username = username;
      return (this);
   }



   /*******************************************************************************
    ** Getter for remoteIp.
    *******************************************************************************/
   public String getRemoteIp()
   {
      return (remoteIp);
   }



   /*******************************************************************************
    ** Fluent setter for remoteIp.
    *******************************************************************************/
   public FileGatewayConnectionLog withRemoteIp(String remoteIp)
   {
      this.remoteIp = remoteIp;
      return (this);
   }



   /*******************************************************************************
    ** Getter for clientVersion.
    *******************************************************************************/
   public String getClientVersion()
   {
      return (clientVersion);
   }



   /*******************************************************************************
    ** Fluent setter for clientVersion.
    *******************************************************************************/
   public FileGatewayConnectionLog withClientVersion(String clientVersion)
   {
      this.clientVersion = clientVersion;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyFingerprint.
    *******************************************************************************/
   public String getKeyFingerprint()
   {
      return (keyFingerprint);
   }



   /*******************************************************************************
    ** Fluent setter for keyFingerprint.
    *******************************************************************************/
   public FileGatewayConnectionLog withKeyFingerprint(String keyFingerprint)
   {
      this.keyFingerprint = keyFingerprint;
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
   public FileGatewayConnectionLog withOutcome(String outcome)
   {
      this.outcome = outcome;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sessionId.
    *******************************************************************************/
   public String getSessionId()
   {
      return (sessionId);
   }



   /*******************************************************************************
    ** Fluent setter for sessionId.
    *******************************************************************************/
   public FileGatewayConnectionLog withSessionId(String sessionId)
   {
      this.sessionId = sessionId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for podName.
    *******************************************************************************/
   public String getPodName()
   {
      return (podName);
   }



   /*******************************************************************************
    ** Fluent setter for podName.
    *******************************************************************************/
   public FileGatewayConnectionLog withPodName(String podName)
   {
      this.podName = podName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for connectedAt.
    *******************************************************************************/
   public Instant getConnectedAt()
   {
      return (connectedAt);
   }



   /*******************************************************************************
    ** Fluent setter for connectedAt.
    *******************************************************************************/
   public FileGatewayConnectionLog withConnectedAt(Instant connectedAt)
   {
      this.connectedAt = connectedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastSeenAt.
    *******************************************************************************/
   public Instant getLastSeenAt()
   {
      return (lastSeenAt);
   }



   /*******************************************************************************
    ** Fluent setter for lastSeenAt.
    *******************************************************************************/
   public FileGatewayConnectionLog withLastSeenAt(Instant lastSeenAt)
   {
      this.lastSeenAt = lastSeenAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for disconnectedAt.
    *******************************************************************************/
   public Instant getDisconnectedAt()
   {
      return (disconnectedAt);
   }



   /*******************************************************************************
    ** Fluent setter for disconnectedAt.
    *******************************************************************************/
   public FileGatewayConnectionLog withDisconnectedAt(Instant disconnectedAt)
   {
      this.disconnectedAt = disconnectedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for disconnectReason.
    *******************************************************************************/
   public String getDisconnectReason()
   {
      return (disconnectReason);
   }



   /*******************************************************************************
    ** Fluent setter for disconnectReason.
    *******************************************************************************/
   public FileGatewayConnectionLog withDisconnectReason(String disconnectReason)
   {
      this.disconnectReason = disconnectReason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for bytesIn.
    *******************************************************************************/
   public Long getBytesIn()
   {
      return (bytesIn);
   }



   /*******************************************************************************
    ** Fluent setter for bytesIn.
    *******************************************************************************/
   public FileGatewayConnectionLog withBytesIn(Long bytesIn)
   {
      this.bytesIn = bytesIn;
      return (this);
   }



   /*******************************************************************************
    ** Getter for bytesOut.
    *******************************************************************************/
   public Long getBytesOut()
   {
      return (bytesOut);
   }



   /*******************************************************************************
    ** Fluent setter for bytesOut.
    *******************************************************************************/
   public FileGatewayConnectionLog withBytesOut(Long bytesOut)
   {
      this.bytesOut = bytesOut;
      return (this);
   }



   /*******************************************************************************
    ** Getter for operationCount.
    *******************************************************************************/
   public Long getOperationCount()
   {
      return (operationCount);
   }



   /*******************************************************************************
    ** Fluent setter for operationCount.
    *******************************************************************************/
   public FileGatewayConnectionLog withOperationCount(Long operationCount)
   {
      this.operationCount = operationCount;
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
   public FileGatewayConnectionLog withClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
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
   public void setUsername(String username)
   {
      this.username = username;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setRemoteIp(String remoteIp)
   {
      this.remoteIp = remoteIp;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setClientVersion(String clientVersion)
   {
      this.clientVersion = clientVersion;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setKeyFingerprint(String keyFingerprint)
   {
      this.keyFingerprint = keyFingerprint;
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
   public void setSessionId(String sessionId)
   {
      this.sessionId = sessionId;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setPodName(String podName)
   {
      this.podName = podName;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setConnectedAt(Instant connectedAt)
   {
      this.connectedAt = connectedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setLastSeenAt(Instant lastSeenAt)
   {
      this.lastSeenAt = lastSeenAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setDisconnectedAt(Instant disconnectedAt)
   {
      this.disconnectedAt = disconnectedAt;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setDisconnectReason(String disconnectReason)
   {
      this.disconnectReason = disconnectReason;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setBytesIn(Long bytesIn)
   {
      this.bytesIn = bytesIn;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setBytesOut(Long bytesOut)
   {
      this.bytesOut = bytesOut;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setOperationCount(Long operationCount)
   {
      this.operationCount = operationCount;
   }



   /*******************************************************************************
    ** Setter used by QRecordEntity population.
    *******************************************************************************/
   public void setClientChannel(String clientChannel)
   {
      this.clientChannel = clientChannel;
   }
}
