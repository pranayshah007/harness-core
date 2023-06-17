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

@TypeAlias("stoYamlCustomIngestConfig")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlCustomIngestConfig")
public enum STOYamlCustomIngestConfig implements STOYamlConfig {
  @JsonProperty("default") DEFAULT("default"),
  @JsonProperty("sarif") SARIF("sarif");
  private final String yamlName;

  STOYamlCustomIngestConfig(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlCustomIngestConfig getValue(@JsonProperty("config") String yamlName) {
    for (STOYamlCustomIngestConfig value : STOYamlCustomIngestConfig.values()) {
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

  public static STOYamlCustomIngestConfig fromString(final String s) {
    return STOYamlCustomIngestConfig.getValue(s);
  }
}
