/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualFailureSpecConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class FailureStrategiesUtils {
  public Map<FailureStrategyActionConfig, Collection<FailureType>> priorityMergeFailureStrategies(
      List<FailureStrategyConfig> stepFailureStrategies, List<FailureStrategyConfig> stepGroupFailureStrategies,
      List<FailureStrategyConfig> stageFailureStrategies) {
    // priority merge all declared failure strategies, least significant are added first to map
    EnumMap<NGFailureType, FailureStrategyActionConfig> failureStrategiesMap = new EnumMap<>(NGFailureType.class);
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stageFailureStrategies));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepGroupFailureStrategies));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepFailureStrategies));

    // invert map so that action become key
    return convertNGFailureTypeToFailureTypesMultiMap(failureStrategiesMap);
  }

  private EnumMap<NGFailureType, FailureStrategyActionConfig> expandFailureStrategiesToMap(
      List<FailureStrategyConfig> failureStrategyConfigList) {
    EnumMap<NGFailureType, FailureStrategyActionConfig> map = new EnumMap<>(NGFailureType.class);

    if (isNotEmpty(failureStrategyConfigList)) {
      int allErrorsCount = 0;
      FailureStrategyActionConfig allErrorFailureStrategyAction = null;
      for (FailureStrategyConfig failureStrategyConfig : failureStrategyConfigList) {
        for (NGFailureType ngFailureType : failureStrategyConfig.getOnFailure().getErrors()) {
          if (map.containsKey(ngFailureType)
              && !map.get(ngFailureType).equals(failureStrategyConfig.getOnFailure().getAction())) {
            throw new InvalidRequestException(
                "Same error cannot point to multiple failure action - for error : " + ngFailureType.getYamlName());
          }

          // Add to put checking if its AllErrors or normal one.
          if (ngFailureType == NGFailureType.ALL_ERRORS) {
            allErrorsCount += 1;
            allErrorFailureStrategyAction = failureStrategyConfig.getOnFailure().getAction();
            if (failureStrategyConfig.getOnFailure().getErrors().size() != 1) {
              throw new InvalidRequestException(
                  "With AllErrors there cannot be other specified errors defined in same list.");
            }
            if (allErrorsCount > 1) {
              throw new InvalidRequestException(
                  "AllErrors are defined multiple times either in stage, stepGroup or step failure strategies.");
            }
          } else {
            map.put(ngFailureType, failureStrategyConfig.getOnFailure().getAction());
          }
        }
      }

      if (allErrorsCount > 0) {
        for (NGFailureType internalFailureType : NGFailureType.values()) {
          if (internalFailureType != NGFailureType.ALL_ERRORS && !map.containsKey(internalFailureType)) {
            map.put(internalFailureType, allErrorFailureStrategyAction);
          }
        }
      }
    }
    return map;
  }

  private Map<FailureStrategyActionConfig, Collection<FailureType>> convertNGFailureTypeToFailureTypesMultiMap(
      EnumMap<NGFailureType, FailureStrategyActionConfig> map) {
    Multimap<FailureStrategyActionConfig, FailureType> invertedMap = ArrayListMultimap.create();

    map.keySet().forEach(ngFailureType -> {
      EnumSet<FailureType> failureTypes = ngFailureType.getFailureTypes();
      failureTypes.forEach(failureType -> invertedMap.put(map.get(ngFailureType), failureType));
    });
    return invertedMap.asMap();
  }

  public void validateRetryFailureAction(RetryFailureActionConfig retryAction) {
    if (retryAction.getSpecConfig() == null) {
      throw new InvalidRequestException("Retry Spec cannot be null or empty");
    }

    ParameterField<Integer> retryCount = retryAction.getSpecConfig().getRetryCount();
    if (retryCount.getValue() == null) {
      throw new InvalidRequestException("Retry Count cannot be null or empty");
    }
    if (retryAction.getSpecConfig().getRetryIntervals().getValue() == null) {
      throw new InvalidRequestException("Retry Interval cannot be null or empty");
    }
    if (retryAction.getSpecConfig().getOnRetryFailure() == null) {
      throw new InvalidRequestException("Retry Action cannot be null or empty");
    }
    if (retryCount.isExpression()) {
      throw new InvalidRequestException("RetryCount fixed value is not given.");
    }
    if (retryAction.getSpecConfig().getRetryIntervals().isExpression()) {
      throw new InvalidRequestException("RetryIntervals cannot be expression/runtime input. Please give values.");
    }
    FailureStrategyActionConfig actionUnderRetry = retryAction.getSpecConfig().getOnRetryFailure().getAction();

    if (!validateActionAfterRetryFailure(actionUnderRetry)) {
      throw new InvalidRequestException("Retry action cannot have post retry failure action as Retry");
    }
    // validating Retry -> Manual Intervention -> Retry
    if (actionUnderRetry.getType().equals(NGFailureActionType.MANUAL_INTERVENTION)) {
      if (validateRetryActionUnderManualAction(
              ((ManualInterventionFailureActionConfig) actionUnderRetry).getSpecConfig())) {
        throw new InvalidRequestException(
            "Retry Action cannot be applied under Manual Action which itself is in Retry Action");
      }
    }
  }

  public void validateManualInterventionFailureAction(ManualInterventionFailureActionConfig actionConfig) {
    if (actionConfig.getSpecConfig() == null) {
      throw new InvalidRequestException("ManualIntervention Spec cannot be null or empty.");
    }
    if (actionConfig.getSpecConfig().getOnTimeout() == null) {
      throw new InvalidRequestException("Action onTimeout of ManualIntervention cannot be null or empty.");
    }
    if (actionConfig.getSpecConfig().getTimeout().getValue() == null) {
      throw new InvalidRequestException(
          "Timeout period for ManualIntervention cannot be null or empty. Please give values");
    }
    if (actionConfig.getSpecConfig().getTimeout().isExpression()) {
      throw new InvalidRequestException(
          "Timeout period for ManualIntervention cannot be expression/runtime input. Please give values.");
    }

    FailureStrategyActionConfig actionUnderManualIntervention = actionConfig.getSpecConfig().getOnTimeout().getAction();
    if (actionUnderManualIntervention.getType().equals(NGFailureActionType.MANUAL_INTERVENTION)) {
      throw new InvalidRequestException("Manual Action cannot be applied as PostTimeOut Action");
    }

    // validating Manual Intervention -> Retry
    if (actionUnderManualIntervention.getType().equals(NGFailureActionType.RETRY)) {
      throw new InvalidRequestException(
          "Retry is not allowed as post timeout action in Manual intervention as it can lead to an infinite loop");
    }
  }

  public boolean validateActionAfterRetryFailure(FailureStrategyActionConfig action) {
    return action.getType() != NGFailureActionType.RETRY;
  }

  public boolean validateManualActionUnderRetryAction(RetryFailureSpecConfig retrySpecConfig) {
    return retrySpecConfig.getOnRetryFailure().getAction().getType().equals(NGFailureActionType.MANUAL_INTERVENTION);
  }

  public boolean validateRetryActionUnderManualAction(ManualFailureSpecConfig manualSpecConfig) {
    return manualSpecConfig.getOnTimeout().getAction().getType().equals(NGFailureActionType.RETRY);
  }
}
