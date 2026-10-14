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


import java.time.Duration;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Configuration shared by the admin application and gateway deployment.
 *******************************************************************************/
public class FileGatewayQBitConfig implements QBitConfig
{
   private String schedulerName = null;
   private Duration serverRetention = Duration.ofDays(1);
   private String backendName = "rdbms";
   private boolean sftpEnabled = false;
   private int sftpPort = 2222;
   private String publicHostName = null;
   private String hostKeyEnvVarName = "FILE_GATEWAY_SSH_HOST_KEY";
   private boolean allowGeneratedHostKey = false;
   private String fileGatewayBucketAlias = "file-gateway";
   private int uploadPartSizeMb = 8;
   private int uploadReorderWindowMb = 64;
   private int downloadReadAheadMb = 16;
   private int maxGeneratedFileMb = 1024;
   private int uploadWorkerThreads = 4;
   private Duration dedupeWindow = Duration.ofDays(7);
   private Duration jobStaleAfter = Duration.ofMinutes(30);
   private List<String> temporaryUploadSuffixes = List.of(".filepart", ".tmp", ".part", ".partial");
   private int lockoutThreshold = 5;
   private Duration lockoutWindow = Duration.ofMinutes(15);
   private Duration lockoutDuration = Duration.ofMinutes(30);
   private int maxConnectionsPerIpPerMinute = 30;
   private int defaultMaxConcurrentSessions = 4;
   private int defaultMaxOperationsPerMinute = 600;
   private int defaultMaxFileSizeMb = 100;
   private Integer defaultMaxUploadMbPerDay = null;
   private Integer defaultMaxDownloadMbPerDay = null;
   private QCodeReference scanner = null;
   private String pgpPrivateKeyEnvVarName = null;
   private String pgpPassphraseEnvVarName = null;
   private Duration logRetention = Duration.ofDays(180);
   private Duration archiveRetention = Duration.ofDays(30);
   private Duration outboxRetention = Duration.ofDays(30);
   private Duration resultsRetention = Duration.ofDays(90);
   private Duration receivingStaleAfter = Duration.ofHours(1);
   private MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer = null;



   /*******************************************************************************
    ** Getter for backendName.
    *******************************************************************************/
   public String getBackendName()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** Fluent setter for backendName.
    *******************************************************************************/
   public FileGatewayQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sftpEnabled.
    *******************************************************************************/
   public boolean getSftpEnabled()
   {
      return (sftpEnabled);
   }



