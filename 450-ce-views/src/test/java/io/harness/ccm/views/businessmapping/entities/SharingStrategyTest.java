/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.entities;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SharingStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testEqualSharingStrategy() {
    final String equal = "EQUAL";
    assertThat(SharingStrategy.EQUAL.name()).isEqualTo(equal);
    assertThat(SharingStrategy.valueOf(equal)).isEqualTo(SharingStrategy.EQUAL);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testProportionalSharingStrategy() {
    final String proportional = "PROPORTIONAL";
    assertThat(SharingStrategy.PROPORTIONAL.name()).isEqualTo(proportional);
    assertThat(SharingStrategy.valueOf(proportional)).isEqualTo(SharingStrategy.PROPORTIONAL);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testFixedSharingStrategy() {
    final String fixed = "FIXED";
    assertThat(SharingStrategy.FIXED.name()).isEqualTo(fixed);
    assertThat(SharingStrategy.valueOf(fixed)).isEqualTo(SharingStrategy.FIXED);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testInvalidSharingStrategy() {
    SharingStrategy.valueOf("INVALID");
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testSharingStrategyCount() {
    assertThat(SharingStrategy.values().length).isEqualTo(3);
  }
}
