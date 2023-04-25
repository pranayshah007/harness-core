/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;

public class CombinedRollbackStep extends RollbackOptionalChildChainStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType("COMBINED_ROLLBACK_OPTIONAL_CHILD_CHAIN")
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  /**
   * The Status of the Execution Step is based on the Status of the `Steps` section. The status of the Stage in turn
   * depends upon the status of Execution Step. In this, the status of rollback steps is lost. In order to make sure the
   * Stage has the information about the status of all Rollback Steps, infra and execution both, CombinedRollbackStep
   * will publish its response data Map as sweeping output, which the stage can read to know its status
   * Where will this be used:
   * -> in rollback mode executions, the status of the rollback steps will be used to determine the status of the stage
   * -> if we introduce a secondary status, the status of rollback steps can act as the secondary status for the stage
   */
  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, RollbackOptionalChildChainStepParameters stepParameters,
      ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    executionSweepingOutputService.consumeOptional(ambiance, YAMLFieldNameConstants.COMBINED_ROLLBACK_STATUS,
        CombinedRollbackSweepingOutput.builder().responseDataMap(responseDataMap).build(),
        StepOutcomeGroup.STAGE.name());
    return super.finalizeExecution(ambiance, stepParameters, passThroughData, responseDataMap);
  }
}
