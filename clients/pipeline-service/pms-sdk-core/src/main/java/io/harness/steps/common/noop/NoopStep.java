package io.harness.steps.common.noop;

import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

public class NoopStep implements SyncExecutable<NoopStepParameters> {
  public static StepType STEP_TYPE =
      StepType.newBuilder().setType(NGCommonUtilPlanCreationConstants.NOOP).setStepCategory(StepCategory.STEP).build();

  @Override
  public Class<NoopStepParameters> getStepParametersClass() {
    return NoopStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, NoopStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }
}
