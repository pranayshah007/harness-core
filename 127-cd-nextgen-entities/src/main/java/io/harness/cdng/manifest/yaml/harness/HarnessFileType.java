/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.harness;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum HarnessFileType {
  @JsonProperty("FileStore") FILE_STORE("FileStore"),
  @JsonProperty("Encrypted") ENCRYPTED("Encrypted");

  private final String value;

  HarnessFileType(String value) {
    this.value = value;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static HarnessFileType fromString(final String value) {
    for (HarnessFileType type : HarnessFileType.values()) {
      if (type.toString().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Unrecognized Harness File type scope type, value: %s,", value));
  }

  @JsonValue
  @Override
  public String toString() {
    return this.value;
  }
}
