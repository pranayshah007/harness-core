/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformStepConfigurationParameters")
public class TerraformStepConfigurationParameters implements TerraformStepConfigurationInterface {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  @NonNull TerraformStepConfigurationType type;
  TerraformExecutionDataParameters spec;
  ParameterField<Boolean> skipTerraformRefresh;
  List<TerraformCliOptionFlag> commandFlags;

  @Override
  public TerraformStepConfigurationEnumInterface getType() {
    return type;
  }

  @Override
  public void setType(TerraformStepConfigurationEnumInterface terraformStepConfigurationType) {
    this.type = (TerraformStepConfigurationType) terraformStepConfigurationType;
  }

  @Override
  public TerraformExecutionDataParameters getSpec() {
    return spec;
  }

  @Override
  public ParameterField<Boolean> getIsSkipTerraformRefresh() {
    return skipTerraformRefresh;
  }

  @Override
  public List<TerraformCliOptionFlag> getCliOptions() {
    return commandFlags;
  }

  @Override
  public void setCliOptions(List<TerraformCliOptionFlag> cliOptionFlags) {
    this.commandFlags = cliOptionFlags;
  }
}
