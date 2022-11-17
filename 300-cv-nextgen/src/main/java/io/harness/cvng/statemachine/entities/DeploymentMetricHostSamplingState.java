package io.harness.cvng.statemachine.entities;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class DeploymentMetricHostSamplingState extends HostSamplingState {
  private final StateType type = StateType.DEPLOYMENT_METRIC_HOST_SAMPLING_STATE;
}
