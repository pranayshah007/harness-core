package io.harness.ci.metrics.beans;

import io.harness.metrics.AutoMetricContext;

public class CIManagerStepMetricContext extends AutoMetricContext {
  public CIManagerStepMetricContext(String stepStatus) {
    put("stepStatus", stepStatus);
  }
}
