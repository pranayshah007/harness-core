package io.harness.delegate.beans.connector.pcfconnector;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PcfCredentialType {
  @JsonProperty(PcfConstants.MANUAL_CONFIG) MANUAL_CREDENTIALS(PcfConstants.MANUAL_CONFIG, true);

  private final String displayName;
  private final boolean decryptable;

  PcfCredentialType(String displayName, boolean decryptable) {
    this.displayName = displayName;
    this.decryptable = decryptable;
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

  @JsonIgnore
  public boolean isDecryptable() {
    return decryptable;
  }
  public static PcfCredentialType fromString(String typeEnum) {
    for (PcfCredentialType enumValue : PcfCredentialType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
