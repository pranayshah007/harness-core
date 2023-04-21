/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.rollback.RollbackUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
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

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class DeploymentStageStep implements ChildExecutable<StageElementParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName())
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<StageElementParameters> getStepParametersClass() {
    return StageElementParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Executing deployment stage with params [{}]", stepParameters);
    DeploymentStageStepParameters stageStepParameters = (DeploymentStageStepParameters) stepParameters.getSpecConfig();
    final String serviceNodeId = stageStepParameters.getChildNodeID();
    return ChildExecutableResponse.newBuilder().setChildNodeId(serviceNodeId).build();
  }

  /**
   * for rollback mode executions, the status of the stage should be the status of the rollback steps, which is being
   * fetched from the corresponding sweeping output. This sweeping output was published by CombinedRollbackStep
   */
  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
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
    log.info("executed deployment stage =[{}]", stepParameters);
    RollbackUtility.publishRollbackInformation(ambiance, responseDataMap, executionSweepingOutputService);
    return createStepResponseFromChildResponse(responseDataMap);
  }
}
