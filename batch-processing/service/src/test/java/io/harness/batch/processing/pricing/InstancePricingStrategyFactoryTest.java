/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.pricing.fargatepricing.EcsFargateInstancePricingStrategy;
import io.harness.batch.processing.pricing.pricingprofile.PricingProfileService;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.GcpCustomBillingService;
import io.harness.batch.processing.pricing.storagepricing.StoragePricingStrategy;
import io.harness.batch.processing.pricing.vmpricing.ComputeInstancePricingStrategy;
import io.harness.batch.processing.pricing.vmpricing.VMPricingServiceImpl;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CE)
@RunWith(MockitoJUnitRunner.class)
public class InstancePricingStrategyFactoryTest extends CategoryTest {
  @Mock private VMPricingServiceImpl vmPricingService;
  @Mock private AwsCustomBillingService awsCustomBillingService;
  @Mock private AzureCustomBillingService azureCustomBillingService;
  @Mock private GcpCustomBillingService gcpCustomBillingService;
  @Mock private InstanceResourceService instanceResourceService;
  @Mock private EcsFargateInstancePricingStrategy ecsFargateInstancePricingStrategy;
  @Mock private CustomBillingMetaDataService customBillingMetaDataService;
  @Mock private PricingProfileService pricingProfileService;

  private static InstancePricingStrategyFactory instancePricingStrategyFactory;

  @Before
  public void before() {
    instancePricingStrategyFactory = new InstancePricingStrategyFactory(
        new ComputeInstancePricingStrategy(vmPricingService, awsCustomBillingService, azureCustomBillingService,
            gcpCustomBillingService, instanceResourceService, ecsFargateInstancePricingStrategy,
            customBillingMetaDataService, pricingProfileService),
        new EcsFargateInstancePricingStrategy(vmPricingService, customBillingMetaDataService, awsCustomBillingService),
        new StoragePricingStrategy());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetInstancePricingStrategy() {
    assertThat(instancePricingStrategyFactory.getInstancePricingStrategy(InstanceType.EC2_INSTANCE))
        .isInstanceOf(ComputeInstancePricingStrategy.class);

    assertThat(instancePricingStrategyFactory.getInstancePricingStrategy(InstanceType.ECS_TASK_FARGATE))
        .isInstanceOf(EcsFargateInstancePricingStrategy.class);

    assertThat(instancePricingStrategyFactory.getInstancePricingStrategy(InstanceType.K8S_PV))
        .isInstanceOf(StoragePricingStrategy.class);
  }
}
