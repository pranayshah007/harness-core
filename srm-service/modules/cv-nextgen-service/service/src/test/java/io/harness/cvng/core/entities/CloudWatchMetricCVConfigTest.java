/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloudWatchMetricCVConfigTest extends CategoryTest {
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testValidateParams_regionIsNull() {
    CloudWatchMetricCVConfig cloudWatchMetricCVConfig = new CloudWatchMetricCVConfig();
    assertThatThrownBy(cloudWatchMetricCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("region should not be null");
  }
}
