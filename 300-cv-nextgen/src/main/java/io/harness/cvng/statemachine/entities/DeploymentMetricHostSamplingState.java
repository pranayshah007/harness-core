package io.harness.cvng.statemachine.entities;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@NoArgsConstructor
@Slf4j
public class DeploymentMetricHostSamplingState extends HostSamplingState {
  private final StateType type = StateType.DEPLOYMENT_METRIC_HOST_SAMPLING_STATE;
}
