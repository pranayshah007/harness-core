/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;

@OwnedBy(PIPELINE)
public class LevelUtils {
  public static boolean isStageLevel(Level level) {
    return level.getStepType().getStepCategory() == StepCategory.STAGE;
  }

  public static boolean isStepLevel(Level level) {
    return level.getStepType().getStepCategory() == StepCategory.STEP;
  }
}
