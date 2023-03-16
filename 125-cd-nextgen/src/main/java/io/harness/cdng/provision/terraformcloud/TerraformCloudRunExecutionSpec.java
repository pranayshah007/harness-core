/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.PLAN;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.PLAN_AND_APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.PLAN_AND_DESTROY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.PLAN_ONLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.REFRESH_STATE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudApplySpec;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudPlanAndApplySpec;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudPlanAndDestroySpec;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudPlanOnlySpec;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudPlanSpec;
import io.harness.cdng.provision.terraformcloud.runspecs.TerraformCloudRefreshSpec;
import io.harness.filters.WithConnectorRef;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;

@OwnedBy(CDP)
@JsonTypeInfo(use = NAME, property = "runType", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = TerraformCloudRefreshSpec.class, name = REFRESH_STATE)
  , @JsonSubTypes.Type(value = TerraformCloudPlanOnlySpec.class, name = PLAN_ONLY),
      @JsonSubTypes.Type(value = TerraformCloudPlanAndApplySpec.class, name = PLAN_AND_APPLY),
      @JsonSubTypes.Type(value = TerraformCloudPlanAndDestroySpec.class, name = PLAN_AND_DESTROY),
      @JsonSubTypes.Type(value = TerraformCloudPlanSpec.class, name = PLAN),
      @JsonSubTypes.Type(value = TerraformCloudApplySpec.class, name = APPLY),
})
public abstract class TerraformCloudRunExecutionSpec implements WithConnectorRef {
  @ApiModelProperty(hidden = true) public abstract TerraformCloudRunType getType();
  @ApiModelProperty(hidden = true) public abstract TerraformCloudRunSpecParameters getSpecParams();
  public void validateParams() {
    getSpecParams().validate();
  }
}
