/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.beans.FeatureName.CCM_WORKLOAD_LABELS_OPTIMISATION;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.avro.ClusterBillingData;
import io.harness.avro.Label;
import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.impl.GoogleCloudStorageServiceImpl;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.K8SWorkloadService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.configuration.DeployMode;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.security.authentication.BatchQueryConfig;

import com.google.common.collect.ImmutableList;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

@RunWith(MockitoJUnitRunner.class)
public class ClusterDataToBigQueryTaskletTest extends BaseTaskletTest {
  public static final String BILLING_DATA = "billing_data";
  public static final int BATCH_SIZE = 500;
  private static final String INSTANCE_ID = "instanceId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String SETTING_ID = "settingId";
  private static final String KIND = "kind";
  private static final String NAMESPACE = "namespace";
  private static final String LABEL_KEY = "labelKey";
  private static final String LABEL_VALUE = "labelValue";
  private static final String NAME_0 = "name_0";
  private static final String NAME_1 = "name_1";

  @Mock BillingDataServiceImpl billingDataService;
  @Mock private BatchMainConfig config;
  @Mock GoogleCloudStorageServiceImpl googleCloudStorageService;
  @Mock private WorkloadRepository workloadRepository;
  @InjectMocks ClusterDataToBigQueryTasklet clusterDataToBigQueryTasklet;
  @Mock private ChunkContext chunkContext;
  @Mock private StepContext stepContext;
  @Mock private StepExecution stepExecution;
  @Mock private JobParameters parameters;
  @Mock private FeatureFlagService featureFlagService;

  private final Instant END_INSTANT = Instant.now();
  private final Instant START_INSTANT = END_INSTANT.minus(1, ChronoUnit.HOURS);

