/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlVersionException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlVersionTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidYamlVersion() {
    assertThat(YamlVersion.fromString("0")).isEqualTo(YamlVersion.V0);
    assertThat(YamlVersion.fromString("1")).isEqualTo(YamlVersion.V1);
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testInvalidValidYamlVersion() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> YamlVersion.fromString("2"));
    assertThatExceptionOfType(InvalidYamlVersionException.class).isThrownBy(() -> YamlVersion.fromString("2.1.2"));
  }
}
