/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.batch.processing.ccm.AzureStorageSyncRecord;
import io.harness.batch.processing.config.AzureStorageSyncConfig;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.intfc.AzureStorageSyncService;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import com.google.common.collect.Lists;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

/**
 * Performs Azure container sync.
 */
@Slf4j
public class AzureStorageSyncServiceImpl implements AzureStorageSyncService {
  @Inject BatchMainConfig configuration;

  private static final int SYNC_TIMEOUT_MINUTES = 5;
  private static final String AZURE_STORAGE_SUFFIX = "blob.core.windows.net";
  private static final String AZURE_STORAGE_URL_FORMAT = "https://%s.%s";

  @Override
  @SuppressWarnings("PMD")
  public boolean syncContainer(AzureStorageSyncRecord azureStorageSyncRecord) {
    AzureStorageSyncConfig azureStorageSyncConfig = configuration.getAzureStorageSyncConfig();
    String sourcePath = "";
    String sourceSasToken = "";

    String destinationPath = "";
    String destinationSasToken = "";

    String destinationPathWithToken = "";
    String sourcePathWithToken = "";

    try {
      // generate SAS token for source
      sourceSasToken = genSasToken(azureStorageSyncRecord.getStorageAccountName(),
          azureStorageSyncRecord.getContainerName(), azureStorageSyncRecord.getTenantId(),
          azureStorageSyncConfig.getAzureAppClientId(), azureStorageSyncConfig.getAzureAppClientSecret(), false);
    } catch (Exception exception) {
      log.error("Error in generating sourceSasToken sas token", exception);
      // Proceed to next sync
      return false;
    }
    try {
      destinationSasToken = azureStorageSyncConfig.getAzureSasToken();
      /* TODO: generate SAS token for destination
      destinationSasToken = genSasToken(azureStorageSyncConfig.getAzureStorageAccountName(),
              azureStorageSyncConfig.getAzureStorageContainerName(),
              azureStorageSyncConfig.getAzureTenantId(),
              azureStorageSyncConfig.getAzureAppClientId(),
              azureStorageSyncConfig.getAzureAppClientSecret(),
              true);
     */
    } catch (Exception exception) {
      log.error("Error in generating destinationSasToken sas token", exception);
      // Proceed to next sync
      return false;
    }
    try {
      // Run the azcopy tool to do the sync
      String sourceStorageAccountUrl =
          String.format(AZURE_STORAGE_URL_FORMAT, azureStorageSyncRecord.getStorageAccountName(), AZURE_STORAGE_SUFFIX);
      String destStorageAccountUrl = String.format(
          AZURE_STORAGE_URL_FORMAT, azureStorageSyncConfig.getAzureStorageAccountName(), AZURE_STORAGE_SUFFIX);
      if (azureStorageSyncRecord.getReportName() != null && isNotEmpty(azureStorageSyncRecord.getReportName())) {
        sourcePath = String.join("/", sourceStorageAccountUrl, azureStorageSyncRecord.getContainerName(),
            azureStorageSyncRecord.getDirectoryName(), azureStorageSyncRecord.getReportName());
        destinationPath = String.join("/", destStorageAccountUrl, azureStorageSyncConfig.getAzureStorageContainerName(),
            azureStorageSyncRecord.getAccountId(), azureStorageSyncRecord.getSettingId(),
            azureStorageSyncRecord.getTenantId(), azureStorageSyncRecord.getReportName());
      } else {
        sourcePath = String.join("/", sourceStorageAccountUrl, azureStorageSyncRecord.getContainerName(),
            azureStorageSyncRecord.getDirectoryName());
        destinationPath = String.join("/", destStorageAccountUrl, azureStorageSyncConfig.getAzureStorageContainerName(),
            azureStorageSyncRecord.getAccountId(), azureStorageSyncRecord.getSettingId(),
            azureStorageSyncRecord.getTenantId());
      }
      sourcePathWithToken = sourcePath + "?" + sourceSasToken;
      destinationPathWithToken = destinationPath + "?" + destinationSasToken;
      log.info("azcopy sync source {}, destination {}", sourcePath, destinationPath);
      final ArrayList<String> cmd =
          Lists.newArrayList("azcopy", "sync", sourcePathWithToken, destinationPathWithToken, "--recursive");
      trySyncStorage(cmd);
    } catch (InvalidExitValueException e) {
      log.error("output: {}", e.getResult().outputUTF8(), e);
      return false;
    } catch (JsonSyntaxException | InterruptedException | TimeoutException | IOException e) {
      log.error(e.getMessage(), e);
      return false;
    }
    return true;
  }

  private String genSasToken(String storageAccountName, String containerName, String tenantId, String azureAppClientId,
      String azureAppClientSecret, boolean isHarnessAccount) {
    BlobContainerSasPermission blobContainerSasPermission =
        new BlobContainerSasPermission().setReadPermission(true).setListPermission(true);
    if (isHarnessAccount) {
      blobContainerSasPermission.setCreatePermission(true)
          .setAddPermission(true)
          .setWritePermission(true)
          .setExecutePermission(true);
    }
    BlobServiceSasSignatureValues builder =
        new BlobServiceSasSignatureValues(OffsetDateTime.now().plusHours(1), blobContainerSasPermission)
            .setProtocol(SasProtocol.HTTPS_ONLY);
    // Create a BlobServiceClient object which will be used to create a container client
    String endpoint = String.format(AZURE_STORAGE_URL_FORMAT, storageAccountName, AZURE_STORAGE_SUFFIX);
    log.info(endpoint);
    ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                                                        .clientId(azureAppClientId)
                                                        .clientSecret(azureAppClientSecret)
                                                        .tenantId(tenantId)
                                                        .build();

    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(endpoint).credential(clientSecretCredential).buildClient();
    BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
    // Get a user delegation key for the Blob service that's valid for one hour.
    // You can use the key to generate any number of shared access signatures over the lifetime of the key.
    OffsetDateTime keyStart = OffsetDateTime.now();
    OffsetDateTime keyExpiry = OffsetDateTime.now().plusHours(1);
    UserDelegationKey userDelegationKey = blobServiceClient.getUserDelegationKey(keyStart, keyExpiry);

    return blobContainerClient.generateUserDelegationSas(builder, userDelegationKey);
  }

  public void trySyncStorage(ArrayList<String> cmd) throws InterruptedException, TimeoutException, IOException {
    log.info("Running the azcopy sync command {}...", String.join(" ", cmd));
    getProcessExecutor()
        .command(cmd)
        .timeout(SYNC_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .redirectOutput(Slf4jStream.of(log).asInfo())
        .exitValue(0)
        .readOutput(true)
        .execute();
    log.info("azcopy sync completed");
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }
}
