/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceClaimTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldScale() throws Exception {
    ResourceClaim resourceClaim = new ResourceClaim(123, 234);
    assertThat(resourceClaim.scale(3)).isEqualTo(new ResourceClaim(369, 702));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldMinus() throws Exception {
    ResourceClaim rc1 = new ResourceClaim(100, 231);
    ResourceClaim rc2 = new ResourceClaim(123, 200);
    assertThat(rc1.minus(rc2)).isEqualTo(new ResourceClaim(-23, 31));
  }
}
