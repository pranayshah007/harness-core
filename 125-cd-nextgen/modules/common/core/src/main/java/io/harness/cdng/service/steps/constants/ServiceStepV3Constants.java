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

public class ServiceStepV3Constants {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE_V3.getName()).setStepCategory(StepCategory.STEP).build();
  public static final String SERVICE_SWEEPING_OUTPUT = "serviceSweepingOutput";
  public static final String FREEZE_SWEEPING_OUTPUT = "freezeSweepingOutput";
  public static final String SERVICE_MANIFESTS_SWEEPING_OUTPUT = "serviceManifestsSweepingOutput";
  public static final String SERVICE_CONFIG_FILES_SWEEPING_OUTPUT = "serviceConfigFilesSweepingOutput";
  public static final String SERVICE_HOOKS_SWEEPING_OUTPUT = "serviceHooksSweepingOutput";
  public static final String SERVICE_APP_SETTINGS_SWEEPING_OUTPUT = "serviceAppSettingsSweepingOutput";
  public static final String SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT = "serviceConnectionStringsSweepingOutput";
  public static final String PIPELINE_EXECUTION_EXPRESSION = "<+pipeline.execution.url>";
  public static final String ECS_SERVICE_SWEEPING_OUTPUT = "serviceCustomSweepingOutput";
}
