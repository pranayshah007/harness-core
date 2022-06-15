/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.batch.processing.ccm.UtilizationInstanceType.K8S_POD;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.timeseries.data.K8sGranularUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.AggregatedUsage;
import io.harness.event.payloads.PodMetric;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class PodUtilizationMetricsWriter extends EventWriter implements ItemWriter<PublishedMessage> {
  private static final String INSTANCE_ID_PATTERN = "%s/%s";

  @Autowired private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;

  @Override
  public void write(List<? extends PublishedMessage> publishedMessages) {
    log.info("Published batch size is PodUtilizationMetricsWriter {} ", publishedMessages.size());
    List<K8sGranularUtilizationData> k8sGranularUtilizationDataList = new ArrayList<>();
    publishedMessages.stream()
        .filter(publishedMessage -> publishedMessage.getType().equals(EventTypeConstants.POD_UTILIZATION))
        .forEach(publishedMessage -> {
          String accountId = publishedMessage.getAccountId();
          PodMetric podUtilizationMetric = (PodMetric) publishedMessage.getMessage();
          log.debug("Pod Utilization {} ", podUtilizationMetric);

          AggregatedUsage aggregatedUsage = podUtilizationMetric.getAggregatedUsage();

          if (aggregatedUsage.getMaxCpuNano() > 0) {
            long endTime = podUtilizationMetric.getTimestamp().getSeconds() * 1000;
            long startTime = endTime - (podUtilizationMetric.getWindow().getSeconds() * 1000);
            double cpuUnits = K8sResourceUtils.getCpuUnits(aggregatedUsage.getAvgCpuNano());
            double memoryMb = K8sResourceUtils.getMemoryMb(aggregatedUsage.getAvgMemoryByte());
            double maxCpuUnits = K8sResourceUtils.getCpuUnits(aggregatedUsage.getMaxCpuNano());
            double maxMemoryMb = K8sResourceUtils.getMemoryMb(aggregatedUsage.getMaxMemoryByte());

            K8sGranularUtilizationData k8sGranularUtilizationData =
                K8sGranularUtilizationData.builder()
                    .accountId(accountId)
                    .instanceId(podUtilizationMetric.getName())
                    .actualInstanceId(String.format(
                        INSTANCE_ID_PATTERN, podUtilizationMetric.getNamespace(), podUtilizationMetric.getName()))
                    .instanceType(K8S_POD)
                    .clusterId(podUtilizationMetric.getClusterId())
                    .settingId(podUtilizationMetric.getCloudProviderId())
                    .startTimestamp(startTime)
                    .endTimestamp(endTime)
                    .cpu(cpuUnits)
                    .memory(memoryMb)
                    .maxCpu(maxCpuUnits)
                    .maxMemory(maxMemoryMb)
                    .build();

            k8sGranularUtilizationDataList.add(k8sGranularUtilizationData);
          }
        });

    k8sUtilizationGranularDataService.create(k8sGranularUtilizationDataList);
  }
}
