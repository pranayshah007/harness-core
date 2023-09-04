/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeployment;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
public enum CustomDeploymentNGVariableType {
  @JsonProperty(CustomDeploymentNGVariableConstants.STRING_TYPE)
  STRING(CustomDeploymentNGVariableConstants.STRING_TYPE),
  @JsonProperty(CustomDeploymentNGVariableConstants.NUMBER_TYPE)
  NUMBER(CustomDeploymentNGVariableConstants.NUMBER_TYPE),
  @JsonProperty(CustomDeploymentNGVariableConstants.SECRET_TYPE)
  SECRET(CustomDeploymentNGVariableConstants.SECRET_TYPE),
  @JsonProperty(CustomDeploymentNGVariableConstants.CONNECTOR_TYPE)
  CONNECTOR(CustomDeploymentNGVariableConstants.CONNECTOR_TYPE);

  private final String yamlProperty;

  CustomDeploymentNGVariableType(String yamlProperty) {
    this.yamlProperty = yamlProperty;
  }

  @JsonValue
  public String getYamlProperty() {
    return yamlProperty;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static CustomDeploymentNGVariableType getNGVariableType(@JsonProperty("type") String yamlProperty) {
    for (CustomDeploymentNGVariableType ngVariableType : CustomDeploymentNGVariableType.values()) {
      if (ngVariableType.getYamlProperty().equalsIgnoreCase(yamlProperty)) {
        return ngVariableType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlProperty);
  }
}
