/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.constants;

import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

public class ServiceStepConstants {
  public static final String SERVICE = "service";
  public static final String SERVICE_OVERRIDES = "service overrides";
  public static final String ENVIRONMENT_GLOBAL_OVERRIDES = "environment global overrides";

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE.getName()).setStepCategory(StepCategory.STEP).build();
  public static final String FREEZE_SWEEPING_OUTPUT = "freezeSweepingOutput";
  public static final String PIPELINE_EXECUTION_EXPRESSION = "<+pipeline.execution.url>";

  public static final String SERVICE_VARIABLES_PATTERN_REGEX = "(.*<\\+serviceVariables\\..+>.*)";
  public static final String ENV_VARIABLES_PATTERN_REGEX = "(.*<\\+env\\.variables\\..+>.*)";
  public static final String ENV_REF = "environment ref";
  public static final String ENV_GROUP_REF = "environment group ref";
}
