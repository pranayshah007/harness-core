package io.harness.ng.chaos;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosK8sRequest {
  String accountId;
  String delegateId;
  String k8sConnectorId;
  String k8sManifest;
  String commandType;
  String commandName;
}
