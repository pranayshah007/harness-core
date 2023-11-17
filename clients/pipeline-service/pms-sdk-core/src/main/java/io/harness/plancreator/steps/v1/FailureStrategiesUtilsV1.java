/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureSpecConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetrySGFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureStrategyActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureActionTypeV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureTypeConstantsV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureTypeV1;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class FailureStrategiesUtilsV1 {
  public Map<FailureStrategyActionConfigV1, Collection<FailureType>> priorityMergeFailureStrategies(
      List<FailureConfigV1> stepFailureStrategies, List<FailureConfigV1> stepGroupFailureStrategies,
      List<FailureConfigV1> stageFailureStrategies) {
    // priority merge all declared failure strategies, least significant are added first to map
    EnumMap<NGFailureTypeV1, FailureStrategyActionConfigV1> failureStrategiesMap = new EnumMap<>(NGFailureTypeV1.class);
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stageFailureStrategies, false));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepGroupFailureStrategies, true));
    failureStrategiesMap.putAll(expandFailureStrategiesToMap(stepFailureStrategies, false));

    // invert map so that action become key
    return convertNGFailureTypeToFailureTypesMultiMap(failureStrategiesMap);
  }

  private EnumMap<NGFailureTypeV1, FailureStrategyActionConfigV1> expandFailureStrategiesToMap(
      List<FailureConfigV1> failureStrategyConfigList, boolean isStepGroup) {
    EnumMap<NGFailureTypeV1, FailureStrategyActionConfigV1> map = new EnumMap<>(NGFailureTypeV1.class);

    if (isNotEmpty(failureStrategyConfigList)) {
      int allErrorsCount = 0;
      FailureStrategyActionConfigV1 allErrorFailureStrategyAction = null;
      for (FailureConfigV1 failureStrategyConfig : failureStrategyConfigList) {
        for (NGFailureTypeV1 ngFailureType : failureStrategyConfig.getErrors()) {
          if (map.containsKey(ngFailureType)
              && !map.get(ngFailureType).equals(failureStrategyConfig.getAction().getType())) {
            throw new InvalidRequestException(
                "Same error cannot point to multiple failure action - for error : " + ngFailureType.getYamlName());
          }

          // Add to put checking if its AllErrors or normal one.
          if (ngFailureType == NGFailureTypeV1.ALL_ERRORS) {
            allErrorsCount += 1;
            allErrorFailureStrategyAction = failureStrategyConfig.getAction();
            if (failureStrategyConfig.getErrors().size() != 1) {
              throw new InvalidRequestException(
                  "With AllErrors there cannot be other specified errors defined in same list.");
            }
            if (allErrorsCount > 1 && !isStepGroup) {
              throw new InvalidRequestException(
                  "AllErrors are defined multiple times either in stage or step failure strategies.");
            }
          } else {
            map.put(ngFailureType, failureStrategyConfig.getAction());
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

  private Map<FailureStrategyActionConfigV1, Collection<FailureType>> convertNGFailureTypeToFailureTypesMultiMap(
      EnumMap<NGFailureTypeV1, FailureStrategyActionConfigV1> map) {
    Multimap<FailureStrategyActionConfigV1, FailureType> invertedMap = ArrayListMultimap.create();

    map.keySet().forEach(ngFailureType -> {
      EnumSet<FailureType> failureTypes = ngFailureType.getFailureTypes();
      failureTypes.forEach(failureType -> invertedMap.put(map.get(ngFailureType), failureType));
    });
    return invertedMap.asMap();
  }

  public void validateRetryFailureAction(RetryFailureActionConfigV1 retryAction) {
    if (retryAction.getSpec() == null) {
      throw new InvalidRequestException("Retry Spec cannot be null or empty");
    }

    ParameterField<Integer> retryCount = retryAction.getSpec().getAttempts();
    if (retryCount.getValue() == null) {
      throw new InvalidRequestException("Retry Count cannot be null or empty");
    }
    if (retryAction.getSpec().getInterval().getValue() == null) {
      throw new InvalidRequestException("Retry Interval cannot be null or empty");
    }
    if (retryAction.getSpec().getFailure() == null || retryAction.getSpec().getFailure().getAction() == null) {
      throw new InvalidRequestException("Retry Action cannot be null or empty");
    }
    if (retryCount.isExpression()) {
      throw new InvalidRequestException("RetryCount fixed value is not given.");
    }
    if (retryAction.getSpec().getInterval().isExpression()) {
      throw new InvalidRequestException("RetryIntervals cannot be expression/runtime input. Please give values.");
    }
    FailureStrategyActionConfigV1 actionUnderRetry = retryAction.getSpec().getFailure().getAction();

    if (!validateActionAfterRetryFailure(actionUnderRetry)) {
      throw new InvalidRequestException("Retry action cannot have post retry failure action as Retry");
    }
    if (actionUnderRetry.getType().equals(NGFailureActionType.PROCEED_WITH_DEFAULT_VALUES)) {
      throw new InvalidRequestException(
          "Retry action cannot have post retry failure action as ProceedWithDefaultValues");
    }
    // validating Retry -> Manual Intervention -> Retry
    if (actionUnderRetry.getType().equals(NGFailureActionType.MANUAL_INTERVENTION)) {
      if (validateRetryActionUnderManualAction(
              ((ManualInterventionFailureActionConfigV1) actionUnderRetry).getSpec())) {
        throw new InvalidRequestException(
            "Retry Action cannot be applied under Manual Action which itself is in Retry Action");
      }
    }
  }

  public void validateManualInterventionFailureAction(ManualInterventionFailureActionConfigV1 actionConfig) {
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

    FailureStrategyActionConfigV1 actionUnderManualIntervention = actionConfig.getSpec().getTimeout_action();
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

  public boolean validateActionAfterRetryFailure(FailureStrategyActionConfigV1 action) {
    return action.getType() != NGFailureActionTypeV1.ABORT.RETRY;
  }

  public boolean validateManualActionUnderRetryAction(RetryFailureSpecConfigV1 retrySpecConfig) {
    return retrySpecConfig.getFailure().getAction().getType().equals(NGFailureActionType.MANUAL_INTERVENTION);
  }

  public boolean validateRetryActionUnderManualAction(ManualFailureSpecConfigV1 manualSpecConfig) {
    return manualSpecConfig.getTimeout_action().getType().equals(NGFailureActionType.RETRY);
  }

  public void validateRetrySGFailureAction(RetrySGFailureActionConfigV1 retryAction) {
    if (retryAction.getSpec() == null) {
      throw new InvalidRequestException("Retry Spec cannot be null or empty");
    }

    ParameterField<Integer> retryCount = retryAction.getSpec().getAttempts();
    if (retryCount.getValue() == null) {
      throw new InvalidRequestException("Retry Count cannot be null or empty");
    }
    if (retryAction.getSpec().getInterval().getValue() == null) {
      throw new InvalidRequestException("Retry Interval cannot be null or empty");
    }
    if (retryCount.isExpression()) {
      throw new InvalidRequestException("RetryCount fixed value is not given.");
    }
    if (retryAction.getSpec().getInterval().isExpression()) {
      throw new InvalidRequestException("RetryIntervals cannot be expression/runtime input. Please give values.");
    }
  }

  public OnFailPipelineRollbackParameters buildOnFailPipelineRollbackParameters(Set<FailureType> failureTypes) {
    return OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failureTypes).build();
  }

  public boolean containsOnlyAllErrorsInSomeConfig(ParameterField<List<FailureConfigV1>> stageFailureStrategies) {
    boolean containsOnlyAllErrors = false;
    for (FailureConfigV1 failureConfig : stageFailureStrategies.getValue()) {
      if (failureConfig.getErrors().size() == 1
          && NGFailureTypeConstantsV1.ALL_ERRORS.contentEquals(failureConfig.getErrors().get(0).getYamlName())) {
        containsOnlyAllErrors = true;
      }
    }
    return containsOnlyAllErrors;
  }
}
