/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.customstage.v1;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.stage.StageExecutionEntityService;
import io.harness.execution.stage.StageExecutionEntityUpdateDTO;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.customstage.CustomStageSpecParams;
import io.harness.tasks.ResponseData;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CustomStageStepV1 implements ChildExecutable<StageElementParametersV1> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.CUSTOM_STAGE_V1).setStepCategory(StepCategory.STAGE).build();
  @Inject private StageExecutionEntityService stageExecutionEntityService;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;

  @Override
  public Class<StageElementParametersV1> getStepParametersClass() {
    return StageElementParametersV1.class;
  }

  private StageExecutionEntityUpdateDTO createStageExecutionEntityUpdateDTOFromStepResponse(StepResponse stepResponse) {
    return StageExecutionEntityUpdateDTO.builder()
        .failureInfo(stepResponse.getFailureInfo())
        .status(stepResponse.getStatus())
        .stageStatus(Status.SUCCEEDED.equals(stepResponse.getStatus()) ? StageStatus.SUCCEEDED : StageStatus.FAILED)
        .build();
  }

  private StageExecutionEntityUpdateDTO createStageExecutionEntityUpdateDTOFromStepParameters(
      StageElementParametersV1 stepParameters) {
    return StageExecutionEntityUpdateDTO.builder()
        .stageName(stepParameters.getName())
        .stageIdentifier(stepParameters.getId())
        .tags(stepParameters.getLabels())
        .build();
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParametersV1 stepParameters, StepInputPackage inputPackage) {
    log.info("Executing custom stage with params [{}]", stepParameters);
    CustomStageSpecParams specParameters = (CustomStageSpecParams) stepParameters.getSpec();
    String executionNodeId = specParameters.getChildNodeID();
    dashboardExecutorService.submit(()
                                        -> stageExecutionEntityService.updateStageExecutionEntity(ambiance,
                                            createStageExecutionEntityUpdateDTOFromStepParameters(stepParameters)));
    return ChildExecutableResponse.newBuilder().setChildNodeId(executionNodeId).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParametersV1 stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Executed custom stage [{}]", stepParameters);
    StepResponse stepResponse = createStepResponseFromChildResponse(responseDataMap);
    dashboardExecutorService.submit(()
                                        -> stageExecutionEntityService.updateStageExecutionEntity(ambiance,
                                            createStageExecutionEntityUpdateDTOFromStepResponse(stepResponse)));
    return stepResponse;
  }
}
