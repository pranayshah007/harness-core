package io.harness.cdng.gitops.syncstep;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SyncResources {
  @JsonProperty("All") ALL("All"),
  @JsonProperty("OutOfSync") OUT_OF_SYNC("OutOfSync");

  private final String value;

  SyncResources(String value) {
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
