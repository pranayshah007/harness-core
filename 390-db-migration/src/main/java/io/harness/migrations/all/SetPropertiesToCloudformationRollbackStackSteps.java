/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.exception.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetPropertiesToCloudformationRollbackStackSteps implements Migration {
  private static final String LOG_PREFIX = "CLOUDFORMATION_ROLLBACK_PROPERTIES_MIGRATION:";
  private static final String EXPERIAN_ACCOUNT_ID = "cpbandfuSD-hva0LU8wz0g";
  private static final String QA_AUTOMATION_ACCOUNT = "XICOBc_qRa2PJmVaWOx-cQ";

  @Inject AccountService accountService;
  @Inject WorkflowService workflowService;
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    migrateForAccount(EXPERIAN_ACCOUNT_ID);
    migrateForAccount(QA_AUTOMATION_ACCOUNT);
    log.info(String.format("%s Migration completed for all the workflows", LOG_PREFIX));
  }

  private void migrateForAccount(String accountId) {
    log.info(String.format("%s Starting migration for accountId: %s", LOG_PREFIX, accountId));

    long workflowsSize = wingsPersistence.createQuery(Workflow.class).filter(WorkflowKeys.accountId, accountId).count();
    log.info(String.format("%s Total workflows for account = %s", LOG_PREFIX, workflowsSize));

    int numberOfPages = (int) ((workflowsSize + 999) / 1000);
    for (int i = 0; i < numberOfPages; i++) {
      List<Workflow> newWorkflows = workflowService
                                        .listWorkflows(aPageRequest()
                                                           .withLimit(UNLIMITED)
                                                           .withOffset(String.valueOf(i * 1000))
                                                           .addFilter(WorkflowKeys.accountId, EQ, accountId)
                                                           .build())
                                        .getResponse();
      if (!isEmpty(newWorkflows)) {
        for (Workflow workflow : newWorkflows) {
          try {
            migrateWorkflow(workflow);
          } catch (Exception e) {
            log.error(String.format("%s migration failed for workflow: %s due to: %s", LOG_PREFIX, workflow.getUuid(),
                          ExceptionUtils.getMessage(e)),
                e);
          }
        }
      }
    }
  }

  private void migrateWorkflow(Workflow workflow) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    notNullCheck(
        "orchestrationWorkflow is null in workflow: " + workflow.getUuid(), workflow.getOrchestrationWorkflow());

    if (orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
      Optional<Graph> preDeploymentGraph = canaryOrchestrationWorkflow.getGraph()
                                               .getSubworkflows()
                                               .values()
                                               .stream()
                                               .filter(s -> s.getGraphName().equals("Pre-Deployment"))
                                               .findFirst();
      List<GraphNode> preDeploymentNodes = preDeploymentGraph.isPresent()
          ? preDeploymentGraph.get()
                .getNodes()
                .stream()
                .filter(graphNode -> graphNode.getType().equals("CLOUD_FORMATION_CREATE_STACK"))
                .collect(Collectors.toList())
          : Collections.emptyList();

      preDeploymentNodes.forEach(graphNode -> {
        if (graphNode.getType().equals("CLOUD_FORMATION_CREATE_STACK")) {
          canaryOrchestrationWorkflow.getGraph().getSubworkflows().values().forEach(graph -> {
            if (graph.getGraphName().equals("Rollback Provisioners")
                || graph.getGraphName().equals("Rollback Provisioners Reverse")) {
              graph.getNodes().forEach(graphNode1 -> {
                if (graphNode1.getName().equals("Rollback " + graphNode.getName())) {
                  graphNode1.getProperties().put("customStackName", graphNode.getProperties().get("customStackName"));
                  graphNode1.getProperties().put("region", graphNode.getProperties().get("region"));
                  graphNode1.getProperties().put(
                      "useCustomStackName", graphNode.getProperties().get("useCustomStackName"));
                  graphNode1.getProperties().put("awsConfigId", graphNode.getProperties().get("awsConfigId"));
                }
              });
            }
          });
        }
      });

      if (isNotEmpty(preDeploymentNodes)) {
        try {
          log.info(String.format("%s Starting migration for workflow: %s", LOG_PREFIX, workflow.getUuid()));
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
  }
}
