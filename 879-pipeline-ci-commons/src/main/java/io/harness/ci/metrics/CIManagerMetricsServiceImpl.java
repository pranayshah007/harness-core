package io.harness.ci.metrics;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.metrics.beans.CIManagerStepMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIManagerMetricsServiceImpl implements CIManagerMetricsService {
  private final MetricService metricService;

  public void recordStepExecutionTime(String status, double time, String metricName) {
    try (CIManagerStepMetricContext ignore = new CIManagerStepMetricContext(status)) {
      metricService.recordMetric(metricName, time);
    }
  }
}
