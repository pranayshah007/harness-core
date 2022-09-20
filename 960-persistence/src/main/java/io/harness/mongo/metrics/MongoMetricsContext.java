package io.harness.mongo.metrics;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.AutoMetricContext;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(PL)
public class MongoMetricsContext extends AutoMetricContext {
  public MongoMetricsContext(String namespace, String containerName, String serverAddress, String clientDescription) {
    put("namespace", namespace);
    put("containerName", containerName);
    put("serverAddress", serverAddress);
    put("clientDescription", clientDescription);
  }
}
