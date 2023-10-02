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
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("stoYamlLogLevel")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlLogLevel")
public enum STOYamlLogLevel {
  @JsonProperty("info") INFO("info"),
  @JsonProperty("debug") DEBUG("debug"),
  @JsonProperty("warning") WARNING("warning"),
  @JsonProperty("error") ERROR("error");
  private final String yamlName;

  STOYamlLogLevel(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlLogLevel getValue(@JsonProperty("type") String yamlName) {
    for (STOYamlLogLevel value : STOYamlLogLevel.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName) || value.name().equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value for log level: " + yamlName + ". Valid values are: "
        + Arrays.stream(STOYamlLogLevel.values()).map(Enum::toString).collect(Collectors.joining(", ")));
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlLogLevel fromString(final String s) {
    return STOYamlLogLevel.getValue(s);
  }
}
