/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.KmsConfig;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import com.amazonaws.services.kms.model.KeyUnavailableException;
import com.google.common.util.concurrent.TimeLimiter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsKmsEncryptorTest extends CategoryTest {
  private final SecureRandom secureRandom = new SecureRandom();
  private AwsKmsEncryptor awsKmsEncryptor;
  private KmsConfig kmsConfig;
  private AWSKMS awskms;

  @Before
  public void setup() {
    TimeLimiter timeLimiter = HTimeLimiter.create();
    awsKmsEncryptor = spy(new AwsKmsEncryptor(timeLimiter));
    kmsConfig = KmsConfig.builder()
                    .uuid(UUIDGenerator.generateUuid())
                    .name(UUIDGenerator.generateUuid())
                    .encryptionType(EncryptionType.KMS)
                    .accountId(UUIDGenerator.generateUuid())
                    .region("us-east-1")
                    .accessKey(UUIDGenerator.generateUuid())
                    .kmsArn(UUIDGenerator.generateUuid())
                    .secretKey(UUIDGenerator.generateUuid())
                    .isDefault(false)
                    .build();
    awskms = mock(AWSKMS.class);
    when(awsKmsEncryptor.getKmsClient(kmsConfig)).thenReturn(awskms);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptDecryptSecret_withRetry() {
    String value = UUIDGenerator.generateUuid();
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);

    GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest();
    dataKeyRequest.setKeyId(kmsConfig.getKmsArn());
    dataKeyRequest.setKeySpec("AES_128");
    GenerateDataKeyResult generateDataKeyResult = mock(GenerateDataKeyResult.class);
    when(generateDataKeyResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(generateDataKeyResult.getCiphertextBlob()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.generateDataKey(dataKeyRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(generateDataKeyResult);

    EncryptedRecord encryptedRecord = awsKmsEncryptor.encryptSecret(UUIDGenerator.generateUuid(), value, kmsConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(new String(bytes, StandardCharsets.ISO_8859_1));
    EncryptedRecordData testRecord = EncryptedRecordData.builder()
                                         .uuid(UUIDGenerator.generateUuid())
                                         .encryptionKey(encryptedRecord.getEncryptionKey())
                                         .encryptedValue(encryptedRecord.getEncryptedValue())
                                         .build();
    DecryptRequest decryptRequest =
        new DecryptRequest().withCiphertextBlob(StandardCharsets.ISO_8859_1.encode(testRecord.getEncryptionKey()));
    DecryptResult decryptResult = mock(DecryptResult.class);
    when(decryptResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.decrypt(decryptRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(decryptResult);
    char[] returnedValue = awsKmsEncryptor.fetchSecretValue(UUIDGenerator.generateUuid(), testRecord, kmsConfig);
    assertThat(returnedValue).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testEncryptDecryptSecret_withRetry_AssumeIAMRole() {
    kmsConfig.setSecretKey(null);
    kmsConfig.setAccessKey(null);
    kmsConfig.setAssumeIamRoleOnDelegate(true);

    String value = UUIDGenerator.generateUuid();
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);

    GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest();
    dataKeyRequest.setKeyId(kmsConfig.getKmsArn());
    dataKeyRequest.setKeySpec("AES_128");
    GenerateDataKeyResult generateDataKeyResult = mock(GenerateDataKeyResult.class);
    when(generateDataKeyResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(generateDataKeyResult.getCiphertextBlob()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.generateDataKey(dataKeyRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(generateDataKeyResult);

    EncryptedRecord encryptedRecord = awsKmsEncryptor.encryptSecret(UUIDGenerator.generateUuid(), value, kmsConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(new String(bytes, StandardCharsets.ISO_8859_1));
    EncryptedRecordData testRecord = EncryptedRecordData.builder()
                                         .uuid(UUIDGenerator.generateUuid())
                                         .encryptionKey(encryptedRecord.getEncryptionKey())
                                         .encryptedValue(encryptedRecord.getEncryptedValue())
                                         .build();
    DecryptRequest decryptRequest =
        new DecryptRequest().withCiphertextBlob(StandardCharsets.ISO_8859_1.encode(testRecord.getEncryptionKey()));
    DecryptResult decryptResult = mock(DecryptResult.class);
    when(decryptResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.decrypt(decryptRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(decryptResult);
    char[] returnedValue = awsKmsEncryptor.fetchSecretValue(UUIDGenerator.generateUuid(), testRecord, kmsConfig);
    assertThat(returnedValue).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testEncryptDecryptSecret_withRetry_AssumeSTSRole() {
    kmsConfig.setSecretKey(null);
    kmsConfig.setAccessKey(null);
    kmsConfig.setAssumeStsRoleOnDelegate(true);

    String value = UUIDGenerator.generateUuid();
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);

    GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest();
    dataKeyRequest.setKeyId(kmsConfig.getKmsArn());
    dataKeyRequest.setKeySpec("AES_128");
    GenerateDataKeyResult generateDataKeyResult = mock(GenerateDataKeyResult.class);
    when(generateDataKeyResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(generateDataKeyResult.getCiphertextBlob()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.generateDataKey(dataKeyRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(generateDataKeyResult);

    EncryptedRecord encryptedRecord = awsKmsEncryptor.encryptSecret(UUIDGenerator.generateUuid(), value, kmsConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(new String(bytes, StandardCharsets.ISO_8859_1));
    EncryptedRecordData testRecord = EncryptedRecordData.builder()
                                         .uuid(UUIDGenerator.generateUuid())
                                         .encryptionKey(encryptedRecord.getEncryptionKey())
                                         .encryptedValue(encryptedRecord.getEncryptedValue())
                                         .build();
    DecryptRequest decryptRequest =
        new DecryptRequest().withCiphertextBlob(StandardCharsets.ISO_8859_1.encode(testRecord.getEncryptionKey()));
    DecryptResult decryptResult = mock(DecryptResult.class);
    when(decryptResult.getPlaintext()).thenReturn(ByteBuffer.wrap(bytes));
    when(awskms.decrypt(decryptRequest))
        .thenThrow(new KeyUnavailableException("Dummy error"))
        .thenReturn(decryptResult);
    char[] returnedValue = awsKmsEncryptor.fetchSecretValue(UUIDGenerator.generateUuid(), testRecord, kmsConfig);
    assertThat(returnedValue).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptSecret_shouldThrowException() {
    String value = UUIDGenerator.generateUuid();
    when(awskms.generateDataKey(any())).thenThrow(new KeyUnavailableException("Dummy error"));
    try {
      awsKmsEncryptor.encryptSecret(UUIDGenerator.generateUuid(), value, kmsConfig);
      fail("The test method should have thrown an exception");
    } catch (DelegateRetryableException e) {
      assertThat(e.getCause().getMessage()).contains("Encryption failed after 3 retries");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecretValue_shouldThrowException() {
    EncryptedRecordData testRecord = EncryptedRecordData.builder()
                                         .uuid(UUIDGenerator.generateUuid())
                                         .name(UUIDGenerator.generateUuid())
                                         .encryptionKey(UUIDGenerator.generateUuid())
                                         .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                         .build();
    when(awskms.decrypt(any())).thenThrow(new KeyUnavailableException("Dummy error"));
    try {
      awsKmsEncryptor.fetchSecretValue(UUIDGenerator.generateUuid(), testRecord, kmsConfig);
      fail("The test method should have thrown an exception");
    } catch (DelegateRetryableException e) {
      assertThat(e.getCause().getMessage())
          .contains(String.format("Decryption failed for encryptedData %s after 3 retries", testRecord.getName()));
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCredentialProviderTypeSelection_shouldPass() {
    System.setProperty("AWS_ACCESS_KEY_ID", "AKIAWQ5IKSASTHBLFKEU");
    System.setProperty("AWS_SECRET_ACCESS_KEY", "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");
    kmsConfig.setAssumeIamRoleOnDelegate(true);
    AWSCredentialsProvider provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
    assertThat(provider).isInstanceOf(DefaultAWSCredentialsProviderChain.class);

    kmsConfig.setAssumeIamRoleOnDelegate(false);
    kmsConfig.setAssumeStsRoleOnDelegate(true);
    kmsConfig.setRoleArn("arn:aws:iam::123456789012:user/JohnDoe");
    provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
    assertThat(provider).isInstanceOf(STSAssumeRoleSessionCredentialsProvider.class);

    kmsConfig.setAssumeStsRoleOnDelegate(false);
    kmsConfig.setAssumeStsRoleOnDelegate(false);
    kmsConfig.setRoleArn(null);
    provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
    assertThat(provider).isInstanceOf(AWSStaticCredentialsProvider.class);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCredentialProviderTypeSelection_shouldFail() {
    AWSCredentialsProvider provider = null;
    kmsConfig.setAssumeIamRoleOnDelegate(false);
    kmsConfig.setAssumeStsRoleOnDelegate(true);
    try {
      provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
      fail("AssumeSTSRole worked without RoleARN");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide RoleARN if AssumeStsRole is selected");
    }

    kmsConfig.setAssumeStsRoleOnDelegate(false);
    kmsConfig.setAssumeStsRoleOnDelegate(false);
    kmsConfig.setRoleArn(null);
    kmsConfig.setAccessKey(null);
    try {
      provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
      fail("AccessKey was null and AssumeRole was also not set");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide an AccessKey if AssumeIAMRole is not enabled");
    }

    kmsConfig.setAccessKey("AKIAWQ5IKSASTHBLFKEU");
    kmsConfig.setSecretKey(null);
    try {
      provider = awsKmsEncryptor.getAwsCredentialsProvider(kmsConfig);
      fail("AccessKey was null and AssumeRole was also not set");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide a SecretKey if AssumeIAMRole is not enabled");
    }
  }
}
