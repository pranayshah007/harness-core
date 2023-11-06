/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.provision.terraform.TerraformStepConfigurationParameters.TerraformStepConfigurationParametersBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
@Data
@NoArgsConstructor
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformStepConfiguration")
public class TerraformStepConfiguration {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  @NotNull @JsonProperty("type") TerraformStepConfigurationType terraformStepConfigurationType;
  @JsonProperty("spec") TerraformExecutionData terraformExecutionData;
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) @YamlSchemaTypes({string}) ParameterField<Boolean> skipRefreshCommand;
  @VariableExpression(skipVariableExpression = true) List<TerraformCliOptionFlag> commandFlags;
  TerraformEncryptOutput encryptOutput;

  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) @YamlSchemaTypes({string}) ParameterField<Boolean> skipStateStorage;

  public TerraformStepConfigurationParameters toStepParameters() {
    TerraformStepConfigurationParametersBuilder builder = TerraformStepConfigurationParameters.builder();
    validateParams();
    builder.type(terraformStepConfigurationType)
        .skipTerraformRefresh(skipRefreshCommand)
        .commandFlags(commandFlags)
        .encryptOutput(encryptOutput)
        .skipStateStorage(skipStateStorage);

    if (terraformExecutionData != null) {
      builder.spec(terraformExecutionData.toStepParameters());
    }
    return builder.build();
  }

  void validateParams() {
    Validator.notNullCheck("Step Configuration Type is null", terraformStepConfigurationType);

    if (terraformStepConfigurationType == TerraformStepConfigurationType.INLINE) {
      Validator.notNullCheck("Spec inside Configuration cannot be null", terraformExecutionData);
      terraformExecutionData.validateParams();
    }
  }
}
