/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.assertj.core.data.Percentage;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GCPCustomInstanceDetailProviderTest extends CategoryTest {
  private static final Percentage MAX_RELATIVE_ERROR_PCT = withinPercentage(5);
  private static final String REGION = "us-west-1";

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetE2CustomVMPricingInfo() throws Exception {
    final String instanceType = "e2-custom-12-32768";

    final double cpuUnit = 12.0D;
    final double memoryUnit = 32.0D;

    ProductDetails pricingInfo = GCPCustomInstanceDetailProvider.getCustomVMPricingInfo(instanceType, REGION);

    assertThat(pricingInfo.getCpusPerVm()).isCloseTo(cpuUnit, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getMemPerVm()).isCloseTo(memoryUnit, MAX_RELATIVE_ERROR_PCT);

    double onDemandPrice = cpuUnit * 0.022890D + memoryUnit * 0.003067D;
    assertThat(pricingInfo.getOnDemandPrice()).isCloseTo(onDemandPrice, MAX_RELATIVE_ERROR_PCT);

    double spotPrice = cpuUnit * 0.006867D + memoryUnit * 0.000920D;
    assertThat(pricingInfo.getSpotPrice().get(0).getPrice()).isCloseTo(spotPrice, MAX_RELATIVE_ERROR_PCT);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetN2CustomVMPricingInfo() throws Exception {
    final String instanceType = "n2-custom-6-3072";

    final double cpuUnit = 6.0D;
    final double memoryUnit = 3.0D;

    ProductDetails pricingInfo = GCPCustomInstanceDetailProvider.getCustomVMPricingInfo(instanceType, REGION);

    assertThat(pricingInfo.getCpusPerVm()).isCloseTo(cpuUnit, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getMemPerVm()).isCloseTo(memoryUnit, MAX_RELATIVE_ERROR_PCT);

    double onDemandPrice = cpuUnit * 0.033174 + memoryUnit * 0.004446;
    assertThat(pricingInfo.getOnDemandPrice()).isCloseTo(onDemandPrice, MAX_RELATIVE_ERROR_PCT);

    double spotPrice = cpuUnit * 0.00802 + memoryUnit * 0.00108;
    assertThat(pricingInfo.getSpotPrice().get(0).getPrice()).isCloseTo(spotPrice, MAX_RELATIVE_ERROR_PCT);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testHardCodedInstanceType() throws Exception {
    final String instanceType = "n2-standard-16";

    ProductDetails pricingInfo = GCPCustomInstanceDetailProvider.getCustomVMPricingInfo(instanceType, REGION);

    assertThat(pricingInfo.getCpusPerVm()).isCloseTo(16D, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getMemPerVm()).isCloseTo(64D, MAX_RELATIVE_ERROR_PCT);

    assertThat(pricingInfo.getOnDemandPrice()).isCloseTo(0.7769D, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getSpotPrice().get(0).getPrice()).isCloseTo(0.1880D, MAX_RELATIVE_ERROR_PCT);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testDefaultCustomInstanceFamily() throws Exception {
    final String instanceType = "custom-8-24576";

    ProductDetails pricingInfo = GCPCustomInstanceDetailProvider.getCustomVMPricingInfo(instanceType, REGION);

    assertThat(pricingInfo.getCpusPerVm()).isCloseTo(8D, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getMemPerVm()).isCloseTo(24D, MAX_RELATIVE_ERROR_PCT);

    assertThat(pricingInfo.getOnDemandPrice()).isCloseTo(0.033174D * 8D + 0.004446D * 24D, MAX_RELATIVE_ERROR_PCT);
    assertThat(pricingInfo.getSpotPrice().get(0).getPrice())
        .isCloseTo(0.00698D * 8D + 0.00094D * 24D, MAX_RELATIVE_ERROR_PCT);
  }
}
