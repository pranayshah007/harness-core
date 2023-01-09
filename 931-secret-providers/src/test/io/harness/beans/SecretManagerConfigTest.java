/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;

import java.security.SecureRandom;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretManagerConfigTest extends CategoryTest {
  private static final SecureRandom random = new SecureRandom();

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSecretManagerRenewalJobIteration() throws IllegalAccessException {
    VaultConfig vaultConfig = VaultConfig.builder().name("SM").build();
    vaultConfig.setUuid("uuid");
    vaultConfig.setEncryptionType(EncryptionType.VAULT);
    long nextTokenRenewIteration = random.nextLong();
    FieldUtils.writeField(vaultConfig, SecretManagerConfigKeys.nextTokenRenewIteration, nextTokenRenewIteration, true);
    assertThat(vaultConfig.obtainNextIteration(SecretManagerConfigKeys.nextTokenRenewIteration))
        .isEqualTo(nextTokenRenewIteration);

    nextTokenRenewIteration = random.nextLong();
    vaultConfig.updateNextIteration(SecretManagerConfigKeys.nextTokenRenewIteration, nextTokenRenewIteration);
    assertThat(vaultConfig.obtainNextIteration(SecretManagerConfigKeys.nextTokenRenewIteration))
        .isEqualTo(nextTokenRenewIteration);

    long nextIteration = random.nextLong();
    FieldUtils.writeField(
        vaultConfig, SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration, nextIteration, true);
    assertThat(vaultConfig.obtainNextIteration(SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration))
        .isEqualTo(nextIteration);

    nextIteration = random.nextLong();
    vaultConfig.updateNextIteration(
        SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration, nextIteration);
    assertThat(vaultConfig.obtainNextIteration(SecretManagerConfigKeys.manuallyEnteredSecretEngineMigrationIteration))
        .isEqualTo(nextIteration);

    try {
      vaultConfig.updateNextIteration(generateUuid(), random.nextLong());
      fail("Did not throw exception");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      vaultConfig.obtainNextIteration(generateUuid());
      fail("Did not throw exception");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
}
