/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackParameters;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualFailureSpecConfigV1;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureSpecConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetrySGFailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureActionTypeV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureTypeV1;
import io.harness.yaml.core.failurestrategy.v1.OnConfigV1;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class FailureStrategiesUtilsV1 {
  public Map<FailureConfigV1, Collection<FailureType>> priorityMergeFailureStrategies(
      OnConfigV1 stepFailureStrategies, OnConfigV1 stepGroupFailureStrategies, OnConfigV1 stageFailureStrategies) {
    // priority merge all declared failure strategies, least significant are added first to map
    EnumMap<NGFailureTypeV1, FailureConfigV1> failureStrategiesMap = new EnumMap<>(NGFailureTypeV1.class);
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stageFailureStrategies, false));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepGroupFailureStrategies, true));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepFailureStrategies, false));

    // invert map so that action become key
    return convertNGFailureTypeToFailureTypesMultiMap(failureStrategiesMap);
  }

  private EnumMap<NGFailureTypeV1, FailureConfigV1> expandFailureStrategiesToMap(
      OnConfigV1 failureStrategyConfigList, boolean isStepGroup) {
    EnumMap<NGFailureTypeV1, FailureConfigV1> map = new EnumMap<>(NGFailureTypeV1.class);

    if (failureStrategyConfigList != null) {
      int allErrorsCount = 0;
      FailureConfigV1 allErrorFailureStrategyAction = null;
      for (FailureConfigV1 failureStrategyConfig : failureStrategyConfigList.getFailure()) {
        for (NGFailureTypeV1 ngFailureType : failureStrategyConfig.getErrors()) {
          if (map.containsKey(ngFailureType) && !map.get(ngFailureType).equals(failureStrategyConfig.getType())) {
            throw new InvalidRequestException(
                "Same error cannot point to multiple failure action - for error : " + ngFailureType.getYamlName());
          }

          // Add to put checking if its AllErrors or normal one.
          if (ngFailureType == NGFailureTypeV1.ALL_ERRORS) {
            allErrorsCount += 1;
            allErrorFailureStrategyAction = failureStrategyConfig;
            if (failureStrategyConfig.getErrors().size() != 1) {
              throw new InvalidRequestException(
                  "With AllErrors there cannot be other specified errors defined in same list.");
            }
            if (allErrorsCount > 1 && !isStepGroup) {
              throw new InvalidRequestException(
                  "AllErrors are defined multiple times either in stage or step failure strategies.");
            }
          } else {
            map.put(ngFailureType, failureStrategyConfig);
          }
        }
      }

      if (allErrorsCount > 0) {
        for (NGFailureTypeV1 internalFailureType : NGFailureTypeV1.values()) {
          if (internalFailureType != NGFailureTypeV1.ALL_ERRORS.ALL_ERRORS && !map.containsKey(internalFailureType)) {
            map.put(internalFailureType, allErrorFailureStrategyAction);
          }
        }
      }
    }
    return map;
  }

  private Map<FailureConfigV1, Collection<FailureType>> convertNGFailureTypeToFailureTypesMultiMap(
      EnumMap<NGFailureTypeV1, FailureConfigV1> map) {
    Multimap<FailureConfigV1, FailureType> invertedMap = ArrayListMultimap.create();

    map.keySet().forEach(ngFailureType -> {
      EnumSet<FailureType> failureTypes = ngFailureType.getFailureTypes();
      failureTypes.forEach(failureType -> invertedMap.put(map.get(ngFailureType), failureType));
    });
    return invertedMap.asMap();
  }

  public void validateRetryFailureAction(RetryFailureConfigV1 retryAction) {
    if (retryAction.getSpec() == null) {
      throw new InvalidRequestException("Retry Spec cannot be null or empty");
    }

    ParameterField<Integer> retryCount = retryAction.getSpec().getAttempts();
    if (retryCount.getValue() == null) {
      throw new InvalidRequestException("Retry Count cannot be null or empty");
    }
    if (retryAction.getSpec().getIntervals().getValue() == null) {
      throw new InvalidRequestException("Retry Interval cannot be null or empty");
    }
    if (retryAction.getSpec().getOn_failure() == null) {
      throw new InvalidRequestException("Retry Action cannot be null or empty");
    }
    if (retryCount.isExpression()) {
      throw new InvalidRequestException("RetryCount fixed value is not given.");
    }
    if (retryAction.getSpec().getIntervals().isExpression()) {
      throw new InvalidRequestException("RetryIntervals cannot be expression/runtime input. Please give values.");
    }
    FailureConfigV1 actionUnderRetry = retryAction.getSpec().getOn_failure();

    if (!validateActionAfterRetryFailure(actionUnderRetry)) {
      throw new InvalidRequestException("Retry action cannot have post retry failure action as Retry");
    }
    if (actionUnderRetry.getType().equals(NGFailureActionType.PROCEED_WITH_DEFAULT_VALUES)) {
      throw new InvalidRequestException(
          "Retry action cannot have post retry failure action as ProceedWithDefaultValues");
    }
    // validating Retry -> Manual Intervention -> Retry
    if (actionUnderRetry.getType().equals(NGFailureActionType.MANUAL_INTERVENTION)) {
      if (validateRetryActionUnderManualAction(((ManualInterventionFailureConfigV1) actionUnderRetry).getSpec())) {
        throw new InvalidRequestException(
            "Retry Action cannot be applied under Manual Action which itself is in Retry Action");
      }
    }
  }

  public void validateManualInterventionFailureAction(ManualInterventionFailureConfigV1 actionConfig) {
    if (actionConfig.getSpec() == null) {
      throw new InvalidRequestException("ManualIntervention Spec cannot be null or empty.");
    }
    if (actionConfig.getSpec().getTimeout_action() == null) {
      throw new InvalidRequestException("Action onTimeout of ManualIntervention cannot be null or empty.");
    }
    if (actionConfig.getSpec().getTimeout().getValue() == null) {
      throw new InvalidRequestException(
          "Timeout period for ManualIntervention cannot be null or empty. Please give values");
    }
    if (actionConfig.getSpec().getTimeout().isExpression()) {
      throw new InvalidRequestException(
          "Timeout period for ManualIntervention cannot be expression/runtime input. Please give values.");
    }

    FailureConfigV1 actionUnderManualIntervention = actionConfig.getSpec().getTimeout_action();
    if (actionUnderManualIntervention.getType().equals(NGFailureActionType.MANUAL_INTERVENTION)) {
      throw new InvalidRequestException("Manual Action cannot be applied as PostTimeOut Action");
    }

    // validating Manual Intervention -> Retry
    if (actionUnderManualIntervention.getType().equals(NGFailureActionType.RETRY)) {
      throw new InvalidRequestException(
          "Retry is not allowed as post timeout action in Manual intervention as it can lead to an infinite loop");
    }
    if (actionUnderManualIntervention.getType().equals(NGFailureActionType.PROCEED_WITH_DEFAULT_VALUES)) {
      throw new InvalidRequestException(
          "ProceedWithDefaultValues is not allowed as post timeout action in Manual intervention");
    }
  }

  public boolean validateActionAfterRetryFailure(FailureConfigV1 action) {
    return action.getType() != NGFailureActionTypeV1.ABORT.RETRY;
  }

  public boolean validateManualActionUnderRetryAction(RetryFailureSpecConfigV1 retrySpecConfig) {
    return retrySpecConfig.getOn_failure().getType().equals(NGFailureActionType.MANUAL_INTERVENTION);
  }

  public boolean validateRetryActionUnderManualAction(ManualFailureSpecConfigV1 manualSpecConfig) {
    return manualSpecConfig.getTimeout_action().getType().equals(NGFailureActionType.RETRY);
  }

  public void validateRetrySGFailureAction(RetrySGFailureConfigV1 retryAction) {
    if (retryAction.getSpec() == null) {
      throw new InvalidRequestException("Retry Spec cannot be null or empty");
    }

    ParameterField<Integer> retryCount = retryAction.getSpec().getAttempts();
    if (retryCount.getValue() == null) {
      throw new InvalidRequestException("Retry Count cannot be null or empty");
    }
    if (retryAction.getSpec().getIntervals().getValue() == null) {
      throw new InvalidRequestException("Retry Interval cannot be null or empty");
    }
    if (retryCount.isExpression()) {
      throw new InvalidRequestException("RetryCount fixed value is not given.");
    }
    if (retryAction.getSpec().getIntervals().isExpression()) {
      throw new InvalidRequestException("RetryIntervals cannot be expression/runtime input. Please give values.");
    }
  }

  public OnFailPipelineRollbackParameters buildOnFailPipelineRollbackParameters(Set<FailureType> failureTypes) {
    return OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failureTypes).build();
  }
}
