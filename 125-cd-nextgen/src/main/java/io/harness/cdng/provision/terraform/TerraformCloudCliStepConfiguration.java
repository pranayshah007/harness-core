/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.TerraformCloudCliStepConfigurationParameters.TerraformCloudCliStepConfigurationParametersBuilder;
import io.harness.validation.Validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformCloudCliStepConfiguration")
public class TerraformCloudCliStepConfiguration {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  @JsonProperty("spec") TerraformCloudCliExecutionData terraformCloudCliExecutionData;

  public TerraformCloudCliStepConfigurationParameters toStepParameters() {
    TerraformCloudCliStepConfigurationParametersBuilder builder =
        TerraformCloudCliStepConfigurationParameters.builder();
    validateParams();
    if (terraformCloudCliExecutionData != null) {
      builder.spec(terraformCloudCliExecutionData.toStepParameters());
    }
    return builder.build();
  }

  void validateParams() {
    Validator.notNullCheck("Spec inside Configuration cannot be null", terraformCloudCliExecutionData);
    terraformCloudCliExecutionData.validateParams();
  }
}
