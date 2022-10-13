package io.harness.cdng.chaos;

import io.harness.pms.sdk.core.data.Outcome;
import io.harness.tasks.ResponseData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosStepNotifyData implements ResponseData, Outcome {
  String phase;
  String experimentRunId;
  Double resiliencyScore;
  Integer faultsPassed;
  Integer faultsFailed;
  Integer faultsAwaited;
  Integer faultsStopped;
  Integer faultsNa;
  Integer totalFaults;

  public boolean isSuccess() {
    return phase != null && phase.equalsIgnoreCase("completed");
  }
}
