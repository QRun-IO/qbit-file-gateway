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

package com.kingsrook.qbits.filegateway.api;


import java.time.Duration;


/*******************************************************************************
 ** Consumer decision, including its reference or reason and retry delay.
 *******************************************************************************/
public final class FileAcceptance
{
   /*******************************************************************************
    ** Delivery decisions.
    *******************************************************************************/
   public enum Status
   {
      ACCEPTED, REJECTED, RETRY_LATER
   }

   private final Status status;
   private final String consumerReference;
   private final String reason;
   private final Duration retryAfter;



   /*******************************************************************************
    ** Create an immutable decision.
    *******************************************************************************/
   private FileAcceptance(Status status, String consumerReference, String reason, Duration retryAfter)
   {
      this.status = status;
      this.consumerReference = consumerReference;
      this.reason = reason;
      this.retryAfter = retryAfter;
   }



   /*******************************************************************************
    ** Accept the delivery.
    *******************************************************************************/
   public static FileAcceptance accepted(String consumerReference)
   {
      return (new FileAcceptance(Status.ACCEPTED, consumerReference, null, null));
   }



   /*******************************************************************************
    ** Reject the delivery.
    *******************************************************************************/
   public static FileAcceptance rejected(String reason)
   {
      return (new FileAcceptance(Status.REJECTED, null, reason, null));
   }



   /*******************************************************************************
    ** Request a retry.
    *******************************************************************************/
   public static FileAcceptance retryLater(String reason, Duration retryAfter)
   {
      return (new FileAcceptance(Status.RETRY_LATER, null, reason, retryAfter));
   }



   /*******************************************************************************
    ** Getter for status.
    *******************************************************************************/
   public Status getStatus()
   {
      return (status);
   }



   /*******************************************************************************
    ** Getter for consumerReference.
    *******************************************************************************/
   public String getConsumerReference()
   {
      return (consumerReference);
   }



   /*******************************************************************************
    ** Getter for reason.
    *******************************************************************************/
   public String getReason()
   {
      return (reason);
   }



   /*******************************************************************************
    ** Getter for retryAfter.
    *******************************************************************************/
   public Duration getRetryAfter()
   {
      return (retryAfter);
   }
}
