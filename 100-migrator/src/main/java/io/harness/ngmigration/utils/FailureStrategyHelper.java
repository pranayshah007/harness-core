/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;

import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.RepairActionCode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.ProceedWithDefaultValuesFailureActionConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualFailureSpecConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.OnTimeoutConfig;
import io.harness.yaml.core.failurestrategy.markFailure.MarkAsFailFailureActionConfig;
import io.harness.yaml.core.failurestrategy.marksuccess.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.OnRetryFailureConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;
import io.harness.yaml.core.failurestrategy.rollback.StageRollbackFailureActionConfig;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.FailureStrategy;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FailureStrategyHelper {
  private static final long DAY_MS = 24 * 60 * 60 * 1000;

  public static FailureStrategyConfig toFailureStrategyConfig(FailureStrategy strategy) {
    if (EmptyPredicate.isEmpty(strategy.getFailureTypes())) {
      return null;
    }
    List<NGFailureType> errors =
        strategy.getFailureTypes().stream().map(FailureStrategyHelper::getFailureType).collect(Collectors.toList());
    if (errors.contains(NGFailureType.ALL_ERRORS)) {
      errors = Collections.singletonList(NGFailureType.ALL_ERRORS);
    }
    return FailureStrategyConfig.builder()
        .onFailure(OnFailureConfig.builder().errors(errors).action(getAction(strategy)).build())
        .build();
  }

  static NGFailureType getFailureType(FailureType failureType) {
    switch (failureType) {
      case EXPIRED:
        return NGFailureType.UNKNOWN;
      case DELEGATE_PROVISIONING:
        return NGFailureType.DELEGATE_PROVISIONING_ERROR;
      case CONNECTIVITY:
        return NGFailureType.CONNECTIVITY_ERROR;
      case AUTHENTICATION:
        return NGFailureType.AUTHENTICATION_ERROR;
      case VERIFICATION_FAILURE:
        return NGFailureType.VERIFICATION_ERROR;
      case AUTHORIZATION_ERROR:
        return NGFailureType.AUTHORIZATION_ERROR;
      case TIMEOUT_ERROR:
        return NGFailureType.TIMEOUT_ERROR;
      case POLICY_EVALUATION_FAILURE:
        return NGFailureType.POLICY_EVALUATION_FAILURE;
      case INPUT_TIMEOUT_FAILURE:
        return NGFailureType.INPUT_TIMEOUT_FAILURE;
      case APPROVAL_REJECTION:
        return NGFailureType.APPROVAL_REJECTION;
      case DELEGATE_RESTART:
        return NGFailureType.DELEGATE_RESTART_ERROR;
      case APPLICATION_ERROR:
      default:
        return NGFailureType.ALL_ERRORS;
    }
  }

  private static FailureStrategyActionConfig getAction(FailureStrategy failureStrategy) {
    RepairActionCode actionCode = failureStrategy.getRepairActionCode();

    FailureStrategyActionConfig config = getAction(actionCode);
    if (config != null) {
      return config;
    }

    if (actionCode == RepairActionCode.MANUAL_INTERVENTION) {
      long days = 1;
      if (failureStrategy.getManualInterventionTimeout() != null) {
        days = Math.max(days, failureStrategy.getManualInterventionTimeout() / DAY_MS);
      }
      return ManualInterventionFailureActionConfig.builder()
          .specConfig(
              ManualFailureSpecConfig.builder()
                  .timeout(ParameterField.createValueField(Timeout.fromString(days + "d")))
                  .onTimeout(
                      OnTimeoutConfig.builder().action(getAction(failureStrategy.getActionAfterTimeout())).build())
                  .build())
          .build();
    }

    if (actionCode == RepairActionCode.RETRY) {
      List<Timeout> intervals = Collections.singletonList(Timeout.fromString("10m"));
      if (EmptyPredicate.isNotEmpty(failureStrategy.getRetryIntervals())) {
        intervals = failureStrategy.getRetryIntervals()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(interval -> Timeout.fromString(interval + "s"))
                        .collect(Collectors.toList());
      }

      return RetryFailureActionConfig.builder()
          .specConfig(
              RetryFailureSpecConfig.builder()
                  .retryIntervals(ParameterField.createValueField(intervals))
                  .retryCount(ParameterField.createValueField(failureStrategy.getRetryCount()))
                  .onRetryFailure(OnRetryFailureConfig.builder()
                                      .action(getActionWithDefault(failureStrategy.getRepairActionCodeAfterRetry(),
                                          AbortFailureActionConfig.builder().build()))
                                      .build())
                  .build())
          .build();
    }

    return ManualInterventionFailureActionConfig.builder()
        .specConfig(ManualFailureSpecConfig.builder()
                        .timeout(ParameterField.createValueField(Timeout.fromString("2d")))
                        .onTimeout(OnTimeoutConfig.builder().action(AbortFailureActionConfig.builder().build()).build())
                        .build())
        .build();
  }

  private static FailureStrategyActionConfig getAction(RepairActionCode actionCode) {
    if (actionCode == RepairActionCode.IGNORE) {
      return IgnoreFailureActionConfig.builder().build();
    }

    if (actionCode == RepairActionCode.ABORT_WORKFLOW_EXECUTION || actionCode == RepairActionCode.END_EXECUTION) {
      return AbortFailureActionConfig.builder().build();
    }

    if (actionCode == RepairActionCode.MARK_AS_FAILURE) {
      return MarkAsFailFailureActionConfig.builder().build();
    }

    if (actionCode == RepairActionCode.CONTINUE_WITH_DEFAULTS) {
      return ProceedWithDefaultValuesFailureActionConfig.builder().build();
    }

    if (actionCode == RepairActionCode.ROLLBACK_WORKFLOW) {
      return StageRollbackFailureActionConfig.builder().build();
    }

    if (actionCode == RepairActionCode.ROLLBACK_PHASE
        || actionCode == RepairActionCode.ROLLBACK_PROVISIONER_AFTER_PHASES) {
      return StageRollbackFailureActionConfig.builder().build();
    }

    return null;
  }

  static FailureStrategyActionConfig getAction(ExecutionInterruptType interruptType) {
    if (Lists
            .newArrayList(
                ExecutionInterruptType.ABORT, ExecutionInterruptType.ABORT_ALL, ExecutionInterruptType.END_EXECUTION)
            .contains(interruptType)) {
      return AbortFailureActionConfig.builder().build();
    }

    if (Lists.newArrayList(ExecutionInterruptType.MARK_FAILED, ExecutionInterruptType.MARK_EXPIRED)
            .contains(interruptType)) {
      return MarkAsFailFailureActionConfig.builder().build();
    }

    if (Lists.newArrayList(ExecutionInterruptType.IGNORE).contains(interruptType)) {
      return IgnoreFailureActionConfig.builder().build();
    }

    if (Lists.newArrayList(ExecutionInterruptType.MARK_SUCCESS).contains(interruptType)) {
      return MarkAsSuccessFailureActionConfig.builder().build();
    }

    if (ExecutionInterruptType.ROLLBACK.equals(interruptType)) {
      return StageRollbackFailureActionConfig.builder().build();
    }
    return AbortFailureActionConfig.builder().build();
  }

  static FailureStrategyActionConfig getActionWithDefault(
      RepairActionCode actionCode, FailureStrategyActionConfig defaultStrat) {
    FailureStrategyActionConfig strat = getAction(actionCode);
    return strat != null ? strat : defaultStrat;
  }
}
