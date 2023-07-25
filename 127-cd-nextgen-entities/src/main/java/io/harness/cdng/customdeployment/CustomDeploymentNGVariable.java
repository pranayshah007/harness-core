/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeployment;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
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
