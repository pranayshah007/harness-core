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

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.MapperUtils;

import software.wings.beans.BuildWorkflow;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;

import com.google.inject.Inject;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class BuildWorkflowCreator extends WorkflowCreator {
  @Inject private WorkflowServiceTemplateHelper workflowServiceTemplateHelper;
  @Inject private WorkflowServiceHelper workflowServiceHelper;

  @Override
  public Workflow createWorkflow(Workflow clientWorkflow) {
    Workflow workflow = aWorkflow().build();
    MapperUtils.mapObject(clientWorkflow, workflow);
    BuildWorkflow orchestrationWorkflow = (BuildWorkflow) workflow.getOrchestrationWorkflow();
    addLinkedPreOrPostDeploymentSteps(orchestrationWorkflow);
    addWorkflowPhases(workflow);
    return workflow;
  }

  private void addWorkflowPhases(Workflow workflow) {
    BuildWorkflow buildWorkflow = (BuildWorkflow) workflow.getOrchestrationWorkflow();
    if (isEmpty(buildWorkflow.getWorkflowPhases())) {
      WorkflowPhase workflowPhase = aWorkflowPhase().build();
      attachWorkflowPhase(workflow, workflowPhase);
    }
  }

  @Override
  public void attachWorkflowPhase(Workflow workflow, WorkflowPhase workflowPhase) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();

    // No need to generate phase steps if it's already created
    if (isNotEmpty(workflowPhase.getPhaseSteps()) && orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases().add(workflowPhase);
      return;
    }

    BuildWorkflow buildWorkflow = (BuildWorkflow) workflow.getOrchestrationWorkflow();
    workflowServiceTemplateHelper.addLinkedWorkflowPhaseTemplate(workflowPhase);
    workflowServiceHelper.generateNewWorkflowPhaseStepsForArtifactCollection(workflowPhase);
    buildWorkflow.getWorkflowPhases().add(workflowPhase);
  }
}
