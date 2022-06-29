/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("osType")
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.OSType")
public enum OSType {
  @JsonProperty("Linux") Linux("Linux"),
  @JsonProperty("MacOS") MacOS("MacOS"),
  @JsonProperty("Windows") Windows("Windows");
  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static OSType getOSType(@JsonProperty("osType") String yamlName) {
    for (OSType osType : OSType.values()) {
      if (osType.yamlName.equalsIgnoreCase(yamlName)) {
        return osType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  OSType(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static OSType fromString(final String s) {
    return OSType.getOSType(s);
  }
}
