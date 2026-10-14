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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Date;
import com.kingsrook.qbits.filegateway.metadata.FileGatewayTables;
import com.kingsrook.qbits.filegateway.pgp.PgpStreams;
import com.kingsrook.qbits.filegateway.upload.UploadJobs;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*******************************************************************************
 ** Real transfer encryption/decryption with ephemeral test-only OpenPGP keys.
 *******************************************************************************/
@ExtendWith(NoDiskExtension.class)
class PgpIntegrationTest
{
   record Keys(String publicKey, String privateKey)
   {
   }

   static Keys keys() throws Exception
   {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var signing = new JcaPGPKeyPair(PGPPublicKey.RSA_SIGN, generator.generateKeyPair(), new Date());
      var encryption = new JcaPGPKeyPair(PGPPublicKey.RSA_ENCRYPT, generator.generateKeyPair(), new Date());
      var rings = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, signing, "ephemeral-test",
         new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1), null, null,
         new BcPGPContentSignerBuilder(PGPPublicKey.RSA_SIGN, HashAlgorithmTags.SHA256), null);
      rings.addSubKey(encryption);
      ByteArrayOutputStream publicBytes = new ByteArrayOutputStream();
      try(var armor = new ArmoredOutputStream(publicBytes))
      {
         rings.generatePublicKeyRing().encode(armor);
      }
      ByteArrayOutputStream privateBytes = new ByteArrayOutputStream();
      try(var armor = new ArmoredOutputStream(privateBytes))
      {
         rings.generateSecretKeyRing().encode(armor);
      }
      return (new Keys(publicBytes.toString(StandardCharsets.UTF_8), privateBytes.toString(StandardCharsets.UTF_8)));
   }

   @Test
   void encryptedBulkUploadInvalidCiphertextAndAccountValidation() throws Exception
   {
      Keys keys = keys();
      try(var h = new FileGatewayTestServer())
      {
         UploadIntegrationTest.profile(h);
         var account = h.account("alice", "A");
         assertThatThrownBy(() -> h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id"))
            .withValue("pgpPublicKey", "invalid"))).isInstanceOf(Exception.class);
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id")).withValue("pgpDecryptUploads", true));
         h.config.withPgpPrivateKeyEnvVarName("TEST_PGP").withPgpPassphraseEnvVarName("TEST_PASS");
         h.runtime.getWorker().withEnvironment(name -> "TEST_PGP".equals(name) ? keys.privateKey() : "");
         var sshKey = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(account, sshKey);
         try(var ssh = h.login("alice", sshKey); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            byte[] ciphertext;
            try(var input = PgpStreams.encrypt(new ByteArrayInputStream(UploadIntegrationTest.CSV), keys.publicKey(), "orders.csv"))
            {
               ciphertext = input.readAllBytes();
            }
            try(var output = sftp.write(UploadIntegrationTest.FOLDER + "orders.csv.pgp"))
            {
               output.write(ciphertext);
            }
            h.runtime.getWorker().runOnce();
            var job = UploadIntegrationTest.jobs(h).getFirst();
            assertThat(job.getValueString("status")).isEqualTo("SUCCEEDED");
            assertThat(job.getValueInteger("insertedCount")).isEqualTo(2);
            assertThat(job.getValueString("plaintextSha256")).isNotBlank().isNotEqualTo(job.getValueString("sha256"));
            assertThat(h.store.objects.get(job.getValueString("plaintextStorageKey"))).isEqualTo(UploadIntegrationTest.CSV);
            assertThat(h.store.objects.get(job.getValueString("storageKey"))).isEqualTo(ciphertext);
            try(var output = sftp.write(UploadIntegrationTest.FOLDER + "bad.csv.gpg"))
            {
               output.write(new byte[]{1, 2, 3});
            }
            h.runtime.getWorker().runOnce();
            assertThat(UploadIntegrationTest.jobs(h)).anySatisfy(row ->
            {
               assertThat(row.getValueString("status")).isEqualTo("REJECTED");
               assertThat(row.getValueString("errorMessage")).contains("PGP decryption failed");
            });
         }
      }
   }

   @Test
   void encryptedGeneratedAndOutboxDownloadsMatchPlaintext() throws Exception
   {
      Keys keys = keys();
      try(var h = new FileGatewayTestServer())
      {
         h.seedRows(2);
         h.saved("savedView", "alpha", "Daily");
         var account = h.account("alice", "A");
         var sshKey = KeyUtils.generateKeyPair("ssh-ed25519", 256);
         h.key(account, sshKey);
         byte[] plain;
         try(var ssh = h.login("alice", sshKey); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            plain = PhaseEIntegrationTest.read(sftp, "/testapp/alpha/views/daily.csv");
         }
         h.audit.update(FileGatewayTables.ACCOUNT, new QRecord().withValue("id", account.getValue("id"))
            .withValue("pgpPublicKey", keys.publicKey()).withValue("pgpEncryptDownloads", true));
         var alias = PhaseEIntegrationTest.alias(h, "out", "/out", "OUTBOX", account);
         h.audit.update(FileGatewayTables.ALIAS, new QRecord().withValue("id", alias.getValue("id")).withValue("consumerName", null));
         com.kingsrook.qbits.filegateway.api.FileGatewayOutbox.put("out", "reply.csv", new ByteArrayInputStream(plain), plain.length);
         try(var ssh = h.login("alice", sshKey); var sftp = SftpClientFactory.instance().createSftpClient(ssh))
         {
            assertThat(PhaseEIntegrationTest.names(sftp, "/testapp/alpha/views")).contains("daily.csv.pgp");
            for(String path : java.util.List.of("/testapp/alpha/views/daily.csv.pgp", "/out/reply.csv.pgp"))
            {
               byte[] encrypted = PhaseEIntegrationTest.read(sftp, path);
               try(var decrypted = PgpStreams.decrypt(new ByteArrayInputStream(encrypted), keys.privateKey(), new char[0], 1_000_000))
               {
                  assertThat(decrypted.readAllBytes()).isEqualTo(plain);
               }
            }
            assertThat(h.audit.query(FileGatewayTables.OUTBOX_FILE, new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter()).getFirst().getValueString("status"))
               .isEqualTo("DOWNLOADED");
         }
      }
   }

   @Test
   void integrityAndPlaintextSizeAreEnforced() throws Exception
   {
      Keys keys = keys();
      byte[] bytes = new byte[160000];
      new java.util.Random(42).nextBytes(bytes);
      byte[] encrypted;
      try(var input = PgpStreams.encrypt(new ByteArrayInputStream(bytes), keys.publicKey(), "file.bin"))
      {
         encrypted = input.readAllBytes();
      }
      try(var input = PgpStreams.decrypt(new ByteArrayInputStream(encrypted), keys.privateKey(), new char[0], bytes.length))
      {
         assertThat(input.readAllBytes()).isEqualTo(bytes);
      }
      assertThatThrownBy(() ->
      {
         try(var input = PgpStreams.decrypt(new ByteArrayInputStream(encrypted), keys.privateKey(), new char[0], 10))
         {
            input.readAllBytes();
         }
      }).isInstanceOf(java.io.IOException.class);
      encrypted[encrypted.length - 1] ^= 1;
      assertThatThrownBy(() ->
      {
         try(var input = PgpStreams.decrypt(new ByteArrayInputStream(encrypted), keys.privateKey(), new char[0], bytes.length))
         {
            input.readAllBytes();
         }
      }).isInstanceOf(java.io.IOException.class);
   }
}
