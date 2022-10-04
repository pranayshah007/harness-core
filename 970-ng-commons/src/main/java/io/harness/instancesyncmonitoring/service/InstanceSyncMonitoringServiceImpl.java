/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instancesyncmonitoring.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.instancesyncmonitoring.model.InstanceSyncMetricContext;
import io.harness.instancesyncmonitoring.model.InstanceSyncMetricDetails;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InstanceSyncMonitoringServiceImpl implements InstanceSyncMonitoringService {
  @Inject private MetricService metricService;
  public static final String NEW_DEPLOYMENT_METRIC_NAME = "instance_sync_new_deployment";
  public static final String PERPETUAL_TASK_METRIC_NAME = "instance_sync_perpetual_task";
  public static final String DURATION_METRIC_PATTERN = "%s_duration";
  public static final String SUCCESS_STATUS = "Success";
  public static final String FAILED_STATUS = "Failed";

  public void recordMetrics(
      InstanceSyncMetricDetails instanceSyncMetricDetails, boolean isNewDeployment, long duration) {
    String countMetricName = isNewDeployment ? NEW_DEPLOYMENT_METRIC_NAME : PERPETUAL_TASK_METRIC_NAME;
    String durationMetricName = String.format(DURATION_METRIC_PATTERN, countMetricName);
    recordMetric(instanceSyncMetricDetails, countMetricName);
    recordMetricDuration(instanceSyncMetricDetails, durationMetricName, Duration.ofMillis(duration));
  }

  private void recordMetric(InstanceSyncMetricDetails instanceSyncMetricDetails, String metricName) {
    try (InstanceSyncMetricContext ignore = new InstanceSyncMetricContext(instanceSyncMetricDetails)) {
      metricService.incCounter(metricName);
    }
  }

  private void recordMetricDuration(
      InstanceSyncMetricDetails instanceSyncMetricDetails, String metricName, Duration duration) {
    try (InstanceSyncMetricContext ignore = new InstanceSyncMetricContext(instanceSyncMetricDetails)) {
      metricService.recordDuration(metricName, duration);
    }
  }
}
