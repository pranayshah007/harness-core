/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.beans.PhaseStepType.CONTAINER_DEPLOY;
import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.migrations.Migration;

import software.wings.api.DeploymentType;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesContainerTaskUtils;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveResizeFromStatefulSetWorkflows implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowService workflowService;
  @Inject private ServiceResourceService serviceResourceService;

  @Override
  public void migrate() {
    log.info("Removing resize steps from stateful set workflows");
    List<Account> accounts = wingsPersistence.createQuery(Account.class, excludeAuthority).asList();
    log.info("Checking {} accounts", accounts.size());
    for (Account account : accounts) {
      List<Application> apps =
          wingsPersistence.createQuery(Application.class).filter(ACCOUNT_ID_KEY, account.getUuid()).asList();
      log.info("Checking {} applications in account {}", apps.size(), account.getAccountName());
      for (Application app : apps) {
        List<Workflow> workflows =
            workflowService
                .listWorkflows(aPageRequest().withLimit(UNLIMITED).addFilter("appId", EQ, app.getUuid()).build())
                .getResponse();
        for (Workflow workflow : workflows) {
          boolean workflowModified = false;
          if (workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow) {
            CanaryOrchestrationWorkflow coWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
            for (WorkflowPhase workflowPhase : coWorkflow.getWorkflowPhases()) {
              if (DeploymentType.KUBERNETES == workflowPhase.getDeploymentType()) {
                boolean isStatefulSet = isStatefulSet(app.getUuid(), workflowPhase.getServiceId());
                if (isStatefulSet) {
                  workflowModified = true;
                  log.info("Found stateful set");
                  workflowPhase.setStatefulSet(true);
                  for (PhaseStep phaseStep : workflowPhase.getPhaseSteps()) {
                    if (CONTAINER_DEPLOY == phaseStep.getPhaseStepType()) {
                      GraphNode removeNode = null;
                      for (GraphNode node : phaseStep.getSteps()) {
                        if (StateType.KUBERNETES_DEPLOY.name().equals(node.getType())) {
                          removeNode = node;
                          WorkflowPhase rollbackPhase =
                              coWorkflow.getRollbackWorkflowPhaseIdMap().get(workflowPhase.getUuid());

                          for (PhaseStep rollbackPhaseStep : rollbackPhase.getPhaseSteps()) {
                            GraphNode removeRollbackNode = null;
                            for (GraphNode rollbackNode : rollbackPhaseStep.getSteps()) {
                              if (StateType.KUBERNETES_DEPLOY.name().equals(rollbackNode.getType())) {
                                removeRollbackNode = rollbackNode;
                              }
                            }
                            if (removeRollbackNode != null) {
                              log.info("Removing deploy rollback step");
                              rollbackPhaseStep.getSteps().remove(removeRollbackNode);
                            }
                          }
                        }
                      }
                      if (removeNode != null) {
                        log.info("Removing deploy step");
                        phaseStep.getSteps().remove(removeNode);
                      }
                    }
                  }
                }
              }
            }
          }
          if (workflowModified) {
            try {
              log.info("--- Workflow updated: {}", workflow.getName());
              workflowService.updateWorkflow(workflow, false);
              Thread.sleep(100);
            } catch (Exception e) {
              log.error("Error updating workflow", e);
            }
          }
        }
      }
    }
  }

  private boolean isStatefulSet(String appId, String serviceId) {
    KubernetesContainerTask containerTask =
        (KubernetesContainerTask) serviceResourceService.getContainerTaskByDeploymentType(
            appId, serviceId, KUBERNETES.name());
    return containerTask != null && KubernetesContainerTaskUtils.checkStatefulSet(containerTask.getAdvancedConfig());
  }
}
