/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class StrategyStep extends ChildrenExecutableWithRollbackAndRbac<StrategyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Inject ParallelismStrategyConfigService parallelismStrategyConfigService;
  @Inject EnforcementClientService enforcementClientService;

  @Override
  public void validateResources(Ambiance ambiance, StrategyStepParameters stepParameters) {
    // do Nothing
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, StrategyStepParameters stepParameters, StepInputPackage inputPackage) {
    boolean shouldProceedIfFailed =
        stepParameters.getShouldProceedIfFailed() != null && stepParameters.getShouldProceedIfFailed();
    int maxConcurrencyLimitBasedOnPlan = 10000;
    try {
      if (enforcementClientService.isEnforcementEnabled()) {
        Optional<RestrictionMetadataDTO> restrictionMetadataDTO = enforcementClientService.getRestrictionMetadata(
            FeatureRestrictionName.STRATEGY_MAX_CONCURRENT, AmbianceUtils.getAccountId(ambiance));
        if (restrictionMetadataDTO.isPresent()
            && restrictionMetadataDTO.get().getRestrictionType() == RestrictionType.STATIC_LIMIT) {
          StaticLimitRestrictionMetadataDTO staticLimitRestrictionDTO =
              (StaticLimitRestrictionMetadataDTO) restrictionMetadataDTO.get();
          maxConcurrencyLimitBasedOnPlan = staticLimitRestrictionDTO.getLimit().intValue();
        }
      }
    } catch (EnforcementServiceConnectionException | WrongFeatureStateException e) {
      log.warn("Got exception while taking to enforcement service, taking default limit of 100 for maxConcurrency");
    }
    if (ParameterField.isNotNull(stepParameters.getStrategyConfig().getMatrixConfig())) {
      if (stepParameters.getStrategyConfig().getMatrixConfig().isExpression()) {
        throw new InvalidRequestException("Expression for matrix at runtime could not be resolved!");
      } else {
        int maxConcurrency = 0;
        if (!ParameterField.isBlank(
                ((MatrixConfig) stepParameters.getStrategyConfig().getMatrixConfig().getValue()).getMaxConcurrency())) {
          maxConcurrency = Double
                               .valueOf(String.valueOf(
                                   ((MatrixConfig) stepParameters.getStrategyConfig().getMatrixConfig().getValue())
                                       .getMaxConcurrency()
                                       .getValue()))
                               .intValue();
        }
        List<Child> children =
            matrixConfigService.fetchChildren(stepParameters.getStrategyConfig(), stepParameters.getChildNodeId());
        if (maxConcurrency == 0) {
          maxConcurrency = children.size();
        }
        if (maxConcurrency > maxConcurrencyLimitBasedOnPlan) {
          maxConcurrency = maxConcurrencyLimitBasedOnPlan;
        }
        return ChildrenExecutableResponse.newBuilder()
            .addAllChildren(children)
            .setMaxConcurrency(maxConcurrency)
            .setShouldProceedIfFailed(shouldProceedIfFailed)
            .build();
      }
    }
    if (stepParameters.getStrategyConfig().getRepeat() != null) {
      int maxConcurrency = 0;
      if (!ParameterField.isBlank(stepParameters.getStrategyConfig().getRepeat().getMaxConcurrency())) {
        maxConcurrency =
            Double
                .valueOf(String.valueOf(stepParameters.getStrategyConfig().getRepeat().getMaxConcurrency().getValue()))
                .intValue();
      }
      List<Child> children = forLoopStrategyConfigService.fetchChildren(
          stepParameters.getStrategyConfig(), stepParameters.getChildNodeId());
      if (maxConcurrency == 0) {
        maxConcurrency = children.size();
      }
      if (maxConcurrency > maxConcurrencyLimitBasedOnPlan) {
        maxConcurrency = maxConcurrencyLimitBasedOnPlan;
      }
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(children)
          .setMaxConcurrency(maxConcurrency)
          .setShouldProceedIfFailed(shouldProceedIfFailed)
          .build();
    }
    if (stepParameters.getStrategyConfig().getParallelism() != null) {
      List<Child> children = parallelismStrategyConfigService.fetchChildren(
          stepParameters.getStrategyConfig(), stepParameters.getChildNodeId());
      int maxConcurrency = stepParameters.getStrategyConfig().getParallelism().getValue();
      if (maxConcurrency > maxConcurrencyLimitBasedOnPlan) {
        maxConcurrency = maxConcurrencyLimitBasedOnPlan;
      }
      return ChildrenExecutableResponse.newBuilder()
          .addAllChildren(children)
          .setMaxConcurrency(maxConcurrency)
          .setShouldProceedIfFailed(shouldProceedIfFailed)
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
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, StrategyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for Strategy Step [{}]", stepParameters);

    if (StatusUtils.checkIfAllChildrenSkipped(responseDataMap.values()
                                                  .stream()
                                                  .map(o -> ((StepResponseNotifyData) o).getStatus())
                                                  .collect(Collectors.toList()))) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    return createStepResponseFromChildResponse(responseDataMap);
  }
}
