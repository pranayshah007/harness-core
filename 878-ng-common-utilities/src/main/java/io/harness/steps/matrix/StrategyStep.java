package io.harness.steps.matrix;

import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

public class StrategyStep implements ChildrenExecutable<StrategyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, StrategyStepParameters stepParameters, StepInputPackage inputPackage) {
    if (stepParameters.getStrategyConfig().getMatrixConfig() != null) {
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(
              matrixConfigService.fetchChildren(stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .build();
    }
    if (stepParameters.getStrategyConfig().getForConfig() != null) {
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(forLoopStrategyConfigService.fetchChildren(
              stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .build();
    }
    return ChildrenExecutableResponse.newBuilder()
        .addChildren(
            ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build())
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
