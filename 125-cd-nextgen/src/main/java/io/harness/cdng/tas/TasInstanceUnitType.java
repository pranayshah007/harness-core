package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum TasInstanceUnitType {
  @JsonProperty(TasConstants.PERCENTAGE) PERCENTAGE(TasConstants.PERCENTAGE),
  @JsonProperty(TasConstants.COUNT) COUNT(TasConstants.COUNT);

  private final String displayName;

  TasInstanceUnitType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static TasInstanceUnitType fromString(String typeEnum) {
    for (TasInstanceUnitType enumValue : TasInstanceUnitType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
