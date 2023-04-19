/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;
import static io.harness.batch.processing.writer.constants.K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE;
import static io.harness.batch.processing.writer.constants.K8sCCMConstants.VIRTUAL_KUBELET;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.service.BillingAmountBreakup;
import io.harness.batch.processing.billing.service.BillingCalculationService;
import io.harness.batch.processing.billing.service.BillingData;
import io.harness.batch.processing.billing.service.IdleCostData;
import io.harness.batch.processing.billing.service.SystemCostData;
import io.harness.batch.processing.billing.service.UtilizationData;
import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.billing.writer.support.BillingDataGenerationValidator;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.pricing.PricingSource;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.ClusterRecordService;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.security.authentication.BatchQueryConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@RunWith(MockitoJUnitRunner.class)
public class InstanceBillingDataTaskletTest extends CategoryTest {
  private static final String PARENT_RESOURCE_ID = "parent_resource_id";
  private static final String ACCOUNT_ID = "account_id";
  private static final String INSTANCE_ID = "instance_id";
  private static final String SERVICE_ID = "service_id";
  private static final String APP_ID = "app_id";
  private static final String CLOUD_PROVIDER_ID = "cloud_provider_id";
  private static final String ENV_ID = "env_id";
  private static final String INFRA_MAPPING_ID = "infra_mapping_id";
  private static final String DEPLOYMENT_SUMMARY_ID = "deployment_summary_id";
  private static final String CLUSTER_ID = "cluster_id";
  private static final String CLUSTER_NAME = "cluster_name";
  private static final String NODE_INSTANCE_ID = "node_instance_id";
  private static final String ACTUAL_PARENT_RESOURCE_ID = "actual_parent_resource_id";

  private final double CPU_UNIT_SECONDS = 400;
  private final double MEMORY_MB_SECONDS = 400;
  private final double CPU_UNIT_LIMIT = 1024;
  private final double CPU_UNIT_REQUEST = 1024;
  private final double MEMORY_MB_LIMIT = 2048;
  private final double MEMORY_MB_REQUEST = 2096;
  private final double USAGE_DURATION_SECONDS = 400;
  private final double CPU_UTILIZATION = 1;
  private final double MEMORY_UTILIZATION = 1;

  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();

  @InjectMocks private InstanceBillingDataTasklet instanceBillingDataTasklet;
  @Mock private BillingDataServiceImpl billingDataService;
  @Mock private BillingCalculationService billingCalculationService;
  @Mock private UtilizationDataServiceImpl utilizationDataService;
  @Mock private BillingDataGenerationValidator billingDataGenerationValidator;
  @Mock private InstanceDataService instanceDataService;
  @Mock private InstanceDataDao instanceDataDao;
  @Mock private CustomBillingMetaDataService customBillingMetaDataService;
  @Mock private BatchMainConfig config;

  @Mock private ClusterRecordService eventsClusterRecordService;

  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  @Mock private FeatureFlagService featureFlagService;

  @Captor private ArgumentCaptor<List<InstanceBillingData>> instanceBillingDataArgumentCaptor;

