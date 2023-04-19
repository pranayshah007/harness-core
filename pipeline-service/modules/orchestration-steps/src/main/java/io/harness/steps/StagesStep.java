/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

@OwnedBy(HarnessTeam.PIPELINE)
public class StagesStep extends NGSectionStepWithRollbackInfo {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.NG_STAGES_STEP).setStepCategory(StepCategory.STAGES).build();
  public static final StepType DEPRECATED_STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.NG_STAGES_STEP).setStepCategory(StepCategory.STEP).build();
}
