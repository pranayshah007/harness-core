/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.mapper.ServiceHookOutcomeMapper;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class IndividualServiceHookStep implements SyncExecutable<ServiceHookStepParameters> {
  private static final String OUTPUT = "output";
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.PRE_HOOK.getName()).setStepCategory(StepCategory.STEP).build();

  @Override
  public Class<ServiceHookStepParameters> getStepParametersClass() {
    return ServiceHookStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceHookStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    // covert actions to string
    List<String> actions = new ArrayList<>();
    stepParameters.getActions().forEach(action -> actions.add(action.getDisplayName()));
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OUTPUT)
                         .outcome(ServiceHookOutcomeMapper.toServiceHookOutcome(stepParameters.getIdentifier(),
                             stepParameters.getType(), actions, stepParameters.getStore(), stepParameters.getOrder()))
                         .build())
        .build();
  }
}
