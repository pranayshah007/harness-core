/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.billing.timeseries.service.impl.ActualIdleBillingDataServiceImpl;
import io.harness.batch.processing.ccm.ActualIdleCostBatchJobData;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;

@RunWith(MockitoJUnitRunner.class)
public class ActualIdleBillingDataReaderTest extends BatchProcessingTestBase {
  @Inject @InjectMocks private ActualIdleBillingDataReader actualIdleBillingDataReader;
  @Mock private ActualIdleBillingDataServiceImpl actualIdleBillingDataService;
  @Mock private JobParameters parameters;
  @Mock private AtomicBoolean runOnlyOnce;

  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String INSTANCE_ID = "instanceId";
  private static final double COST = 1.6;
  private static final double CPU_COST = 0.8;
  private static final double MEMORY_COST = 0.8;
  private static final double IDLE_COST = 0.8;
  private static final double CPU_IDLE_COST = 0.4;
  private static final double MEMORY_IDLE_COST = 0.4;
  private static final double SYSTEM_COST = 0.4;
  private static final double CPU_SYSTEM_COST = 0.2;
  private static final double MEMORY_SYSTEM_COST = 0.2;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(actualIdleBillingDataService.getActualIdleCostDataForNodes(
             ACCOUNT_ID, START_TIME_MILLIS, END_TIME_MILLIS, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY))
        .thenReturn(Collections.singletonList(mockActualIdleCostData(INSTANCE_ID, "PARENT_INSTANCE_ID")));
    when(actualIdleBillingDataService.getActualIdleCostDataForPods(
             ACCOUNT_ID, START_TIME_MILLIS, END_TIME_MILLIS, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY))
        .thenReturn(Collections.singletonList(mockActualIdleCostData(null, INSTANCE_ID)));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.BATCH_JOB_TYPE))
        .thenReturn(BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY.name());
    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_TIME_MILLIS));
    runOnlyOnce = new AtomicBoolean(false);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testActualIdleBillingDataReader() {
    ActualIdleCostBatchJobData data = actualIdleBillingDataReader.read();
    assertThat(data).isNotNull();
    List<ActualIdleCostData> nodeData = data.getNodeData();
    List<ActualIdleCostData> podData = data.getPodData();

    assertThat(nodeData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(nodeData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(nodeData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(nodeData.get(0).getParentInstanceId()).isEqualTo("PARENT_INSTANCE_ID");
    assertThat(nodeData.get(0).getCost()).isEqualTo(COST);
    assertThat(nodeData.get(0).getCpuCost()).isEqualTo(CPU_COST);
    assertThat(nodeData.get(0).getMemoryCost()).isEqualTo(MEMORY_COST);
    assertThat(nodeData.get(0).getIdleCost()).isEqualTo(IDLE_COST);
    assertThat(nodeData.get(0).getCpuIdleCost()).isEqualTo(CPU_IDLE_COST);
    assertThat(nodeData.get(0).getMemoryIdleCost()).isEqualTo(MEMORY_IDLE_COST);
    assertThat(nodeData.get(0).getSystemCost()).isEqualTo(SYSTEM_COST);
    assertThat(nodeData.get(0).getCpuSystemCost()).isEqualTo(CPU_SYSTEM_COST);
    assertThat(nodeData.get(0).getMemorySystemCost()).isEqualTo(MEMORY_SYSTEM_COST);

    assertThat(podData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(podData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(podData.get(0).getInstanceId()).isEqualTo(null);
    assertThat(podData.get(0).getParentInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(podData.get(0).getCost()).isEqualTo(COST);
    assertThat(podData.get(0).getCpuCost()).isEqualTo(CPU_COST);
    assertThat(podData.get(0).getMemoryCost()).isEqualTo(MEMORY_COST);
    assertThat(podData.get(0).getIdleCost()).isEqualTo(IDLE_COST);
    assertThat(podData.get(0).getCpuIdleCost()).isEqualTo(CPU_IDLE_COST);
    assertThat(podData.get(0).getMemoryIdleCost()).isEqualTo(MEMORY_IDLE_COST);
    assertThat(podData.get(0).getSystemCost()).isEqualTo(SYSTEM_COST);
    assertThat(podData.get(0).getCpuSystemCost()).isEqualTo(CPU_SYSTEM_COST);
    assertThat(podData.get(0).getMemorySystemCost()).isEqualTo(MEMORY_SYSTEM_COST);
  }

  private ActualIdleCostData mockActualIdleCostData(String instanceId, String parentInstanceId) {
    return ActualIdleCostData.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(instanceId)
        .parentInstanceId(parentInstanceId)
        .cost(COST)
        .cpuCost(CPU_COST)
        .memoryCost(MEMORY_COST)
        .idleCost(IDLE_COST)
        .cpuIdleCost(CPU_IDLE_COST)
        .memoryIdleCost(MEMORY_IDLE_COST)
        .systemCost(SYSTEM_COST)
        .cpuSystemCost(CPU_SYSTEM_COST)
        .memorySystemCost(MEMORY_SYSTEM_COST)
        .build();
  }
}
