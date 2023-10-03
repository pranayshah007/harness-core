/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class SecretUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void containsSecretTest() {
    assertThat(SecretUtils.containsSecret("${ngSecretManager.obtain(\"secret_ref\", 1234)}")).isTrue();
    assertThat(SecretUtils.containsSecret("${ngSecretManager.obtain(\"secret_ref\", 1234)")).isFalse();
    assertThat(SecretUtils.containsSecret("plain text ngSecretManager obtain")).isFalse();

    assertThat(SecretUtils.containsSecret("${ngSecretManager.obtain(\"secret_ref\")}")).isTrue();
    assertThat(SecretUtils.containsSecret("this is some content before ${ngSecretManager.obtain(\"secret_ref\")}"))
        .isTrue();
    assertThat(SecretUtils.containsSecret("${ngSecretManager.obtain(\"secret_ref\")} this is some content after"))
        .isTrue();
    assertThat(SecretUtils.containsSecret(
                   "this is some content before ${ngSecretManager.obtain(\"secret_ref\")} this is some content after"))
        .isTrue();
    assertThat(SecretUtils.containsSecret("${ngSecretManager.obtain(\"secret_ref\")")).isFalse();
  }
}
