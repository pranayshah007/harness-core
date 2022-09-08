package io.harness.yaml.core.variables;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;

@JsonTypeInfo(use = NAME, property = "type", include = PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.
  Type(value = CustomDeploymentNumberNGVariable.class, name = CustomDeploymentNGVariableConstants.NUMBER_TYPE)
  ,
      @JsonSubTypes.Type(
          value = CustomDeploymentStringNGVariable.class, name = CustomDeploymentNGVariableConstants.STRING_TYPE),
      @JsonSubTypes.Type(
          value = CustomDeploymentSecretNGVariable.class, name = CustomDeploymentNGVariableConstants.SECRET_TYPE),
      @JsonSubTypes.Type(
          value = CustomDeploymentConnectorNGVariable.class, name = CustomDeploymentNGVariableConstants.CONNECTOR_TYPE)
})
public interface CustomDeploymentNGVariable {
  CustomDeploymentNGVariableType getType();
  String getName();
  String getDescription();
  boolean isRequired();
  @ApiModelProperty(hidden = true) ParameterField<?> getCurrentValue();
}
