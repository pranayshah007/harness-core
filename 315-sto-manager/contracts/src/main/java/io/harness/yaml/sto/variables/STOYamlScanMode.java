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

@TypeAlias("stoYamlScanMode")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlScanMode")
public enum STOYamlScanMode {
  @JsonProperty("ingestion") INGESTION("ingestion", "ingestionOnly"),
  @JsonProperty("orchestration") ORCHESTRATION("orchestration", "orchestratedScan"),
  @JsonProperty("extraction") EXTRACTION("extraction", "dataLoad");
  private final String yamlName;
  private final String pluginName;

  STOYamlScanMode(String yamlName, String pluginName) {
    this.yamlName = yamlName;
    this.pluginName = pluginName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlScanMode getValue(@JsonProperty("mode") String yamlName) {
    for (STOYamlScanMode value : STOYamlScanMode.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName) || value.name().equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value for log level: " + yamlName + ". Valid values are: "
        + Arrays.stream(STOYamlScanMode.values()).map(Enum::toString).collect(Collectors.joining(", ")));
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  public String getPluginName() {
    return pluginName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlScanMode fromString(final String s) {
    return STOYamlScanMode.getValue(s);
  }
}
