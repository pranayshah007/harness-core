/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.steps.common.rollback.RollbackUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGSpecStep extends NGSectionStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.NG_SPEC_STEP)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, NGSectionStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    RollbackUtility.publishRollbackInformation(ambiance, responseDataMap, executionSweepingOutputService);
    return super.handleChildResponse(ambiance, stepParameters, responseDataMap);
  }
}
