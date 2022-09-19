package io.harness.ng.chaos;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosStepNotifyResponse {
  String notifyId;
  ChaosStepNotifyData data;
}
