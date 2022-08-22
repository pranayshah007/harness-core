/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.pricing.pricingprofile.PricingProfileDaoImpl;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.entities.PricingProfile;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PricingProfileDaoImplTest extends BatchProcessingTestBase {
  @Inject private PricingProfileDaoImpl pricingProfileDaoImpl;

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreate() {
    PricingProfile defaultProfile =
        PricingProfile.builder().accountId("default").vCpuPricePerHr(0.0016).memoryGbPricePerHr(0.008).build();
    assertThat(pricingProfileDaoImpl.create(defaultProfile)).isEqualTo(true);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFetch() {
    PricingProfile defaultProfile =
        PricingProfile.builder().accountId("default").vCpuPricePerHr(0.0016).memoryGbPricePerHr(0.008).build();
    pricingProfileDaoImpl.create(defaultProfile);
    PricingProfile defaultPricingProfile = pricingProfileDaoImpl.fetchPricingProfile(defaultProfile.getAccountId());
    assertThat(defaultPricingProfile.getAccountId()).isEqualTo(defaultProfile.getAccountId());
    assertThat(defaultPricingProfile.getVCpuPricePerHr()).isEqualTo(defaultProfile.getVCpuPricePerHr());
    assertThat(defaultPricingProfile.getMemoryGbPricePerHr()).isEqualTo(defaultProfile.getMemoryGbPricePerHr());
  }
}
