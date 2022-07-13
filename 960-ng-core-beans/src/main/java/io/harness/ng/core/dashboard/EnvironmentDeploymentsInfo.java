package io.harness.ng.core.dashboard;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EnvironmentDeploymentsInfo {
  private String envId;
  private String envName;
  private String envType;
}
