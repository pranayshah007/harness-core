/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.advisers.rollback.OnFailRollbackOutput;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class RollbackExecutableUtility {
  public void publishRollbackInfo(Ambiance ambiance, StepBaseParameters stepParameters, Map<String, String> metadata,
      ExecutionSweepingOutputService executionSweepingOutputService) {
    String nextNodeId =
        AmbianceUtils.getStageSetupIdAmbiance(ambiance) + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    executionSweepingOutputService.consume(ambiance, YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY,
        OnFailRollbackOutput.builder().nextNodeId(nextNodeId).build(), StepOutcomeGroup.STEP.name());

    // MI -> Rollback
    try {
      executionSweepingOutputService.consume(ambiance, YAMLFieldNameConstants.STOP_STEPS_SEQUENCE,
          OnFailRollbackOutput.builder().nextNodeId(nextNodeId).build(), StepCategory.STAGE.name());
    } catch (Exception e) {
      log.warn("Ignoring duplicate sweeping output of - " + YAMLFieldNameConstants.STOP_STEPS_SEQUENCE);
    }
  }
}
