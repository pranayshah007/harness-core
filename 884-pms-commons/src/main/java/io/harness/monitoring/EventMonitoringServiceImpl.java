/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.monitoring;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.protobuf.Message;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class EventMonitoringServiceImpl implements EventMonitoringService {
  private static final Long SAMPLE_SIZE = 20L;
  private static final Map<String, Long> countMap = new ConcurrentHashMap<>();

  @Inject MetricService metricService;

  public <T extends Message> void sendMetric(
      String metricName, MonitoringInfo monitoringInfo, Map<String, String> metadataMap) {
    try {
      String metricValue = String.format(metricName, monitoringInfo.getMetricPrefix());
      long newCount = countMap.compute(metricValue, (k, v) -> v == null ? 1 : ((v + 1) % SAMPLE_SIZE));
      if (newCount == 1 || (monitoringInfo.getReadTs() - monitoringInfo.getCreatedAt() > 5000)) {
        log.debug(
            String.format("Sampled the metric [%s]", String.format(metricName, monitoringInfo.getMetricPrefix())));
        metricService.recordMetric(metricValue, monitoringInfo.getReadTs() - monitoringInfo.getCreatedAt());
      }
    } catch (Exception ex) {
      log.error("Exception Occurred while recording metrics", ex);
    }
  }
}
