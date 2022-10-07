/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AwsCustomBillingServiceImplTest extends CategoryTest {
  @InjectMocks private AwsCustomBillingServiceImpl awsCustomBillingService;
  @Mock BigQueryHelperService bigQueryHelperService;
  @Mock InstanceDataService instanceDataService;

  private final String DATA_SET_ID = "dataSetId";
  private final String RESOURCE_ID = "resourceId1";
  private final String PARENT_RESOURCE_ID = "parentResourceId1";
  private final double COMPUTE_COST = 10.5;
  private final double NETWORK_COST = 20.5;
  private final Instant NOW = Instant.now();
  private final Instant START_TIME = NOW.minus(1, ChronoUnit.HOURS);
  private final Instant END_TIME = NOW;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetComputeVMPricingInfo() {
    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    VMInstanceBillingData vmInstanceBillingData = VMInstanceBillingData.builder()
                                                      .computeCost(COMPUTE_COST)
                                                      .networkCost(NETWORK_COST)
                                                      .resourceId(RESOURCE_ID)
                                                      .build();
    vmInstanceBillingDataMap.put(RESOURCE_ID, vmInstanceBillingData);
    List<String> resourceIds = Collections.singletonList("resourceId1");
    when(bigQueryHelperService.getAwsEC2BillingData(resourceIds, START_TIME, END_TIME, DATA_SET_ID))
        .thenReturn(vmInstanceBillingDataMap);
    awsCustomBillingService.updateAwsEC2BillingDataCache(resourceIds, START_TIME, END_TIME, DATA_SET_ID);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, RESOURCE_ID);
    InstanceData instanceData = InstanceData.builder().metaData(metaData).build();
    VMInstanceBillingData computeVMPricingInfo =
        awsCustomBillingService.getComputeVMPricingInfo(instanceData, START_TIME, END_TIME);
    assertThat(computeVMPricingInfo).isEqualTo(vmInstanceBillingData);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceIdWhenActualParentResourceIdPresent() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, PARENT_RESOURCE_ID);
    InstanceData instanceData = InstanceData.builder().instanceType(InstanceType.K8S_POD).metaData(metaData).build();
    when(instanceDataService.fetchInstanceData(PARENT_RESOURCE_ID)).thenReturn(getParentInstanceData());
    String resourceId = awsCustomBillingService.getResourceId(instanceData);
    assertThat(resourceId).isEqualTo(RESOURCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceIdWhenParentResourceIdPresent() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, PARENT_RESOURCE_ID);
    InstanceData instanceData = InstanceData.builder().instanceType(InstanceType.K8S_POD).metaData(metaData).build();
    when(instanceDataService.fetchInstanceDataWithName(any(), any(), any(), any())).thenReturn(getParentInstanceData());
    String resourceId = awsCustomBillingService.getResourceId(instanceData);
    assertThat(resourceId).isEqualTo(RESOURCE_ID);
  }

  private InstanceData getParentInstanceData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, RESOURCE_ID);
    return InstanceData.builder().metaData(metaData).build();
  }
}
