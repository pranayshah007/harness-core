/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.runspecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunExecutionSpec;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunSpecParameters;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunType;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
@FieldNameConstants(innerTypeName = "TerraformCloudApplySpecKeys")
@RecasterAlias("io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudApplySpec")
public class TerraformCloudApplySpec extends TerraformCloudRunExecutionSpec {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> provisionerIdentifier;

  @Override
  public TerraformCloudRunType getType() {
    return TerraformCloudRunType.APPLY;
  }

  @Override
  public TerraformCloudRunSpecParameters getSpecParams() {
    return TerraformCloudApplySpecParameters.builder().provisionerIdentifier(provisionerIdentifier).build();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    return new HashMap<>();
  }
}
