package io.harness.yaml.core.variables;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

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
