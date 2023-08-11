/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.advisers.rollback.OnFailRollbackOutput;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
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
  private final String ROLLBACK = "ROLLBACK";
  public void publishRollbackInfo(Ambiance ambiance, StepBaseParameters stepParameters, Map<String, String> metadata,
      ExecutionSweepingOutputService executionSweepingOutputService) {
    OnFailRollbackParameters onFailRollbackParameters = getOnFailRollbackParameters(stepParameters);
    RollbackStrategy strategy;
    if (onFailRollbackParameters.getStrategy() == RollbackStrategy.UNKNOWN) {
      if (!metadata.containsKey(ROLLBACK)) {
        throw new InvalidRequestException(
            "Rollback strategy is not propagated to the step with parameters - " + stepParameters.toString());
      }
      strategy = RollbackStrategy.fromYamlName(metadata.get(ROLLBACK));
    } else {
      strategy = onFailRollbackParameters.getStrategy();
    }
    String nextNodeId = onFailRollbackParameters.getStrategyToUuid().get(strategy);
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

  private OnFailRollbackParameters getOnFailRollbackParameters(StepBaseParameters stepParameters) {
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      return stepElementParameters.getRollbackParameters();
    }
    StepElementParametersV1 stepElementParameters = (StepElementParametersV1) stepParameters;
    return stepElementParameters.getRollbackParameters();
  }
}
