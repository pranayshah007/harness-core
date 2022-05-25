package io.harness.steps.matrix;

import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildrenExecutableWithRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;

public class StrategyStep implements ChildrenExecutableWithRbac<StrategyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();
  @Override
  public void validateResources(Ambiance ambiance, StrategyStepParameters stepParameters) {
    // Todo (implement)
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, StrategyStepParameters stepParameters, StepInputPackage inputPackage) {
    return ChildrenExecutableResponse.newBuilder()
        .addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(stepParameters.childNodeId).build())
        .addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(stepParameters.childNodeId).build())
        .build();
  }

  @Override
  public Class<StrategyStepParameters> getStepParametersClass() {
    return StrategyStepParameters.class;
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, StrategyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }
}
