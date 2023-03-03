/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix.v1;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.strategy.v1.StrategyTypeV1;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StrategyStepV1 extends ChildrenExecutableWithRollbackAndRbac<StrategyStepParametersV1> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.STRATEGY_V1)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Inject MatrixConfigServiceV1 matrixConfigService;
  @Inject ForConfigServiceV1 forConfigService;
  @Inject EnforcementClientService enforcementClientService;

  @Override
  public void validateResources(Ambiance ambiance, StrategyStepParametersV1 stepParameters) {
    // do Nothing
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, StrategyStepParametersV1 stepParameters, StepInputPackage inputPackage) {
    boolean shouldProceedIfFailed =
        stepParameters.getShouldProceedIfFailed() != null && stepParameters.getShouldProceedIfFailed();
    // TODO: Take this value from the config/setting
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
    int maxConcurrency = 0;
    if (!ParameterField.isBlank(
            (stepParameters.getStrategyConfig().getStrategyInfoConfig().getValue()).getMaxConcurrency())) {
      maxConcurrency =
          (stepParameters.getStrategyConfig().getStrategyInfoConfig().getValue()).getMaxConcurrency().getValue();
    }
    List<Child> children = new ArrayList<>();
    // TODO: BRIJESH] add support for While looping strategy as well.
    if (stepParameters.getStrategyConfig().getType() == StrategyTypeV1.MATRIX) {
      if (stepParameters.getStrategyConfig().getStrategyInfoConfig().isExpression()) {
        throw new InvalidRequestException("Expression for matrix at runtime could not be resolved!");
      } else {
        children =
            matrixConfigService.fetchChildren(stepParameters.getStrategyConfig(), stepParameters.getChildNodeId());
      }
    } else if (stepParameters.getStrategyConfig().getType() == StrategyTypeV1.FOR) {
      children = forConfigService.fetchChildren(stepParameters.getStrategyConfig(), stepParameters.getChildNodeId());
    }
    // If children list is not empty then return all the children.
    if (!children.isEmpty()) {
      // If maxConcurrency was not defined then set it the count of all children.
      if (maxConcurrency == 0) {
        maxConcurrency = children.size();
      }
      // MaxConcurrency must not be more than maxConcurrencyLimitBasedOnPlan.
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
        .addChildren(Child.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build())
        .build();
  }

  @Override
  public Class<StrategyStepParametersV1> getStepParametersClass() {
    return StrategyStepParametersV1.class;
  }

  @Override
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, StrategyStepParametersV1 stepParameters, Map<String, ResponseData> responseDataMap) {
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
