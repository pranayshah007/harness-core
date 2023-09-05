/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customstage;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.customstage.CustomStageSpecParams;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CustomStageStep implements ChildExecutable<StageElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build();
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  //@Inject private StageExecutionEntityService stageExecutionEntityService;
  //@Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;

  @Override
  public Class<StageElementParameters> getStepParametersClass() {
    return StageElementParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Executing custom stage with params [{}]", stepParameters);
    CustomStageSpecParams specParameters = (CustomStageSpecParams) stepParameters.getSpecConfig();
    String executionNodeId = specParameters.getChildNodeID();
    /*
    if (cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC)) {
      dashboardExecutorService.submit(()
                                          -> stageExecutionEntityService.updateStageExecutionEntity(ambiance,
                                              createStageExecutionEntityUpdateDTOFromStepParameters(stepParameters)));
    }

     */
    return ChildExecutableResponse.newBuilder().setChildNodeId(executionNodeId).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Executed custom stage [{}]", stepParameters);
    StepResponse stepResponse = createStepResponseFromChildResponse(responseDataMap);
    /*
    if (cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC)) {
      dashboardExecutorService.submit(()
                                          -> stageExecutionEntityService.updateStageExecutionEntity(ambiance,
                                              createStageExecutionEntityUpdateDTOFromStepResponse(stepResponse)));
    }

     */
    return stepResponse;
  }
/*
  private StageExecutionEntityUpdateDTO createStageExecutionEntityUpdateDTOFromStepResponse(StepResponse stepResponse) {
    return StageExecutionEntityUpdateDTO.builder()
        .failureInfo(stepResponse.getFailureInfo())
        .status(stepResponse.getStatus())
        .stageStatus(Status.SUCCEEDED.equals(stepResponse.getStatus()) ? StageStatus.SUCCEEDED : StageStatus.FAILED)
        .build();
  }

  private StageExecutionEntityUpdateDTO createStageExecutionEntityUpdateDTOFromStepParameters(
      StageElementParameters stepParameters) {
    return StageExecutionEntityUpdateDTO.builder()
        .stageName(stepParameters.getName())
        .stageIdentifier(stepParameters.getIdentifier())
        .tags(stepParameters.getTags())
        .build();
  }

 */
}
