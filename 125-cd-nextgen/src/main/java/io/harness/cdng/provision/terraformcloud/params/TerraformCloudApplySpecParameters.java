/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.params;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunSpecParameters;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunType;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters")
public class TerraformCloudApplySpecParameters implements TerraformCloudRunSpecParameters {
  ParameterField<String> provisionerIdentifier;

  @Override
  public TerraformCloudRunType getType() {
    return TerraformCloudRunType.APPLY;
  }

  @Override
  public void validate() {
    Validator.notNullCheck("Provisioner identifier for Terraform Cloud is null", provisionerIdentifier);
  }

  @Override
  public List<ParameterField<String>> extractConnectorRefs() {
    return Collections.emptyList();
  }
}
