package io.harness.cdng.chaos;

import io.harness.tasks.ResponseData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosStepNotifyData implements ResponseData {
  enum Phase { PENDING, ERROR, HALTED, COMPLETED }

  Phase phase;
  String experimentRunId;
  Double resiliencyScore;
  Integer faultsPassed;
  Integer faultsFailed;
  Integer faultsAwaited;
  Integer faultsStopped;
  Integer faultsNa;
  Integer totalFaults;

  public boolean isFailed() {
    return phase != Phase.COMPLETED;
  }
}
