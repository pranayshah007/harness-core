/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.CostAttribution;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.PricingGroup;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanceTypeTest extends CategoryTest {
  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testInstanceType() {
    InstanceType instanceType = InstanceType.EC2_INSTANCE;
    PricingGroup pricingGroup = instanceType.getPricingGroup();
    CostAttribution costAttribution = instanceType.getCostAttribution();
    double minChargeableSeconds = instanceType.getMinChargeableSeconds();
    assertThat(pricingGroup).isEqualTo(PricingGroup.COMPUTE);
    assertThat(costAttribution).isEqualTo(CostAttribution.COMPLETE);
    assertThat(minChargeableSeconds).isEqualTo(3600);
  }
}