   /*******************************************************************************
    ** Fluent setter for sftpEnabled.
    *******************************************************************************/
   public FileGatewayQBitConfig withSftpEnabled(boolean sftpEnabled)
   {
      this.sftpEnabled = sftpEnabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sftpPort.
    *******************************************************************************/
   public int getSftpPort()
   {
      return (sftpPort);
   }



   /*******************************************************************************
    ** Public SFTP hostname advertised in Preferences, when configured.
    *******************************************************************************/
   public String getPublicHostName()
   {
      return (publicHostName);
   }



   /*******************************************************************************
    ** Set the public SFTP hostname; null omits it from Preferences.
    *******************************************************************************/
   public FileGatewayQBitConfig withPublicHostName(String publicHostName)
   {
      this.publicHostName = publicHostName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sftpPort.
    *******************************************************************************/
   public FileGatewayQBitConfig withSftpPort(int sftpPort)
   {
      this.sftpPort = sftpPort;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hostKeyEnvVarName.
    *******************************************************************************/
   public String getHostKeyEnvVarName()
   {
      return (hostKeyEnvVarName);
   }



   /*******************************************************************************
    ** Fluent setter for hostKeyEnvVarName.
    *******************************************************************************/
   public FileGatewayQBitConfig withHostKeyEnvVarName(String hostKeyEnvVarName)
   {
      this.hostKeyEnvVarName = hostKeyEnvVarName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowGeneratedHostKey.
    *******************************************************************************/
   public boolean getAllowGeneratedHostKey()
   {
      return (allowGeneratedHostKey);
   }



   /*******************************************************************************
    ** Fluent setter for allowGeneratedHostKey.
    *******************************************************************************/
   public FileGatewayQBitConfig withAllowGeneratedHostKey(boolean allowGeneratedHostKey)
   {
      this.allowGeneratedHostKey = allowGeneratedHostKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fileGatewayBucketAlias.
    *******************************************************************************/
   public String getFileGatewayBucketAlias()
   {
      return (fileGatewayBucketAlias);
   }



   /*******************************************************************************
    ** Fluent setter for fileGatewayBucketAlias.
    *******************************************************************************/
   public FileGatewayQBitConfig withFileGatewayBucketAlias(String fileGatewayBucketAlias)
   {
      this.fileGatewayBucketAlias = fileGatewayBucketAlias;
      return (this);
   }



   /*******************************************************************************
    ** Getter for uploadPartSizeMb.
    *******************************************************************************/
   public int getUploadPartSizeMb()
   {
      return (uploadPartSizeMb);
   }



   /*******************************************************************************
    ** Fluent setter for uploadPartSizeMb.
    *******************************************************************************/
   public FileGatewayQBitConfig withUploadPartSizeMb(int uploadPartSizeMb)
   {
      this.uploadPartSizeMb = uploadPartSizeMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for uploadReorderWindowMb.
    *******************************************************************************/
   public int getUploadReorderWindowMb()
   {
      return (uploadReorderWindowMb);
   }



   /*******************************************************************************
    ** Fluent setter for uploadReorderWindowMb.
    *******************************************************************************/
   public FileGatewayQBitConfig withUploadReorderWindowMb(int uploadReorderWindowMb)
   {
      this.uploadReorderWindowMb = uploadReorderWindowMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for downloadReadAheadMb.
    *******************************************************************************/
   public int getDownloadReadAheadMb()
   {
      return (downloadReadAheadMb);
   }



   /*******************************************************************************
    ** Fluent setter for downloadReadAheadMb.
    *******************************************************************************/
   public FileGatewayQBitConfig withDownloadReadAheadMb(int downloadReadAheadMb)
   {
      this.downloadReadAheadMb = downloadReadAheadMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxGeneratedFileMb.
    *******************************************************************************/
   public int getMaxGeneratedFileMb()
   {
      return (maxGeneratedFileMb);
   }



   /*******************************************************************************
    ** Fluent setter for maxGeneratedFileMb.
    *******************************************************************************/
   public FileGatewayQBitConfig withMaxGeneratedFileMb(int maxGeneratedFileMb)
   {
      this.maxGeneratedFileMb = maxGeneratedFileMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for uploadWorkerThreads.
    *******************************************************************************/
   public int getUploadWorkerThreads()
   {
      return (uploadWorkerThreads);
   }



   /*******************************************************************************
    ** Fluent setter for uploadWorkerThreads.
    *******************************************************************************/
   public FileGatewayQBitConfig withUploadWorkerThreads(int uploadWorkerThreads)
   {
      this.uploadWorkerThreads = uploadWorkerThreads;
      return (this);
   }



   /*******************************************************************************
    ** Getter for dedupeWindow.
    *******************************************************************************/
   public Duration getDedupeWindow()
   {
      return (dedupeWindow);
   }



   /*******************************************************************************
    ** Fluent setter for dedupeWindow.
    *******************************************************************************/
   public FileGatewayQBitConfig withDedupeWindow(Duration dedupeWindow)
   {
      this.dedupeWindow = dedupeWindow;
      return (this);
   }



   /*******************************************************************************
    ** Getter for jobStaleAfter.
    *******************************************************************************/
   public Duration getJobStaleAfter()
   {
      return (jobStaleAfter);
   }



   /*******************************************************************************
    ** Fluent setter for jobStaleAfter.
    *******************************************************************************/
   public FileGatewayQBitConfig withJobStaleAfter(Duration jobStaleAfter)
   {
      this.jobStaleAfter = jobStaleAfter;
      return (this);
   }



   /*******************************************************************************
    ** Getter for temporaryUploadSuffixes.
    *******************************************************************************/
   public List<String> getTemporaryUploadSuffixes()
   {
      return (temporaryUploadSuffixes);
   }



   /*******************************************************************************
    ** Fluent setter for temporaryUploadSuffixes.
    *******************************************************************************/
   public FileGatewayQBitConfig withTemporaryUploadSuffixes(List<String> temporaryUploadSuffixes)
   {
      this.temporaryUploadSuffixes = temporaryUploadSuffixes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lockoutThreshold.
    *******************************************************************************/
   public int getLockoutThreshold()
   {
      return (lockoutThreshold);
   }



   /*******************************************************************************
    ** Fluent setter for lockoutThreshold.
    *******************************************************************************/
   public FileGatewayQBitConfig withLockoutThreshold(int lockoutThreshold)
   {
      this.lockoutThreshold = lockoutThreshold;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lockoutWindow.
    *******************************************************************************/
   public Duration getLockoutWindow()
   {
      return (lockoutWindow);
   }



   /*******************************************************************************
    ** Fluent setter for lockoutWindow.
    *******************************************************************************/
   public FileGatewayQBitConfig withLockoutWindow(Duration lockoutWindow)
   {
      this.lockoutWindow = lockoutWindow;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lockoutDuration.
    *******************************************************************************/
   public Duration getLockoutDuration()
   {
      return (lockoutDuration);
   }



   /*******************************************************************************
    ** Fluent setter for lockoutDuration.
    *******************************************************************************/
   public FileGatewayQBitConfig withLockoutDuration(Duration lockoutDuration)
   {
      this.lockoutDuration = lockoutDuration;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxConnectionsPerIpPerMinute.
    *******************************************************************************/
   public int getMaxConnectionsPerIpPerMinute()
   {
      return (maxConnectionsPerIpPerMinute);
   }



   /*******************************************************************************
    ** Fluent setter for maxConnectionsPerIpPerMinute.
    *******************************************************************************/
   public FileGatewayQBitConfig withMaxConnectionsPerIpPerMinute(int maxConnectionsPerIpPerMinute)
   {
      this.maxConnectionsPerIpPerMinute = maxConnectionsPerIpPerMinute;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultMaxConcurrentSessions.
    *******************************************************************************/
   public int getDefaultMaxConcurrentSessions()
   {
      return (defaultMaxConcurrentSessions);
   }



   /*******************************************************************************
    ** Fluent setter for defaultMaxConcurrentSessions.
    *******************************************************************************/
   public FileGatewayQBitConfig withDefaultMaxConcurrentSessions(int defaultMaxConcurrentSessions)
   {
      this.defaultMaxConcurrentSessions = defaultMaxConcurrentSessions;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultMaxOperationsPerMinute.
    *******************************************************************************/
   public int getDefaultMaxOperationsPerMinute()
   {
      return (defaultMaxOperationsPerMinute);
   }



   /*******************************************************************************
    ** Fluent setter for defaultMaxOperationsPerMinute.
    *******************************************************************************/
   public FileGatewayQBitConfig withDefaultMaxOperationsPerMinute(int defaultMaxOperationsPerMinute)
   {
      this.defaultMaxOperationsPerMinute = defaultMaxOperationsPerMinute;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultMaxFileSizeMb.
    *******************************************************************************/
   public int getDefaultMaxFileSizeMb()
   {
      return (defaultMaxFileSizeMb);
   }



   /*******************************************************************************
    ** Fluent setter for defaultMaxFileSizeMb.
    *******************************************************************************/
   public FileGatewayQBitConfig withDefaultMaxFileSizeMb(int defaultMaxFileSizeMb)
   {
      this.defaultMaxFileSizeMb = defaultMaxFileSizeMb;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultMaxUploadMbPerDay.
    *******************************************************************************/
   public Integer getDefaultMaxUploadMbPerDay()
   {
      return (defaultMaxUploadMbPerDay);
   }



   /*******************************************************************************
    ** Fluent setter for defaultMaxUploadMbPerDay.
    *******************************************************************************/
   public FileGatewayQBitConfig withDefaultMaxUploadMbPerDay(Integer defaultMaxUploadMbPerDay)
   {
      this.defaultMaxUploadMbPerDay = defaultMaxUploadMbPerDay;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultMaxDownloadMbPerDay.
    *******************************************************************************/
   public Integer getDefaultMaxDownloadMbPerDay()
   {
      return (defaultMaxDownloadMbPerDay);
   }



   /*******************************************************************************
    ** Fluent setter for defaultMaxDownloadMbPerDay.
    *******************************************************************************/
   public FileGatewayQBitConfig withDefaultMaxDownloadMbPerDay(Integer defaultMaxDownloadMbPerDay)
   {
      this.defaultMaxDownloadMbPerDay = defaultMaxDownloadMbPerDay;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scanner.
    *******************************************************************************/
   public QCodeReference getScanner()
   {
      return (scanner);
   }



   /*******************************************************************************
    ** Fluent setter for scanner.
    *******************************************************************************/
   public FileGatewayQBitConfig withScanner(QCodeReference scanner)
   {
      this.scanner = scanner;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pgpPrivateKeyEnvVarName.
    *******************************************************************************/
   public String getPgpPrivateKeyEnvVarName()
   {
      return (pgpPrivateKeyEnvVarName);
   }



   /*******************************************************************************
    ** Fluent setter for pgpPrivateKeyEnvVarName.
    *******************************************************************************/
   public FileGatewayQBitConfig withPgpPrivateKeyEnvVarName(String pgpPrivateKeyEnvVarName)
   {
      this.pgpPrivateKeyEnvVarName = pgpPrivateKeyEnvVarName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pgpPassphraseEnvVarName.
    *******************************************************************************/
   public String getPgpPassphraseEnvVarName()
   {
      return (pgpPassphraseEnvVarName);
   }



   /*******************************************************************************
    ** Fluent setter for pgpPassphraseEnvVarName.
    *******************************************************************************/
   public FileGatewayQBitConfig withPgpPassphraseEnvVarName(String pgpPassphraseEnvVarName)
   {
      this.pgpPassphraseEnvVarName = pgpPassphraseEnvVarName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for logRetention.
    *******************************************************************************/
   public Duration getLogRetention()
   {
      return (logRetention);
   }



   /*******************************************************************************
    ** Fluent setter for logRetention.
    *******************************************************************************/
   public FileGatewayQBitConfig withLogRetention(Duration logRetention)
   {
      this.logRetention = logRetention;
      return (this);
   }



   /*******************************************************************************
    ** Getter for archiveRetention.
    *******************************************************************************/
   public Duration getArchiveRetention()
   {
      return (archiveRetention);
   }



   /*******************************************************************************
    ** Fluent setter for archiveRetention.
    *******************************************************************************/
   public FileGatewayQBitConfig withArchiveRetention(Duration archiveRetention)
   {
      this.archiveRetention = archiveRetention;
      return (this);
   }



   /*******************************************************************************
    ** Getter for outboxRetention.
    *******************************************************************************/
   public Duration getOutboxRetention()
   {
      return (outboxRetention);
   }



   /*******************************************************************************
    ** Fluent setter for outboxRetention.
    *******************************************************************************/
   public FileGatewayQBitConfig withOutboxRetention(Duration outboxRetention)
   {
      this.outboxRetention = outboxRetention;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resultsRetention.
    *******************************************************************************/
   public Duration getResultsRetention()
   {
      return (resultsRetention);
   }



   /*******************************************************************************
    ** Fluent setter for resultsRetention.
    *******************************************************************************/
   public FileGatewayQBitConfig withResultsRetention(Duration resultsRetention)
   {
      this.resultsRetention = resultsRetention;
      return (this);
   }



   /*******************************************************************************
    ** Getter for receivingStaleAfter.
    *******************************************************************************/
   public Duration getReceivingStaleAfter()
   {
      return (receivingStaleAfter);
   }



   /*******************************************************************************
    ** Fluent setter for receivingStaleAfter.
    *******************************************************************************/
   public FileGatewayQBitConfig withReceivingStaleAfter(Duration receivingStaleAfter)
   {
      this.receivingStaleAfter = receivingStaleAfter;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableMetaDataCustomizer.
    *******************************************************************************/
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (tableMetaDataCustomizer);
   }



   /*******************************************************************************
    ** Fluent setter for tableMetaDataCustomizer.
    *******************************************************************************/
   public FileGatewayQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }



   /*******************************************************************************
    ** Getter for schedulerName.
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (schedulerName);
   }



   /*******************************************************************************
    ** Fluent setter for schedulerName.
    *******************************************************************************/
   public FileGatewayQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for serverRetention.
    *******************************************************************************/
   public Duration getServerRetention()
   {
      return (serverRetention);
   }



   /*******************************************************************************
    ** Fluent setter for serverRetention.
    *******************************************************************************/
   public FileGatewayQBitConfig withServerRetention(Duration serverRetention)
   {
      this.serverRetention = serverRetention;
      return (this);
   }



}
