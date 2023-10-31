/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.terraform.provider.TerraformProviderType;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Data
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformAwsProviderCredentialConfig")
@FieldNameConstants(innerTypeName = "keys")
public class TerraformAwsProviderCredentialConfig implements TerraformProviderCredentialConfig {
  @NotNull private String connectorRef;
  private String region;
  private String roleArn;
  private TerraformProviderType type;
}
