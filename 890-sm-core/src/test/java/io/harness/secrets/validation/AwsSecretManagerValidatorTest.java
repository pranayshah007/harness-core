/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.eraro.ErrorCode.AWS_SECRETS_MANAGER_OPERATION_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.SimpleEncryption.CHARSET;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.beans.HarnessSecret;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.validation.validators.AwsSecretManagerValidator;
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.security.encryption.common.EncryptionType;
import io.harness.stream.BoundedInputStream;

import com.google.common.collect.Sets;
import java.security.SecureRandom;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsSecretManagerValidatorTest extends CategoryTest {
  private AwsSecretManagerValidator awsSecretManagerValidator;
  private SecretsDao secretsDao;

  @Before
  public void setup() {
    secretsDao = mock(SecretsDao.class);
    awsSecretManagerValidator = new AwsSecretManagerValidator(secretsDao);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateInlineSecretText_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_INLINE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    awsSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateInlineSecretText_invalidName_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid() + ")";
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_INLINE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      awsSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Invalid characters in the name should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name can only contain alphanumeric characters, or any of: -/_+=.@!");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretTextUpdate_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                                                       .name(UUIDGenerator.generateUuid())
                                                                       .value(UUIDGenerator.generateUuid())
                                                                       .build()))
                                       .build();

    awsSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretTextUpdate_invalidName_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid() + ")";
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                                                       .name(UUIDGenerator.generateUuid())
                                                                       .value(UUIDGenerator.generateUuid())
                                                                       .build()))
                                       .build();

    try {
      awsSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Invalid characters in the name should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name can only contain alphanumeric characters, or any of: -/_+=.@!");
    }
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateEncryptedFile_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretFile.builder()
                               .name(name)
                               .kmsId(accountId)
                               .fileContent(UUIDGenerator.generateUuid().getBytes(CHARSET))
                               .build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_FILE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    awsSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateEncryptedFile_invalidName_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid() + ")";
    HarnessSecret secret = SecretFile.builder()
                               .name(name)
                               .kmsId(accountId)
                               .fileContent(UUIDGenerator.generateUuid().getBytes(CHARSET))
                               .build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_FILE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      awsSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Invalid characters in the name should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name can only contain alphanumeric characters, or any of: -/_+=.@!");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateEncryptedFile_exceedFileLimits_shouldThrowError() {
    SecureRandom secureRandom = new SecureRandom();
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    byte[] bytes = new byte[70000];
    secureRandom.nextBytes(bytes);
    HarnessSecret secret = SecretFile.builder().name(name).kmsId(accountId).fileContent(bytes).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_FILE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      awsSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("File size greater, should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("AWS Secrets Manager limits secret value to 65536 bytes.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretFileUpdate_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretFile.builder()
                               .name(name)
                               .kmsId(kmsId)
                               .fileContent(UUIDGenerator.generateUuid().getBytes(CHARSET))
                               .build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(CONFIG_FILE)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .build();
    awsSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretFileUpdate_invalidName_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid() + ")";
    String kmsId = UUIDGenerator.generateUuid();
    long fileSize = 1500;
    BoundedInputStream boundedInputStream = mock(BoundedInputStream.class);
    when(boundedInputStream.getSize()).thenReturn(fileSize + 100);
    HarnessSecret secret = SecretFile.builder()
                               .name(name)
                               .kmsId(kmsId)
                               .fileContent(UUIDGenerator.generateUuid().getBytes(CHARSET))
                               .build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(CONFIG_FILE)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .build();
    try {
      awsSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Invalid characters in the name should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name can only contain alphanumeric characters, or any of: -/_+=.@!");
    }
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretFileUpdate_fileSizeCheck_shouldThrowError() {
    SecureRandom secureRandom = new SecureRandom();
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    byte[] bytes = new byte[70000];
    secureRandom.nextBytes(bytes);
    HarnessSecret secret = SecretFile.builder().name(name).kmsId(kmsId).fileContent(bytes).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(CONFIG_FILE)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .build();
    try {
      awsSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("File size greater, should have failed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(AWS_SECRETS_MANAGER_OPERATION_ERROR);
      assertThat(e.getMessage()).isEqualTo("AWS Secrets Manager limits secret value to 65536 bytes.");
    }
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }
}
