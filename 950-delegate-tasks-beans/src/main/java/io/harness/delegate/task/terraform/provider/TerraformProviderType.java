/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.provider;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.provision.TerraformProviderCredentialTypeConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
public enum TerraformProviderType {
  @JsonProperty(TerraformProviderCredentialTypeConstants.AWS) AWS(TerraformProviderCredentialTypeConstants.AWS);
  private final String displayName;

  TerraformProviderType(String displayName) {
    this.displayName = displayName;
  }
}
