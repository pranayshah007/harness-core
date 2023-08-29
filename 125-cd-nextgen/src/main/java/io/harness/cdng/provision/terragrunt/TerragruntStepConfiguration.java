/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terragrunt.TerragruntStepConfigurationParameters.TerragruntStepConfigurationParametersBuilder;
import io.harness.validation.Validator;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntStepConfiguration")
public class TerragruntStepConfiguration {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;
  @NotNull @JsonProperty("type") TerragruntStepConfigurationType terragruntStepConfigurationType;
  @JsonProperty("spec") TerragruntExecutionData terragruntExecutionData;
  @VariableExpression(skipVariableExpression = true) List<TerragruntCliOptionFlag> commandFlags;

  public TerragruntStepConfigurationParameters toStepParameters() {
    TerragruntStepConfigurationParametersBuilder builder = TerragruntStepConfigurationParameters.builder();
    validateParams();
    builder.type(terragruntStepConfigurationType);
    builder.commandFlags(commandFlags);
    if (terragruntExecutionData != null) {
      builder.spec(terragruntExecutionData.toStepParameters());
    }
    return builder.build();
  }

  void validateParams() {
    Validator.notNullCheck("Step Configuration Type is null", terragruntStepConfigurationType);

    if (terragruntStepConfigurationType == TerragruntStepConfigurationType.INLINE) {
      Validator.notNullCheck("Spec inside Configuration cannot be null", terragruntExecutionData);
      terragruntExecutionData.validateParams();
    }
  }
}
