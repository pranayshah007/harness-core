/*

 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDP)
public enum TerraformCloudRunType {
  @JsonProperty(TerraformCloudConstants.REFRESH_STATE) REFRESH_STATE(TerraformCloudConstants.REFRESH_STATE),
  @JsonProperty(TerraformCloudConstants.PLAN_ONLY) PLAN_ONLY(TerraformCloudConstants.PLAN_ONLY),
  @JsonProperty(TerraformCloudConstants.PLAN_AND_APPLY) PLAN_AND_APPLY(TerraformCloudConstants.PLAN_AND_APPLY),
  @JsonProperty(TerraformCloudConstants.PLAN_AND_DESTROY) PLAN_AND_DESTROY(TerraformCloudConstants.PLAN_AND_DESTROY),
  @JsonProperty(TerraformCloudConstants.PLAN) PLAN(TerraformCloudConstants.PLAN),
  @JsonProperty(TerraformCloudConstants.APPLY) APPLY(TerraformCloudConstants.APPLY);

  private final String displayName;

  public String getDisplayName() {
    return this.displayName;
  }

  TerraformCloudRunType(String displayName) {
    this.displayName = displayName;
  }
}
