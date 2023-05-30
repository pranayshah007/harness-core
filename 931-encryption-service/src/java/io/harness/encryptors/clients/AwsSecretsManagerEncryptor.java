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
import static io.harness.eraro.ErrorCode.AWS_SECRETS_MANAGER_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.helpers.ext.vault.VaultRestClientFactory.getFullPath;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.VaultEncryptor;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptedRecordData.EncryptedRecordDataBuilder;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AwsSecretsManagerConfig;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.CreateSecretResult;
import com.amazonaws.services.secretsmanager.model.DeleteSecretRequest;
import com.amazonaws.services.secretsmanager.model.DeleteSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest;
import com.amazonaws.services.secretsmanager.model.UpdateSecretResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class AwsSecretsManagerEncryptor implements VaultEncryptor {
  private final TimeLimiter timeLimiter;
  private static final int NUM_OF_RETRIES = 3;
  private static final String KEY_SEPARATOR = "#";
  private static final JsonParser JSON_PARSER = new JsonParser();
  private static final String AWS_SECRETS_MANAGER_VALIDATION_URL = "aws_secrets_manager_validation";
  private static final String AWS_EXCEPTION_ERROR =
      "AWS_SECRETS_MANAGER_OPERATION_ERROR : validateSecretManagerConfiguration {} of secret with name {} and id {}";
  private static final String ENCRYPTION_FAILED = "encryption failed. trial num: {}";
  private static final String SECRET_MANAGEMENT_EXCEPTION =
      "Secret Management Delegate Exception : Secret with name {} and id {} ";
  private static final String FAILED_AFTER_RETRIES = "failed after {} retries.";
  private static final int WAITING_TIME = 15;
  private static final int SLEEP_DURATION = 1000;
  private static class ParsedSecretRef {
    String secretPath;
    String keyName;
  }

  @Inject
  public AwsSecretsManagerEncryptor(TimeLimiter timeLimiter) {
    this.timeLimiter = timeLimiter;
  }

  public void errorHandlingLogicAWS(
      EncryptionConfig encryptionConfig, Exception e, int failedAttempts, String customessage) {
    log.warn(ENCRYPTION_FAILED, failedAttempts, e);
    if (failedAttempts == NUM_OF_RETRIES) {
      if (e instanceof AWSSecretsManagerException) {
        log.error(AWS_EXCEPTION_ERROR, ((AWSSecretsManagerException) e).getErrorMessage(), encryptionConfig.getName(),
            encryptionConfig.getUuid());
        throw(AWSSecretsManagerException) e;
      } else if (e instanceof UncheckedTimeoutException) {
        throw timeoutException(e);
      } else {
        String message = String.format("Secret with name [%s] %sfailed after %d retries. %s",
            encryptionConfig.getName(), customessage, NUM_OF_RETRIES, e.getMessage());
        log.error(SECRET_MANAGEMENT_EXCEPTION + customessage + FAILED_AFTER_RETRIES, encryptionConfig.getName(),
            encryptionConfig.getUuid(), NUM_OF_RETRIES);
        throw new SecretManagementDelegateException(AWS_SECRETS_MANAGER_OPERATION_ERROR, message, e, USER);
      }
    }
    sleep(ofMillis(SLEEP_DURATION));
  }

  @Override
  public EncryptedRecord createSecret(
      String accountId, String name, String plaintext, EncryptionConfig encryptionConfig) {
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(WAITING_TIME),
            () -> upsertSecretInternal(name, plaintext, null, awsSecretsManagerConfig));
      } catch (Exception e) {
        failedAttempts++;
        errorHandlingLogicAWS(encryptionConfig, e, failedAttempts, "creation ");
      }
    }
  }

  @Override
  public EncryptedRecord updateSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(WAITING_TIME),
            () -> upsertSecretInternal(name, plaintext, existingRecord, awsSecretsManagerConfig));
      } catch (Exception e) {
        failedAttempts++;
        errorHandlingLogicAWS(encryptionConfig, e, failedAttempts, "update ");
      }
    }
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(WAITING_TIME),
            () -> renameSecretInternal(name, existingRecord, awsSecretsManagerConfig));
      } catch (Exception e) {
        failedAttempts++;
        errorHandlingLogicAWS(encryptionConfig, e, failedAttempts, "update ");
      }
    }
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    long startTime = System.currentTimeMillis();
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;

    AWSSecretsManager client = getAwsSecretsManagerClient(awsSecretsManagerConfig);
    DeleteSecretRequest request =
        new DeleteSecretRequest().withSecretId(existingRecord.getEncryptionKey()).withForceDeleteWithoutRecovery(true);
    DeleteSecretResult result = client.deleteSecret(request);

    log.info("Done deleting AWS secret {} in {}ms", existingRecord.getEncryptionKey(),
        System.currentTimeMillis() - startTime);
    return result != null;
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
    AwsSecretsManagerConfig awsSecretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(WAITING_TIME),
            () -> fetchSecretValueInternal(encryptedRecord, awsSecretsManagerConfig));
      } catch (Exception e) {
        failedAttempts++;
        log.warn(ENCRYPTION_FAILED, failedAttempts, e);
        errorHandlingLogicAWS(encryptionConfig, e, failedAttempts, "fetching ");
      }
    }
  }

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    AwsSecretsManagerConfig secretsManagerConfig = (AwsSecretsManagerConfig) encryptionConfig;
    try {
      log.info("Validating AWS SecretManager configuration Start: {}", secretsManagerConfig);
      AWSSecretsManager client = getAwsSecretsManagerClient(secretsManagerConfig);
      GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(getFullPath(
          secretsManagerConfig.getSecretNamePrefix(), AWS_SECRETS_MANAGER_VALIDATION_URL + System.currentTimeMillis()));
      client.getSecretValue(request);
    } catch (ResourceNotFoundException e) {
      log.info("Resource Not Found Exception" + e);
      // this exception is expected. It means the credentials are correct, but can't find the resource
      // which means the connectivity to AWS Secrets Manger is ok.
    } catch (AWSSecretsManagerException e) {
      log.error(AWS_EXCEPTION_ERROR, e.getErrorMessage(), encryptionConfig.getName(), encryptionConfig.getUuid());
      throw e;
    } catch (UncheckedTimeoutException e) {
      throw timeoutException(e);
    }
    log.info("Test connection to AWS Secrets Manager Succeeded for {}", secretsManagerConfig.getName());
    return true;
  }

  private EncryptedRecord renameSecretInternal(
      String name, EncryptedRecord existingRecord, AwsSecretsManagerConfig secretsManagerConfig) {
    char[] value = fetchSecretValueInternal(existingRecord, secretsManagerConfig);
    if (isEmpty(value)) {
      String message = "Empty value fetched when trying to rename the secret " + existingRecord.getName();
      throw new SecretManagementDelegateException(AWS_SECRETS_MANAGER_OPERATION_ERROR, message, USER);
    }
    return upsertSecretInternal(name, new String(value), existingRecord, secretsManagerConfig);
  }

  private EncryptedRecord upsertSecretInternal(
      String name, String value, EncryptedRecord existingSecret, AwsSecretsManagerConfig secretsManagerConfig) {
    final String fullSecretName = getFullPath(secretsManagerConfig.getSecretNamePrefix(), name);
    long startTime = System.currentTimeMillis();
    log.info("Saving secret '{}' into AWS Secrets Manager: {}", fullSecretName, secretsManagerConfig.getName());
    AWSSecretsManager client = getAwsSecretsManagerClient(secretsManagerConfig);

    boolean secretExists = false;
    try {
      secretExists = isNotEmpty(
          fetchSecretValueInternal(EncryptedRecordData.builder().path(fullSecretName).build(), secretsManagerConfig));
    } catch (ResourceNotFoundException e) {
      log.info("Resource Not Found Exception: Resource Doesn't exist" + e);
      // If reaching here, it means the resource doesn't exist.
    }
    EncryptedRecordDataBuilder encryptedRecordDataBuilder = EncryptedRecordData.builder();
    if (!secretExists) {
      // Create the secret with proper tags.
      CreateSecretRequest request = new CreateSecretRequest()
                                        .withName(fullSecretName)
                                        .withSecretString(value)
                                        .withTags(new Tag().withKey("createdBy").withValue("Harness"));
      CreateSecretResult createSecretResult = client.createSecret(request);
      encryptedRecordDataBuilder.encryptionKey(fullSecretName)
          .encryptedValue(createSecretResult.getARN().toCharArray());
    } else {
      // Update the existing secret with new secret value.
      UpdateSecretRequest request = new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(value);
      UpdateSecretResult updateSecretResult = client.updateSecret(request);
      encryptedRecordDataBuilder.encryptionKey(fullSecretName)
          .encryptedValue(updateSecretResult.getARN().toCharArray());
    }
    if (existingSecret != null) {
      final String oldFullSecretName = existingSecret.getEncryptionKey();
      if (!oldFullSecretName.equals(fullSecretName)) {
        log.info("Old path of the secret {} is different than the current one {}. Deleting the old secret",
            oldFullSecretName, fullSecretName);
        deleteSecret(secretsManagerConfig.getAccountId(), existingSecret, secretsManagerConfig);
      }
    }
    log.info("Done saving secret {} into AWS Secrets Manager in {} ms", fullSecretName,
        System.currentTimeMillis() - startTime);
    return encryptedRecordDataBuilder.build();
  }

  private char[] fetchSecretValueInternal(EncryptedRecord data, AwsSecretsManagerConfig secretsManagerConfig) {
    long startTime = System.currentTimeMillis();

    final String secretName;
    String refKeyName = null;
    if (StringUtils.isNotBlank(data.getPath())) {
      String path = data.getPath();
      ParsedSecretRef secretRef = parsedSecretRef(path);
      secretName = secretRef.secretPath;
      refKeyName = secretRef.keyName;
    } else {
      secretName = data.getEncryptionKey();
    }

    AWSSecretsManager client = getAwsSecretsManagerClient(secretsManagerConfig);
    GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretName);
    GetSecretValueResult result = client.getSecretValue(request);
    String secretValue = result.getSecretString();

    char[] decryptedValue = null;
    if (StringUtils.isNotBlank(refKeyName)) {
      JsonElement element = JSON_PARSER.parse(secretValue);
      if (element.getAsJsonObject().has(refKeyName)) {
        JsonElement refKeyedElement = element.getAsJsonObject().get(refKeyName);
        decryptedValue = refKeyedElement.getAsString().toCharArray();
      }
    } else {
      decryptedValue = secretValue.toCharArray();
    }

    log.info("Done decrypting AWS secret {} in {}ms", secretName, System.currentTimeMillis() - startTime);
    return decryptedValue;
  }

  @VisibleForTesting
  public AWSSecretsManager getAwsSecretsManagerClient(AwsSecretsManagerConfig secretsManagerConfig) {
    return AWSSecretsManagerClientBuilder.standard()
        .withCredentials(getAwsCredentialsProvider(secretsManagerConfig))
        .withRegion(secretsManagerConfig.getRegion() == null ? Regions.US_EAST_1
                                                             : Regions.fromName(secretsManagerConfig.getRegion()))
        .build();
  }

  public AWSCredentialsProvider getAwsCredentialsProvider(AwsSecretsManagerConfig secretsManagerConfig) {
    if (secretsManagerConfig.isAssumeIamRoleOnDelegate()) {
      log.info("Assuming IAM role on delegate : Instantiating DefaultCredentialProviderChain to resolve credential"
          + secretsManagerConfig);
      try {
        return new DefaultAWSCredentialsProviderChain();
      } catch (SdkClientException exception) {
        log.info("SDK Client Exception " + exception.getMessage());
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, exception.getMessage(), USER_SRE);
      }
    } else if (secretsManagerConfig.isAssumeStsRoleOnDelegate()) {
      log.info("Assuming STS role on delegate : Instantiating STSAssumeRoleSessionCredentialsProvider with config:"
          + secretsManagerConfig);
      if (StringUtils.isBlank(secretsManagerConfig.getRoleArn())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide RoleARN if AssumeStsRole is selected", USER);
      }
      STSAssumeRoleSessionCredentialsProvider.Builder sessionCredentialsProviderBuilder =
          new STSAssumeRoleSessionCredentialsProvider.Builder(
              secretsManagerConfig.getRoleArn(), UUIDGenerator.generateUuid());
      if (secretsManagerConfig.getAssumeStsRoleDuration() > 0) {
        sessionCredentialsProviderBuilder.withRoleSessionDurationSeconds(
            secretsManagerConfig.getAssumeStsRoleDuration());
      }
      sessionCredentialsProviderBuilder.withExternalId(secretsManagerConfig.getExternalName());
      return sessionCredentialsProviderBuilder.build();
    } else {
      if (StringUtils.isBlank(secretsManagerConfig.getAccessKey())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide an AccessKey if AssumeIAMRole is not enabled", USER);
      }
      if (StringUtils.isBlank(secretsManagerConfig.getSecretKey())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide a SecretKey if AssumeIAMRole is not enabled", USER);
      }
      log.warn("Using Secret and Access Key (Deprecated): Instantiating AWSStaticCredentialsProvider with config:"
          + secretsManagerConfig);
      return new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(secretsManagerConfig.getAccessKey(), secretsManagerConfig.getSecretKey()));
    }
  }

  private ParsedSecretRef parsedSecretRef(String path) {
    String[] parts = path.split(KEY_SEPARATOR);
    ParsedSecretRef secretRef = new ParsedSecretRef();
    secretRef.secretPath = parts[0];
    if (parts.length > 1) {
      secretRef.keyName = parts[1];
    }
    return secretRef;
  }

  private SecretManagementDelegateException timeoutException(Exception e) {
    String message =
        "After " + NUM_OF_RETRIES + " tries, delegate(s) is not able to establish connection to AWS services.";
    return new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
  }
}