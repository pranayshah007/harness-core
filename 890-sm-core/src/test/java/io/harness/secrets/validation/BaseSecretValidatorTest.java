/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_PARAMETERIZED_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_REFERENCE_SECRET;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
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
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.security.encryption.common.EncryptionType;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BaseSecretValidatorTest extends CategoryTest {
  private BaseSecretValidator baseSecretValidator;
  private SecretsDao secretsDao;

  @Before
  public void setup() {
    secretsDao = mock(SecretsDao.class);
    baseSecretValidator = new BaseSecretValidator(secretsDao);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretName_emptyCheck_shouldThrowError() {
    HarnessSecret secret = HarnessSecret.builder().kmsId(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    try {
      baseSecretValidator.validateSecret(UUIDGenerator.generateUuid(), secret, secretManagerConfig);
      fail("Should have thrown an error as name is empty");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name cannot be empty");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretName_emptyCheck_shouldThrowError() {
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().kmsId(kmsId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder().name("name").kmsId(kmsId).build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as updated name is empty");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Secret name cannot be empty");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretName_illegalCharactersCheck_shouldThrowError() {
    HarnessSecret secret = HarnessSecret.builder().name("!abcd").kmsId(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    try {
      baseSecretValidator.validateSecret(UUIDGenerator.generateUuid(), secret, secretManagerConfig);
      fail("Should have thrown an error as name has illegal characters");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("Secret name should not have any of the following characters [~!@#$%^&*'\"/?<>,;.]");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretName_illegalCharactersCheck_shouldThrowError() {
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().name("!abcd").kmsId(kmsId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder().name("abcd").kmsId(kmsId).build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as name has illegal characters");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("Secret name should not have any of the following characters [~!@#$%^&*'\"/?<>,;.]");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretName_duplicateNameCheck_shouldThrowError() {
    String kmsId = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().name(name).kmsId(kmsId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData encryptedData = mock(EncryptedData.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.of(encryptedData));
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as name has duplicate name");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("A secret exists with the proposed secret name in your account. Please choose a different name");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretName_duplicateNameCheck_shouldThrowError() {
    String kmsId = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String oldName = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().name(name).kmsId(kmsId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder().name(oldName).kmsId(kmsId).accountId(accountId).build();
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.of(existingRecord));
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as name has duplicate name");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("A secret exists with the proposed secret name in your account. Please choose a different name");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecret_kmsIdChangeCheckLocal_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String oldName = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().name(name).kmsId(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord =
        EncryptedData.builder().name(oldName).encryptionType(EncryptionType.LOCAL).accountId(accountId).build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as kmsId changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot change secret manager while updating secret");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecret_kmsIdChangeCheckCustom_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String oldName = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    String oldKmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = HarnessSecret.builder().name(name).kmsId(kmsId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(oldName)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(oldKmsId)
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as kmsId changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot change secret manager while updating secret");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecret_secretChangeType1_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretFile.builder().name(name).kmsId(accountId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(name)
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.LOCAL)
                                       .accountId(accountId)
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as type changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot convert encrypted text to encrypted file.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecret_secretChangeType2_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(name)
                                       .type(CONFIG_FILE)
                                       .encryptionType(EncryptionType.LOCAL)
                                       .accountId(accountId)
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as type changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot convert encrypted file to encrypted text.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretText_emptyValueCheck_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the secret is empty");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot create empty secret");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretText_SMCapabilityCheck1_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String path = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).path(path).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(new ArrayList<>());
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the secret manager cannot create referenced secrets.");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot create a referenced secret with the selected secret manager");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretText_SMCapabilityCheck2_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String value = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).value(value).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(new ArrayList<>());
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the secret manager cannot create inline secrets.");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot create an inline secret with the selected secret manager");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretText_SMCapabilityCheck3_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).parameters(new HashSet<>()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(new ArrayList<>());
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the secret manager cannot create parameterized secrets.");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot create a parameterized secret with the selected secret manager");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateParameterizedSecretText_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).parameters(new HashSet<>()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_PARAMETERIZED_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
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
    baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateReferenceSecretText_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).path(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_REFERENCE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretFile_emptyFileCheck_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretFile.builder().name(name).kmsId(accountId).fileContent(new byte[0]).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the file is empty");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Encrypted file is empty");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreate_validateSecretFile_SMCapabilityCheck_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretFile.builder()
                               .name(name)
                               .kmsId(accountId)
                               .fileContent(UUIDGenerator.generateUuid().getBytes(CHARSET))
                               .build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Should have thrown an error as the file is larger than accepted");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot create an encrypted file with the selected secret manager");
    }
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
    baseSecretValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretText_secretChangeType1_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(name)
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.LOCAL)
                                       .accountId(accountId)
                                       .path(UUIDGenerator.generateUuid())
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as secret text type changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot change the type of secret");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretText_secretChangeType2_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(name)
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.CUSTOM)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                                                       .name(UUIDGenerator.generateUuid())
                                                                       .value(UUIDGenerator.generateUuid())
                                                                       .build()))
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as secret text type changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot change the type of secret");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdate_validateSecretText_secretChangeType3_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).value(UUIDGenerator.generateUuid()).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(name)
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.CUSTOM)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .parameters(Sets.newHashSet(EncryptedDataParams.builder()
                                                                       .name(UUIDGenerator.generateUuid())
                                                                       .value(UUIDGenerator.generateUuid())
                                                                       .build()))
                                       .build();
    try {
      baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Should have thrown an error as secret text type changed");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Cannot change the type of secret");
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

    baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
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
    baseSecretValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }
}
