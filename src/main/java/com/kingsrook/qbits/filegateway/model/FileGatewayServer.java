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
 ** Persisted gateway heartbeat, visible across deployments.
 *******************************************************************************/
public class FileGatewayServer extends QRecordEntity
{
   public static final String TABLE_NAME = "file_gateway_server";

   @QField
   private Integer id;

   @QField
   private Instant createDate;

   @QField
   private Instant modifyDate;

   @QField
   private String podName;

   @QField
   private String hostKeyFingerprint;

   @QField
   private Integer port;

   @QField
   private Instant startedAt;

   @QField
   private Instant lastSeenAt;

   /*******************************************************************************
    ** Empty record.
    *******************************************************************************/
   public FileGatewayServer()
   {
   }



   /*******************************************************************************
    ** Populate from gateway persistence.
    *******************************************************************************/
   public FileGatewayServer(QRecord record) throws QException
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Access id.
    *******************************************************************************/
   public Integer getId()
   {
      return (id);
   }



   /*******************************************************************************
    ** Access id.
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Access id.
    *******************************************************************************/
   public FileGatewayServer withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Access createDate.
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (createDate);
   }



   /*******************************************************************************
    ** Access createDate.
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Access createDate.
    *******************************************************************************/
   public FileGatewayServer withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Access modifyDate.
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (modifyDate);
   }



   /*******************************************************************************
    ** Access modifyDate.
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Access modifyDate.
    *******************************************************************************/
   public FileGatewayServer withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Access podName.
    *******************************************************************************/
   public String getPodName()
   {
      return (podName);
   }



   /*******************************************************************************
    ** Access podName.
    *******************************************************************************/
   public void setPodName(String podName)
   {
      this.podName = podName;
   }



   /*******************************************************************************
    ** Access podName.
    *******************************************************************************/
   public FileGatewayServer withPodName(String podName)
   {
      this.podName = podName;
      return (this);
   }



   /*******************************************************************************
    ** Access hostKeyFingerprint.
    *******************************************************************************/
   public String getHostKeyFingerprint()
   {
      return (hostKeyFingerprint);
   }



   /*******************************************************************************
    ** Access hostKeyFingerprint.
    *******************************************************************************/
   public void setHostKeyFingerprint(String hostKeyFingerprint)
   {
      this.hostKeyFingerprint = hostKeyFingerprint;
   }



   /*******************************************************************************
    ** Access hostKeyFingerprint.
    *******************************************************************************/
   public FileGatewayServer withHostKeyFingerprint(String hostKeyFingerprint)
   {
      this.hostKeyFingerprint = hostKeyFingerprint;
      return (this);
   }



   /*******************************************************************************
    ** Access port.
    *******************************************************************************/
   public Integer getPort()
   {
      return (port);
   }



   /*******************************************************************************
    ** Access port.
    *******************************************************************************/
   public void setPort(Integer port)
   {
      this.port = port;
   }



   /*******************************************************************************
    ** Access port.
    *******************************************************************************/
   public FileGatewayServer withPort(Integer port)
   {
      this.port = port;
      return (this);
   }



   /*******************************************************************************
    ** Access startedAt.
    *******************************************************************************/
   public Instant getStartedAt()
   {
      return (startedAt);
   }



   /*******************************************************************************
    ** Access startedAt.
    *******************************************************************************/
   public void setStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
   }



   /*******************************************************************************
    ** Access startedAt.
    *******************************************************************************/
   public FileGatewayServer withStartedAt(Instant startedAt)
   {
      this.startedAt = startedAt;
      return (this);
   }



   /*******************************************************************************
    ** Access lastSeenAt.
    *******************************************************************************/
   public Instant getLastSeenAt()
   {
      return (lastSeenAt);
   }



   /*******************************************************************************
    ** Access lastSeenAt.
    *******************************************************************************/
   public void setLastSeenAt(Instant lastSeenAt)
   {
      this.lastSeenAt = lastSeenAt;
   }



   /*******************************************************************************
    ** Access lastSeenAt.
    *******************************************************************************/
   public FileGatewayServer withLastSeenAt(Instant lastSeenAt)
   {
      this.lastSeenAt = lastSeenAt;
      return (this);
   }
}
