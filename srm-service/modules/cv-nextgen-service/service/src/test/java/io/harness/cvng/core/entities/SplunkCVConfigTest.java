/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SplunkCVConfigTest extends CategoryTest {
  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testValidateParams_whenServiceInstanceIdentifierIsUndefined() {
    assertThatThrownBy(() -> new SplunkCVConfig().validateParams())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("serviceInstanceIdentifier should not be null");
  }
}
