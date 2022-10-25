/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow.creation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.MapperUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.workflow.creation.helpers.K8AbstractWorkflowHelper;
import software.wings.service.impl.workflow.creation.helpers.K8RollingWorkflowPhaseHelper;
import software.wings.service.impl.workflow.creation.helpers.RancherK8RollingWorkflowPhaseHelper;
import software.wings.service.intfc.InfrastructureDefinitionService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class K8V2RollingWorkflowCreator extends WorkflowCreator {
  private static final String PHASE_NAME = "Rolling";
  @Inject private K8RollingWorkflowPhaseHelper k8RollingWorkflowPhaseHelper;
  @Inject private RancherK8RollingWorkflowPhaseHelper rancherK8RollingWorkflowPhaseHelper;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;

  @Override
  public Workflow createWorkflow(Workflow clientWorkflow) {
    Workflow workflow = aWorkflow().build();
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.getInfraDefById(
        clientWorkflow.getAccountId(), clientWorkflow.getInfraDefinitionId());
    MapperUtils.mapObject(clientWorkflow, workflow);

    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    notNullCheck("orchestrationWorkflow", canaryOrchestrationWorkflow);
    K8AbstractWorkflowHelper k8AbstractWorkflowHelper = k8RollingWorkflowPhaseHelper;
    if (infrastructureDefinition != null
        && infrastructureDefinition.getInfrastructure().getInfrastructureType().equals("RANCHER_KUBERNETES")) {
      k8AbstractWorkflowHelper = rancherK8RollingWorkflowPhaseHelper;
    }
    if (k8AbstractWorkflowHelper.isCreationRequired(canaryOrchestrationWorkflow)) {
      addLinkedPreOrPostDeploymentSteps(canaryOrchestrationWorkflow);
      addWorkflowPhases(workflow);
    }
    return workflow;
  }

  private void addWorkflowPhases(Workflow workflow) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionService.getInfraDefById(workflow.getAccountId(), workflow.getInfraDefinitionId());
    K8AbstractWorkflowHelper k8AbstractWorkflowHelper =
        infrastructureDefinition.getInfrastructure().getInfrastructureType().equals("RANCHER_KUBERNETES")
        ? rancherK8RollingWorkflowPhaseHelper
        : k8RollingWorkflowPhaseHelper;

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowRollingPhase = k8AbstractWorkflowHelper.getWorkflowPhase(workflow, PHASE_NAME);
    orchestrationWorkflow.getWorkflowPhases().add(workflowRollingPhase);
    orchestrationWorkflow.getRollbackWorkflowPhaseIdMap().put(workflowRollingPhase.getUuid(),
        k8AbstractWorkflowHelper.getRollbackPhaseForWorkflowPhase(workflowRollingPhase));

    for (WorkflowPhase phase : orchestrationWorkflow.getWorkflowPhases()) {
      attachWorkflowPhase(workflow, phase);
    }
  }

  @Override
  public void attachWorkflowPhase(Workflow workflow, WorkflowPhase workflowPhase) {
    // No action needed in attach Phase
  }
}
