/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.AZURE_BLOB_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.AZURE_KEY_VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.encryptors.VaultEncryptor;
import io.harness.exception.AzureKeyVaultOperationException;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.azure.AzureBlobAuthenticator;
import io.harness.helpers.ext.azure.AzureParsedSecretReference;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AzureBlobConfig;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.keyvault.core.IKey;
import com.microsoft.azure.keyvault.extensions.KeyVaultKeyResolver;
import com.microsoft.azure.storage.blob.BlobEncryptionPolicy;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.rest.RestException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    return upsertSecret(accountId, name, plaintext, null, encryptionConfig);
  }

  @Override
  public EncryptedRecord updateSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    return upsertSecret(accountId, name, plaintext, existingRecord, encryptionConfig);
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureBlobConfig azureConfig = (AzureBlobConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible(timeLimiter, Duration.ofSeconds(15),
            () -> renameSecretInternal(accountId, name, existingRecord, azureConfig));
      } catch (Exception e) {
        failedAttempts++;
        log.warn("encryption failed. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "After " + NUM_OF_RETRIES + " tries, encryption for secret " + name + " failed.";
          throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, e, USER);
        }
        sleep(ofMillis(1000));
      }
    }
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureBlobConfig azureBlobConfig = (AzureBlobConfig) encryptionConfig;
    CloudBlockBlob azureBlob = getAzureBlob(azureBlobConfig, existingRecord.getName());
    try {
      azureBlob.deleteIfExists();
      return true;
    } catch (Exception ex) {
      log.error("Failed to delete secret {} from Azure Blob: {}", existingRecord.getName(),
          azureBlobConfig.getContainerName(), ex);
      return false;
    }
  }

  @Override
  public boolean validateReference(String accountId, String path, EncryptionConfig encryptionConfig) {
    return isNotEmpty(fetchSecretValue(accountId, EncryptedRecordData.builder().path(path).build(), encryptionConfig));
  }

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    try {
      createSecret(accountId, AzureBlobConfig.AZURE_BLOB_VALIDATION_URL, Boolean.TRUE.toString(), encryptionConfig);
    } catch (Exception exception) {
      log.error("Validation for Secret Manager/KMS failed: " + encryptionConfig.getName());
      throw exception;
    }
    return true;
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    if (isEmpty(encryptedRecord.getEncryptionKey()) && isEmpty(encryptedRecord.getPath())) {
      return null;
    }
    AzureBlobConfig azureConfig = (AzureBlobConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        log.info("Trying to decrypt record {} by {}", encryptedRecord.getEncryptionKey(), azureConfig.getVaultName());
        return HTimeLimiter.callInterruptible(timeLimiter, Duration.ofSeconds(15),
            () -> fetchSecretValueInternal(encryptedRecord, azureConfig, encryptedRecord.getName()));
      } catch (Exception e) {
        failedAttempts++;
        log.warn("decryption failed. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          String message =
              "After " + NUM_OF_RETRIES + " tries, decryption for secret " + encryptedRecord.getName() + " failed.";
          throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, e, USER);
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private EncryptedRecord upsertSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    AzureBlobConfig azureConfig = (AzureBlobConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible(timeLimiter, Duration.ofSeconds(15),
            () -> upsertInternal(accountId, name, plaintext, existingRecord, azureConfig));
      } catch (Exception e) {
        failedAttempts++;
        log.warn("encryption failed. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "After " + NUM_OF_RETRIES + " tries, encryption for secret " + name + " failed.";
          if (e instanceof RestException) {
            throw(RestException) e;
          } else {
            throw new SecretManagementDelegateException(AZURE_BLOB_OPERATION_ERROR, message, e, USER);
          }
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private char[] fetchSecretValueInternal(
      EncryptedRecord data, AzureBlobConfig azureBlobConfig, String fullSecretName) {
    long startTime = System.currentTimeMillis();

    AzureParsedSecretReference parsedSecretReference = isNotEmpty(data.getPath())
        ? new AzureParsedSecretReference(data.getPath())
        : new AzureParsedSecretReference(data.getEncryptionKey());

    CloudBlockBlob azureBlob = getAzureBlob(azureBlobConfig, fullSecretName);
    try {
      KeyVaultKeyResolver keyResolver =
          AzureBlobAuthenticator.getKeyResolverClient(azureBlobConfig.getClientId(), azureBlobConfig.getSecretKey());
      String keyIdentifier = azureBlobConfig.getEncryptionServiceUrl() + "/keys/" + azureBlobConfig.getKeyName() + "/"
          + azureBlobConfig.getKeyId();
      IKey key = keyResolver.resolveKeyAsync(keyIdentifier).get();
      BlobEncryptionPolicy policy = new BlobEncryptionPolicy(key, null);
      BlobRequestOptions options = new BlobRequestOptions();
      options.setEncryptionPolicy(policy);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      azureBlob.download(outputStream, null, options, null);

      log.info("Done decrypting Azure Blob secret {} in {} ms", parsedSecretReference.getSecretName(),
          System.currentTimeMillis() - startTime);
      if (outputStream == null) {
        throw new AzureKeyVaultOperationException(
            "Received null value for " + parsedSecretReference.getSecretName(), AZURE_BLOB_OPERATION_ERROR, USER_SRE);
      }
      return outputStream.toString().toCharArray();
    } catch (Exception ex) {
      log.error("Failed to decrypt secret in azure blob due to exception", ex);
      String message = format("Failed to decrypt secret %s in Azure Blob %s in account %s due to error %s",
          parsedSecretReference.getSecretName(), azureBlobConfig.getName(), azureBlobConfig.getAccountId(),
          ex.getMessage());
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, USER);
    }
  }

  private EncryptedRecord renameSecretInternal(
      String accountId, String name, EncryptedRecord existingRecord, AzureBlobConfig azureConfig) {
    char[] value = fetchSecretValueInternal(existingRecord, azureConfig, name);
    return upsertInternal(accountId, name, new String(value), existingRecord, azureConfig);
  }

  private EncryptedRecord upsertInternal(String accountId, String fullSecretName, String plaintext,
      EncryptedRecord existingRecord, AzureBlobConfig azureBlobConfig) {
    log.info("Saving secret '{}' into Azure Blob Secrets Manager: {}", fullSecretName, azureBlobConfig.getName());
    long startTime = System.currentTimeMillis();
    CloudBlockBlob azureBlob = getAzureBlob(azureBlobConfig, fullSecretName);
    try {
      KeyVaultKeyResolver keyResolver =
          AzureBlobAuthenticator.getKeyResolverClient(azureBlobConfig.getClientId(), azureBlobConfig.getSecretKey());
      String keyId = azureBlobConfig.getEncryptionServiceUrl() + "/keys/" + azureBlobConfig.getKeyName() + "/"
          + azureBlobConfig.getKeyId();
      IKey key = keyResolver.resolveKeyAsync(keyId).get();
      BlobEncryptionPolicy policy = new BlobEncryptionPolicy(key, null);
      BlobRequestOptions options = new BlobRequestOptions();
      options.setEncryptionPolicy(policy);

      azureBlob.upload(new ByteArrayInputStream(plaintext.getBytes(StandardCharsets.UTF_8)), plaintext.length(), null,
          options, null);
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

    return EncryptedRecordData.builder()
        .encryptedValue(fullSecretName.toCharArray())
        .encryptionKey(fullSecretName)
        .build();
  }

  private CloudBlockBlob getAzureBlob(AzureBlobConfig azureBlobConfig, String blobName) {
    return AzureBlobAuthenticator.getBlobClient(
        azureBlobConfig.getConnectionString(), azureBlobConfig.getContainerName(), blobName);
  }
}
