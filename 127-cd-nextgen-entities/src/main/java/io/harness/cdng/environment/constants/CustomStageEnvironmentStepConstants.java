/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.constants;

import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

public class CustomStageEnvironmentStepConstants {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CUSTOM_STAGE_ENVIRONMENT.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String ENVIRONMENT_STEP_COMMAND_UNIT = "Environment Step";
  public static final String OVERRIDES_COMMAND_UNIT = "Overrides";
}
