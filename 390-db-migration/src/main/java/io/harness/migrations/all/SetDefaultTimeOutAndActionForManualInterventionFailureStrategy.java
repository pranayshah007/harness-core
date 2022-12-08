/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.RepairActionCode;
import io.harness.exception.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.FailureStrategy;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetDefaultTimeOutAndActionForManualInterventionFailureStrategy implements Migration {
  private static final ExecutionInterruptType DEFAULT_ACTION_AFTER_TIMEOUT = ExecutionInterruptType.END_EXECUTION;
  private static final long DEFAULT_TIMEOUT = 1209600000L; // 14days
  private static final String LOG_PREFIX = "MANUAL_INTERVENTION_TIMEOUT_MIGRATION:";

  @Inject AccountService accountService;
  @Inject WorkflowService workflowService;
  @Inject WingsPersistence wingsPersistence;

  private final UnaryOperator<FailureStrategy> manualInterventionUpdater = failureStrategy
      -> RepairActionCode.MANUAL_INTERVENTION.equals(failureStrategy.getRepairActionCode())
      ? checkAndUpdateManualIntervention(failureStrategy)
      : failureStrategy;

  private FailureStrategy checkAndUpdateManualIntervention(FailureStrategy failureStrategy) {
    ExecutionInterruptType actionAfterTimeout = isValidAction(failureStrategy.getActionAfterTimeout())
        ? failureStrategy.getActionAfterTimeout()
        : DEFAULT_ACTION_AFTER_TIMEOUT;
    Long manualInterventionTimeout = isValidTimeOut(failureStrategy.getManualInterventionTimeout())
        ? failureStrategy.getManualInterventionTimeout()
        : DEFAULT_TIMEOUT;
    return failureStrategy.toBuilder()
        .actionAfterTimeout(actionAfterTimeout)
        .manualInterventionTimeout(manualInterventionTimeout)
        .build();
  }

  private boolean isValidTimeOut(Long manualInterventionTimeout) {
    return manualInterventionTimeout != null && manualInterventionTimeout >= 60000L;
  }

  private boolean isValidAction(ExecutionInterruptType actionAfterTimeout) {
    return Arrays.asList(ExecutionInterruptType.values()).contains(actionAfterTimeout);
  }

  @Override
  public void migrate() {
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      migrateForAccount(account);
    }
    log.info(String.format("%s Manual Intervention Timeouts completed for all the workflows", LOG_PREFIX));
  }

  private void migrateForAccount(Account account) {
    String accountId = account.getUuid();
    log.info(
        String.format("%s Starting Manual Intervention Timeouts migration for accountId: %s", LOG_PREFIX, accountId));

    List<Workflow> workflows = WorkflowAndPipelineMigrationUtils.fetchAllWorkflowsForAccount(
        wingsPersistence, workflowService, account.getUuid());

    for (Workflow workflow : workflows) {
      try {
        log.info(String.format(
            "%s Starting Manual Intervention Timeouts migration for workflow: %s", LOG_PREFIX, workflow.getUuid()));
        migrateWorkflow(workflow);
      } catch (Exception e) {
        log.error(String.format("%s Manual Intervention Timeouts migration failed for workflow: %s due to: %s",
                      LOG_PREFIX, workflow.getUuid(), ExceptionUtils.getMessage(e)),
            e);
      }
    }
  }

  private void migrateWorkflow(Workflow workflow) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    notNullCheck(
        "orchestrationWorkflow is null in workflow: " + workflow.getUuid(), workflow.getOrchestrationWorkflow());

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;

    if (workflowModified(canaryOrchestrationWorkflow)) {
      try {
        workflowService.updateWorkflow(workflow, true);
        log.info(String.format(
            "%s Workflow: {id: %s, name: %s} updated", LOG_PREFIX, workflow.getUuid(), workflow.getName()));
        Thread.sleep(100);
      } catch (Exception e) {
        log.error(String.format("%s Failed to update workflow: %s due to: %s", LOG_PREFIX, workflow.getUuid(),
                      ExceptionUtils.getMessage(e)),
            e);
      }
    }
  }

  private boolean workflowModified(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    boolean orchestrationLevelFailureStrategiesModified =
        checkAndModifyOrchestrationLevelFailureStrategies(canaryOrchestrationWorkflow);
    boolean phaseLevelFailureStrategiesModified =
        checkAndModifyPhaseLevelFailureStrategies(canaryOrchestrationWorkflow);
    boolean rollbackPhaseLevelFailureStrategiesModified =
        checkAndModifyRollbackPhaseLevelFailureStrategies(canaryOrchestrationWorkflow);
    boolean preDeploymentStepsLevelFailureStrategiesModified =
        checkAndModifyPreDeploymentStepsLevelFailureStrategies(canaryOrchestrationWorkflow);
    boolean postDeploymentStepsLevelFailureStrategiesModified =
        checkAndModifyPostDeploymentStepsLevelFailureStrategies(canaryOrchestrationWorkflow);

    return orchestrationLevelFailureStrategiesModified || phaseLevelFailureStrategiesModified
        || rollbackPhaseLevelFailureStrategiesModified || preDeploymentStepsLevelFailureStrategiesModified
        || postDeploymentStepsLevelFailureStrategiesModified;
  }

  boolean checkAndModifyPostDeploymentStepsLevelFailureStrategies(
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    return checkAndModifySteps(Collections.singletonList(canaryOrchestrationWorkflow.getPostDeploymentSteps()));
  }

  boolean checkAndModifyPreDeploymentStepsLevelFailureStrategies(
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    return checkAndModifySteps(Collections.singletonList(canaryOrchestrationWorkflow.getPreDeploymentSteps()));
  }

  boolean checkAndModifyRollbackPhaseLevelFailureStrategies(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap();
    if (isEmpty(rollbackWorkflowPhaseIdMap)) {
      return false;
    }
    List<WorkflowPhase> workflowPhases = new ArrayList<>(rollbackWorkflowPhaseIdMap.values());
    return checkAndModifyPhases(workflowPhases);
  }

  boolean checkAndModifyOrchestrationLevelFailureStrategies(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    final List<FailureStrategy> failureStrategies = canaryOrchestrationWorkflow.getFailureStrategies();
    if (migrationRequired(failureStrategies)) {
      canaryOrchestrationWorkflow.setFailureStrategies(upgradeManualInterventions(failureStrategies));
      return true;
    }
    return false;
  }

  boolean checkAndModifyPhaseLevelFailureStrategies(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    final List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
    return checkAndModifyPhases(workflowPhases);
  }

  private boolean checkAndModifyPhases(List<WorkflowPhase> workflowPhases) {
    boolean modified = false;
    if (isNotEmpty(workflowPhases)) {
      for (WorkflowPhase phase : workflowPhases) {
        List<PhaseStep> phaseSteps = phase.getPhaseSteps();
        modified = checkAndModifySteps(phaseSteps);
      }
    }
    return modified;
  }

  private boolean checkAndModifySteps(List<PhaseStep> phaseSteps) {
    boolean modified = false;
    if (isNotEmpty(phaseSteps)) {
      for (PhaseStep phaseStep : phaseSteps) {
        List<FailureStrategy> failureStrategies = phaseStep.getFailureStrategies();
        if (migrationRequired(failureStrategies)) {
          phaseStep.setFailureStrategies(upgradeManualInterventions(failureStrategies));
          modified = true;
        }
      }
    }
    return modified;
  }

  private List<FailureStrategy> upgradeManualInterventions(List<FailureStrategy> originalFailureStrategies) {
    return originalFailureStrategies.stream().map(manualInterventionUpdater).collect(Collectors.toList());
  }

  boolean migrationRequired(List<FailureStrategy> originalFailureStrategies) {
    if (isEmpty(originalFailureStrategies)) {
      return false;
    }
    List<RepairActionCode> manualInterventions =
        originalFailureStrategies.stream()
            .map(FailureStrategy::getRepairActionCode)
            .filter(repairActionCode -> repairActionCode.equals(RepairActionCode.MANUAL_INTERVENTION))
            .collect(Collectors.toList());
    return isNotEmpty(manualInterventions);
  }
}
