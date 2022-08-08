/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.variables;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NGVariableType {
  @JsonProperty(NGVariableConstants.STRING_TYPE) STRING(NGVariableConstants.STRING_TYPE),
  @JsonProperty(NGVariableConstants.NUMBER_TYPE) NUMBER(NGVariableConstants.NUMBER_TYPE),
  @JsonProperty(NGVariableConstants.SECRET_TYPE) SECRET(NGVariableConstants.SECRET_TYPE);

  private final String yamlProperty;

  NGVariableType(String yamlProperty) {
    this.yamlProperty = yamlProperty;
  }

  @JsonValue
  public String getYamlProperty() {
    return yamlProperty;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static NGVariableType getNGVariableType(@JsonProperty("type") String yamlProperty) {
    for (NGVariableType ngVariableType : NGVariableType.values()) {
      if (ngVariableType.getYamlProperty().equalsIgnoreCase(yamlProperty)) {
        return ngVariableType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlProperty);
  }
}
