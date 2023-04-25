/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;

import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class K8sUtilizationMetricsWriter extends EventWriter implements ItemWriter<List<String>> {
  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  private static final int BATCH_SIZE = 50;
  private JobParameters parameters;

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends List<String>> lists) {
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    long startDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_START_DATE));
    long endDate = Long.parseLong(parameters.getString(CCMJobConstants.JOB_END_DATE));
    log.info("Published batch size is K8sUtilizationMetricsWriter {} ", lists.size());
    List<InstanceUtilizationData> instanceUtilizationDataList = new ArrayList<>();

    lists.forEach(list -> Lists.partition(list, BATCH_SIZE).forEach(instanceIdsBatch -> {
      Map<String, InstanceUtilizationData> aggregatedUtilizationData =
          k8sUtilizationGranularDataService.getAggregatedUtilizationData(
              accountId, instanceIdsBatch, startDate, endDate);

      for (Map.Entry<String, InstanceUtilizationData> entry : aggregatedUtilizationData.entrySet()) {
        InstanceUtilizationData instanceUtilizationData = entry.getValue();
        String clusterId = instanceUtilizationData.getClusterId();
        String settingId = instanceUtilizationData.getSettingId();
        String instanceId = entry.getKey();

        PrunedInstanceData instanceData =
            instanceDataService.fetchPrunedInstanceDataWithName(accountId, clusterId, instanceId, startDate);
        // Initialisation to 100% Utilisation

        if (instanceData.getInstanceId() != null) {
          double cpuAvgPercentage = 1;
          double cpuMaxPercentage = 1;
          double memoryAvgPercentage = 1;
          double memoryMaxPercentage = 1;

          double cpuAvgValue = instanceUtilizationData.getCpuUtilizationAvg();
          double cpuMaxValue = instanceUtilizationData.getCpuUtilizationMax();
          double memoryAvgValue = instanceUtilizationData.getMemoryUtilizationAvg();
          double memoryMaxValue = instanceUtilizationData.getMemoryUtilizationMax();

          Double limitCpuUnits =
              instanceData.getLimitResource() == null ? null : instanceData.getLimitResource().getCpuUnits();

          Double limitMemoryValue =
              instanceData.getLimitResource() == null ? null : instanceData.getLimitResource().getMemoryMb();

          if (limitCpuUnits != null && limitCpuUnits > 0) {
            if (cpuAvgValue > 4 * limitCpuUnits) {
              log.warn(
                  "Limit Exceeded CPU AVG :: accountId: {}, instanceId: {}, cpuUtilizationAvg: {}, limitCpuUnits: {}",
                  accountId, instanceId, cpuAvgValue, limitCpuUnits);
              cpuAvgValue = 4 * limitCpuUnits;
            }
            if (cpuMaxValue > 4 * limitCpuUnits) {
              log.warn(
                  "Limit Exceeded CPU MAX :: accountId: {}, instanceId: {}, cpuUtilizationMax: {}, limitCpuUnits: {}",
                  accountId, instanceId, cpuMaxValue, limitCpuUnits);
              cpuMaxValue = 4 * cpuMaxValue;
            }
          }

          if (limitMemoryValue != null && limitMemoryValue > 0) {
            if (memoryAvgValue > 4 * limitMemoryValue) {
              log.warn(
                  "Limit Exceeded Memory AVG :: accountId: {}, instanceId: {}, memoryUtilizationAvg: {}, limitMemoryValue: {}",
                  accountId, instanceId, memoryAvgValue, limitMemoryValue);
              memoryAvgValue = 4 * limitMemoryValue;
            }
            if (memoryMaxValue > 4 * limitMemoryValue) {
              log.warn(
                  "Limit Exceeded Memory MAX :: accountId: {}, instanceId: {}, memoryUtilizationMax: {}, limitMemoryValue: {}",
                  accountId, instanceId, memoryMaxValue, limitMemoryValue);
              memoryMaxValue = 4 * limitMemoryValue;
            }
          }

          if (instanceData.getTotalResource() != null) {
            Double totalCpuResource = instanceData.getTotalResource().getCpuUnits();
            Double totalMemoryResource = instanceData.getTotalResource().getMemoryMb();
            if (totalCpuResource != 0) {
              cpuAvgPercentage = cpuAvgValue / totalCpuResource;
              cpuMaxPercentage = cpuMaxValue / totalCpuResource;
            }
            if (totalMemoryResource != 0) {
              memoryAvgPercentage = memoryAvgValue / totalMemoryResource;
              memoryMaxPercentage = memoryMaxValue / totalMemoryResource;
            }
          }
          instanceUtilizationData.setInstanceId(instanceData.getInstanceId());
          instanceUtilizationData.setSettingId(settingId);
          instanceUtilizationData.setClusterId(clusterId);
          instanceUtilizationData.setCpuUtilizationAvg(cpuAvgPercentage);
          instanceUtilizationData.setCpuUtilizationMax(cpuMaxPercentage);
          instanceUtilizationData.setMemoryUtilizationAvg(memoryAvgPercentage);
          instanceUtilizationData.setMemoryUtilizationMax(memoryMaxPercentage);
          instanceUtilizationData.setCpuUtilizationAvgValue(cpuAvgValue);
          instanceUtilizationData.setCpuUtilizationMaxValue(cpuMaxValue);
          instanceUtilizationData.setMemoryUtilizationAvgValue(memoryAvgValue);
          instanceUtilizationData.setMemoryUtilizationMaxValue(memoryMaxValue);
          instanceUtilizationData.setStartTimestamp(startDate);
          instanceUtilizationData.setEndTimestamp(endDate);

          instanceUtilizationDataList.add(instanceUtilizationData);
        }
      }
    }));
    utilizationDataService.create(instanceUtilizationDataList);
  }
}
