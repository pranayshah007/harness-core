/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.encryption.common.EncryptionType.CUSTOM;
import static io.harness.security.encryption.common.EncryptionType.GCP_KMS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.SMCoreTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.security.encryption.common.EncryptionConfig;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KmsEncryptorsRegistryTest extends SMCoreTestBase {
  @Inject KmsEncryptorsRegistry kmsEncryptorsRegistry;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetKmsEncryptor_shouldReturn() {
    EncryptionConfig encryptionConfig = mock(EncryptionConfig.class);
    when(encryptionConfig.isGlobalKms()).thenReturn(false);
    when(encryptionConfig.getEncryptionType()).thenReturn(GCP_KMS);
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
    assertThat(kmsEncryptor).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetGlobalKmsEncryptor_shouldReturn() {
    EncryptionConfig encryptionConfig = mock(EncryptionConfig.class);
    when(encryptionConfig.isGlobalKms()).thenReturn(true);
    when(encryptionConfig.getEncryptionType()).thenReturn(GCP_KMS);
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
    assertThat(kmsEncryptor).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetKmsEncryptor_shouldThrowError() {
    try {
      EncryptionConfig encryptionConfig = mock(EncryptionConfig.class);
      when(encryptionConfig.isGlobalKms()).thenReturn(false);
      when(encryptionConfig.getEncryptionType()).thenReturn(CUSTOM);
      KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
      fail("The method should have thrown an error");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
    }
  }
}
