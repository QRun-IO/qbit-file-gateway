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

package com.kingsrook.qbits.filegateway.metadata;


import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PossibleValueEnum;


/*******************************************************************************
 ** Stable persisted values for the gateway schema.
 *******************************************************************************/
public final class FileGatewayEnums
{


   /*******************************************************************************
    ** Namespace for schema values.
    *******************************************************************************/
   private FileGatewayEnums()
   {
   }


   /*******************************************************************************
    ** AccountType values.
    *******************************************************************************/
   public enum AccountType implements PossibleValueEnum<String>
   {
      USER("USER"), SERVICE("SERVICE");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      AccountType(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** AccountStatus values.
    *******************************************************************************/
   public enum AccountStatus implements PossibleValueEnum<String>
   {
      ACTIVE("ACTIVE"), DISABLED("DISABLED"), LOCKED("LOCKED");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      AccountStatus(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** RootMode values.
    *******************************************************************************/
   public enum RootMode implements PossibleValueEnum<String>
   {
      FULL_TREE("FULL_TREE"), ENDPOINTS_ONLY("ENDPOINTS_ONLY");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      RootMode(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** AliasKind values.
    *******************************************************************************/
   public enum AliasKind implements PossibleValueEnum<String>
   {
      BULK_LOAD("BULK_LOAD"), INBOX("INBOX"), VIEW("VIEW"), REPORT("REPORT"), OUTBOX("OUTBOX");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      AliasKind(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** UploadStatus values.
    *******************************************************************************/
   public enum UploadStatus implements PossibleValueEnum<String>
   {
      RECEIVING("RECEIVING"), QUEUED("QUEUED"), RUNNING("RUNNING"), SUCCEEDED("SUCCEEDED"), PARTIAL("PARTIAL"), FAILED("FAILED"), REJECTED("REJECTED"), DUPLICATE("DUPLICATE"), ABORTED("ABORTED");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      UploadStatus(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** OutboxStatus values.
    *******************************************************************************/
   public enum OutboxStatus implements PossibleValueEnum<String>
   {
      READY("READY"), DOWNLOADED("DOWNLOADED"), REMOVED("REMOVED"), EXPIRED("EXPIRED");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      OutboxStatus(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** Operation values.
    *******************************************************************************/
   public enum Operation implements PossibleValueEnum<String>
   {
      LIST("LIST"), UPLOAD("UPLOAD"), DOWNLOAD("DOWNLOAD"), RENAME("RENAME"), REMOVE("REMOVE");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      Operation(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** ObjectKind values.
    *******************************************************************************/
   public enum ObjectKind implements PossibleValueEnum<String>
   {
      APP("APP"), TABLE("TABLE"), PROFILE("PROFILE"), VIEW("VIEW"), REPORT("REPORT"), RESULT("RESULT"), ARCHIVE("ARCHIVE"), ENDPOINT("ENDPOINT"), OUTBOX("OUTBOX");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      ObjectKind(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** TransferOutcome values.
    *******************************************************************************/
   public enum TransferOutcome implements PossibleValueEnum<String>
   {
      OK("OK"), DENIED("DENIED"), NOT_FOUND("NOT_FOUND"), LIMIT("LIMIT"), ERROR("ERROR");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      TransferOutcome(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** ConnectionOutcome values.
    *******************************************************************************/
   public enum ConnectionOutcome implements PossibleValueEnum<String>
   {
      CONNECTED("CONNECTED"), AUTH_FAILED("AUTH_FAILED"), LOCKED_OUT("LOCKED_OUT"), IP_DENIED("IP_DENIED"), EXPIRED("EXPIRED"), DISABLED("DISABLED"), LIMIT("LIMIT");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      ConnectionOutcome(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** Format values.
    *******************************************************************************/
   public enum Format implements PossibleValueEnum<String>
   {
      CSV("csv"), XLSX("xlsx"), JSON("json");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      Format(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }


   /*******************************************************************************
    ** KeyType values.
    *******************************************************************************/
   public enum KeyType implements PossibleValueEnum<String>
   {
      SSH_ED25519("ssh-ed25519"), ECDSA_SHA2_NISTP256("ecdsa-sha2-nistp256"), ECDSA_SHA2_NISTP384("ecdsa-sha2-nistp384"), ECDSA_SHA2_NISTP521("ecdsa-sha2-nistp521"), SSH_RSA("ssh-rsa");

      private final String value;

      /*******************************************************************************
       ** Construct a persisted value.
       *******************************************************************************/
      KeyType(String value)
      {
         this.value = value;
      }


      /*******************************************************************************
       ** Possible value id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (value);
      }


      /*******************************************************************************
       ** Possible value label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (value);
      }
   }
}
