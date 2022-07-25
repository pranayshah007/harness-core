/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.service;

import static io.harness.ccm.commons.constants.InstanceMetaDataConstants.GCE_STORAGE_CLASS;
import static io.harness.ccm.commons.constants.InstanceMetaDataConstants.PV_TYPE;
import static io.harness.perpetualtask.k8s.watch.PVInfo.PVType.PV_TYPE_GCE_PERSISTENT_DISK;

import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.pricing.InstancePricingStrategyFactory;
import io.harness.batch.processing.pricing.PricingData;
import io.harness.batch.processing.pricing.PricingSource;
import io.harness.batch.processing.pricing.fargatepricing.EcsFargateInstancePricingStrategy;
import io.harness.batch.processing.pricing.pricingprofile.PricingProfileService;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.pricing.storagepricing.StoragePricingStrategy;
import io.harness.batch.processing.pricing.vmpricing.ComputeInstancePricingStrategy;
import io.harness.batch.processing.pricing.vmpricing.EcsFargatePricingInfo;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.batch.processing.pricing.vmpricing.VMPricingServiceImpl;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.entities.PricingProfile;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ZonePrice;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CE)
@RunWith(MockitoJUnitRunner.class)
public class BillingCalculationServiceTest extends CategoryTest {
  @InjectMocks private BillingCalculationService billingCalculationService;
  @Mock private InstancePricingStrategyFactory instancePricingStrategyRegistry;
  @Mock private VMPricingServiceImpl vmPricingService;
  @Mock private AwsCustomBillingService awsCustomBillingService;
  @Mock private AzureCustomBillingService azureCustomBillingService;
  @Mock private InstanceResourceService instanceResourceService;
  @Mock private EcsFargateInstancePricingStrategy ecsFargateInstancePricingStrategy;
  @Mock private CustomBillingMetaDataService customBillingMetaDataService;
  @Mock private PricingProfileService pricingProfileService;

