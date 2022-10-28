package io.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MaxConcurrencyConfig {
  @JsonProperty("free") int free;
  @JsonProperty("team") int team;
  @JsonProperty("enterprise") int enterprise;
}
