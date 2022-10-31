package io.harness.ng.chaos;

import io.harness.cdng.chaos.ChaosStepNotifyData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosStepNotifyResponse {
  String notifyId;
  ChaosStepNotifyData data;
}