  private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.DAYS);
  private final Instant INSTANCE_STOP_TIMESTAMP = NOW;
  private final Instant INSTANCE_START_TIMESTAMP = NOW.minus(1, ChronoUnit.DAYS);
  private final Long ONE_DAY_SECONDS = 86400l;
  private final Long HALF_DAY_SECONDS = 43200l;

  private final String ACCOUNT_ID = "accountId";
  private final String REGION = "us-east-1";
  private final String GCP_REGION = "us-central1";
  private final String GCP_ZONE_1 = "us-central1-a";
  private final String GCP_ZONE_2 = "us-central1-b";
  private final String DEFAULT_INSTANCE_FAMILY = "c4.8xlarge";
  private final String GCP_INSTANCE_FAMILY = "n1-standard-4";
  private final String GCP_CUSTOM_INSTANCE_FAMILY = "custom-8-20480";
  private final Double DEFAULT_INSTANCE_CPU = 36D;
  private final Double DEFAULT_INSTANCE_MEMORY = 60D;
  private final double DEFAULT_INSTANCE_PRICE = 1.60;
  private final double CPU_UTILIZATION = 0.5;
  private final double CPU_UTILIZATION_HIGH = 1.5;
  private final double MEMORY_UTILIZATION = 0.5;
  private final double MEMORY_UTILIZATION_HIGH = 1.5;
  private static final double STORAGE_CAPACITY = 1024D;
  private static final double STORAGE_UTIL_VALUE = 10D;
  private static final double STORAGE_REQUEST_VALUE = 1000D;
  // Increase offset's absolute value in case test fails with minor difference.
  private static final Offset<BigDecimal> BIG_DECIMAL_OFFSET = Assertions.within(new BigDecimal("0.000000000001"));

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetActiveInstanceTimeInInterval() {
    InstanceData instanceData = getInstanceWithTime(INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Long activeInstanceTimeInInterval = billingCalculationService.getActiveInstanceTimeInInterval(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(activeInstanceTimeInInterval).isEqualTo(ONE_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetActiveInstanceTimeInIntervalWhenStartedBefore() {
    InstanceData instanceData =
        getInstanceWithTime(INSTANCE_START_TIMESTAMP.minus(2, ChronoUnit.DAYS), INSTANCE_STOP_TIMESTAMP);
    Long activeInstanceTimeInInterval = billingCalculationService.getActiveInstanceTimeInInterval(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(activeInstanceTimeInInterval).isEqualTo(ONE_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetActiveInstanceTimeInIntervalWhenStartedAfter() {
    InstanceData instanceData =
        getInstanceWithTime(INSTANCE_START_TIMESTAMP.plus(12, ChronoUnit.HOURS), INSTANCE_STOP_TIMESTAMP);
    Long activeInstanceTimeInInterval = billingCalculationService.getActiveInstanceTimeInInterval(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(activeInstanceTimeInInterval).isEqualTo(HALF_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetActiveInstanceTimeInIntervalWhenStoppedBefore() {
    InstanceData instanceData =
        getInstanceWithTime(INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS));
    Long activeInstanceTimeInInterval = billingCalculationService.getActiveInstanceTimeInInterval(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(activeInstanceTimeInInterval).isEqualTo(HALF_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSeconds() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP)
                                    .usageStopTime(INSTANCE_STOP_TIMESTAMP)
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(ONE_DAY_SECONDS.doubleValue());
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveTimeIsLessThanMinDuration() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP)
                                    .usageStopTime(INSTANCE_START_TIMESTAMP.plus(1, ChronoUnit.MINUTES))
                                    .instanceType(InstanceType.EC2_INSTANCE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(3600);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceIsRunning() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_STOP_TIMESTAMP.minus(2, ChronoUnit.SECONDS))
                                    .instanceType(InstanceType.EC2_INSTANCE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveTimeIsLessThanMinChargeableTime() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP.minus(4, ChronoUnit.SECONDS))
                                    .usageStopTime(INSTANCE_START_TIMESTAMP.plus(40, ChronoUnit.SECONDS))
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(56);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveTimeIsEqualToMinChargeableTime() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP.minus(6, ChronoUnit.SECONDS))
                                    .usageStopTime(INSTANCE_START_TIMESTAMP.plus(54, ChronoUnit.SECONDS))
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(54);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveTimeIsGreaterThanMinChargeableTime() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP.minus(6, ChronoUnit.SECONDS))
                                    .usageStopTime(INSTANCE_START_TIMESTAMP.plus(57, ChronoUnit.SECONDS))
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(57);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveStartTimeIsNotInRange() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_STOP_TIMESTAMP.plus(6, ChronoUnit.HOURS))
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveSecondsWhenInstanceActiveEndTimeIsNotInRange() {
    InstanceData instanceData = InstanceData.builder()
                                    .usageStartTime(INSTANCE_START_TIMESTAMP.minus(6, ChronoUnit.HOURS))
                                    .usageStopTime(INSTANCE_START_TIMESTAMP.minus(2, ChronoUnit.HOURS))
                                    .instanceType(InstanceType.ECS_TASK_FARGATE)
                                    .build();
    double instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetBillingAmountForResource() {
    PricingData pricingData = new PricingData(0, 10, 0, 0, 256.0, 512.0, 0, PricingSource.PUBLIC_API);
    Resource instanceResource = getInstanceResource(256, 512);
    Map<String, String> metaData = new HashMap<>();
    addParentResource(metaData, 1024, 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP, InstanceType.ECS_TASK_EC2);
    BillingAmountBreakup billingAmountForResource =
        billingCalculationService.getBillingAmountBreakupForResource(instanceData, BigDecimal.valueOf(200),
            instanceResource.getCpuUnits(), instanceResource.getMemoryMb(), 0, 0, pricingData);
    Assertions.assertThat(billingAmountForResource.getBillingAmount()).isEqualTo(new BigDecimal("75.000"));
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetBillingAmountOfPodForNodeWithZeroResource() {
    PricingData pricingData = new PricingData(0, 10, 0, 0, 256.0, 512.0, 0, PricingSource.PUBLIC_API);
    Resource instanceResource = getInstanceResource(256, 512);

    Map<String, String> metaData = new HashMap<>();
    addParentResource(metaData, 0.0, 0.0);

    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP, InstanceType.K8S_POD);

    BillingAmountBreakup billingAmountForResource =
        billingCalculationService.getBillingAmountBreakupForResource(instanceData, BigDecimal.valueOf(200),
            instanceResource.getCpuUnits(), instanceResource.getMemoryMb(), 0, 0, pricingData);

    Assertions.assertThat(billingAmountForResource.getBillingAmount()).isEqualTo(new BigDecimal("0.0"));
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetIdleCostForResource() {
    Resource instanceResource = getInstanceResource(256, 512);
    Map<String, String> metaData = new HashMap<>();
    addParentResource(metaData, 1024, 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP, InstanceType.ECS_TASK_EC2);
    BigDecimal billingAmount = BigDecimal.valueOf(200);
    BigDecimal cpuBillingAmount = BigDecimal.valueOf(100);
    BigDecimal memoryBillingAmount = BigDecimal.valueOf(100);
    BillingAmountBreakup billingAmountBreakup =
        new BillingAmountBreakup(billingAmount, cpuBillingAmount, memoryBillingAmount, BigDecimal.ZERO);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    IdleCostData idleCost =
        billingCalculationService.getIdleCostForResource(billingAmountBreakup, utilizationData, instanceData);
    Assertions.assertThat(idleCost.getIdleCost()).isEqualTo(BigDecimal.valueOf(100.0));
    Assertions.assertThat(idleCost.getMemoryIdleCost()).isEqualTo(BigDecimal.valueOf(50.0));
    Assertions.assertThat(idleCost.getCpuIdleCost()).isEqualTo(BigDecimal.valueOf(50.0));
    Assertions.assertThat(idleCost.getStorageIdleCost()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetBillingAmount() {
    PricingData pricingData = new PricingData(0, 10, 0, 0, 256.0, 512.0, 0, PricingSource.PUBLIC_API);
    Resource instanceResource = getInstanceResource(256, 512);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    addParentResource(metaData, 1024, 1024);
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP, InstanceType.ECS_TASK_EC2);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount =
        billingCalculationService.getBillingAmount(instanceData, utilizationData, pricingData, ONE_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("90.0000"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getCpuBillingAmount())
        .isEqualTo(new BigDecimal("30.0000"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getMemoryBillingAmount())
        .isEqualTo(new BigDecimal("60.000"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("45.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("15.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("30.0"));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(ONE_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(256.0 * ONE_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(512.0 * ONE_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT)
  @Category(UnitTests.class)
  public void testGetBillingAmountWithZeroResourceInInstance() {
    PricingData pricingData = new PricingData(10, 10, 0, 0, 256.0, 512.0, 0, PricingSource.PUBLIC_API);
    Resource instanceResource = getInstanceResource(0, 0);
    Map<String, String> metaData = new HashMap<>();
    addParentResource(metaData, 1024, 1024);
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP, InstanceType.K8S_POD);
    UtilizationData utilizationData = getUtilizationWithValues(CPU_UTILIZATION_HIGH, MEMORY_UTILIZATION_HIGH, 256, 512);
    BillingData billingAmount =
        billingCalculationService.getBillingAmount(instanceData, utilizationData, pricingData, ONE_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("90.0000"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getCpuBillingAmount())
        .isEqualTo(new BigDecimal("30.0000"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getMemoryBillingAmount())
        .isEqualTo(new BigDecimal("60.000"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost().doubleValue()).isEqualTo(0.0);
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost().doubleValue()).isEqualTo(0.0);
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost().doubleValue()).isEqualTo(0.0);
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(ONE_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(256.0 * ONE_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(512.0 * ONE_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getNetworkCost()).isEqualTo(0d);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetBillingAmountWhereResourceIsNotPresent() {
    PricingData pricingData = new PricingData(10, 10, 0, 0, 256.0, 512.0, 0, PricingSource.CUR_REPORT);
    Map<String, String> metaData = new HashMap<>();
    InstanceData instanceData = getInstance(null, null, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_NODE);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount =
        billingCalculationService.getBillingAmount(instanceData, utilizationData, pricingData, HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("120.0"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getCpuBillingAmount())
        .isEqualTo(new BigDecimal("60.00"));
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getMemoryBillingAmount())
        .isEqualTo(new BigDecimal("60.00"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("60.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("30.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("30.0"));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(256.0 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(512.0 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getNetworkCost()).isEqualTo(10d);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountForCompute() throws IOException {
    when(vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS))
        .thenReturn(createVMComputePricingInfo());
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.ECS_TASK_EC2))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(18432, 30720);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, DEFAULT_INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.ON_DEMAND.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    addParentResource(metaData, DEFAULT_INSTANCE_CPU * 1024, DEFAULT_INSTANCE_MEMORY * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.ECS_TASK_EC2);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount()).isEqualTo(new BigDecimal("9.60"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("4.8"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("2.4"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("2.4"));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(18432 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(30720 * HALF_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveDuration() throws IOException {
    InstanceData instanceDataNode = getInstance("node_id", "node_cluster_id", INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_NODE);
    InstanceData instanceDataNode2 = getInstance("node_id", "cluster_id", INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(18, ChronoUnit.HOURS), InstanceType.K8S_POD);
    InstanceData instanceDataPod = getInstance(
        "pod_id", "node_cluster_id", INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP, InstanceType.K8S_POD);
    List<InstanceData> instanceDataList =
        ImmutableList.of(instanceDataNode, instanceDataPod, instanceDataNode2, instanceDataPod);
    Map<String, Double> instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        instanceDataList, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds)
        .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of(
            "pod_id:node_cluster_id", 86400.0, "node_id:node_cluster_id", 43200.0, "node_id:cluster_id", 21600.0));
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceActiveDurationOfEmptyList() throws IOException {
    Map<String, Double> instanceActiveSeconds = billingCalculationService.getInstanceActiveSeconds(
        Collections.emptyList(), INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(instanceActiveSeconds).hasSize(0);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountForComputeFromCurReport() throws IOException {
    when(instanceResourceService.getComputeVMResource(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS))
        .thenReturn(getResource());
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn("AWSDataSet");
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_NODE))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(18432, 30720);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, DEFAULT_INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.ON_DEMAND.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    addParentResource(metaData, DEFAULT_INSTANCE_CPU * 1024, DEFAULT_INSTANCE_MEMORY * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_NODE);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    when(awsCustomBillingService.getComputeVMPricingInfo(
             instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP))
        .thenReturn(
            VMInstanceBillingData.builder().resourceId("resourceId").networkCost(10.0).computeCost(40.0).build());
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount()).isEqualTo(new BigDecimal("20.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("10.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("5.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("5.0"));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(18432 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(30720 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getNetworkCost()).isEqualTo(10.0);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountForComputeFromCurReport2() throws IOException {
    when(instanceResourceService.getComputeVMResource(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS))
        .thenReturn(getResource());
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn("AWSDataSet");
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_NODE))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(18432, 30720);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, DEFAULT_INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.ON_DEMAND.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    addParentResource(metaData, DEFAULT_INSTANCE_CPU * 1024, DEFAULT_INSTANCE_MEMORY * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_NODE);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    when(awsCustomBillingService.getComputeVMPricingInfo(
             instanceData, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP))
        .thenReturn(
            VMInstanceBillingData.builder().resourceId("resourceId").networkCost(10.0).computeCost(40.0).build());
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 16 * 3600D, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount()).isEqualTo(new BigDecimal("30.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("15.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("7.5"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("7.5"));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(18432 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(30720 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getNetworkCost()).isEqualTo(10.0);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountForSpotComputeInstance() throws IOException {
    when(vmPricingService.getComputeVMPricingInfo(GCP_INSTANCE_FAMILY, GCP_REGION, CloudProvider.GCP)).thenReturn(null);
    when(pricingProfileService.fetchPricingProfile(ACCOUNT_ID, InstanceCategory.SPOT))
        .thenReturn(
            PricingProfile.builder().accountId(ACCOUNT_ID).vCpuPricePerHr(0.2).memoryGbPricePerHr(0.05).build());
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_NODE))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource totalResource = getInstanceResource(4096, 16384);
    Resource instanceResource = getInstanceResource(3988, 14360);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, GCP_INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.REGION, GCP_REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.SPOT.name());
    metaData.put(InstanceMetaDataConstants.ZONE, GCP_ZONE_1);
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    InstanceData instanceData = getInstance(totalResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_NODE);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount()).isEqualTo(new BigDecimal("19.2"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(new BigDecimal("9.6"));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(new BigDecimal("4.8"));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(new BigDecimal("4.8"));
    Assertions.assertThat(billingAmount.getSystemCostData().getMemorySystemCost())
        .isEqualTo(BigDecimal.valueOf(1.1859374999999999));
    Assertions.assertThat(billingAmount.getSystemCostData().getCpuSystemCost()).isEqualTo(BigDecimal.valueOf(0.253125));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(4096 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(16384 * HALF_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountForFargate() throws IOException {
    when(vmPricingService.getFargatePricingInfo(REGION)).thenReturn(createEcsFargatePricingInfo());
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.ECS_TASK_FARGATE))
        .thenReturn(new EcsFargateInstancePricingStrategy(
            vmPricingService, customBillingMetaDataService, awsCustomBillingService));
    Resource instanceResource = getInstanceResource(320, 2048);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.ECS_TASK_FARGATE);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount()).isEqualTo(new BigDecimal("24.0"));
    Assertions.assertThat(billingAmount.getIdleCostData().getIdleCost()).isEqualTo(BigDecimal.valueOf(12.0));
    Assertions.assertThat(billingAmount.getIdleCostData().getMemoryIdleCost()).isEqualTo(BigDecimal.valueOf(6.0));
    Assertions.assertThat(billingAmount.getIdleCostData().getCpuIdleCost()).isEqualTo(BigDecimal.valueOf(6.0));
    Assertions.assertThat(billingAmount.getUsageDurationSeconds()).isEqualTo(HALF_DAY_SECONDS.doubleValue());
    Assertions.assertThat(billingAmount.getCpuUnitSeconds()).isEqualTo(320 * HALF_DAY_SECONDS);
    Assertions.assertThat(billingAmount.getMemoryMbSeconds()).isEqualTo(2048 * HALF_DAY_SECONDS);
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountGCPCustomInstance() throws IOException {
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_POD))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(4 * 1024, 5 * 1024);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, GCP_CUSTOM_INSTANCE_FAMILY);
    metaData.put(InstanceMetaDataConstants.REGION, REGION);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.SPOT.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    addParentResource(metaData, 8 * 1024, 20 * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_POD);
    UtilizationData utilizationData = getUtilization(CPU_UTILIZATION, MEMORY_UTILIZATION);
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 0.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("0.3358799999999999625"));
  }

  private ComputeInstancePricingStrategy getComputeInstancePricingStrategy() {
    return new ComputeInstancePricingStrategy(vmPricingService, awsCustomBillingService, azureCustomBillingService,
        instanceResourceService, ecsFargateInstancePricingStrategy, customBillingMetaDataService,
        pricingProfileService);
  }

  @Test
  @Owner(developers = OwnerRule.SANDESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountCustomInstance() throws IOException {
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_POD))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(4 * 1024, 5 * 1024);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.IBM.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, "b2c.4x16.encrypted");
    metaData.put(InstanceMetaDataConstants.REGION, "eu-de");
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.ON_DEMAND.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    addParentResource(metaData, 8 * 1024, 20 * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_POD);
    when(pricingProfileService.fetchPricingProfile(instanceData.getAccountId(), InstanceCategory.ON_DEMAND))
        .thenReturn(PricingProfile.builder()
                        .accountId(instanceData.getAccountId())
                        .vCpuPricePerHr(0.016)
                        .memoryGbPricePerHr(0.008)
                        .build());
    UtilizationData utilizationData = getUtilization(1, 1);
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 43200.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("1.2960000000000001500"));
  }

  @Test
  @Owner(developers = OwnerRule.HITESH)
  @Category(UnitTests.class)
  public void testGetInstanceBillingAmountIBMInstance() throws IOException {
    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_POD))
        .thenReturn(getComputeInstancePricingStrategy());
    Resource instanceResource = getInstanceResource(4 * 1024, 5 * 1024);
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.IBM.name());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, "b2c.4x16.encrypted");
    metaData.put(InstanceMetaDataConstants.REGION, "eu-de");
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, InstanceCategory.ON_DEMAND.name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    addParentResource(metaData, 8 * 1024, 20 * 1024);
    InstanceData instanceData = getInstance(instanceResource, instanceResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_POD);
    UtilizationData utilizationData = getUtilization(1, 1);
    when(pricingProfileService.fetchPricingProfile(instanceData.getAccountId(), InstanceCategory.ON_DEMAND))
        .thenReturn(PricingProfile.builder()
                        .accountId(instanceData.getAccountId())
                        .vCpuPricePerHr(0.016)
                        .memoryGbPricePerHr(0.008)
                        .build());
    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isEqualTo(new BigDecimal("1.2960000000000001500"));
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetPVTotalAndIdleCost() {
    StorageResource storageResource = StorageResource.builder().capacity(STORAGE_CAPACITY).build();
    UtilizationData utilizationData = getStorageUtilization();
    Map<String, String> metaData = ImmutableMap.of();
    InstanceData instanceData = getInstance(storageResource, metaData, INSTANCE_START_TIMESTAMP,
        INSTANCE_STOP_TIMESTAMP.minus(12, ChronoUnit.HOURS), InstanceType.K8S_PV);

    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_PV))
        .thenReturn(new StoragePricingStrategy());

    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);

    BigDecimal totalCost = getTotalCost(0.040D, 12D);
    BigDecimal idleCost = getIdleCost(totalCost);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isCloseTo(totalCost, BIG_DECIMAL_OFFSET);

    Assertions.assertThat(billingAmount.getIdleCostData().getStorageIdleCost()).isCloseTo(idleCost, BIG_DECIMAL_OFFSET);

    Assertions.assertThat(billingAmount.getPricingSource()).isEqualTo(PricingSource.HARDCODED);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetPVTotalAndIdleCostWithNULLStopTime() {
    StorageResource storageResource = StorageResource.builder().capacity(STORAGE_CAPACITY).build();
    UtilizationData utilizationData = getStorageUtilization();
    Map<String, String> metaData =
        ImmutableMap.of(PV_TYPE, PV_TYPE_GCE_PERSISTENT_DISK.name(), GCE_STORAGE_CLASS, "pd-ssd");
    InstanceData instanceData =
        getInstance(storageResource, metaData, INSTANCE_START_TIMESTAMP, null, InstanceType.K8S_PV);

    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_PV))
        .thenReturn(new StoragePricingStrategy());

    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 86400.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);

    BigDecimal totalCost = getTotalCost(0.17D, 24D);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isCloseTo(totalCost, BIG_DECIMAL_OFFSET);
    Assertions.assertThat(billingAmount.getIdleCostData().getStorageIdleCost())
        .isCloseTo(getIdleCost(totalCost), BIG_DECIMAL_OFFSET);

    Assertions.assertThat(billingAmount.getPricingSource()).isEqualTo(PricingSource.PUBLIC_API);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetPVCostWithUtilizationNotAvailable() {
    StorageResource storageResource = StorageResource.builder().capacity(STORAGE_CAPACITY).build();
    UtilizationData utilizationData = null;
    Map<String, String> metaData = ImmutableMap.of();
    InstanceData instanceData =
        getInstance(storageResource, metaData, INSTANCE_START_TIMESTAMP, null, InstanceType.K8S_PV);

    when(instancePricingStrategyRegistry.getInstancePricingStrategy(InstanceType.K8S_PV))
        .thenReturn(new StoragePricingStrategy());

    BillingData billingAmount = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, 43200.0, INSTANCE_START_TIMESTAMP, INSTANCE_STOP_TIMESTAMP);

    BigDecimal totalCost = getTotalCost(0.040, 24D);
    Assertions.assertThat(billingAmount.getBillingAmountBreakup().getBillingAmount())
        .isCloseTo(totalCost, BIG_DECIMAL_OFFSET);
    // The absence of util may indicate either orphaned PV or simply missing util data due to delegate/batch-process
    // errors. All cost becomes unallocated -> idle = 0;
    Assertions.assertThat(billingAmount.getIdleCostData().getStorageIdleCost())
        .isCloseTo(BigDecimal.ZERO, BIG_DECIMAL_OFFSET);

    Assertions.assertThat(billingAmount.getPricingSource()).isEqualTo(PricingSource.HARDCODED);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testGetPVIdleCostWithCapacityUsageRequestAsZero() {
    final BillingAmountBreakup billingDataForResource = BillingAmountBreakup.builder()
                                                            .cpuBillingAmount(BigDecimal.ONE)
                                                            .memoryBillingAmount(BigDecimal.ONE)
                                                            .storageBillingAmount(BigDecimal.ONE)
                                                            .build();

    final UtilizationData utilizationData = UtilizationData.builder()
                                                .avgCpuUtilization(1D)
                                                .avgMemoryUtilization(1D)
                                                .avgStorageUsageValue(0D)
                                                .avgStorageRequestValue(0D)
                                                .build();

    final InstanceData instanceData = InstanceData.builder()
                                          .instanceType(InstanceType.K8S_PV)
                                          .storageResource(StorageResource.builder().capacity(0D).build())
                                          .build();

    try {
      IdleCostData idleCostData =
          billingCalculationService.getIdleCostForResource(billingDataForResource, utilizationData, instanceData);
      Assertions.assertThat(idleCostData.getStorageIdleCost()).isCloseTo(BigDecimal.ZERO, BIG_DECIMAL_OFFSET);
    } catch (Exception ex) {
      Assertions.fail(
          "Should calculate idle cost even if storageCapacity = storageRequest = storageUtilization = 0", ex);
    }
  }

  private InstanceData getInstanceWithTime(Instant startInstant, Instant endInstant) {
    return InstanceData.builder().usageStartTime(startInstant).usageStopTime(endInstant).build();
  }

  private InstanceData getInstance(Resource totalResource, Resource instanceResource, Map<String, String> metaData,
      Instant startInstant, Instant endInstant, InstanceType instanceType) {
    return InstanceData.builder()
        .accountId(ACCOUNT_ID)
        .totalResource(totalResource)
        .allocatableResource(instanceResource)
        .metaData(metaData)
        .usageStartTime(startInstant)
        .usageStopTime(endInstant)
        .instanceType(instanceType)
        .build();
  }

  private InstanceData getInstance(StorageResource storageResource, Map<String, String> metaData, Instant startInstant,
      Instant endInstant, InstanceType instanceType) {
    return InstanceData.builder()
        .accountId(ACCOUNT_ID)
        .storageResource(storageResource)
        .metaData(metaData)
        .usageStartTime(startInstant)
        .usageStopTime(endInstant)
        .instanceType(instanceType)
        .build();
  }

  private InstanceData getInstance(
      String instanceId, String clusterId, Instant startInstant, Instant endInstant, InstanceType instanceType) {
    return InstanceData.builder()
        .accountId(ACCOUNT_ID)
        .instanceId(instanceId)
        .clusterId(clusterId)
        .instanceType(instanceType)
        .usageStartTime(startInstant)
        .usageStopTime(endInstant)
        .build();
  }

  private UtilizationData getUtilization(double cpuUtilization, double memoryUtilization) {
    return UtilizationData.builder()
        .maxCpuUtilization(cpuUtilization)
        .maxMemoryUtilization(memoryUtilization)
        .avgCpuUtilization(cpuUtilization)
        .avgMemoryUtilization(memoryUtilization)
        .build();
  }

  private static UtilizationData getStorageUtilization() {
    return UtilizationData.builder()
        .avgStorageCapacityValue(STORAGE_CAPACITY)
        .maxStorageRequestValue(STORAGE_REQUEST_VALUE)
        .maxStorageUsageValue(STORAGE_UTIL_VALUE)
        .build();
  }

  private UtilizationData getUtilizationWithValues(
      double cpuUtilization, double memoryUtilization, double cpuUtilizationValue, double memoryUtilizationValue) {
    return UtilizationData.builder()
        .maxCpuUtilization(cpuUtilization)
        .maxMemoryUtilization(memoryUtilization)
        .avgCpuUtilization(cpuUtilization)
        .avgMemoryUtilization(memoryUtilization)
        .maxCpuUtilizationValue(cpuUtilizationValue)
        .maxMemoryUtilizationValue(memoryUtilizationValue)
        .avgCpuUtilizationValue(cpuUtilizationValue)
        .avgMemoryUtilizationValue(memoryUtilizationValue)
        .build();
  }

  private Resource getResource() {
    return Resource.builder().cpuUnits(DEFAULT_INSTANCE_CPU).memoryMb(DEFAULT_INSTANCE_MEMORY).build();
  }

  private ProductDetails createVMComputePricingInfo() {
    return ProductDetails.builder()
        .cpusPerVm(DEFAULT_INSTANCE_CPU)
        .memPerVm(DEFAULT_INSTANCE_MEMORY)
        .onDemandPrice(DEFAULT_INSTANCE_PRICE)
        .type(DEFAULT_INSTANCE_FAMILY)
        .spotPrice(createZonePrice())
        .build();
  }

  private List<ZonePrice> createZonePrice() {
    List<ZonePrice> zonePrices = new ArrayList<>();
    zonePrices.add(ZonePrice.builder().price(0.4).zone(GCP_ZONE_1).build());
    zonePrices.add(ZonePrice.builder().price(0.5).zone(GCP_ZONE_2).build());
    return zonePrices;
  }

  private EcsFargatePricingInfo createEcsFargatePricingInfo() {
    return EcsFargatePricingInfo.builder().cpuPrice(2).memoryPrice(0.5).build();
  }

  private Resource getInstanceResource(double cpu, double memory) {
    return Resource.builder().cpuUnits(cpu).memoryMb(memory).build();
  }

  private void addParentResource(Map<String, String> metaData, double parentCpu, double parentMemory) {
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_CPU, String.valueOf(parentCpu));
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY, String.valueOf(parentMemory));
  }

  private static BigDecimal getIdleCost(BigDecimal totalCost) {
    return BigDecimal.valueOf((STORAGE_REQUEST_VALUE - STORAGE_UTIL_VALUE) / STORAGE_CAPACITY).multiply(totalCost);
  }

  private static BigDecimal getTotalCost(double pricePerGBPerMonth, double activeHours) {
    return BigDecimal.valueOf(pricePerGBPerMonth * activeHours / (30D * 24D));
  }
}
