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

package com.kingsrook.qbits.filegateway.pgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

/*******************************************************************************
 ** Bounded, in-memory OpenPGP streams; no key files, providers or background threads.
 *******************************************************************************/
public final class PgpStreams
{
   /*******************************************************************************
    ** Static stream factories only.
    *******************************************************************************/
   private PgpStreams()
   {
   }



   /*******************************************************************************
    ** Require a usable encryption subkey, rather than accepting signing-only keys.
    *******************************************************************************/
   public static PGPPublicKey encryptionKey(String text) throws IOException, PGPException
   {
      if(text == null || text.isBlank())
      {
         throw (new PGPException("PGP public key is required"));
      }
      var rings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))), new BcKeyFingerprintCalculator());
      for(var ring : rings)
      {
         for(var key : ring)
         {
            if(!key.isMasterKey() && key.isEncryptionKey() && !key.hasRevocation()
               && (key.getValidSeconds() == 0 || key.getCreationTime().toInstant().plusSeconds(key.getValidSeconds()).isAfter(java.time.Instant.now())))
            {
               return (key);
            }
         }
      }
      throw (new PGPException("PGP public key must contain an unrevoked, unexpired encryption subkey"));
   }



   /*******************************************************************************
    ** AES-256 binary packets with integrity protection and bounded partial packets.
    *******************************************************************************/
   public static InputStream encrypt(InputStream source, String keyText, String filename) throws IOException
   {
      try
      {
         ByteArrayOutputStream pending = new ByteArrayOutputStream(131072);
         var generator = new PGPEncryptedDataGenerator(new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
            .setWithIntegrityPacket(true).setSecureRandom(new SecureRandom()));
         generator.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(encryptionKey(keyText)));
         OutputStream encrypted = generator.open(pending, new byte[32768]);
         var literalGenerator = new PGPLiteralDataGenerator();
         OutputStream literal = literalGenerator.open(encrypted, PGPLiteralData.BINARY, filename, new Date(0), new byte[32768]);
         return (new InputStream()
         {
            private InputStream available = InputStream.nullInputStream();
            private boolean finished;
            private boolean closed;

            @Override
            public int read() throws IOException
            {
               byte[] one = new byte[1];
               return (read(one, 0, 1) < 0 ? -1 : one[0] & 255);
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException
            {
               java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
               if(closed)
               {
                  throw (new IOException("PGP stream closed"));
               }
               if(length == 0)
               {
                  return (0);
               }
               while(true)
               {
                  int count = available.read(bytes, offset, length);
                  if(count >= 0)
                  {
                     return (count);
                  }
                  if(pending.size() > 0)
                  {
                     available = new ByteArrayInputStream(pending.toByteArray());
                     pending.reset();
                  }
                  else if(finished)
                  {
                     return (-1);
                  }
                  else
                  {
                     byte[] chunk = source.readNBytes(32768);
                     if(chunk.length == 0)
                     {
                        literal.close();
                        encrypted.close();
                        finished = true;
                     }
                     else
                     {
                        literal.write(chunk);
                     }
                  }
               }
            }

            @Override
            public void close() throws IOException
            {
               closed = true;
               source.close();
            }
         });
      }
      catch(PGPException e)
      {
         throw (new IOException("Unable to encrypt PGP download", e));
      }
   }



   /*******************************************************************************
    ** Verify integrity at EOF before object storage can complete its plaintext upload.
    *******************************************************************************/
   public static InputStream decrypt(InputStream source, String secret, char[] passphrase, long maximum) throws IOException
   {
      try
      {
         if(secret == null)
         {
            throw (new PGPException("PGP private key environment variable is missing"));
         }
         var keys = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(secret.getBytes(StandardCharsets.UTF_8))), new BcKeyFingerprintCalculator());
         var factory = new PGPObjectFactory(PGPUtil.getDecoderStream(source), new BcKeyFingerprintCalculator());
         Object packet = factory.nextObject();
         if(!(packet instanceof PGPEncryptedDataList))
         {
            packet = factory.nextObject();
         }
         if(!(packet instanceof PGPEncryptedDataList encrypted))
         {
            throw (new PGPException("Expected PGP encrypted data"));
         }
         for(var item : encrypted)
         {
            if(!(item instanceof PGPPublicKeyEncryptedData data))
            {
               continue;
            }
            var key = keys.getSecretKey(data.getKeyID());
            if(key == null)
            {
               continue;
            }
            var privateKey = key.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(passphrase));
            InputStream clear = data.getDataStream(new BcPublicKeyDataDecryptorFactory(privateKey));
            Object message = new PGPObjectFactory(clear, new BcKeyFingerprintCalculator()).nextObject();
            for(int depth = 0; message instanceof PGPCompressedData compressed && depth < 4; depth++)
            {
               message = new PGPObjectFactory(compressed.getDataStream(), new BcKeyFingerprintCalculator()).nextObject();
            }
            if(!(message instanceof PGPLiteralData literal) || !data.isIntegrityProtected())
            {
               throw (new PGPException("Expected integrity-protected literal PGP content"));
            }
            return (new FilterInputStream(literal.getInputStream())
            {
               private long consumed;
               private boolean verified;

               @Override
               public int read() throws IOException
               {
                  byte[] one = new byte[1];
                  return (read(one, 0, 1) < 0 ? -1 : one[0] & 255);
               }

               @Override
               public int read(byte[] bytes, int offset, int length) throws IOException
               {
                  int count = in.read(bytes, offset, length);
                  if(count > 0 && (consumed += count) > maximum)
                  {
                     throw (new IOException("Decrypted upload exceeds file size limit"));
                  }
                  if(count < 0 && !verified)
                  {
                     try
                     {
                        clear.transferTo(OutputStream.nullOutputStream());
                        if(!data.verify())
                        {
                           throw (new IOException("PGP integrity check failed"));
                        }
                        verified = true;
                     }
                     catch(PGPException e)
                     {
                        throw (new IOException("PGP integrity check failed", e));
                     }
                  }
                  return (count);
               }

               @Override
               public void close() throws IOException
               {
                  try
                  {
                     super.close();
                  }
                  finally
                  {
                     source.close();
                  }
               }
            });
         }
         throw (new PGPException("No matching PGP private key"));
      }
      catch(PGPException | RuntimeException e)
      {
         throw (new IOException("Unable to decrypt PGP upload", e));
      }
   }
}
