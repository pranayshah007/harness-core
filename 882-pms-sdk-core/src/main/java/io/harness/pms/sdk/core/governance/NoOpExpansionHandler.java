package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;

import com.fasterxml.jackson.databind.JsonNode;

@OwnedBy(PIPELINE)
public class NoOpExpansionHandler implements JsonExpansionHandler {
  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata) {
    return ExpansionResponse.builder()
        .success(true)
        .errorMessage("")
        .key("")
        .value(StringExpandedValue.builder().value("").build())
        .placement(ExpansionPlacementStrategy.PARALLEL)
        .build();
  }
}
