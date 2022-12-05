/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.beans.SecretManagerCapabilities.CREATE_REFERENCE_SECRET;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;

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
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.validation.validators.VaultSecretManagerValidator;
import io.harness.security.encryption.common.EncryptionType;

import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VaultSecretManagerValidatorTest extends CategoryTest {
  private VaultSecretManagerValidator vaultSecretManagerValidator;
  private SecretsDao secretsDao;

  @Before
  public void setup() {
    secretsDao = mock(SecretsDao.class);
    vaultSecretManagerValidator = new VaultSecretManagerValidator(secretsDao);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateReferenceSecretText_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String path = UUIDGenerator.generateUuid() + "#" + UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).path(path).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_REFERENCE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    vaultSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
    verify(secretManagerConfig, times(1)).getSecretManagerCapabilities();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateReferenceSecretText_invalidPath_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String path = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(accountId).path(path).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfig.getSecretManagerCapabilities()).thenReturn(Lists.list(CREATE_REFERENCE_SECRET));
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    try {
      vaultSecretManagerValidator.validateSecret(accountId, secret, secretManagerConfig);
      fail("Path is incorrect");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(VAULT_OPERATION_ERROR);
      assertThat(e.getMessage())
          .isEqualTo(
              "Secret path need to include the # sign with the the key name after. E.g. /foo/bar/my-secret#my-key.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretTextUpdate_shouldThrowError() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    String path = UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).path(path).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .path(UUIDGenerator.generateUuid())
                                       .build();

    try {
      vaultSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
      fail("Path is incorrect");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(VAULT_OPERATION_ERROR);
      assertThat(e.getMessage())
          .isEqualTo(
              "Secret path need to include the # sign with the the key name after. E.g. /foo/bar/my-secret#my-key.");
    }
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretTextUpdate_shouldPass() {
    String accountId = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    String kmsId = UUIDGenerator.generateUuid();
    String path = UUIDGenerator.generateUuid() + "#" + UUIDGenerator.generateUuid();
    HarnessSecret secret = SecretText.builder().name(name).kmsId(kmsId).path(path).build();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretsDao.getSecretByName(accountId, name)).thenReturn(Optional.empty());
    EncryptedData existingRecord = EncryptedData.builder()
                                       .name(UUIDGenerator.generateUuid())
                                       .type(SECRET_TEXT)
                                       .encryptionType(EncryptionType.KMS)
                                       .accountId(accountId)
                                       .kmsId(kmsId)
                                       .path(UUIDGenerator.generateUuid())
                                       .build();

    vaultSecretManagerValidator.validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    verify(secretsDao, times(1)).getSecretByName(accountId, name);
  }
}
