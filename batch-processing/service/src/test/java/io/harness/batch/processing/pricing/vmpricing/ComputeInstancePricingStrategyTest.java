/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.vmpricing;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.batch.processing.pricing.PricingData;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ComputeInstancePricingStrategyTest extends CategoryTest {
  @InjectMocks private ComputeInstancePricingStrategy computeInstancePricingStrategy;
  @Mock CustomBillingMetaDataService customBillingMetaDataService;
  @Mock InstanceResourceService instanceResourceService;
  @Mock AzureCustomBillingService azureCustomBillingService;

  private static final Instant NOW = Instant.now();
  private final String REGION = "us-east-1";
  private final String INSTANCE_FAMILY = "t2.large";
  private final String ACCOUNT_ID = "accountId";

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCustomVMPricing() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AZURE.name());
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, INSTANCE_FAMILY);
    InstanceData instanceData = InstanceData.builder()
                                    .metaData(metaData)
                                    .accountId(ACCOUNT_ID)
                                    .totalResource(Resource.builder().cpuUnits(1.0 * 1024).memoryMb(8.0 * 1024).build())
                                    .build();

    doReturn(Resource.builder().cpuUnits(1.0 * 1024).memoryMb(9.0 * 1024).build())
        .when(instanceResourceService)
        .getComputeVMResource(INSTANCE_FAMILY, REGION, CloudProvider.AZURE);
    doReturn("azure_dataset").when(customBillingMetaDataService).getAzureDataSetId(ACCOUNT_ID);
    doReturn(VMInstanceBillingData.builder().computeCost(24).build())
        .when(azureCustomBillingService)
        .getComputeVMPricingInfo(instanceData, NOW.minus(24, ChronoUnit.HOURS), NOW);

    PricingData pricingData = computeInstancePricingStrategy.getCustomVMPricing(
        instanceData, NOW.minus(24, ChronoUnit.HOURS), NOW, 24 * 3600D, INSTANCE_FAMILY, REGION, CloudProvider.AZURE);

    assertThat(pricingData.getPricePerHour()).isEqualTo(1.000);
    assertThat(pricingData.getCpuUnit()).isEqualTo(1.0 * 1024);
    assertThat(pricingData.getMemoryMb()).isEqualTo(9.0 * 1024);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCustomVMPricingWithRate() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AZURE.name());
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, INSTANCE_FAMILY);
    InstanceData instanceData = InstanceData.builder()
                                    .metaData(metaData)
                                    .accountId(ACCOUNT_ID)
                                    .totalResource(Resource.builder().cpuUnits(1.0 * 1024).memoryMb(8.0 * 1024).build())
                                    .build();

    doReturn(Resource.builder().cpuUnits(1.0 * 1024).memoryMb(9.0 * 1024).build())
        .when(instanceResourceService)
        .getComputeVMResource(INSTANCE_FAMILY, REGION, CloudProvider.AZURE);
    doReturn("azure_dataset").when(customBillingMetaDataService).getAzureDataSetId(ACCOUNT_ID);
    doReturn(VMInstanceBillingData.builder().computeCost(24).rate(1.5).build())
        .when(azureCustomBillingService)
        .getComputeVMPricingInfo(instanceData, NOW.minus(24, ChronoUnit.HOURS), NOW);

    PricingData pricingData = computeInstancePricingStrategy.getCustomVMPricing(
        instanceData, NOW.minus(24, ChronoUnit.HOURS), NOW, 24 * 3600D, INSTANCE_FAMILY, REGION, CloudProvider.AZURE);

    assertThat(pricingData.getPricePerHour()).isEqualTo(1.500);
    assertThat(pricingData.getCpuUnit()).isEqualTo(1.0 * 1024);
    assertThat(pricingData.getMemoryMb()).isEqualTo(9.0 * 1024);
  }
}
