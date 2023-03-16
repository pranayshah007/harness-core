/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("deploymentStageStepParameters")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.pipeline.beans.DeploymentStageStepParameters")
public class DeploymentStageStepParameters implements SpecParameters {
  String childNodeID;

  public static DeploymentStageStepParameters getStepParameters(String childNodeID) {
    return DeploymentStageStepParameters.builder().childNodeID(childNodeID).build();
  }

  @Override
  public SpecParameters getViewJsonObject() {
    return DeploymentStageStepParameters.builder().build();
  }
}
