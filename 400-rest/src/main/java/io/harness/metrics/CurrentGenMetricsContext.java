package io.harness.metrics;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(PL)
public class CurrentGenMetricsContext extends AutoMetricContext {
  public CurrentGenMetricsContext(String namespace, String containerName) {
    put("namespace", namespace);
    put("containerName", containerName);
  }
}
