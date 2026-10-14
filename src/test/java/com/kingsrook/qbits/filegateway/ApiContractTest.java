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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import com.kingsrook.qbits.filegateway.api.FileAcceptance;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumer;
import com.kingsrook.qbits.filegateway.api.FileGatewayConsumersMetaData;
import com.kingsrook.qbits.filegateway.api.FileGatewayOutbox;
import com.kingsrook.qbits.filegateway.api.FileGatewayUnavailableException;
import com.kingsrook.qbits.filegateway.api.InboundFile;
import com.kingsrook.qbits.filegateway.api.OutboxFileRef;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Independently usable consumer and outbox contracts.
 *******************************************************************************/
class ApiContractTest
{
   /*******************************************************************************
    ** The public API may depend on core, but never gateway implementation types.
    *******************************************************************************/
   @Test
   void apiImportsAreIsolated() throws Exception
   {
      try(var sources = Files.list(Path.of("src/main/java/com/kingsrook/qbits/filegateway/api")))
      {
         for(Path source : sources.filter(p -> p.toString().endsWith(".java")).toList())
         {
            String text = Files.readString(source);
            assertThat(text).as(source.toString()).doesNotContainPattern("com\\.kingsrook\\.qbits\\.filegateway\\.(?!api[.;])");
         }
      }
   }



   /*******************************************************************************
    ** Fresh streams, immutable decisions, consumer registration and provider dispatch.
    *******************************************************************************/
   @Test
   void consumerAndOutboxContract() throws Exception
   {
      var file = new InboundFile(1, "orders", "alice", "A", "127.0.0.1", "/orders/a.csv", "a.csv", 3, "sha", Instant.EPOCH,
         "bucket", "key", () -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
      assertThat(file.openStream()).isNotSameAs(file.openStream());
      assertThat(file.openStream().readAllBytes()).containsExactly(1, 2, 3);
      assertThat(file.remoteAddress()).isEqualTo("127.0.0.1");
      FileGatewayConsumer consumer = new FileGatewayConsumer()
      {
         /*******************************************************************************
          ** Get name.
          *******************************************************************************/
         public String getName()
         {
            return ("test");
         }



         /*******************************************************************************
          ** Accept.
          *******************************************************************************/
         public FileAcceptance accept(InboundFile inbound)
         {
            return (FileAcceptance.accepted(inbound.sha256()));
         }
      };
      assertThat(consumer.getName()).isEqualTo("test");
      var accepted = consumer.accept(file);
      assertThat(accepted.getStatus()).isEqualTo(FileAcceptance.Status.ACCEPTED);
      assertThat(accepted.getConsumerReference()).isEqualTo("sha");
      assertThat(accepted.getReason()).isNull();
      var rejected = FileAcceptance.rejected("invalid");
      assertThat(rejected.getStatus()).isEqualTo(FileAcceptance.Status.REJECTED);
      assertThat(rejected.getReason()).isEqualTo("invalid");
      var retry = FileAcceptance.retryLater("busy", Duration.ofMinutes(1));
      assertThat(retry.getStatus()).isEqualTo(FileAcceptance.Status.RETRY_LATER);
      assertThat(retry.getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
      var ref = new OutboxFileRef(2, "orders", "/orders/b.csv", "b.csv", 3, "sha", Instant.EPOCH);
      consumer.onDownloaded(ref);
      FileGatewayOutbox.setProvider(null);
      assertThat(FileGatewayOutbox.isAvailable()).isFalse();
      assertThatThrownBy(() -> FileGatewayOutbox.put("orders", "b.csv", file.openStream(), 3)).isInstanceOf(FileGatewayUnavailableException.class);
      try
      {
         FileGatewayOutbox.setProvider((endpoint, name, stream, size) ->
         {
            assertThat(endpoint).isEqualTo("orders");
            assertThat(name).isEqualTo("b.csv");
            assertThat(size).isEqualTo(3);
            return (ref);
         });
         assertThat(FileGatewayOutbox.isAvailable()).isTrue();
         assertThat(FileGatewayOutbox.put("orders", "b.csv", file.openStream(), 3)).isSameAs(ref);
      }
      finally
      {
         FileGatewayOutbox.setProvider(null);
      }
      var instance = new QInstance();
      var registry = FileGatewayConsumersMetaData.ofOrWithNew(instance);
      var code = new QCodeReference(consumer.getClass());
      assertThat(registry.withConsumer(code)).isSameAs(registry);
      assertThat(registry.getName()).isEqualTo("fileGatewayConsumers");
      assertThat(FileGatewayConsumersMetaData.ofOrWithNew(instance)).isSameAs(registry);
      assertThat(registry.getConsumers()).containsExactly(code);
      assertThatThrownBy(() -> registry.getConsumers().clear()).isInstanceOf(UnsupportedOperationException.class);
   }
}