  @Before
  public void setup() {
    InstanceBillingData instanceBillingData = createBillingData(NAME_0);
    when(config.getBatchQueryConfig()).thenReturn(BatchQueryConfig.builder().queryBatchSize(BATCH_SIZE).build());
    when(config.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    when(config.isClickHouseEnabled()).thenReturn(false);
    when(config.getBatchQueryConfig())
        .thenReturn(BatchQueryConfig.builder().billingDataQueryBatchSize(BATCH_SIZE).build());
    when(billingDataService.read(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS),
             Instant.ofEpochMilli(END_TIME_MILLIS), BATCH_SIZE, 0, BatchJobType.CLUSTER_DATA_TO_BIG_QUERY))
        .thenReturn(Collections.singletonList(instanceBillingData));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldExecute() throws Exception {
    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);
    when(parameters.getString(CCMJobConstants.BATCH_JOB_TYPE))
        .thenReturn(BatchJobType.CLUSTER_DATA_TO_BIG_QUERY.name());
    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_INSTANT.toEpochMilli()));
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_INSTANT.toEpochMilli()));
    RepeatStatus execute = clusterDataToBigQueryTasklet.execute(null, chunkContext);
    assertThat(execute).isNull();
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetLabelMapForGroup() {
    mockGetWorkload();
    when(featureFlagService.isNotEnabled(CCM_WORKLOAD_LABELS_OPTIMISATION, ACCOUNT_ID)).thenReturn(true);
    final List<InstanceBillingData> instances = ImmutableList.of(createBillingData(NAME_0), createBillingData(NAME_1));
    Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap =
        clusterDataToBigQueryTasklet.getLabelMapForClusterGroup(instances,
            ClusterDataToBigQueryTasklet.AccountClusterKey.getAccountClusterKeyFromInstanceData(instances.get(0)));
    verify(workloadRepository, times(1));
    assertEquals(labelMap,
        Collections.singletonMap(new K8SWorkloadService.WorkloadUidCacheKey(ACCOUNT_ID, CLUSTER_ID, NAME_0),
            Collections.singletonMap(LABEL_KEY, LABEL_VALUE)));
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetLabelMapForGroupEmptyWorkloads() {
    final List<InstanceBillingData> instances = ImmutableList.of(createBillingData(NAME_0), createBillingData(NAME_1));
    when(workloadRepository.getWorkload(any(), any(), any(), any())).thenReturn(Collections.emptyList());
    Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap =
        clusterDataToBigQueryTasklet.getLabelMapForClusterGroup(instances,
            ClusterDataToBigQueryTasklet.AccountClusterKey.getAccountClusterKeyFromInstanceData(instances.get(0)));
    verify(workloadRepository, times(1));
    assertEquals(labelMap, Collections.emptyMap());
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetClusterBillingDataForBatch() {
    mockGetWorkload();
    when(featureFlagService.isNotEnabled(CCM_WORKLOAD_LABELS_OPTIMISATION, ACCOUNT_ID)).thenReturn(true);
    final List<InstanceBillingData> instances = ImmutableList.of(createBillingData(NAME_0), createBillingData(NAME_1));
    List<ClusterBillingData> clusterBillingData = clusterDataToBigQueryTasklet.getClusterBillingDataForBatch(
        ACCOUNT_ID, BatchJobType.CLUSTER_DATA_TO_BIG_QUERY, instances);
    assertEquals(clusterBillingData.size(), instances.size());
    assertEquals(clusterBillingData.get(0).getLabels(), Collections.singletonList(new Label(LABEL_KEY, LABEL_VALUE)));
    assertEquals(clusterBillingData.get(1).getLabels(), Collections.emptyList());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testAddendLabel() {
    List<Label> labels = new ArrayList<>();
    Set<String> labelKeySet = new HashSet<>();
    clusterDataToBigQueryTasklet.appendLabel("k1", "v1", labelKeySet, labels);
    clusterDataToBigQueryTasklet.appendLabel("k2", "v2", labelKeySet, labels);
    clusterDataToBigQueryTasklet.appendLabel("k1", "v3", labelKeySet, labels);
    assertEquals(labels.size(), 2);
  }

  private void mockGetWorkload() {
    K8sWorkload workload = K8sWorkload.builder()
                               .accountId(ACCOUNT_ID)
                               .clusterId(CLUSTER_ID)
                               .settingId(SETTING_ID)
                               .name(NAME_0)
                               .namespace(NAMESPACE)
                               .uid(NAME_0)
                               .kind(KIND)
                               .labels(Collections.singletonMap(LABEL_KEY, LABEL_VALUE))
                               .build();
    when(workloadRepository.getWorkloadByWorkloadUid(any(), any(), any()))
        .thenReturn(Collections.singletonList(workload));
  }

  private InstanceBillingData createBillingData(@NotNull String name) {
    return InstanceBillingData.builder()
        .startTimestamp(START_TIME_MILLIS)
        .endTimestamp(END_TIME_MILLIS)
        .accountId(ACCOUNT_ID)
        .instanceId(INSTANCE_ID)
        .taskId(name)
        .clusterId(CLUSTER_ID)
        .instanceType(InstanceType.K8S_POD.name())
        .billingAmount(BigDecimal.ZERO)
        .cpuBillingAmount(BigDecimal.ZERO)
        .memoryBillingAmount(BigDecimal.ZERO)
        .idleCost(BigDecimal.ZERO)
        .cpuIdleCost(BigDecimal.ZERO)
        .memoryIdleCost(BigDecimal.ZERO)
        .systemCost(BigDecimal.ZERO)
        .cpuSystemCost(BigDecimal.ZERO)
        .memorySystemCost(BigDecimal.ZERO)
        .actualIdleCost(BigDecimal.ZERO)
        .cpuActualIdleCost(BigDecimal.ZERO)
        .memoryActualIdleCost(BigDecimal.ZERO)
        .unallocatedCost(BigDecimal.ZERO)
        .cpuUnallocatedCost(BigDecimal.ZERO)
        .memoryUnallocatedCost(BigDecimal.ZERO)
        .storageBillingAmount(BigDecimal.ZERO)
        .storageActualIdleCost(BigDecimal.ZERO)
        .storageUnallocatedCost(BigDecimal.ZERO)
        .storageUtilizationValue(0D)
        .storageRequest(0D)
        .maxStorageUtilizationValue(0D)
        .maxStorageRequest(0D)
        .orgIdentifier("orgIdentifier")
        .projectIdentifier("projectIdentifier")
        .namespace(NAMESPACE)
        .workloadName(name)
        .build();
  }
}
