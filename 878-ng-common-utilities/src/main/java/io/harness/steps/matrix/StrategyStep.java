/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static io.harness.steps.StepUtils.createStepResponseFromChildResponse;

import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StrategyStep implements ChildrenExecutable<StrategyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Inject ParallelismStrategyConfigService parallelismStrategyConfigService;
  @Inject EnforcementClientService enforcementClientService;

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, StrategyStepParameters stepParameters, StepInputPackage inputPackage) {
    int maxConcurrencyLimitBasedOnPlan = 100;
    try {
      Optional<RestrictionMetadataDTO> restrictionMetadataDTO = enforcementClientService.getRestrictionMetadata(
          FeatureRestrictionName.STRATEGY_MAX_CONCURRENT, AmbianceUtils.getAccountId(ambiance));
      if (restrictionMetadataDTO.isPresent()) {
        StaticLimitRestrictionMetadataDTO staticLimitRestrictionDTO =
            (StaticLimitRestrictionMetadataDTO) restrictionMetadataDTO.get();
        maxConcurrencyLimitBasedOnPlan = staticLimitRestrictionDTO.getLimit().intValue();
      }
    } catch (EnforcementServiceConnectionException | WrongFeatureStateException e) {
      log.warn("Got exception while taking to enforcement service, taking default limit of 100 for maxConcurrency");
    }
    if (stepParameters.getStrategyConfig().getMatrixConfig() != null) {
      int maxConcurrency = 0;
      if (!ParameterField.isBlank(
              ((MatrixConfig) stepParameters.getStrategyConfig().getMatrixConfig()).getMaxConcurrency())) {
        maxConcurrency =
            ((MatrixConfig) stepParameters.getStrategyConfig().getMatrixConfig()).getMaxConcurrency().getValue();
      }
      if (maxConcurrency == 0 || maxConcurrency > maxConcurrencyLimitBasedOnPlan) {
        maxConcurrency = maxConcurrencyLimitBasedOnPlan;
      }
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(
              matrixConfigService.fetchChildren(stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .setMaxConcurrency(maxConcurrency)
          .build();
    }
    if (stepParameters.getStrategyConfig().getForConfig() != null) {
      int maxConcurrency = 0;
      if (!ParameterField.isBlank(stepParameters.getStrategyConfig().getForConfig().getMaxConcurrency())) {
        maxConcurrency = stepParameters.getStrategyConfig().getForConfig().getMaxConcurrency().getValue();
      }
      if (maxConcurrency == 0 || maxConcurrency > maxConcurrencyLimitBasedOnPlan) {
        maxConcurrency = maxConcurrencyLimitBasedOnPlan;
      }
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(forLoopStrategyConfigService.fetchChildren(
              stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .setMaxConcurrency(maxConcurrency)
          .build();
    }
    if (stepParameters.getStrategyConfig().getParallelism() != null) {
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(parallelismStrategyConfigService.fetchChildren(
              stepParameters.getStrategyConfig(), stepParameters.getChildNodeId()))
          .setMaxConcurrency(maxConcurrencyLimitBasedOnPlan)
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
