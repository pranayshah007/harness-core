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
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.migrations.Migration;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesContainerTaskUtils;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetDaemonSetInWorkflowPhase implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowService workflowService;
  @Inject private ServiceResourceService serviceResourceService;

  @Override
  public void migrate() {
    log.info("Checking for DaemonSets in all CanaryOrchestrationWorkflows");
    PageRequest<Application> pageRequest = aPageRequest().withLimit(UNLIMITED).build();
    log.info("Retrieving applications");
    PageResponse<Application> pageResponse = wingsPersistence.query(Application.class, pageRequest);

    List<Application> apps = pageResponse.getResponse();
    if (pageResponse.isEmpty() || isEmpty(apps)) {
      log.info("No applications found");
      return;
    }
    log.info("Checking {} applications", apps.size());
    for (Application app : apps) {
      List<Workflow> workflows =
          workflowService
              .listWorkflows(aPageRequest().withLimit(UNLIMITED).addFilter("appId", EQ, app.getUuid()).build())
              .getResponse();
      int updateCount = 0;
      for (Workflow workflow : workflows) {
        boolean workflowModified = false;
        if (workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow) {
          CanaryOrchestrationWorkflow coWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
          for (WorkflowPhase workflowPhase : coWorkflow.getWorkflowPhases()) {
            if (!workflowPhase.isDaemonSet() && isDaemonSet(app.getUuid(), workflowPhase.getServiceId())) {
              workflowModified = true;
              workflowPhase.setDaemonSet(true);
            }
          }
        }
        if (workflowModified) {
          try {
            log.info("... Updating workflow: {} - {}", workflow.getUuid(), workflow.getName());
            workflowService.updateWorkflow(workflow, false);
            Thread.sleep(100);
          } catch (Exception e) {
            log.error("Error updating workflow", e);
          }
          updateCount++;
        }
      }
      log.info("Application migrated: {} - {}. Updated {} out of {} workflows", app.getUuid(), app.getName(),
          updateCount, workflows.size());
    }
  }

  private boolean isDaemonSet(String appId, String serviceId) {
    KubernetesContainerTask containerTask =
        (KubernetesContainerTask) serviceResourceService.getContainerTaskByDeploymentType(
            appId, serviceId, DeploymentType.KUBERNETES.name());
    return containerTask != null && KubernetesContainerTaskUtils.checkDaemonSet(containerTask.getAdvancedConfig());
  }
}
