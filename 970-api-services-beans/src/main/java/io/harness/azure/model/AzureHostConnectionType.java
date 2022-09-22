/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.infra.HostConnectionTypeKind;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum AzureHostConnectionType {
  HOSTNAME(HostConnectionTypeKind.HOSTNAME),
  PRIVATE_IP(HostConnectionTypeKind.PRIVATE_IP),
  PUBLIC_IP(HostConnectionTypeKind.PUBLIC_IP);

  AzureHostConnectionType(String value) {
    this.value = value;
  }

  private final String value;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static AzureHostConnectionType fromString(final String value) {
    for (AzureHostConnectionType type : AzureHostConnectionType.values()) {
      if (type.toString().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Unrecognized Azure Host Connection Type, value: %s,", value));
  }

  @JsonValue
  @Override
  public String toString() {
    return this.value;
  }
}
