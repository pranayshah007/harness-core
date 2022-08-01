/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * Flow for rollback execution:
 * - If a step fails, onFailRollbackAdviser publishes the information of the nextNodeId to run which is the
 * rollbackSteps
 * - The failed node id traverses to the parent using RollbackUtility#publishRollbackInformation, till the parent on
 * which the RollbackCustomAdviser is attached.
 * - RollbackCustomAdviser gets called and the rollbackSteps start executing
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NGSectionStepWithRollbackInfo extends ChildExecutableWithRollbackAndRbac<NGSectionStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.NG_SECTION_WITH_ROLLBACK_INFO)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public String getGroupType() {
    return StepOutcomeGroup.EXECUTION.name();
  }

  @Override
  public void validateResources(Ambiance ambiance, NGSectionStepParameters stepParameters) {
    // Do Nothing
  }

  @Override
  public ChildExecutableResponse obtainChildAfterRbac(
      Ambiance ambiance, NGSectionStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for " + stepParameters.getLogMessage() + " Step [{}]", stepParameters);
    return ChildExecutableResponse.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build();
  }

  @Override
  public StepResponse handleChildResponseInternal(
      Ambiance ambiance, NGSectionStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed execution for " + stepParameters.getLogMessage() + " Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<NGSectionStepParameters> getStepParametersClass() {
    return NGSectionStepParameters.class;
  }
}
