/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.encryption.SecretRefData;
import io.harness.pms.yaml.ParameterField;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.validator.NGVariableName;
import io.harness.yaml.core.VariableExpression;

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
@JsonTypeName(CustomDeploymentNGVariableConstants.SECRET_TYPE)
@TypeAlias("customDeploymentSecretNGVariable")
@RecasterAlias("io.harness.cdng.customdeployment.CustomDeploymentSecretNGVariable")
@OwnedBy(CDP)
public class CustomDeploymentSecretNGVariable implements CustomDeploymentNGVariable {
  @NGVariableName
  @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN)
  @VariableExpression(skipVariableExpression = true)
  String name;
  @ApiModelProperty(allowableValues = CustomDeploymentNGVariableConstants.SECRET_TYPE)
  @VariableExpression(skipVariableExpression = true)
  CustomDeploymentNGVariableType type = CustomDeploymentNGVariableType.SECRET;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @VariableExpression(policy = REGULAR_WITH_CUSTOM_FIELD, skipInnerObjectTraversal = true)
  ParameterField<SecretRefData> value;
  @VariableExpression(skipVariableExpression = true) String description;
  @VariableExpression(skipVariableExpression = true) boolean required;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @Override
  public ParameterField<?> getCurrentValue() {
    return value;
  }
}
