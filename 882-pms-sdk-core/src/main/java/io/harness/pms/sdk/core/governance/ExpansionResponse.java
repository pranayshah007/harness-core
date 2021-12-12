package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class ExpansionResponse {
  String key;
  ExpandedValue value;
  ExpansionPlacementStrategy placement;
  boolean success;
  String errorMessage;
}
