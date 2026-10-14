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

package com.kingsrook.qbits.filegateway.sftp;


import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.vfs.GatewayFileSystem;
import com.kingsrook.qbits.filegateway.vfs.GatewayUiApiClient;
import com.kingsrook.qbits.filegateway.vfs.VirtualTree;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.invoke.UiApiInvoker;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;


/*******************************************************************************
 ** Public-key-only SFTP with explicit modern algorithms and an entirely virtual filesystem.
 *******************************************************************************/
public class SftpProtocolServer implements FileGatewayProtocolServer
{
   private static final QLogger LOG = QLogger.getLogger(SftpProtocolServer.class);
   private final SshServer server;
   private final SshKeyAuthenticator authenticator;
   private final Duration pollInterval;
   private ScheduledExecutorService poller;



   /*******************************************************************************
    ** Configure the listener, security policy and injected runtime dependencies.
    *******************************************************************************/
   public SftpProtocolServer(QInstance instance, FileGatewayQBitConfig config, UiApiInvoker invoker, KeyPair hostKey, Clock clock, Duration pollInterval, String podName)
   {
      this.pollInterval = pollInterval;
      AuditWriter audit = new AuditWriter(instance);
      authenticator = new SshKeyAuthenticator(instance, config, audit, clock, podName);
      server = SshServer.setUpDefaultServer();
      server.setPort(config.getSftpPort());
      server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
      server.setCipherFactories(List.of(BuiltinCiphers.cc20p1305_openssh, BuiltinCiphers.aes128gcm, BuiltinCiphers.aes256gcm, BuiltinCiphers.aes128ctr, BuiltinCiphers.aes192ctr, BuiltinCiphers.aes256ctr));
      server.setMacFactories(List.of(BuiltinMacs.hmacsha256etm, BuiltinMacs.hmacsha512etm));
      server.setKeyExchangeFactories(List.of(BuiltinDHFactories.curve25519, BuiltinDHFactories.curve25519_libssh, BuiltinDHFactories.ecdhp256, BuiltinDHFactories.ecdhp384, BuiltinDHFactories.ecdhp521, BuiltinDHFactories.dhg16_512, BuiltinDHFactories.dhg18_512)
         .stream().filter(BuiltinDHFactories::isSupported).map(ServerBuilder.DH2KEX).toList());
      server.setSignatureFactories(List.of(BuiltinSignatures.ed25519, BuiltinSignatures.nistp256, BuiltinSignatures.nistp384, BuiltinSignatures.nistp521, BuiltinSignatures.rsaSHA256, BuiltinSignatures.rsaSHA512));
      server.setPasswordAuthenticator(null);
      server.setKeyboardInteractiveAuthenticator(null);
      server.setGSSAuthenticator(null);
      server.setHostBasedAuthenticator(null);
      server.setUserAuthFactories(List.of(UserAuthPublicKeyFactory.INSTANCE));
      server.setPublickeyAuthenticator(authenticator);
      server.setShellFactory(null);
      server.setCommandFactory(null);
      server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
      server.setAgentFactory(null);
      server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
      CoreModuleProperties.IDLE_TIMEOUT.set(server, Duration.ofMinutes(15));
      CoreModuleProperties.AUTH_TIMEOUT.set(server, Duration.ofSeconds(30));
      server.setFileSystemFactory(new FileSystemFactory()
      {
         /*******************************************************************************
          ** Create the session virtual root.
          *******************************************************************************/
         @Override
         public Path getUserHomeDir(SessionContext session) throws IOException
         {
            return (createFileSystem(session).getPath("/"));
         }
      
         /*******************************************************************************
          ** No native filesystem is consulted.
          *******************************************************************************/
         @Override
         public FileSystem createFileSystem(SessionContext session)
         {
            GatewaySession state = session.getAttribute(GatewaySession.ATTRIBUTE);
            return (new GatewayFileSystem(new VirtualTree(new GatewayUiApiClient(invoker, state), state, audit, clock)));
         }
      });
      server.addSessionListener(new SessionListener()
      {
         /*******************************************************************************
          ** Persist accept before authentication.
          *******************************************************************************/
         @Override
         public void sessionCreated(Session session)
         {
            try
            {
               authenticator.accepted((ServerSession) session);
            }
            catch(Exception e)
            {
               LOG.warn("Unable to audit SFTP accept");
               session.close(true);
            }
         }
      
         /*******************************************************************************
          ** Login bookkeeping starts only after proof of private-key possession.
          *******************************************************************************/
         @Override
         public void sessionEvent(Session session, Event event)
         {
            if(event == Event.Authenticated)
            {
               try
               {
                  authenticator.authenticated((ServerSession) session);
               }
               catch(Exception e)
               {
                  LOG.warn("Unable to finalize SFTP authentication");
                  session.close(true);
               }
            }
         }



         /*******************************************************************************
          ** Always revoke the in-process token.
          *******************************************************************************/
         @Override
         public void sessionClosed(Session session)
         {
            try
            {
               authenticator.closed((ServerSession) session);
            }
            catch(Exception e)
            {
               LOG.warn("Unable to finalize SFTP connection audit");
            }
         }
      });
   }



   /*******************************************************************************
    ** Start the listener and revocation heartbeat.
    *******************************************************************************/
   @Override
   public void start() throws IOException
   {
      server.start();
      poller = Executors.newSingleThreadScheduledExecutor(runnable ->
      {
         Thread thread = new Thread(runnable, "file-gateway-revocation");
         thread.setDaemon(true);
         return (thread);
      });
      poller.scheduleWithFixedDelay(() ->
      {
         try
         {
            authenticator.poll();
         }
         catch(Exception e)
         {
            LOG.warn("Unable to verify live SFTP sessions; closing them");
            server.getActiveSessions().forEach(session -> session.close(true));
         }
      }, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
   }



   /*******************************************************************************
    ** Stop polling, revoke sessions and stop the listener.
    *******************************************************************************/
   @Override
   public void stop() throws IOException
   {
      if(poller != null)
      {
         poller.shutdownNow();
      }
      server.stop(true);
      try
      {
         authenticator.shutdown();
      }
      catch(com.kingsrook.qqq.backend.core.exceptions.QException e)
      {
         throw (new IOException("Unable to finalize gateway shutdown", e));
      }
   }



   /*******************************************************************************
    ** Count active SSH connections.
    *******************************************************************************/
   @Override
   public int activeSessions()
   {
      return (server.getActiveSessions().size());
   }



   /*******************************************************************************
    ** Bound port, including ephemeral test ports.
    *******************************************************************************/
   public int getPort()
   {
      return (server.getPort());
   }



   /*******************************************************************************
    ** Server security configuration for inspection.
    *******************************************************************************/
   public SshServer getServer()
   {
      return (server);
   }



   /*******************************************************************************
    ** Live revocation hook for deterministic tests.
    *******************************************************************************/
   public SshKeyAuthenticator getAuthenticator()
   {
      return (authenticator);
   }
}
