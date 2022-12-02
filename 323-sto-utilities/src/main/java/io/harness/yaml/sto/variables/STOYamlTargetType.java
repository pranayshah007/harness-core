/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.sto.variables;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("stoYamlTargetType")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlTargetType")
public enum STOYamlTargetType {
  @JsonProperty("repository") REPOSITORY("repository"),
  @JsonProperty("container") CONTAINER("container"),
  @JsonProperty("instance") INSTANCE("instance"),
  @JsonProperty("configuration") CONFIGURATION("configuration");
  private final String yamlName;

  STOYamlTargetType(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlTargetType getValue(@JsonProperty("type") String yamlName) {
    for (STOYamlTargetType value : STOYamlTargetType.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlTargetType fromString(final String s) {
    return STOYamlTargetType.getValue(s);
  }
}
