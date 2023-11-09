/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps.v1;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParametersV1;
import io.harness.cdng.pipeline.steps.output.CombinedRollbackSweepingOutput;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.rollback.RollbackUtility;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.tasks.ResponseData;
import io.harness.utils.ExecutionModeUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class DeploymentStageStepV1 implements ChildExecutable<StageElementParametersV1> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP_V1.getName())
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Override
  public Class<StageElementParametersV1> getStepParametersClass() {
    return StageElementParametersV1.class;
  }

  private StageExecutionInfoUpdateDTO createStageExecutionEntityUpdateDTOFromStepParameters(
      StageElementParametersV1 stepParameters) {
    return StageExecutionInfoUpdateDTO.builder()
        .stageName(stepParameters.getName())
        .stageIdentifier(stepParameters.getId())
        .tags(stepParameters.getLabels())
        .build();
  }

  private StageExecutionInfoUpdateDTO createStageExecutionEntityUpdateDTOFromStepResponse(StepResponse stepResponse) {
    return StageExecutionInfoUpdateDTO.builder()
        .failureInfo(stepResponse.getFailureInfo())
        .status(stepResponse.getStatus())
        .stageStatus(Status.SUCCEEDED.equals(stepResponse.getStatus()) ? StageStatus.SUCCEEDED : StageStatus.FAILED)
        .build();
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParametersV1 stepParameters, StepInputPackage inputPackage) {
    log.info("Executing deployment stage with params [{}]", stepParameters);
    DeploymentStageStepParametersV1 stageStepParameters = (DeploymentStageStepParametersV1) stepParameters.getSpec();
    final String serviceNodeId = stageStepParameters.getChildNodeID();
    stageExecutionInfoService.upsertStageExecutionInfo(
        ambiance, createStageExecutionEntityUpdateDTOFromStepParameters(stepParameters));
    return ChildExecutableResponse.newBuilder().setChildNodeId(serviceNodeId).build();
  }

  /**
   * for rollback mode executions, the status of the stage should be the status of the rollback steps, which is being
   * fetched from the corresponding sweeping output. This sweeping output was published by CombinedRollbackStep
   */
  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParametersV1 stepParameters, Map<String, ResponseData> responseDataMap) {
    ExecutionMode executionMode = ambiance.getMetadata().getExecutionMode();
    if (ExecutionModeUtils.isRollbackMode(executionMode)) {
      OptionalSweepingOutput sweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS));
      if (sweepingOutput.isFound()) {
        CombinedRollbackSweepingOutput combinedRollbackOutput =
            (CombinedRollbackSweepingOutput) sweepingOutput.getOutput();
        return createStepResponseFromChildResponse(combinedRollbackOutput.getResponseDataMap());
      }
    }
    log.info("Executed deployment stage [{}]", stepParameters);
    RollbackUtility.publishRollbackInformation(ambiance, responseDataMap, executionSweepingOutputService);
    StepResponse stepResponse = createStepResponseFromChildResponse(responseDataMap);
    stageExecutionInfoService.upsertStageExecutionInfo(
        ambiance, createStageExecutionEntityUpdateDTOFromStepResponse(stepResponse));
    return stepResponse;
  }
}
