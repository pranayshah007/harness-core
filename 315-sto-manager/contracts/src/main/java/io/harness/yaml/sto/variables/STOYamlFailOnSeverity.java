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

@TypeAlias("stoYamlFailOnSeverity")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlFailOnSeverity")
public enum STOYamlFailOnSeverity {
  @JsonProperty("critical") CRITICAL("critical"),
  @JsonProperty("high") HIGH("high"),
  @JsonProperty("medium") MEDIUM("medium"),
  @JsonProperty("low") LOW("low"),
  @JsonProperty("none") NONE("none");
  private final String yamlName;

  STOYamlFailOnSeverity(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlFailOnSeverity getValue(@JsonProperty("type") String yamlName) {
    for (STOYamlFailOnSeverity value : STOYamlFailOnSeverity.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value for fail on severity: " + yamlName + ". Valid values are: "
        + Arrays.stream(STOYamlFailOnSeverity.values()).map(Enum::toString).collect(Collectors.joining(", ")));
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlFailOnSeverity fromString(final String s) {
    return STOYamlFailOnSeverity.getValue(s);
  }
}
