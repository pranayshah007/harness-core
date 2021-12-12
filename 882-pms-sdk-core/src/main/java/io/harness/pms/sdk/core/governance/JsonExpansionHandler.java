package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;

@OwnedBy(PIPELINE)
public interface JsonExpansionHandler {
  ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata);
}
