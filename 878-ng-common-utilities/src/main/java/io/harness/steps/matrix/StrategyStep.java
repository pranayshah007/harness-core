/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static io.harness.steps.StepUtils.createStepResponseFromChildResponse;

import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
          .setMaxConcurrency(((MatrixConfig) stepParameters.getStrategyConfig().getMatrixConfig()).getMaxConcurrency())
          .build();
    }
    if (stepParameters.getStrategyConfig().getForConfig() != null) {
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(forLoopStrategyConfigService.fetchChildren(
              stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .setMaxConcurrency(stepParameters.getStrategyConfig().getForConfig().getMaxConcurrency().getValue())
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
    log.info("Completed  execution for Strategy Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }
}
