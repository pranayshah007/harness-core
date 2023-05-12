/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.exception.HintException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.GcpKmsConfig;

import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@Slf4j
public class GcpKmsEncryptorTest extends CategoryTest {
  private GcpKmsEncryptor gcpKmsEncryptor;
  private GcpKmsConfig gcpKmsConfig;

  @Before
  public void setup() {
    TimeLimiter timeLimiter = HTimeLimiter.create();
    gcpKmsEncryptor = spy(new GcpKmsEncryptor(timeLimiter));
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    gcpKmsConfig = GcpKmsConfig.builder()
                       .name(UUIDGenerator.generateUuid())
                       .uuid(UUIDGenerator.generateUuid())
                       .accountId(UUIDGenerator.generateUuid())
                       .projectId(UUIDGenerator.generateUuid())
                       .region(UUIDGenerator.generateUuid())
                       .keyRing(UUIDGenerator.generateUuid())
                       .keyName(UUIDGenerator.generateUuid())
                       .encryptionType(EncryptionType.GCP_KMS)
                       .credentials(credentials)
                       .isDefault(false)
                       .build();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptDecrypt() {
    String encryptedDek = "encryptedDek";
    String plainTextValue = "value";

    KeyManagementServiceClient keyManagementServiceClient = mock(KeyManagementServiceClient.class);
    EncryptResponse encryptResponse =
        EncryptResponse.newBuilder()
            .setCiphertext(ByteString.copyFrom(encryptedDek.getBytes(StandardCharsets.ISO_8859_1)))
            .build();
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(keyManagementServiceClient).when(gcpKmsEncryptor).getClientInternal(any());

    // Encryption Test
    when(keyManagementServiceClient.encrypt(eq(resourceName), any(ByteString.class))).thenReturn(encryptResponse);
    EncryptedRecord encryptedRecord =
        gcpKmsEncryptor.encryptSecret(gcpKmsConfig.getAccountId(), plainTextValue, gcpKmsConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(encryptedDek);

    ArgumentCaptor<ByteString> captor = ArgumentCaptor.forClass(ByteString.class);
    verify(keyManagementServiceClient, times(1)).encrypt(eq(resourceName), captor.capture());
    ByteString plainTextDek = captor.getValue();

    // Decryption Test
    EncryptedRecordData encryptedData = EncryptedRecordData.builder()
                                            .uuid(UUIDGenerator.generateUuid())
                                            .encryptionKey(encryptedRecord.getEncryptionKey())
                                            .encryptedValue(encryptedRecord.getEncryptedValue())
                                            .build();
    DecryptResponse decryptResponse = DecryptResponse.newBuilder().setPlaintext(plainTextDek).build();
    when(keyManagementServiceClient.decrypt(eq(resourceName), any())).thenReturn(decryptResponse);
    char[] decryptedValue = gcpKmsEncryptor.fetchSecretValue(gcpKmsConfig.getAccountId(), encryptedData, gcpKmsConfig);
    assertThat(String.valueOf(decryptedValue)).isEqualTo(plainTextValue);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEncryptSecret_shouldThrowError() {
    String plainTextValue = "value";

    KeyManagementServiceClient keyManagementServiceClient = mock(KeyManagementServiceClient.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(keyManagementServiceClient).when(gcpKmsEncryptor).getClientInternal(any());

    // Encryption Test
    when(keyManagementServiceClient.encrypt(eq(resourceName), any(ByteString.class))).thenThrow(new RuntimeException());
    try {
      gcpKmsEncryptor.encryptSecret(gcpKmsConfig.getAccountId(), plainTextValue, gcpKmsConfig);
      fail("Method call should have thrown an exception");
    } catch (DelegateRetryableException e) {
      assertThat(e.getCause().getMessage()).isEqualTo("Encryption failed after 3 retries");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchValue_shouldThrowError() {
    KeyManagementServiceClient keyManagementServiceClient = mock(KeyManagementServiceClient.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(keyManagementServiceClient).when(gcpKmsEncryptor).getClientInternal(any());
    EncryptedRecordData encryptedData = EncryptedRecordData.builder()
                                            .uuid(UUIDGenerator.generateUuid())
                                            .name(UUIDGenerator.generateUuid())
                                            .encryptionKey(UUIDGenerator.generateUuid())
                                            .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                            .build();
    when(keyManagementServiceClient.decrypt(eq(resourceName), any())).thenThrow(new RuntimeException());
    try {
      gcpKmsEncryptor.fetchSecretValue(gcpKmsConfig.getAccountId(), encryptedData, gcpKmsConfig);
      fail("Method call should have thrown an exception");
    } catch (DelegateRetryableException e) {
      assertThat(e.getCause().getMessage())
          .isEqualTo(String.format("Decryption failed for encryptedData %s after 3 retries", encryptedData.getName()));
    }
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testEncryptSecretShouldThrowAuthenticationError() {
    KeyManagementServiceClient kmsClient = mock(KeyManagementServiceClient.class);
    StatusCode statusCode = mock(StatusCode.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(kmsClient).when(gcpKmsEncryptor).getClientInternal(any());

    when(statusCode.getCode()).thenReturn(StatusCode.Code.UNAUTHENTICATED);
    when(kmsClient.encrypt(eq(resourceName), any(ByteString.class)))
        .thenThrow(
            new UnauthenticatedException("Request had invalid authentication credentials.", null, statusCode, false));

    assertThatThrownBy(() -> gcpKmsEncryptor.encryptSecret(gcpKmsConfig.getAccountId(), "MySecretValue", gcpKmsConfig))
        .isInstanceOf(HintException.class)
        .hasMessageContaining(HintException.HINT_GCP_ACCESS_DENIED);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testEncryptSecretShouldThrowAuthenticationErrorWithRetry() {
    KeyManagementServiceClient kmsClient = mock(KeyManagementServiceClient.class);
    StatusCode statusCode = mock(StatusCode.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(kmsClient).when(gcpKmsEncryptor).getClientInternal(any());

    when(statusCode.getCode()).thenReturn(StatusCode.Code.UNAUTHENTICATED);
    when(kmsClient.encrypt(eq(resourceName), any(ByteString.class)))
        .thenThrow(
            new UnauthenticatedException("Request had invalid authentication credentials.", null, statusCode, true));

    assertThatThrownBy(() -> gcpKmsEncryptor.encryptSecret(gcpKmsConfig.getAccountId(), "MySecretValue", gcpKmsConfig))
        .isInstanceOf(HintException.class)
        .hasMessageContaining(HintException.HINT_GCP_ACCESS_DENIED);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testFetchSecretShouldThrowAuthenticationError() {
    KeyManagementServiceClient kmsClient = mock(KeyManagementServiceClient.class);
    StatusCode statusCode = mock(StatusCode.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(kmsClient).when(gcpKmsEncryptor).getClientInternal(any());
    EncryptedRecordData encryptedData = EncryptedRecordData.builder()
                                            .uuid(UUIDGenerator.generateUuid())
                                            .name(UUIDGenerator.generateUuid())
                                            .encryptionKey(UUIDGenerator.generateUuid())
                                            .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                            .build();

    when(statusCode.getCode()).thenReturn(StatusCode.Code.UNAUTHENTICATED);
    when(kmsClient.decrypt(eq(resourceName), any()))
        .thenThrow(
            new UnauthenticatedException("Request had invalid authentication credentials.", null, statusCode, false));

    assertThatThrownBy(() -> gcpKmsEncryptor.fetchSecretValue(gcpKmsConfig.getAccountId(), encryptedData, gcpKmsConfig))
        .isInstanceOf(HintException.class)
        .hasMessageContaining(HintException.HINT_GCP_ACCESS_DENIED);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testFetchSecretShouldThrowAuthenticationErrorWithRetry() {
    KeyManagementServiceClient kmsClient = mock(KeyManagementServiceClient.class);
    StatusCode statusCode = mock(StatusCode.class);
    String resourceName = CryptoKeyName.format(
        gcpKmsConfig.getProjectId(), gcpKmsConfig.getRegion(), gcpKmsConfig.getKeyRing(), gcpKmsConfig.getKeyName());
    doReturn(kmsClient).when(gcpKmsEncryptor).getClientInternal(any());
    EncryptedRecordData encryptedData = EncryptedRecordData.builder()
                                            .uuid(UUIDGenerator.generateUuid())
                                            .name(UUIDGenerator.generateUuid())
                                            .encryptionKey(UUIDGenerator.generateUuid())
                                            .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                            .build();

    when(statusCode.getCode()).thenReturn(StatusCode.Code.UNAUTHENTICATED);
    when(kmsClient.decrypt(eq(resourceName), any()))
        .thenThrow(
            new UnauthenticatedException("Request had invalid authentication credentials.", null, statusCode, true));

    assertThatThrownBy(() -> gcpKmsEncryptor.fetchSecretValue(gcpKmsConfig.getAccountId(), encryptedData, gcpKmsConfig))
        .isInstanceOf(HintException.class)
        .hasMessageContaining(HintException.HINT_GCP_ACCESS_DENIED);
  }
}
