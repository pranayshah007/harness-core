package io.harness.ng.core.dashboard;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InfrastructureInfo {
  String infrastructureIdentifier;
  String infrastructureName;
}
