package io.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class MaxConcurrencyConfig {
  @JsonProperty("free") int free;
  @JsonProperty("team") int team;
  @JsonProperty("enterprise") int enterprise;
}
