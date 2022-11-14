/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instancesyncmonitoring.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.beans.AccountMetricContext;
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
  public static final String NG_SUFFIX_PATTERN = "%s_ng";

  public void recordMetrics(String accountId, boolean isNg, boolean isNewDeployment, long duration) {
    String countMetricName = isNewDeployment ? NEW_DEPLOYMENT_METRIC_NAME : PERPETUAL_TASK_METRIC_NAME;
    String durationMetricName = String.format(DURATION_METRIC_PATTERN, countMetricName);
    if (isNg) {
      countMetricName = String.format(NG_SUFFIX_PATTERN, countMetricName);
      durationMetricName = String.format(NG_SUFFIX_PATTERN, durationMetricName);
    }
    recordMetric(accountId, countMetricName);
    recordMetricDuration(accountId, durationMetricName, Duration.ofMillis(duration));
  }

  private void recordMetric(String accountId, String metricName) {
    try (AccountMetricContext ignore = new AccountMetricContext(accountId)) {
      metricService.incCounter(metricName);
    }
  }

  private void recordMetricDuration(String accountId, String metricName, Duration duration) {
    try (AccountMetricContext ignore = new AccountMetricContext(accountId)) {
      metricService.recordDuration(metricName, duration);
    }
  }
}
