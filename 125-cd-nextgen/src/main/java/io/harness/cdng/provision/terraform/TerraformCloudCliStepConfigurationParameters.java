/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformCloudCliStepConfigurationParameters")
public class TerraformCloudCliStepConfigurationParameters implements TerraformStepConfigurationInterface {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  TerraformExecutionDataParameters spec;

  @Override
  public TerraformCloudCliStepConfigurationType getType() {
    return TerraformCloudCliStepConfigurationType.INLINE;
  }

  @Override
  public void setType(TerraformStepConfigurationEnumInterface terraformStepConfigurationType) {
    // always INLINE
  }

  @Override
  public TerraformExecutionDataParameters getSpec() {
    return spec;
  }

  @Override
  public ParameterField<Boolean> getIsSkipTerraformRefresh() {
    // always false for cloud CLI
    return ParameterField.createValueField(false);
  }
}
