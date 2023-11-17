/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName(YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class DeploymentStageNodeV1 extends DeploymentAbstractStageNodeV1 {
  String type = YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1;
  @NotNull DeploymentStageConfigV1 spec;

  @Override
  public String getType() {
    return YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1;
  }
}
