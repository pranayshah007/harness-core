/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.validation.Validator.notNullCheck;

import io.harness.exception.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class MigrateServiceNowCriteriaInWorkflows implements Migration {
  @Inject private WorkflowService workflowService;
  @Inject private AppService appService;
  private final String DEBUG_LINE = " SERVICENOW_CRITERIA_MIGRATION: ";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  public void migrate() {
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      String accountId = account.getUuid();
      log.info(StringUtils.join(DEBUG_LINE, "Starting ServiceNow Criteria migration for accountId:", accountId));
      migrate(account);
    }
    log.info("{}: ServiceNow critieria migration over for all workflows", DEBUG_LINE);
  }

  public void migrate(Account account) {
    log.info(StringUtils.join(
        DEBUG_LINE, "Starting ServiceNow Criteria migration for Workflows, accountId ", account.getUuid()));

    List<Workflow> workflows = io.harness.migrations.all.WorkflowAndPipelineMigrationUtils.fetchAllWorkflowsForAccount(
        wingsPersistence, workflowService, account.getUuid());

    for (Workflow workflow : workflows) {
      try {
        log.info(StringUtils.join(
            DEBUG_LINE, "Starting ServiceNow Condition migration for Workflow, workflowId ", workflow.getUuid()));
        workflowService.loadOrchestrationWorkflow(workflow, workflow.getDefaultVersion());
        migrate(workflow);
      } catch (Exception e) {
        log.error("[SERVICENOW_CRITERIA_ERROR] Migration failed for WorkflowId: " + workflow.getUuid()
            + ExceptionUtils.getMessage(e));
      }
    }
  }

  public void migrate(Workflow workflow) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    notNullCheck(
        "orchestrationWorkflow is null in workflow: " + workflow.getUuid(), workflow.getOrchestrationWorkflow());

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;

    if (canaryOrchestrationWorkflow.getPreDeploymentSteps() != null
        && canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps() != null) {
      for (GraphNode node : canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps()) {
        if (updateGraphNode(node)) {
          workflowService.updatePreDeployment(
              workflow.getAppId(), workflow.getUuid(), canaryOrchestrationWorkflow.getPreDeploymentSteps());
        }
      }
    }

    if (canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps() != null) {
      for (GraphNode node : canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps()) {
        if (updateGraphNode(node)) {
          workflowService.updatePostDeployment(
              workflow.getAppId(), workflow.getUuid(), canaryOrchestrationWorkflow.getPostDeploymentSteps());
        }
      }
    }

    if (canaryOrchestrationWorkflow.getWorkflowPhases() != null) {
      for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
        migrate(phase, workflow.getUuid(), workflow.getAppId(), phase.getUuid());
      }
    }
    if (canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap() != null) {
      for (Map.Entry<String, WorkflowPhase> phaseEntry :
          canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().entrySet()) {
        String phaseId = "";
        for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
          if (phase.getName().equals(phaseEntry.getValue().getPhaseNameForRollback())) {
            phaseId = phase.getUuid();
          }
        }
        migrate(phaseEntry.getValue(), workflow.getUuid(), workflow.getAppId(), phaseId);
      }
    }
  }

  public void migrate(WorkflowPhase phase, String workflowId, String appId, String phaseId) {
    boolean workflowModified = false;
    if (phase.getPhaseSteps() != null) {
      for (PhaseStep phaseStep : phase.getPhaseSteps()) {
        if (phaseStep.getSteps() != null) {
          for (GraphNode node : phaseStep.getSteps()) {
            workflowModified = updateGraphNode(node) || workflowModified;
          }
        }
      }
    }

    if (phase.getPhaseSteps() != null) {
      for (PhaseStep phaseStep : phase.getPhaseSteps()) {
        if (phaseStep.getSteps() != null) {
          for (GraphNode node : phaseStep.getSteps()) {
            workflowModified = updateGraphNode(node) || workflowModified;
          }
        }
      }
    }

    if (workflowModified) {
      try {
        if (phase.isRollback()) {
          workflowService.updateWorkflowPhaseRollback(appId, workflowId, phaseId, phase);
        }
        workflowService.updateWorkflowPhase(appId, workflowId, phase);
      } catch (Exception e) {
        log.error("[SERVICENOW_CRITERIA_ERROR] Error updating workflow " + workflowId, e);
      }
    }
  }

  private boolean updateGraphNode(GraphNode node) {
    if (StateType.APPROVAL.name().equals(node.getType()) && node.getProperties() != null) {
      return WorkflowAndPipelineMigrationUtils.updateServiceNowProperties(node.getProperties());
    }
    return false;
  }
}
