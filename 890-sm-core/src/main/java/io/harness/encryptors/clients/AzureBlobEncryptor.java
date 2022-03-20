/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.AZURE_BLOB_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.AZURE_KEY_VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.encryptors.VaultEncryptor;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.azure.AzureBlobADALAuthenticator;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AzureBlobConfig;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.keyvault.core.IKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobEncryptionPolicy;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.rest.RestException;
import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.Duration;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class AzureBlobEncryptor implements VaultEncryptor {
  private final TimeLimiter timeLimiter;
  private final int NUM_OF_RETRIES = 3;

  @Inject
  public AzureBlobEncryptor(TimeLimiter timeLimiter) {
    this.timeLimiter = timeLimiter;
  }

  @Override
  public EncryptedRecord createSecret(
      String accountId, String name, String plaintext, EncryptionConfig encryptionConfig) {
    AzureBlobConfig azureConfig = (AzureBlobConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(
            timeLimiter, Duration.ofSeconds(15), () -> upsertInternal(accountId, name, plaintext, null, azureConfig));
      } catch (Exception e) {
        failedAttempts++;
        log.warn("encryption failed. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "After " + NUM_OF_RETRIES + " tries, encryption for secret " + name + " failed.";
          if (e instanceof RestException) {
            throw(RestException) e;
          } else {
            throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, e, USER);
          }
        }
        sleep(ofMillis(1000));
      }
    }
  }

  @Override
  public EncryptedRecord updateSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    return null;
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    return null;
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    return false;
  }

  @Override
  public boolean validateReference(String accountId, String path, EncryptionConfig encryptionConfig) {
    return true;
  }

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    return true;
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return new char[0];
  }

  private EncryptedRecord upsertInternal(String accountId, String fullSecretName, String plaintext,
      EncryptedRecord existingRecord, AzureBlobConfig azureBlobConfig)
      throws URISyntaxException, InvalidKeyException, StorageException {
    log.info("Saving secret '{}' into Azure Blob Secrets Manager: {}", fullSecretName, azureBlobConfig.getName());
    long startTime = System.currentTimeMillis();
    CloudBlockBlob azureBlobClient = getAzureBlobClient(azureBlobConfig, fullSecretName);
    try {
      KeyVaultKeyResolver keyResolver = AzureBlobADALAuthenticator.getKeyResolverClient(
          azureBlobConfig.getClientId(), azureBlobConfig.getSecretKey());
      IKey key = keyResolver.resolveKeyAsync(azureBlobConfig.getKeyId()).get();
      BlobEncryptionPolicy policy = new BlobEncryptionPolicy(key, null);
      BlobRequestOptions options = new BlobRequestOptions();
      options.setEncryptionPolicy(policy);

      azureBlobClient.upload(new ByteArrayInputStream(plaintext.getBytes(StandardCharsets.UTF_8)), plaintext.length(),
          null, options, null);
    } catch (Exception ex) {
      String message = format(
          "The Secret could not be saved in Azure Blob. accountId: %s, Secret name: %s", accountId, fullSecretName);
      throw new SecretManagementDelegateException(AZURE_BLOB_OPERATION_ERROR, message, ex, USER);
    }
    if (existingRecord != null && !existingRecord.getEncryptionKey().equals(fullSecretName)) {
      deleteSecret(accountId, existingRecord, azureBlobConfig);
    }
    log.info("Done saving secret {} into Azure Blob Secrets Manager for {} in {} ms", fullSecretName,
        azureBlobConfig.getName(), System.currentTimeMillis() - startTime);

    EncryptedRecordData newRecord = EncryptedRecordData.builder()
                                        .encryptedValue(fullSecretName.toCharArray())
                                        .encryptionKey(fullSecretName)
                                        .build();
    return newRecord;
  }

  private CloudBlockBlob getAzureBlobClient(AzureBlobConfig azureBlobConfig, String blobName)
      throws URISyntaxException, InvalidKeyException, StorageException {
    return AzureBlobADALAuthenticator.getBlobClient(
        azureBlobConfig.getConnectionString(), azureBlobConfig.getContainerName(), blobName);
  }
}
