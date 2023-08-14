/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.validator.NGVariableName;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName(CustomDeploymentNGVariableConstants.STRING_TYPE)
@TypeAlias("customDeploymentStringNGVariable")
@RecasterAlias("io.harness.cdng.customdeployment.CustomDeploymentStringNGVariable")
@OwnedBy(CDP)
public class CustomDeploymentStringNGVariable implements CustomDeploymentNGVariable {
  @NGVariableName
  @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN)
  @VariableExpression(skipVariableExpression = true)
  String name;
  @ApiModelProperty(allowableValues = CustomDeploymentNGVariableConstants.STRING_TYPE)
  @VariableExpression(skipVariableExpression = true)
  CustomDeploymentNGVariableType type = CustomDeploymentNGVariableType.STRING;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @VariableExpression(policy = REGULAR, skipInnerObjectTraversal = true)
  ParameterField<String> value;
  //  String value;
  @VariableExpression(skipVariableExpression = true) String description;
  @VariableExpression(skipVariableExpression = true) boolean required;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @Override
  public ParameterField<?> getCurrentValue() {
    return value;
    //    return ParameterField.createValueField(value);
  }
}
