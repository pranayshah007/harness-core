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

@TypeAlias("stoYamlSBOMFormat")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlSBOMFormat")
public enum STOYamlSBOMFormat {
  @JsonProperty("spdx-json") SPDX("spdx-json"),
  @JsonProperty("cyclonedx-json") CYCLONEDX("cyclonedx-json");

  private final String yamlName;

  STOYamlSBOMFormat(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlSBOMFormat getValue(@JsonProperty("type") String yamlName) {
    for (STOYamlSBOMFormat value : STOYamlSBOMFormat.values()) {
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

  public static STOYamlSBOMFormat fromString(final String s) {
    return STOYamlSBOMFormat.getValue(s);
  }
}
