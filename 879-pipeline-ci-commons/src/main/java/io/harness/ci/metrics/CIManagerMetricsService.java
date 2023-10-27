package io.harness.ci.metrics;

public interface CIManagerMetricsService {
  public void recordStepExecutionTime(String status, double time, String metricName);
}