  @Before
  public void setup() {
    when(config.getBatchQueryConfig()).thenReturn(BatchQueryConfig.builder().instanceDataBatchSize(50).build());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetValueForKeyFromInstanceMetaData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, PARENT_RESOURCE_ID);
    InstanceData instanceData = InstanceData.builder().metaData(metaData).build();
    String parentInstanceId =
        getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.PARENT_RESOURCE_ID, instanceData);
    assertThat(parentInstanceId).isEqualTo(PARENT_RESOURCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testInValidAzureInstanceForBilling() {
    Map<String, String> labels = new HashMap<>();
    labels.put(InstanceMetaDataConstants.TYPE_LABEL, VIRTUAL_KUBELET);
    InstanceData instanceData = InstanceData.builder()
                                    .instanceType(InstanceType.K8S_NODE)
                                    .instanceName("virtual-node-aci-linux")
                                    .labels(labels)
                                    .build();
    boolean validInstanceForBilling = instanceBillingDataTasklet.validInstanceForBilling(instanceData);
    assertThat(validInstanceForBilling).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testValidAzureInstanceForBilling() {
    Map<String, String> labels = new HashMap<>();
    InstanceData instanceData =
        InstanceData.builder().instanceType(InstanceType.K8S_NODE).instanceName("aks-ins").labels(labels).build();
    boolean validInstanceForBilling = instanceBillingDataTasklet.validInstanceForBilling(instanceData);
    assertThat(validInstanceForBilling).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testValidAzurePODInstanceForBilling() {
    Map<String, String> labels = new HashMap<>();
    labels.put(InstanceMetaDataConstants.TYPE_LABEL, VIRTUAL_KUBELET);
    InstanceData instanceData = InstanceData.builder()
                                    .instanceType(InstanceType.K8S_POD)
                                    .instanceName("virtual-node-aci-linux")
                                    .labels(labels)
                                    .build();
    boolean validInstanceForBilling = instanceBillingDataTasklet.validInstanceForBilling(instanceData);
    assertThat(validInstanceForBilling).isTrue();
  }

  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testInValidAWSInstanceForBilling() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.COMPUTE_TYPE, AWS_FARGATE_COMPUTE_TYPE);
    InstanceData instanceData = InstanceData.builder().instanceType(InstanceType.K8S_NODE).metaData(metaData).build();
    boolean validInstanceForBilling = instanceBillingDataTasklet.validInstanceForBilling(instanceData);
    assertThat(validInstanceForBilling).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetNullValueForKeyFromInstanceMetaData() {
    InstanceData instanceData = InstanceData.builder().build();
    String parentInstanceId =
        getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.PARENT_RESOURCE_ID, instanceData);
    assertThat(parentInstanceId).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetEmptyHarnessServiceInfo() {
    InstanceData instanceData = InstanceData.builder().build();
    HarnessServiceInfo harnessServiceInfo = instanceBillingDataTasklet.getHarnessServiceInfo(instanceData);
    assertThat(harnessServiceInfo).isNotNull();
    assertThat(harnessServiceInfo.getAppId()).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetActualParentInstanceId() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, ACTUAL_PARENT_RESOURCE_ID);
    InstanceData instanceData = InstanceData.builder().metaData(metaData).build();
    String parentInstanceId = instanceBillingDataTasklet.getParentInstanceId(instanceData);
    assertThat(parentInstanceId).isEqualTo(ACTUAL_PARENT_RESOURCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetParentInstanceId() {
    InstanceData nodeInstanceData = InstanceData.builder().instanceId(NODE_INSTANCE_ID).build();
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, PARENT_RESOURCE_ID);
    when(instanceDataService.fetchInstanceDataWithName(any(), any(), any(), any())).thenReturn(nodeInstanceData);
    InstanceData instanceData = InstanceData.builder()
                                    .accountId(ACCOUNT_ID)
                                    .clusterId(CLUSTER_ID)
                                    .instanceType(InstanceType.K8S_POD)
                                    .metaData(metaData)
                                    .build();
    String parentInstanceId = instanceBillingDataTasklet.getParentInstanceId(instanceData);
    assertThat(parentInstanceId).isEqualTo(NODE_INSTANCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetParentInstanceName() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, PARENT_RESOURCE_ID);
    when(instanceDataService.fetchInstanceDataWithName(any(), any(), any(), any())).thenReturn(null);
    InstanceData instanceData = InstanceData.builder()
                                    .accountId(ACCOUNT_ID)
                                    .clusterId(CLUSTER_ID)
                                    .instanceType(InstanceType.K8S_POD)
                                    .metaData(metaData)
                                    .build();
    String parentInstanceId = instanceBillingDataTasklet.getParentInstanceId(instanceData);
    assertThat(parentInstanceId).isEqualTo(PARENT_RESOURCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetHarnessServiceInfo() {
    InstanceData instanceData = InstanceData.builder().harnessServiceInfo(getHarnessServiceInfo()).build();
    HarnessServiceInfo harnessServiceInfo = instanceBillingDataTasklet.getHarnessServiceInfo(instanceData);
    assertThat(harnessServiceInfo).isNotNull();
    assertThat(harnessServiceInfo.getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCloudServiceNameWhenNotPresentForEC2() {
    Map<String, String> metaDataMap = new HashMap<>();
    metaDataMap.put(InstanceMetaDataConstants.LAUNCH_TYPE, LaunchType.EC2.name());
    InstanceData instanceData =
        InstanceData.builder().instanceType(InstanceType.ECS_TASK_EC2).metaData(metaDataMap).build();
    String cloudServiceName = instanceBillingDataTasklet.getCloudServiceName(instanceData);
    assertThat(cloudServiceName).isEqualTo("none");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCloudServiceNameWhenNotPresentForFargate() {
    Map<String, String> metaDataMap = new HashMap<>();
    metaDataMap.put(InstanceMetaDataConstants.LAUNCH_TYPE, LaunchType.FARGATE.name());
    InstanceData instanceData =
        InstanceData.builder().instanceType(InstanceType.ECS_TASK_FARGATE).metaData(metaDataMap).build();
    String cloudServiceName = instanceBillingDataTasklet.getCloudServiceName(instanceData);
    assertThat(cloudServiceName).isEqualTo("none");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCloudServiceNameWhenNotPresent() {
    Map<String, String> metaDataMap = new HashMap<>();
    InstanceData instanceData = InstanceData.builder().build();
    String cloudServiceName = instanceBillingDataTasklet.getCloudServiceName(instanceData);
    assertThat(cloudServiceName).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testExecute() {
    ChunkContext chunkContext = mock(ChunkContext.class);
    StepContext stepContext = mock(StepContext.class);
    StepExecution stepExecution = mock(StepExecution.class);
    JobParameters parameters = mock(JobParameters.class);

    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);

    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.BATCH_JOB_TYPE)).thenReturn(BatchJobType.INSTANCE_BILLING.name());

    InstanceData instanceData =
        InstanceData.builder()
            .instanceType(InstanceType.EC2_INSTANCE)
            .accountId(ACCOUNT_ID)
            .instanceId(INSTANCE_ID)
            .clusterId(CLUSTER_ID)
            .clusterName(CLUSTER_NAME)
            .usageStartTime(Instant.ofEpochMilli(START_TIME_MILLIS - ChronoUnit.DAYS.getDuration().toMillis()))
            .activeInstanceIterator(Instant.ofEpochMilli(START_TIME_MILLIS + ChronoUnit.DAYS.getDuration().toMillis()))
            .totalResource(Resource.builder().cpuUnits(CPU_UNIT_REQUEST).memoryMb(MEMORY_MB_REQUEST).build())
            .limitResource(Resource.builder().cpuUnits(CPU_UNIT_LIMIT).memoryMb(MEMORY_MB_LIMIT).build())
            .harnessServiceInfo(getHarnessServiceInfo())
            .build();

    when(instanceDataDao.getInstanceDataListsOfTypesAndClusterId(any(), anyInt(), any(), any(), any(), any()))
        .thenReturn(Arrays.asList(instanceData));

    RepeatStatus repeatStatus = instanceBillingDataTasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testWriteBillingData() throws Exception {
    Map<String, String> metaDataMap = new HashMap<>();
    metaDataMap.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
    Map<String, UtilizationData> utilizationDataForInstances = new HashMap<>();
    utilizationDataForInstances.put(INSTANCE_ID,
        UtilizationData.builder()
            .maxCpuUtilization(CPU_UTILIZATION)
            .maxMemoryUtilization(MEMORY_UTILIZATION)
            .avgCpuUtilization(CPU_UTILIZATION)
            .avgMemoryUtilization(MEMORY_UTILIZATION)
            .build());
    InstanceData instanceData =
        InstanceData.builder()
            .instanceType(InstanceType.EC2_INSTANCE)
            .metaData(metaDataMap)
            .accountId(ACCOUNT_ID)
            .instanceId(INSTANCE_ID)
            .clusterId(CLUSTER_ID)
            .clusterName(CLUSTER_NAME)
            .totalResource(Resource.builder().cpuUnits(CPU_UNIT_REQUEST).memoryMb(MEMORY_MB_REQUEST).build())
            .limitResource(Resource.builder().cpuUnits(CPU_UNIT_LIMIT).memoryMb(MEMORY_MB_LIMIT).build())
            .harnessServiceInfo(getHarnessServiceInfo())
            .build();
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn("AWS_DATA_SETID");
    when(utilizationDataService.getUtilizationDataForInstances(any(), any(), any(), any(), any(), any()))
        .thenReturn(utilizationDataForInstances);
    when(billingCalculationService.getInstanceBillingAmount(any(), any(), any(), any(), any()))
        .thenReturn(new BillingData(BillingAmountBreakup.builder().billingAmount(BigDecimal.ONE).build(),
            new IdleCostData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            new SystemCostData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), USAGE_DURATION_SECONDS,
            CPU_UNIT_SECONDS, MEMORY_MB_SECONDS, 5.0, 0, PricingSource.PUBLIC_API));
    when(billingDataGenerationValidator.shouldGenerateBillingData(
             ACCOUNT_ID, CLUSTER_ID, Instant.ofEpochMilli(START_TIME_MILLIS)))
        .thenReturn(true);

    instanceBillingDataTasklet.createBillingData(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
        Instant.ofEpochMilli(END_TIME_MILLIS), BatchJobType.INSTANCE_BILLING, Arrays.asList(instanceData),
        ImmutableMap.of(), ImmutableMap.of());
    ArgumentCaptor<BatchJobType> batchJobTypeArgumentCaptor = ArgumentCaptor.forClass(BatchJobType.class);
    verify(billingDataService)
        .create(instanceBillingDataArgumentCaptor.capture(), batchJobTypeArgumentCaptor.capture());
    InstanceBillingData instanceBillingData = instanceBillingDataArgumentCaptor.getValue().get(0);
    assertThat(instanceBillingData.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceBillingData.getClusterId()).isEqualTo(null);
    assertThat(instanceBillingData.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(instanceBillingData.getBillingAmount()).isEqualTo(BigDecimal.ONE);
    assertThat(instanceBillingData.getIdleCost()).isEqualTo(BigDecimal.ZERO);
    assertThat(instanceBillingData.getUsageDurationSeconds()).isEqualTo(USAGE_DURATION_SECONDS);
    assertThat(instanceBillingData.getCpuUnitSeconds()).isEqualTo(CPU_UNIT_SECONDS);
    assertThat(instanceBillingData.getMemoryMbSeconds()).isEqualTo(MEMORY_MB_SECONDS);
    assertThat(instanceBillingData.getCpuLimit()).isEqualTo(CPU_UNIT_LIMIT);
    assertThat(instanceBillingData.getMemoryLimit()).isEqualTo(MEMORY_MB_LIMIT);
    assertThat(instanceBillingData.getCpuRequest()).isEqualTo(CPU_UNIT_REQUEST);
    assertThat(instanceBillingData.getMemoryRequest()).isEqualTo(MEMORY_MB_REQUEST);
    assertThat(instanceBillingData.getCloudProvider()).isEqualTo(CloudProvider.AWS.name());
    assertThat(instanceBillingData.getStartTimestamp()).isEqualTo(START_TIME_MILLIS);
    assertThat(instanceBillingData.getEndTimestamp()).isEqualTo(END_TIME_MILLIS);
    assertThat(instanceBillingData.getAppId()).isEqualTo(APP_ID);
    assertThat(instanceBillingData.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(instanceBillingData.getMaxCpuUtilization()).isEqualTo(CPU_UTILIZATION);
    assertThat(instanceBillingData.getMaxMemoryUtilization()).isEqualTo(MEMORY_UTILIZATION);
    assertThat(instanceBillingData.getAvgCpuUtilization()).isEqualTo(CPU_UTILIZATION);
    assertThat(instanceBillingData.getAvgMemoryUtilization()).isEqualTo(MEMORY_UTILIZATION);
  }

  private HarnessServiceInfo getHarnessServiceInfo() {
    return new HarnessServiceInfo(
        SERVICE_ID, APP_ID, CLOUD_PROVIDER_ID, ENV_ID, INFRA_MAPPING_ID, DEPLOYMENT_SUMMARY_ID);
  }
}
