/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.service.step.StepMapperFactory;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.MultiServiceOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.MultiServiceWorkflowYamlHandler;
import software.wings.yaml.workflow.MultiServiceWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;

public class MultiServiceWorkflowHandlerImpl extends WorkflowHandler {
  @Inject MultiServiceWorkflowYamlHandler multiServiceWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

  @Override
  public List<Yaml> getPhases(Workflow workflow) {
    MultiServiceWorkflowYaml multiServiceWorkflowYaml =
        multiServiceWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return multiServiceWorkflowYaml.getPhases();
  }

  @Override
  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.PIPELINE_TEMPLATE;
  }

  @Override
  public boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    return areSimilar(stepMapperFactory, workflow1, workflow2);
  }

  @Override
  public List<GraphNode> getSteps(Workflow workflow) {
    MultiServiceOrchestrationWorkflow orchestrationWorkflow =
        (MultiServiceOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return getSteps(orchestrationWorkflow.getWorkflowPhases(), orchestrationWorkflow.getPreDeploymentSteps(),
        orchestrationWorkflow.getPostDeploymentSteps());
  }

  PhaseStep.Yaml getPreDeploymentPhase(Workflow workflow) {
    MultiServiceWorkflowYaml workflowYaml = multiServiceWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return PhaseStep.Yaml.builder()
        .stepSkipStrategies(workflowYaml.getPreDeploymentStepSkipStrategy())
        .stepsInParallel(orchestrationWorkflow.getPreDeploymentSteps().isStepsInParallel())
        .steps(workflowYaml.getPreDeploymentSteps())
        .build();
  }

  PhaseStep.Yaml getPostDeploymentPhase(Workflow workflow) {
    MultiServiceWorkflowYaml workflowYaml = multiServiceWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return PhaseStep.Yaml.builder()
        .stepSkipStrategies(workflowYaml.getPreDeploymentStepSkipStrategy())
        .stepsInParallel(orchestrationWorkflow.getPostDeploymentSteps().isStepsInParallel())
        .steps(workflowYaml.getPostDeploymentSteps())
        .build();
  }

  @Override
  public JsonNode getTemplateSpec(Workflow workflow) {
    return buildMultiStagePipelineTemplate(stepMapperFactory, workflow);
  }

  @Override
  List<Yaml> getRollbackPhases(Workflow workflow) {
    MultiServiceWorkflowYaml workflowYaml = multiServiceWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return workflowYaml.getRollbackPhases();
  }
}
