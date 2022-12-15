/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.ANGELO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorTrackingCVConfigTest extends CategoryTest {
  @Test
  @Owner(developers = ANGELO)
  @Category(UnitTests.class)
  public void testValidateParams() {
    // As of this writing the OverOps CV config has no parameters. So the following
    // call should always return as there is nothing to validate. As parameters are
    // added this test method should be modified to ensure that
    // OverOpsCVConfig.validateParams() is detecting invalid parameter values.
    new ErrorTrackingCVConfig().validateParams();
    assertThat(true);
  }
}
