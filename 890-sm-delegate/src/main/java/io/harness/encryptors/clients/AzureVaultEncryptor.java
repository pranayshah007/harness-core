/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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
import io.harness.helpers.ext.azure.AzureParsedSecretReference;
import io.harness.helpers.ext.azure.KeyVaultADALAuthenticator;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AzureVaultConfig;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.models.SecretBundle;
import com.microsoft.azure.keyvault.requests.SetSecretRequest;
import com.microsoft.rest.RestException;
import java.time.Duration;
import java.util.HashMap;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class AzureVaultEncryptor implements VaultEncryptor {
  private final TimeLimiter timeLimiter;
  private final int NUM_OF_RETRIES = 3;

  @Inject
  public AzureVaultEncryptor(TimeLimiter timeLimiter) {
    this.timeLimiter = timeLimiter;
  }

  @Override
  public EncryptedRecord createSecret(
      String accountId, String name, String plaintext, EncryptionConfig encryptionConfig) {
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
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
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
            () -> upsertInternal(accountId, name, plaintext, existingRecord, azureConfig));
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
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
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

  private EncryptedRecord renameSecretInternal(
      String accountId, String name, EncryptedRecord existingRecord, AzureVaultConfig azureConfig) {
    char[] value = fetchSecretValueInternal(existingRecord, azureConfig);
    return upsertInternal(accountId, name, new String(value), existingRecord, azureConfig);
  }

  private EncryptedRecord upsertInternal(String accountId, String fullSecretName, String plaintext,
      EncryptedRecord existingRecord, AzureVaultConfig azureVaultConfig) {
    log.info("Saving secret '{}' into Azure Secrets Manager: {}", fullSecretName, azureVaultConfig.getName());
    long startTime = System.currentTimeMillis();
    KeyVaultClient azureVaultClient = getAzureVaultClient(azureVaultConfig);
    SetSecretRequest setSecretRequest =
        new SetSecretRequest.Builder(azureVaultConfig.getEncryptionServiceUrl(), fullSecretName, plaintext)
            .withTags(getMetadata())
            .build();

    SecretBundle secretBundle;
    try {
      secretBundle = azureVaultClient.setSecret(setSecretRequest);
    } catch (Exception ex) {
      String message =
          format("The Secret could not be saved in Azure Vault. accountId: %s, Secret name: %s, Vault name: %s",
              accountId, fullSecretName, azureVaultConfig.getVaultName());
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, ex, USER);
    }
    EncryptedRecordData newRecord = EncryptedRecordData.builder()
                                        .encryptedValue(secretBundle.id().toCharArray())
                                        .encryptionKey(fullSecretName)
                                        .build();
    if (existingRecord != null && !existingRecord.getEncryptionKey().equals(fullSecretName)) {
      deleteSecret(accountId, existingRecord, azureVaultConfig);
    }
    log.info("Done saving secret {} into Azure Secrets Manager for {} in {} ms", fullSecretName,
        azureVaultConfig.getName(), System.currentTimeMillis() - startTime);
    return newRecord;
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
    KeyVaultClient azureVaultClient = getAzureVaultClient(azureConfig);
    try {
      azureVaultClient.deleteSecret(azureConfig.getEncryptionServiceUrl(), existingRecord.getEncryptionKey());
      log.info("deletion of key {} in azure vault {} was successful.", existingRecord.getEncryptionKey(),
          azureConfig.getVaultName());
      return true;
    } catch (Exception ex) {
      log.error("Failed to delete key {} from azure vault: {}", existingRecord.getEncryptionKey(),
          azureConfig.getVaultName(), ex);
      return false;
    }
  }

  @Override
  public boolean validateReference(String accountId, String path, EncryptionConfig encryptionConfig) {
    return isNotEmpty(fetchSecretValue(accountId, EncryptedRecordData.builder().path(path).build(), encryptionConfig));
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    if (isEmpty(encryptedRecord.getEncryptionKey()) && isEmpty(encryptedRecord.getPath())) {
      return null;
    }
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        log.info("Trying to decrypt record {} by {}", encryptedRecord.getEncryptionKey(), azureConfig.getVaultName());
        return HTimeLimiter.callInterruptible21(
            timeLimiter, Duration.ofSeconds(15), () -> fetchSecretValueInternal(encryptedRecord, azureConfig));
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

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    try {
      createSecret(accountId, AzureVaultConfig.AZURE_VAULT_VALIDATION_URL, Boolean.TRUE.toString(), encryptionConfig);
    } catch (Exception exception) {
      log.error("Validation for Secret Manager/KMS failed: " + encryptionConfig.getName());
      throw exception;
    }
    return true;
  }

  private HashMap<String, String> getMetadata() {
    return new HashMap<String, String>() {
      { put("createdBy", "Harness"); }
    };
  }

  private char[] fetchSecretValueInternal(EncryptedRecord data, AzureVaultConfig azureConfig) {
    long startTime = System.currentTimeMillis();

    AzureParsedSecretReference parsedSecretReference = isNotEmpty(data.getPath())
        ? new AzureParsedSecretReference(data.getPath())
        : new AzureParsedSecretReference(data.getEncryptionKey());

    KeyVaultClient azureVaultClient = getAzureVaultClient(azureConfig);
    try {
      SecretBundle secret = azureVaultClient.getSecret(azureConfig.getEncryptionServiceUrl(),
          parsedSecretReference.getSecretName(), parsedSecretReference.getSecretVersion());

      log.info("Done decrypting Azure secret {} in {} ms", parsedSecretReference.getSecretName(),
          System.currentTimeMillis() - startTime);
      if (secret.value() == null) {
        throw new AzureKeyVaultOperationException("Received null value for " + parsedSecretReference.getSecretName(),
            AZURE_KEY_VAULT_OPERATION_ERROR, USER_SRE);
      }
      return secret.value().toCharArray();
    } catch (Exception ex) {
      log.error("Failed to decrypt azure secret in vault due to exception", ex);
      String message = format("Failed to decrypt Azure secret %s in vault %s in account %s due to error %s",
          parsedSecretReference.getSecretName(), azureConfig.getName(), azureConfig.getAccountId(), ex.getMessage());
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, USER);
    }
  }

  private KeyVaultClient getAzureVaultClient(AzureVaultConfig azureVaultConfig) {
    return KeyVaultADALAuthenticator.getClient(azureVaultConfig.getClientId(), azureVaultConfig.getSecretKey());
  }
}
