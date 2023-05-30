/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.STO)
public class SecurityStep extends AbstractStepExecutable {
  @Inject private STOServiceUtils stoServiceUtils;

  public static final StepType STEP_TYPE = SecurityStepInfo.STEP_TYPE;

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = super.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
    StepResponseBuilder responseBuilder = stepResponse.toBuilder();
    responseBuilder.clearStepOutcomes();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    Optional<Level> stageLevelOpt = AmbianceUtils.getStageLevelFromAmbiance(ambiance);

    String stepId = AmbianceUtils.obtainStepIdentifier(ambiance);
    String stageId = "unknown";

    if (stageLevelOpt.isPresent()) {
      Level stageLevel = stageLevelOpt.get();
      stageId = stageLevel.getIdentifier();
    }

    Map<String, String> outputVairables = new HashMap<>();

    try {
      outputVairables =
          stoServiceUtils.getOutputVariables(accountId, orgId, projectId, planExecutionId, stageId, stepId);
    } catch (Exception e) {
      log.error("Exception trying to fetch output variables: {}", e);
      outputVairables.put("ERROR_MESSAGE", "Failed to fetch scan data");
    }

    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder()
            .outcome(CIStepOutcome.builder().outputVariables(outputVairables).build())
            .name("output")
            .build();
    responseBuilder.stepOutcome(stepOutcome);

    return responseBuilder.build();
  }
}
