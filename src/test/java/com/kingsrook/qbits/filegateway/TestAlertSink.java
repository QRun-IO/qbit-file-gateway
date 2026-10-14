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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qqq.backend.core.alerts.AlertResolution;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertSinkInterface;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;

/*******************************************************************************
 ** Captures real core notification dispatch.
 *******************************************************************************/
public class TestAlertSink implements AlertSinkInterface
{
   public static final List<NotificationSignal> notifications = new CopyOnWriteArrayList<>();
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void raise(AlertSignal signal)
   {
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void resolve(AlertResolution resolution)
   {
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void heartbeat(String key, Instant instant)
   {
   }
   /*******************************************************************************
    ** Test fixture operation.
    *******************************************************************************/
   public void notify(NotificationSignal signal)
   {
      notifications.add(signal);
   }
}
