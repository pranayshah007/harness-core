/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.ActualIdleCostBatchJobData;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.ActualIdleCostWriterData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class ActualIdleBillingDataWriter extends EventWriter implements ItemWriter<ActualIdleCostBatchJobData> {
  @Autowired private BillingDataServiceImpl billingDataService;

  private JobParameters parameters;

  @Value
  private static class CostDistribution {
    Double total;
    Double cpu;
    Double memory;
  }

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends ActualIdleCostBatchJobData> list) throws Exception {
    BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters);
    list.forEach(entry -> {
      List<ActualIdleCostData> podsData = entry.getPodData();
      List<ActualIdleCostData> nodesData = entry.getNodeData();
      Map<String, ActualIdleCostData> parentInstanceIdToPodData = getParentInstanceIdToDataMap(podsData);
      nodesData.forEach(nodeData -> {
        String parentInstanceId = nodeData.getInstanceId();
        if (!parentInstanceIdToPodData.containsKey(parentInstanceId)) {
          InstanceData parentInstanceData =
              instanceDataService.fetchInstanceData(nodeData.getAccountId(), parentInstanceId);
          if (null != parentInstanceData) {
            parentInstanceId = parentInstanceData.getInstanceName();
          }
        }

        CostDistribution unallocatedCostForNode = new CostDistribution(nodeData.getCost() - nodeData.getSystemCost(),
            nodeData.getCpuCost() - nodeData.getCpuSystemCost(),
            nodeData.getMemoryCost() - nodeData.getMemorySystemCost());

        if (parentInstanceIdToPodData.containsKey(parentInstanceId)) {
          ActualIdleCostData podData = parentInstanceIdToPodData.get(parentInstanceId);

          unallocatedCostForNode = new CostDistribution(
              nodeData.getCost() - podData.getMemoryCost() - podData.getCpuCost() - nodeData.getSystemCost(),
              nodeData.getCpuCost() - podData.getCpuCost() - nodeData.getCpuSystemCost(),
              nodeData.getMemoryCost() - podData.getMemoryCost() - nodeData.getMemorySystemCost());

          if (isNegative(unallocatedCostForNode)) {
            log.debug("Unallocated billing amount -ve for node account {} cluster {} instance {} startdate {}: {}",
                nodeData.getAccountId(), nodeData.getClusterId(), nodeData.getInstanceId(), nodeData.getStartTime(),
                unallocatedCostForNode);
            unallocatedCostForNode = new CostDistribution(0D, 0D, 0D);
          }
        }

        CostDistribution actualIdleCostForNode =
            new CostDistribution(nodeData.getIdleCost() - unallocatedCostForNode.getTotal(),
                nodeData.getCpuIdleCost() - unallocatedCostForNode.getCpu(),
                nodeData.getMemoryIdleCost() - unallocatedCostForNode.getMemory());

        if (isNegative(actualIdleCostForNode)) {
          log.debug("Unallocated idle cost -ve for node account {} cluster {} instance {} startdate {}: {}",
              nodeData.getAccountId(), nodeData.getClusterId(), nodeData.getInstanceId(), nodeData.getStartTime(),
              actualIdleCostForNode);
          actualIdleCostForNode = new CostDistribution(0D, 0D, 0D);
        }

        billingDataService.update(ActualIdleCostWriterData.builder()
                                      .accountId(nodeData.getAccountId())
                                      .instanceId(nodeData.getInstanceId())
                                      .parentInstanceId(nodeData.getParentInstanceId())
                                      .unallocatedCost(BigDecimal.valueOf(unallocatedCostForNode.getTotal()))
                                      .cpuUnallocatedCost(BigDecimal.valueOf(unallocatedCostForNode.getCpu()))
                                      .memoryUnallocatedCost(BigDecimal.valueOf(unallocatedCostForNode.getMemory()))
                                      .actualIdleCost(BigDecimal.valueOf(actualIdleCostForNode.getTotal()))
                                      .cpuActualIdleCost(BigDecimal.valueOf(actualIdleCostForNode.getCpu()))
                                      .memoryActualIdleCost(BigDecimal.valueOf(actualIdleCostForNode.getMemory()))
                                      .startTime(nodeData.getStartTime())
                                      .clusterId(nodeData.getClusterId())
                                      .build(),
            batchJobType);
      });
    });
  }

  private static boolean isNegative(final CostDistribution costDistribution) {
    return costDistribution.getTotal() < 0D || costDistribution.getMemory() < 0D || costDistribution.getCpu() < 0D;
  }

  private Map<String, ActualIdleCostData> getParentInstanceIdToDataMap(List<ActualIdleCostData> podsData) {
    Map<String, ActualIdleCostData> parentInstanceIdToData = new HashMap<>();
    if (podsData != null) {
      podsData.forEach(entry -> { parentInstanceIdToData.put(entry.getParentInstanceId(), entry); });
    }
    return parentInstanceIdToData;
  }
}
