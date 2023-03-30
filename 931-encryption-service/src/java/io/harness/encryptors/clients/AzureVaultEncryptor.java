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
import static io.harness.eraro.ErrorCode.AZURE_AUTHENTICATION_ERROR;
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
import io.harness.helpers.ext.azure.KeyVaultAuthenticator;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AzureVaultConfig;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.administration.implementation.models.KeyVaultErrorException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.aad.msal4j.MsalException;
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
        SecretClient keyVaultClient = getAzureVaultSecretsClient(azureConfig);
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
            () -> upsertInternal(accountId, name, plaintext, null, azureConfig, keyVaultClient));
      } catch (KeyVaultErrorException e) {
        // Key Vault Error Exception is non-retryable
        throw new SecretManagementDelegateException(
            AZURE_KEY_VAULT_OPERATION_ERROR, prepareKeyVaultErrorMessage(e, accountId, name), e, USER);
      } catch (MsalException e) {
        throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
      } catch (HttpResponseException e) {
        throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
      } catch (Exception e) {
        failedAttempts++;
        log.warn("encryption failed. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          String message =
              format("After %d tries, encryption for secret %s failed. %s", NUM_OF_RETRIES, name, e.getMessage());
          throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, e, USER);
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
        SecretClient keyVaultClient = getAzureVaultSecretsClient(azureConfig);
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
            () -> upsertInternal(accountId, name, plaintext, existingRecord, azureConfig, keyVaultClient));
      } catch (KeyVaultErrorException e) {
        // Key Vault Error Exception is non-retryable
        throw new SecretManagementDelegateException(
            AZURE_KEY_VAULT_OPERATION_ERROR, prepareKeyVaultErrorMessage(e, accountId, name), e, USER);
      } catch (MsalException e) {
        throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
      } catch (HttpResponseException e) {
        throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
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
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureVaultConfig azureConfig = (AzureVaultConfig) encryptionConfig;
    int failedAttempts = 0;

    while (true) {
      try {
        SecretClient keyVaultClient = getAzureVaultSecretsClient(azureConfig);
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
            () -> renameSecretInternal(accountId, name, existingRecord, azureConfig, keyVaultClient));
      } catch (KeyVaultErrorException e) {
        // Key Vault Error Exception is non-retryable
        throw new SecretManagementDelegateException(
            AZURE_KEY_VAULT_OPERATION_ERROR, prepareKeyVaultErrorMessage(e, accountId, name), e, USER);
      } catch (MsalException e) {
        throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
      } catch (HttpResponseException e) {
        throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
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

  private EncryptedRecord renameSecretInternal(String accountId, String name, EncryptedRecord existingRecord,
      AzureVaultConfig azureConfig, SecretClient keyVaultClient) throws Exception {
    char[] value = fetchSecretValueInternal(existingRecord, azureConfig, keyVaultClient);
    return upsertInternal(accountId, name, new String(value), existingRecord, azureConfig, keyVaultClient);
  }

  private EncryptedRecord upsertInternal(String accountId, String fullSecretName, String plaintext,
      EncryptedRecord existingRecord, AzureVaultConfig azureVaultConfig, SecretClient azureVaultSecretsClient)
      throws Exception {
    log.info("Saving secret '{}' into Azure Secrets Manager: {}", fullSecretName, azureVaultConfig.getName());
    long startTime = System.currentTimeMillis();

    KeyVaultSecret keyVaultSecret = new KeyVaultSecret(fullSecretName, plaintext);
    keyVaultSecret.setProperties(new SecretProperties().setTags(getMetadata()));

    Response<KeyVaultSecret> response = azureVaultSecretsClient.setSecretWithResponse(keyVaultSecret, Context.NONE);
    if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
      throw new AzureKeyVaultOperationException(
          format("Saving Secret in Azure Vault has failed with response code [%d]", response.getStatusCode()),
          AZURE_KEY_VAULT_OPERATION_ERROR, USER_SRE);
    }

    EncryptedRecordData newRecord = EncryptedRecordData.builder()
                                        .encryptedValue(response.getValue().getId().toCharArray())
                                        .encryptionKey(fullSecretName)
                                        .build();

    try {
      if (existingRecord != null && !existingRecord.getEncryptionKey().equals(fullSecretName)) {
        deleteSecret(accountId, existingRecord, azureVaultConfig, azureVaultSecretsClient);
      }
    } catch (Exception e) {
      log.error("Delete secret failed in upsert secret call with the following error {}", e.getMessage());
    }
    log.info("Done saving secret {} into Azure Secrets Manager for {} in {} ms", fullSecretName,
        azureVaultConfig.getName(), System.currentTimeMillis() - startTime);
    return newRecord;
  }

  private String prepareKeyVaultErrorMessage(Exception e, String accountId, String fullSecretName) {
    KeyVaultErrorException keyVaultEx = (KeyVaultErrorException) e;
    String message = "Azure Key Vault exception received.";
    log.error(message + " accountId: %s, Secret name: %s, error: %s ", accountId, fullSecretName, e.toString());
    if (keyVaultEx.getValue() == null) {
      return format(message);
    }
    String errorMsg = keyVaultEx.getValue().getError().getMessage();
    String errorCode = keyVaultEx.getValue().getError().getCode();
    return format(message + " error code: %s, error message: %s ", errorCode, errorMsg);
  }

  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig,
      SecretClient keyVaultClient) {
    AzureVaultConfig azureVaultConfig = (AzureVaultConfig) encryptionConfig;
    try {
      // The deletion time can vary significantly. On some occasions can take over 90 seconds. Since we have not way of
      // controlling the deletion time our approach here is to initiate a deletion and verify that the secret is not
      // retrievable after that regardless when it actually gets deleted. Otherwise we would have to increase timeout on
      // DELETE_SECRET task which again would not guarantee that timeout will not occur simply because of Azure's
      // system as well as UI needs to be updated to support async reactive nature of this call.
      SyncPoller<DeletedSecret, Void> syncPoller = keyVaultClient.beginDeleteSecret(existingRecord.getName());
      syncPoller.waitUntil(Duration.ofSeconds(30), LongRunningOperationStatus.IN_PROGRESS);
      try {
        while (true) {
          // if the secret is unobtainable/unretrievable this will throw an exception
          keyVaultClient.getSecret(existingRecord.getName());
        }
      } catch (ResourceNotFoundException e) {
        log.info(e.getMessage());
        log.info("deletion of key {} in azure vault {} was successful.", existingRecord.getEncryptionKey(),
            azureVaultConfig.getVaultName());
        return true;
      }
    } catch (MsalException e) {
      throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
    } catch (HttpResponseException e) {
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
    } catch (Exception ex) {
      log.error("Failed to delete key {} from azure vault: {}", existingRecord.getEncryptionKey(),
          azureVaultConfig.getVaultName(), ex);
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, ex.toString(), ex, USER);
    }
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AzureVaultConfig azureVaultConfig = (AzureVaultConfig) encryptionConfig;
    try {
      return deleteSecret(accountId, existingRecord, encryptionConfig, getAzureVaultSecretsClient(azureVaultConfig));
    } catch (MsalException e) {
      throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
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
        SecretClient keyVaultClient = getAzureVaultSecretsClient(azureConfig);
        log.info("Trying to decrypt record {} by {}", encryptedRecord.getEncryptionKey(), azureConfig.getVaultName());
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(15),
            () -> fetchSecretValueInternal(encryptedRecord, azureConfig, keyVaultClient));
      } catch (KeyVaultErrorException e) {
        throw new SecretManagementDelegateException(
            AZURE_KEY_VAULT_OPERATION_ERROR, prepareKeyVaultErrorMessage(e, accountId, azureConfig.getName()), e, USER);
      } catch (MsalException e) {
        throw new SecretManagementDelegateException(AZURE_AUTHENTICATION_ERROR, e.getMessage(), e, USER);
      } catch (HttpResponseException e) {
        throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
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

  private char[] fetchSecretValueInternal(
      EncryptedRecord data, AzureVaultConfig azureVaultConfig, SecretClient azureVaultSecretsClient) {
    long startTime = System.currentTimeMillis();

    AzureParsedSecretReference parsedSecretReference = isNotEmpty(data.getPath())
        ? new AzureParsedSecretReference(data.getPath())
        : new AzureParsedSecretReference(data.getEncryptionKey());

    try {
      Response<KeyVaultSecret> response = azureVaultSecretsClient.getSecretWithResponse(
          parsedSecretReference.getSecretName(), parsedSecretReference.getSecretVersion(), Context.NONE);
      if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
        throw new AzureKeyVaultOperationException(
            format("Retrieving Secret from Azure Vault has failed with response code [%d]", response.getStatusCode()),
            AZURE_KEY_VAULT_OPERATION_ERROR, USER_SRE);
      }
      log.info("Done decrypting Azure secret {} in {} ms", parsedSecretReference.getSecretName(),
          System.currentTimeMillis() - startTime);
      if (response.getValue() == null || response.getValue().getValue() == null) {
        throw new AzureKeyVaultOperationException("Received null value for " + parsedSecretReference.getSecretName(),
            AZURE_KEY_VAULT_OPERATION_ERROR, USER_SRE);
      }
      return response.getValue().getValue().toCharArray();
    } catch (KeyVaultErrorException | MsalException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Failed to decrypt azure secret in vault due to exception", ex);
      String message = format("Failed to decrypt Azure secret %s in vault %s in account %s due to error %s",
          parsedSecretReference.getSecretName(), azureVaultConfig.getName(), azureVaultConfig.getAccountId(),
          ex.getMessage());
      throw new SecretManagementDelegateException(AZURE_KEY_VAULT_OPERATION_ERROR, message, USER);
    }
  }
  private SecretClient getAzureVaultSecretsClient(AzureVaultConfig azureVaultConfig) {
    return KeyVaultAuthenticator.getSecretsClient(azureVaultConfig.getVaultName(),
        KeyVaultAuthenticator.getAzureHttpPipeline(azureVaultConfig.getClientId(), azureVaultConfig.getSecretKey(),
            azureVaultConfig.getTenantId(), azureVaultConfig.getSubscription(),
            azureVaultConfig.getAzureEnvironmentType(), azureVaultConfig.getUseManagedIdentity(),
            azureVaultConfig.getAzureManagedIdentityType(), azureVaultConfig.getManagedClientId()),
        azureVaultConfig.getAzureEnvironmentType());
  }
}
