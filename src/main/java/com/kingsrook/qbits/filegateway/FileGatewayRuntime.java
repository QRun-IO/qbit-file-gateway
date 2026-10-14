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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;
import com.kingsrook.qbits.filegateway.sftp.SftpProtocolServer;
import com.kingsrook.qbits.filegateway.storage.GatewayObjectStore;
import com.kingsrook.qbits.filegateway.storage.ObjectStorageGatewayStore;
import com.kingsrook.qbits.filegateway.upload.UploadJobWorker;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;


/*******************************************************************************
 ** Host-started SFTP runtime. Host private keys are parsed in memory and never persisted.
 *******************************************************************************/
public final class FileGatewayRuntime implements AutoCloseable
{
   private static final QLogger LOG = QLogger.getLogger(FileGatewayRuntime.class);
   private final UiApiInvoker invoker;
   private final SftpProtocolServer protocol;
   private final UploadJobWorker worker;



   /*******************************************************************************
    ** Bind the host UI API server to the SFTP channel.
    *******************************************************************************/
   private FileGatewayRuntime(QInstance instance, QApplicationJavalinServer server, FileGatewayQBitConfig config, KeyPair key, Clock clock, Duration interval, String podName, GatewayObjectStore store)
   {
      invoker = UiApiInvoker.forServer(server, "sftp");
      protocol = new SftpProtocolServer(instance, config, invoker, key, clock, interval, podName, store);
      worker = new UploadJobWorker(instance, invoker, config, store, clock, podName);
   }



   /*******************************************************************************
    ** Start only when the registered qbit configuration enables SFTP.
    *******************************************************************************/
   public static FileGatewayRuntime start(QInstance instance, QApplicationJavalinServer server) throws Exception
   {
      return (start(instance, server, System::getenv));
   }



   /*******************************************************************************
    ** Start with an injectable environment reader.
    *******************************************************************************/
   public static FileGatewayRuntime start(QInstance instance, QApplicationJavalinServer server, Function<String, String> environment) throws Exception
   {
      var qbit = instance.getQBits().get(FileGatewayQBitProducer.GROUP_ID + ":" + FileGatewayQBitProducer.ARTIFACT_ID);
      if(qbit == null || !(qbit.getConfig() instanceof FileGatewayQBitConfig config) || !config.getSftpEnabled())
      {
         return (null);
      }
      FileGatewayRuntime runtime = new FileGatewayRuntime(instance, server, config, hostKey(config, environment), Clock.systemUTC(), Duration.ofSeconds(60), environment.apply("HOSTNAME"), new ObjectStorageGatewayStore(instance, config.getFileGatewayBucketAlias()));
      runtime.worker.withEnvironment(environment);
      runtime.protocol.start();
      runtime.worker.start(Duration.ofSeconds(60));
      return (runtime);
   }



   /*******************************************************************************
    ** Start with an explicit ephemeral port, in-memory host key and controllable clock.
    *******************************************************************************/
   public static FileGatewayRuntime startForTest(QInstance instance, QApplicationJavalinServer server, FileGatewayQBitConfig config, KeyPair key, Clock clock, Duration pollInterval) throws IOException
   {
      return (startForTest(instance, server, config, key, clock, pollInterval, new ObjectStorageGatewayStore(instance, config.getFileGatewayBucketAlias())));
   }



   /*******************************************************************************
    ** Transfer tests supply an in-memory store with no production fallback.
    *******************************************************************************/
   public static FileGatewayRuntime startForTest(QInstance instance, QApplicationJavalinServer server, FileGatewayQBitConfig config, KeyPair key, Clock clock, Duration pollInterval, GatewayObjectStore store) throws IOException
   {
      FileGatewayRuntime runtime = new FileGatewayRuntime(instance, server, config, key, clock, pollInterval, "test", store);
      runtime.protocol.start();
      return (runtime);
   }



   /*******************************************************************************
    ** Read an OpenSSH private key directly from memory.
    *******************************************************************************/
   public static KeyPair hostKey(FileGatewayQBitConfig config, Function<String, String> environment) throws Exception
   {
      String pem = environment.apply(config.getHostKeyEnvVarName());
      if(pem == null || pem.isBlank())
      {
         if(!config.getAllowGeneratedHostKey())
         {
            throw (new IllegalStateException("SFTP host key environment variable is missing: " + config.getHostKeyEnvVarName()));
         }
         LOG.warn("Using a generated in-memory SFTP host key; do not use in production");
         return (KeyUtils.generateKeyPair("ecdsa-sha2-nistp256", 256));
      }
      try(var stream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)))
      {
         var keys = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName("environment-host-key"), stream, null).iterator();
         if(!keys.hasNext())
         {
            throw (new IllegalArgumentException("No SFTP host key in environment variable"));
         }
         return (keys.next());
      }
   }



   /*******************************************************************************
    ** Stop the SFTP runtime.
    *******************************************************************************/
   public void stop() throws IOException
   {
      protocol.stop();
      try
      {
         worker.close();
      }
      catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
      {
         throw (new IOException("Unable to release upload jobs", e));
      }
   }



   /*******************************************************************************
    ** Close this runtime.
    *******************************************************************************/
   @Override
   public void close() throws IOException
   {
      stop();
   }



   /*******************************************************************************
    ** Access the protocol listener.
    *******************************************************************************/
   public SftpProtocolServer getProtocol()
   {
      return (protocol);
   }



   /*******************************************************************************
    ** The fixed sftp-channel invoker.
    *******************************************************************************/
   public UiApiInvoker getInvoker()
   {
      return (invoker);
   }

   /*******************************************************************************
    ** Deterministic worker hook for hosts and integration tests.
    *******************************************************************************/
   public UploadJobWorker getWorker()
   {
      return (worker);
   }

}
