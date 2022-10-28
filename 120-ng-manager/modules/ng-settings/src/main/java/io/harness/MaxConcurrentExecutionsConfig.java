package io.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MaxConcurrentExecutionsConfig {
  @JsonProperty("maxConcurrentPipelinesConfig") MaxConcurrencyConfig maxConcurrentPipelinesConfig;
  @JsonProperty("maxConcurrentNodesConfig") MaxConcurrencyConfig maxConcurrentNodesConfig;
}
