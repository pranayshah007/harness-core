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

public class UnallocatedCostStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUnallocatedCostDisplayNameStrategy() {
    final String displayName = "DISPLAY_NAME";
    assertThat(UnallocatedCostStrategy.DISPLAY_NAME.name()).isEqualTo(displayName);
    assertThat(UnallocatedCostStrategy.valueOf(displayName)).isEqualTo(UnallocatedCostStrategy.DISPLAY_NAME);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUnallocatedCostHideStrategy() {
    final String hide = "HIDE";
    assertThat(UnallocatedCostStrategy.HIDE.name()).isEqualTo(hide);
    assertThat(UnallocatedCostStrategy.valueOf(hide)).isEqualTo(UnallocatedCostStrategy.HIDE);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUnallocatedCostShareStrategy() {
    final String share = "SHARE";
    assertThat(UnallocatedCostStrategy.SHARE.name()).isEqualTo(share);
    assertThat(UnallocatedCostStrategy.valueOf(share)).isEqualTo(UnallocatedCostStrategy.SHARE);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUnallocatedCostInvalidStrategy() {
    UnallocatedCostStrategy.valueOf("INVALID");
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUnallocatedCostStrategyCount() {
    assertThat(UnallocatedCostStrategy.values().length).isEqualTo(3);
  }
}
