/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsStepVariableCreator
    extends GenericStepVariableCreator<EcsBlueGreenSwapTargetGroupsStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
  }

  @Override
  public Class<EcsBlueGreenSwapTargetGroupsStepNode> getFieldClass() {
    return EcsBlueGreenSwapTargetGroupsStepNode.class;
  }
}
