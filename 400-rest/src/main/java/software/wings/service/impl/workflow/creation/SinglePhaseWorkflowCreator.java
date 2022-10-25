/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow.creation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.MapperUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.impl.workflow.creation.helpers.WorkflowPhaseHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class SinglePhaseWorkflowCreator extends WorkflowCreator {
  @Inject private WorkflowServiceHelper workflowServiceHelper;
  @Inject private WorkflowServiceTemplateHelper workflowServiceTemplateHelper;
  @Inject private WorkflowPhaseHelper workflowPhaseHelper;

  @Override
  public Workflow createWorkflow(Workflow clientWorkflow) {
    Workflow workflow = aWorkflow().build();
    MapperUtils.mapObject(clientWorkflow, workflow);
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    notNullCheck("orchestrationWorkflow", canaryOrchestrationWorkflow);
    addLinkedPreOrPostDeploymentSteps(canaryOrchestrationWorkflow);
    addWorkflowPhases(workflow);
    return workflow;
  }

  private void addWorkflowPhases(Workflow workflow) {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase;
    if (isEmpty(canaryOrchestrationWorkflow.getWorkflowPhases())) {
      workflowPhase = aWorkflowPhase()
                          .infraMappingId(workflow.getInfraMappingId())
                          .infraDefinitionId(workflow.getInfraDefinitionId())
                          .serviceId(workflow.getServiceId())
                          .daemonSet(workflowPhaseHelper.isDaemonSet(workflow.getAppId(), workflow.getServiceId()))
                          .statefulSet(workflowPhaseHelper.isStatefulSet(workflow.getAppId(), workflow.getServiceId()))
                          .build();
      attachWorkflowPhase(workflow, workflowPhase);
    }
  }

  @Override
  public void attachWorkflowPhase(Workflow workflow, WorkflowPhase workflowPhase) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();

    if (orchestrationWorkflow.needCloudProvider()) {
      workflowPhaseHelper.setCloudProvider(workflow.getAppId(), workflowPhase);
    }

    // No need to generate phase steps if it's already created
    if (isNotEmpty(workflowPhase.getPhaseSteps()) && orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases().add(workflowPhase);
      return;
    }

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    workflowServiceHelper.generateNewWorkflowPhaseSteps(workflow.getAppId(), workflowPhase, false,
        orchestrationWorkflow.getOrchestrationWorkflowType(), workflow.getCreationFlags());

    workflowServiceTemplateHelper.addLinkedWorkflowPhaseTemplate(workflowPhase);
    canaryOrchestrationWorkflow.getWorkflowPhases().add(workflowPhase);

    WorkflowPhase rollbackWorkflowPhase = workflowServiceHelper.generateRollbackWorkflowPhase(workflow.getAppId(),
        workflowPhase, true, orchestrationWorkflow.getOrchestrationWorkflowType(), workflow.getCreationFlags());
    workflowServiceTemplateHelper.addLinkedWorkflowPhaseTemplate(rollbackWorkflowPhase);
    canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().put(workflowPhase.getUuid(), rollbackWorkflowPhase);
  }
}
