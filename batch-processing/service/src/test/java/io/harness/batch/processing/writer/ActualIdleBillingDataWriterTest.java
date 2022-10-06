/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.ActualIdleCostBatchJobData;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.ActualIdleCostWriterData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;

@RunWith(MockitoJUnitRunner.class)
public class ActualIdleBillingDataWriterTest extends CategoryTest {
  @Inject @InjectMocks private ActualIdleBillingDataWriter actualIdleBillingDataWriter;
  @Mock private BillingDataServiceImpl billingDataService;
  @Mock private JobParameters parameters;

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
  private static final BigDecimal zeroPointZero = BigDecimal.valueOf(0.0D);
  private static final Offset<BigDecimal> bigDecimalOffset = within(new BigDecimal("0.00000000000001"));

  private ActualIdleCostBatchJobData actualIdleCostBatchJobData;

  @Before
  public void setup() {
    when(parameters.getString(CCMJobConstants.BATCH_JOB_TYPE)).thenReturn(BatchJobType.ACTUAL_IDLE_COST_BILLING.name());
    when(billingDataService.create(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldWriteActualIdleCostData() throws Exception {
    actualIdleCostBatchJobData = mockActualIdleCostBatchJobData(
        INSTANCE_ID, IDLE_COST, CPU_IDLE_COST, MEMORY_IDLE_COST, COST / 2, CPU_COST / 2, MEMORY_COST / 2);
    actualIdleBillingDataWriter.write(Collections.singletonList(actualIdleCostBatchJobData));
    ArgumentCaptor<ActualIdleCostWriterData> actualIdleCostWriterDataArgumentCaptor =
        ArgumentCaptor.forClass(ActualIdleCostWriterData.class);
    ArgumentCaptor<BatchJobType> batchJobTypeArgumentCaptor = ArgumentCaptor.forClass(BatchJobType.class);
    verify(billingDataService, atMost(1))
        .update(actualIdleCostWriterDataArgumentCaptor.capture(), batchJobTypeArgumentCaptor.capture());
    List<ActualIdleCostWriterData> actualIdleCostWriterData = actualIdleCostWriterDataArgumentCaptor.getAllValues();
    assertThat(actualIdleCostWriterData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(actualIdleCostWriterData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actualIdleCostWriterData.get(0).getParentInstanceId()).isEqualTo("PARENT_INSTANCE_ID");
    assertThat(actualIdleCostWriterData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(actualIdleCostWriterData.get(0).getActualIdleCost())
        .isCloseTo(BigDecimal.valueOf(0.4), bigDecimalOffset);
    assertThat(actualIdleCostWriterData.get(0).getCpuActualIdleCost()).isEqualTo(BigDecimal.valueOf(0.2));
    assertThat(actualIdleCostWriterData.get(0).getMemoryActualIdleCost()).isEqualTo(BigDecimal.valueOf(0.2));
    assertThat(actualIdleCostWriterData.get(0).getUnallocatedCost())
        .isCloseTo(BigDecimal.valueOf(0.4), bigDecimalOffset);
    assertThat(actualIdleCostWriterData.get(0).getCpuUnallocatedCost()).isEqualTo(BigDecimal.valueOf(0.2));
    assertThat(actualIdleCostWriterData.get(0).getMemoryUnallocatedCost()).isEqualTo(BigDecimal.valueOf(0.2));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldWriteActualIdleCostDataWhenUtilDataIsNotPresent() throws Exception {
    actualIdleCostBatchJobData =
        mockActualIdleCostBatchJobData(INSTANCE_ID, 0.0, 0.0, 0.0, COST / 2, CPU_COST / 2, MEMORY_COST / 2);
    actualIdleBillingDataWriter.write(Collections.singletonList(actualIdleCostBatchJobData));
    ArgumentCaptor<ActualIdleCostWriterData> actualIdleCostWriterDataArgumentCaptor =
        ArgumentCaptor.forClass(ActualIdleCostWriterData.class);
    ArgumentCaptor<BatchJobType> batchJobTypeArgumentCaptor = ArgumentCaptor.forClass(BatchJobType.class);
    verify(billingDataService, atMost(1))
        .update(actualIdleCostWriterDataArgumentCaptor.capture(), batchJobTypeArgumentCaptor.capture());
    List<ActualIdleCostWriterData> actualIdleCostWriterData = actualIdleCostWriterDataArgumentCaptor.getAllValues();
    assertThat(actualIdleCostWriterData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(actualIdleCostWriterData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actualIdleCostWriterData.get(0).getParentInstanceId()).isEqualTo("PARENT_INSTANCE_ID");
    assertThat(actualIdleCostWriterData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(actualIdleCostWriterData.get(0).getActualIdleCost()).isEqualTo(zeroPointZero);
    assertThat(actualIdleCostWriterData.get(0).getCpuActualIdleCost()).isEqualTo(zeroPointZero);
    assertThat(actualIdleCostWriterData.get(0).getMemoryActualIdleCost()).isEqualTo(zeroPointZero);
    assertThat(actualIdleCostWriterData.get(0).getUnallocatedCost())
        .isCloseTo(BigDecimal.valueOf(0.4), bigDecimalOffset);
    assertThat(actualIdleCostWriterData.get(0).getCpuUnallocatedCost()).isEqualTo(BigDecimal.valueOf(0.2));
    assertThat(actualIdleCostWriterData.get(0).getMemoryUnallocatedCost()).isEqualTo(BigDecimal.valueOf(0.2));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testActualIdleCostDataWhenUnallocatedCostIsNegative() throws Exception {
    actualIdleCostBatchJobData = mockActualIdleCostBatchJobData(
        INSTANCE_ID, IDLE_COST, CPU_IDLE_COST, MEMORY_IDLE_COST, COST * 2, CPU_COST * 2, MEMORY_COST * 2);
    actualIdleBillingDataWriter.write(Collections.singletonList(actualIdleCostBatchJobData));
    ArgumentCaptor<ActualIdleCostWriterData> actualIdleCostWriterDataArgumentCaptor =
        ArgumentCaptor.forClass(ActualIdleCostWriterData.class);
    ArgumentCaptor<BatchJobType> batchJobTypeArgumentCaptor = ArgumentCaptor.forClass(BatchJobType.class);
    verify(billingDataService, atMost(1))
        .update(actualIdleCostWriterDataArgumentCaptor.capture(), batchJobTypeArgumentCaptor.capture());
    List<ActualIdleCostWriterData> actualIdleCostWriterData = actualIdleCostWriterDataArgumentCaptor.getAllValues();
    assertThat(actualIdleCostWriterData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(actualIdleCostWriterData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(actualIdleCostWriterData.get(0).getParentInstanceId()).isEqualTo("PARENT_INSTANCE_ID");
    assertThat(actualIdleCostWriterData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(actualIdleCostWriterData.get(0).getActualIdleCost()).isEqualTo(BigDecimal.valueOf(IDLE_COST));
    assertThat(actualIdleCostWriterData.get(0).getCpuActualIdleCost()).isEqualTo(BigDecimal.valueOf(CPU_IDLE_COST));
    assertThat(actualIdleCostWriterData.get(0).getMemoryActualIdleCost())
        .isEqualTo(BigDecimal.valueOf(MEMORY_IDLE_COST));
    assertThat(actualIdleCostWriterData.get(0).getUnallocatedCost()).isEqualTo(zeroPointZero);
    assertThat(actualIdleCostWriterData.get(0).getCpuUnallocatedCost()).isEqualTo(zeroPointZero);
    assertThat(actualIdleCostWriterData.get(0).getMemoryUnallocatedCost()).isEqualTo(zeroPointZero);
  }

  private ActualIdleCostData mockActualIdleCostDataForNode(
      String instanceId, String parentInstanceId, double idleCost, double cpuIdleCost, double memoryIdleCost) {
    return ActualIdleCostData.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(instanceId)
        .parentInstanceId(parentInstanceId)
        .cost(COST)
        .cpuCost(CPU_COST)
        .memoryCost(MEMORY_COST)
        .idleCost(idleCost)
        .cpuIdleCost(cpuIdleCost)
        .memoryIdleCost(memoryIdleCost)
        .systemCost(SYSTEM_COST)
        .cpuSystemCost(CPU_SYSTEM_COST)
        .memorySystemCost(MEMORY_SYSTEM_COST)
        .build();
  }

  private ActualIdleCostData mockActualIdleCostDataForPod(
      String instanceId, String parentInstanceId, double podCost, double podCpuCost, double podMemoryCost) {
    return ActualIdleCostData.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(instanceId)
        .parentInstanceId(parentInstanceId)
        .cost(podCost)
        .cpuCost(podCpuCost)
        .memoryCost(podMemoryCost)
        .idleCost(IDLE_COST / 2)
        .cpuIdleCost(CPU_IDLE_COST / 2)
        .memoryIdleCost(MEMORY_IDLE_COST / 2)
        .systemCost(SYSTEM_COST / 2)
        .cpuSystemCost(CPU_SYSTEM_COST / 2)
        .memorySystemCost(MEMORY_SYSTEM_COST / 2)
        .build();
  }

  private ActualIdleCostBatchJobData mockActualIdleCostBatchJobData(String instanceId, double idleCost,
      double cpuIdleCost, double memoryIdleCost, double podCost, double podCpuCost, double podMemoryCost) {
    return ActualIdleCostBatchJobData.builder()
        .nodeData(Collections.singletonList(
            mockActualIdleCostDataForNode(instanceId, "PARENT_INSTANCE_ID", idleCost, cpuIdleCost, memoryIdleCost)))
        .podData(Collections.singletonList(
            mockActualIdleCostDataForPod(null, instanceId, podCost, podCpuCost, podMemoryCost)))
        .build();
  }
}
