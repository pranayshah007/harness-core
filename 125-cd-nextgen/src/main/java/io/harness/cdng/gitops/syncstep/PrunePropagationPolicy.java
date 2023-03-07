package io.harness.cdng.gitops.syncstep;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PrunePropagationPolicy {
  @JsonProperty("foreground") FOREGROUND("foreground"),
  @JsonProperty("background") BACKGROUND("background"),
  @JsonProperty("orphan") ORPHAN("orphan");

  private final String value;

  PrunePropagationPolicy(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
