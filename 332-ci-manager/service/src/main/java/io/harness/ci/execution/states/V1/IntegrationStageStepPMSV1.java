/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.cimanager.stages.V1.IntegrationStageSpecParamsV1;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class IntegrationStageStepPMSV1 implements ChildExecutable<StageElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("IntegrationStageStepPMSV1").setStepCategory(StepCategory.STAGE).build();
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Override
  public Class<StageElementParameters> getStepParametersClass() {
    return StageElementParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    log.info("Executing integration stage with params accountId {} projectId {} [{}]", accountId, projectIdentifier,
        stepParameters);
    IntegrationStageSpecParamsV1 params = (IntegrationStageSpecParamsV1) stepParameters.getSpecConfig();
    StageDetails stageDetails =
        StageDetails.builder()
            .stageID(stepParameters.getIdentifier())
            .stageRuntimeID(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            //            .buildStatusUpdateParameter(integrationStageStepParametersPMS.getBuildStatusUpdateParameter())
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .build();
    executionSweepingOutputResolver.consume(
        ambiance, ContextElement.stageDetails, stageDetails, StepOutcomeGroup.STAGE.name());
    return ChildExecutableResponse.newBuilder().setChildNodeId(params.getChildNodeID()).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();
    saveStageExecutionSweepingOutput(ambiance, currentTime - startTime);
    StepResponseNotifyData stepResponseNotifyData = filterStepResponse(responseDataMap);
    Status stageStatus = stepResponseNotifyData.getStatus();
    log.info("Executed integration stage {} in {} milliseconds with status {} ", stepParameters.getIdentifier(),
        (currentTime - startTime) / 1000, stageStatus);
    StepResponseBuilder stepResponseBuilder = createStepResponseFromChildResponse(responseDataMap).toBuilder();
    return stepResponseBuilder.build();
  }

  private StepResponseNotifyData filterStepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof StepResponseNotifyData)
        .findFirst()
        .map(obj -> (StepResponseNotifyData) obj.getValue())
        .orElse(null);
  }

  private void saveStageExecutionSweepingOutput(Ambiance ambiance, long buildTime) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION));
    if (!optionalSweepingOutput.isFound()) {
      try {
        StageExecutionSweepingOutput stageExecutionSweepingOutput =
            StageExecutionSweepingOutput.builder().stageExecutionTime(buildTime).build();
        executionSweepingOutputResolver.consume(
            ambiance, STAGE_EXECUTION, stageExecutionSweepingOutput, StepOutcomeGroup.STAGE.name());
      } catch (Exception e) {
        log.error("Error while consuming stage execution sweeping output", e);
      }
    }
  }
}
