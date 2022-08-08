package io.harness.ng.chaos.client;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosApplyManifestResponse {
  String taskId;
  String status; // SUCCESS FAILED
}
