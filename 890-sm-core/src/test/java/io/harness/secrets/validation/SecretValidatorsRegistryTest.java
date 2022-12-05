/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.SMCoreTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.security.encryption.common.EncryptionType;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretValidatorsRegistryTest extends SMCoreTestBase {
  @Inject SecretValidatorsRegistry secretValidatorsRegistry;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetValidator_shouldReturnValidator() {
    EncryptionType encryptionType = EncryptionType.KMS;
    SecretValidator secretValidator = secretValidatorsRegistry.getSecretValidator(encryptionType);
    assertThat(secretValidator).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetValidator_shouldThrowError() {
    try {
      secretValidatorsRegistry.getSecretValidator(null);
      fail("The registry should have thrown an error");
    } catch (SecretManagementException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("No encryptor registered against the encryption type null");
    }
  }
}
