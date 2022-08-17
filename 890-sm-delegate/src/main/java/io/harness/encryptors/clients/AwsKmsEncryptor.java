/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.AWS_SECRETS_MANAGER_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.KMS_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.encryptors.KmsEncryptor;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.SecretManagementException;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.KmsConfig;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.AWSKMSException;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DependencyTimeoutException;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import com.amazonaws.services.kms.model.KMSInternalException;
import com.amazonaws.services.kms.model.KeyUnavailableException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.executable.ValidateOnExecution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class AwsKmsEncryptor implements KmsEncryptor {
  private static final int DEFAULT_KMS_TIMEOUT = 30; // in seconds
  private final int NUM_OF_RETRIES = 3;
  private final TimeLimiter timeLimiter;
  private final Cache<KmsEncryptionKeyCacheKey, byte[]> kmsEncryptionKeyCache =
      Caffeine.newBuilder().maximumSize(2000).expireAfterAccess(2, TimeUnit.HOURS).build();
  private static final String AWS_SECRETS_MANAGER_VALIDATION_URL = "aws_secrets_manager_validation";

  @Inject
  public AwsKmsEncryptor(TimeLimiter timeLimiter) {
    this.timeLimiter = timeLimiter;
  }

  @Override
  public EncryptedRecord encryptSecret(String accountId, String value, EncryptionConfig encryptionConfig) {
    KmsConfig kmsConfig = (KmsConfig) encryptionConfig;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(
            timeLimiter, Duration.ofSeconds(DEFAULT_KMS_TIMEOUT), () -> encryptInternal(accountId, value, kmsConfig));
      } catch (Exception e) {
        failedAttempts++;
        log.warn("Encryption failed. trial num: {}", failedAttempts, e);
        if (isRetryable(e)) {
          if (failedAttempts == NUM_OF_RETRIES) {
            String reason = format("Encryption failed after %d retries.%n%s", NUM_OF_RETRIES, e.getMessage());
            throw new DelegateRetryableException(
                new SecretManagementDelegateException(KMS_OPERATION_ERROR, reason, e, USER));
          }
          sleep(ofMillis(1000));
        } else {
          throw new SecretManagementDelegateException(KMS_OPERATION_ERROR, e.getMessage(), e, USER);
        }
      }
    }
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord data, EncryptionConfig encryptionConfig) {
    KmsConfig kmsConfig = (KmsConfig) encryptionConfig;
    if (data.getEncryptedValue() == null) {
      return null;
    }
    int failedAttempts = 0;
    while (true) {
      try {
        byte[] cachedEncryptedKey = getCachedEncryptedKey(data);
        if (isNotEmpty(cachedEncryptedKey)) {
          return decryptInternalIfCached(data, cachedEncryptedKey, System.currentTimeMillis());
        } else {
          // Use HTimeLimiter.callInterruptible only if the KMS plain text key is not cached.
          return HTimeLimiter.callInterruptible21(
              timeLimiter, Duration.ofSeconds(DEFAULT_KMS_TIMEOUT), () -> decryptInternal(data, kmsConfig));
        }
      } catch (Exception e) {
        failedAttempts++;
        log.warn("Decryption failed. trial num: {}", failedAttempts, e);
        if (isRetryable(e)) {
          if (failedAttempts == NUM_OF_RETRIES) {
            String reason =
                format("Decryption failed for encryptedData %s after %d retries", data.getName(), NUM_OF_RETRIES)
                + e.getMessage();
            throw new DelegateRetryableException(
                new SecretManagementDelegateException(KMS_OPERATION_ERROR, reason, e, USER));
          }
          sleep(ofMillis(1000));
        } else {
          throw new SecretManagementDelegateException(KMS_OPERATION_ERROR, e.getMessage(), e, USER);
        }
      }
    }
  }

  @Override
  public boolean validateKmsConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    log.info("Validating AWS KMS configuration Start {}", encryptionConfig.getName());
    GenerateDataKeyResult generateDataKeyResult = generateKmsKey((KmsConfig) encryptionConfig);
    boolean isValidConfiguration = generateDataKeyResult != null && !generateDataKeyResult.getKeyId().isEmpty();
    log.info("Validating AWS KMS configuration End {0} isValidConfiguration: {1}", encryptionConfig.getName(),
        isValidConfiguration);
    if (!isValidConfiguration) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "The data key could not be generated", USER);
    }
    return isValidConfiguration;
  }

  private GenerateDataKeyResult generateKmsKey(KmsConfig kmsConfig) {
    final AWSKMS kmsClient = getKmsClient(kmsConfig);

    try {
      GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest();
      dataKeyRequest.setKeyId(kmsConfig.getKmsArn());
      dataKeyRequest.setKeySpec("AES_128");
      return kmsClient.generateDataKey(dataKeyRequest);
    } finally {
      if (kmsClient != null) {
        // Shutdown the KMS client so as to prevent resource leaking,\
        kmsClient.shutdown();
      }
    }
  }

  public byte[] getPlainTextKeyFromKMS(KmsConfig kmsConfig, String encryptionKey) {
    AWSKMS kmsClient = null;
    try {
      kmsClient = getKmsClient(kmsConfig);

      DecryptRequest decryptRequest =
          new DecryptRequest().withCiphertextBlob(StandardCharsets.ISO_8859_1.encode(encryptionKey));
      ByteBuffer plainTextKey = kmsClient.decrypt(decryptRequest).getPlaintext();
      return getByteArray(plainTextKey);
    } finally {
      if (kmsClient != null) {
        // Shutdown the KMS client so as to prevent resource leaking,
        kmsClient.shutdown();
      }
    }
  }

  @VisibleForTesting
  public AWSKMS getKmsClient(KmsConfig kmsConfig) {
    return AWSKMSClientBuilder.standard()
        .withCredentials(getAwsCredentialsProvider(kmsConfig))
        .withRegion(kmsConfig.getRegion() == null ? Regions.US_EAST_1 : Regions.fromName(kmsConfig.getRegion()))
        .build();
  }

  public AWSCredentialsProvider getAwsCredentialsProvider(KmsConfig kmsConfig) {
    if (kmsConfig.isAssumeIamRoleOnDelegate()) {
      log.info("Assuming IAM role on delegate : Instantiating DefaultCredentialProviderChain to resolve credential"
          + kmsConfig);
      try {
        return new DefaultAWSCredentialsProviderChain();
      } catch (SdkClientException exception) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, exception.getMessage(), USER_SRE);
      }
    } else if (kmsConfig.isAssumeStsRoleOnDelegate()) {
      log.info("Assuming STS role on delegate : Instantiating STSAssumeRoleSessionCredentialsProvider with config:"
          + kmsConfig);
      if (StringUtils.isBlank(kmsConfig.getRoleArn())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide RoleARN if AssumeStsRole is selected", USER);
      }
      STSAssumeRoleSessionCredentialsProvider.Builder sessionCredentialsProviderBuilder =
          new STSAssumeRoleSessionCredentialsProvider.Builder(kmsConfig.getRoleArn(), UUIDGenerator.generateUuid());
      if (kmsConfig.getAssumeStsRoleDuration() > 0) {
        sessionCredentialsProviderBuilder.withRoleSessionDurationSeconds(kmsConfig.getAssumeStsRoleDuration());
      }
      sessionCredentialsProviderBuilder.withExternalId(kmsConfig.getExternalName());
      return sessionCredentialsProviderBuilder.build();
    } else {
      if (StringUtils.isBlank(kmsConfig.getAccessKey())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide an AccessKey if AssumeIAMRole is not enabled", USER);
      }
      if (StringUtils.isBlank(kmsConfig.getSecretKey())) {
        throw new SecretManagementDelegateException(
            AWS_SECRETS_MANAGER_OPERATION_ERROR, "You must provide a SecretKey if AssumeIAMRole is not enabled", USER);
      }
      log.warn("Using Secret and Access Key (Deprecated): Instantiating AWSStaticCredentialsProvider with config:"
          + kmsConfig);
      return new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(kmsConfig.getAccessKey(), kmsConfig.getSecretKey()));
    }
  }

  private EncryptedRecord encryptInternal(String accountId, String value, KmsConfig kmsConfig)
      throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
             NoSuchPaddingException {
    long startTime = System.currentTimeMillis();
    log.info("Encrypting one secret in account {} with KMS secret manager '{}'", accountId, kmsConfig.getName());

    GenerateDataKeyResult dataKeyResult = generateKmsKey(kmsConfig);

    ByteBuffer plainTextKey = dataKeyResult.getPlaintext();

    char[] encryptedValue = encrypt(value, new SecretKeySpec(getByteArray(plainTextKey), "AES"));
    String encryptedKeyString = StandardCharsets.ISO_8859_1.decode(dataKeyResult.getCiphertextBlob()).toString();

    log.info("Finished encrypting one secret in account {} with KMS secret manager '{}' in {} ms.", accountId,
        kmsConfig.getName(), System.currentTimeMillis() - startTime);
    return EncryptedRecordData.builder().encryptionKey(encryptedKeyString).encryptedValue(encryptedValue).build();
  }

  private char[] decryptInternal(EncryptedRecord data, KmsConfig kmsConfig)
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
             NoSuchPaddingException {
    long startTime = System.currentTimeMillis();
    log.info("Decrypting secret {} with KMS secret manager '{}'", data.getUuid(), kmsConfig.getName());
    KmsEncryptionKeyCacheKey cacheKey = new KmsEncryptionKeyCacheKey(data.getUuid(), data.getEncryptionKey());
    // HAR-9752: Caching KMS encryption key to plain text key mapping to reduce KMS decrypt call volume.
    byte[] encryptedPlainTextKey = kmsEncryptionKeyCache.get(cacheKey, key -> {
      byte[] plainTextKey = getPlainTextKeyFromKMS(kmsConfig, key.encryptionKey);
      // Encrypt plain text KMS key before caching it in memory.
      byte[] encryptedKey = encryptPlainTextKey(plainTextKey, key.uuid);

      log.info("Decrypted encryption key from KMS secret manager '{}' in {} ms.", kmsConfig.getName(),
          System.currentTimeMillis() - startTime);
      return encryptedKey;
    });

    return decryptInternalIfCached(data, encryptedPlainTextKey, startTime);
  }

  private byte[] getCachedEncryptedKey(EncryptedRecord data) {
    KmsEncryptionKeyCacheKey cacheKey = new KmsEncryptionKeyCacheKey(data.getUuid(), data.getEncryptionKey());
    return kmsEncryptionKeyCache.getIfPresent(cacheKey);
  }

  @VisibleForTesting
  char[] decryptInternalIfCached(EncryptedRecord data, byte[] encryptedPlainTextKey, long startTime)
      throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
             NoSuchPaddingException {
    KmsEncryptionKeyCacheKey cacheKey = new KmsEncryptionKeyCacheKey(data.getUuid(), data.getEncryptionKey());
    byte[] plainTextKey = decryptPlainTextKey(encryptedPlainTextKey, cacheKey.uuid);
    String decrypted = decrypt(data.getEncryptedValue(), new SecretKeySpec(plainTextKey, "AES"));

    log.info("Finished decrypting secret {} in {} ms.", data.getUuid(), System.currentTimeMillis() - startTime);
    return decrypted == null ? null : decrypted.toCharArray();
  }

  public static char[] encrypt(String src, Key key) throws NoSuchAlgorithmException, NoSuchPaddingException,
                                                           InvalidKeyException, IllegalBlockSizeException,
                                                           BadPaddingException {
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return encodeBase64(cipher.doFinal(src.getBytes(Charsets.UTF_8))).toCharArray();
  }

  public static String decrypt(char[] src, Key key) throws NoSuchAlgorithmException, NoSuchPaddingException,
                                                           InvalidKeyException, IllegalBlockSizeException,
                                                           BadPaddingException {
    if (src == null) {
      return null;
    }
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, key);
    return new String(cipher.doFinal(decodeBase64(src)), Charsets.UTF_8);
  }

  private byte[] encryptPlainTextKey(byte[] plainTextKey, String localEncryptionKey) {
    SimpleEncryption simpleEncryption = new SimpleEncryption(localEncryptionKey);
    return simpleEncryption.encrypt(plainTextKey);
  }

  @VisibleForTesting
  byte[] decryptPlainTextKey(byte[] encryptedPlainTextKey, String localEncryptionKey) {
    SimpleEncryption simpleEncryption = new SimpleEncryption(localEncryptionKey);
    return simpleEncryption.decrypt(encryptedPlainTextKey);
  }

  private byte[] getByteArray(ByteBuffer b) {
    byte[] byteArray = new byte[b.remaining()];
    b.get(byteArray);
    return byteArray;
  }

  @Data
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class KmsEncryptionKeyCacheKey {
    String uuid;
    String encryptionKey;
  }

  private static boolean isRetryable(Exception e) {
    // TimeLimiter.callWithTimer will throw a new exception wrapping around the AwsKMS exceptions. Unwrap it.
    Throwable t = e.getCause() == null ? e : e.getCause();

    if (t instanceof AWSKMSException) {
      log.info("Got AWSKMSException {}: {}", t.getClass().getName(), t.getMessage());
      return t instanceof KMSInternalException || t instanceof DependencyTimeoutException
          || t instanceof KeyUnavailableException;
    } else {
      // Else if not IllegalArgumentException, it should retry.
      return !(t instanceof IllegalArgumentException);
    }
  }
}
