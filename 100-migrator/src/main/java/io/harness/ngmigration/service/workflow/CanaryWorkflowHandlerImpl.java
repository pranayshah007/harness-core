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
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.CanaryWorkflowYamlHandler;
import software.wings.yaml.workflow.CanaryWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;

public class CanaryWorkflowHandlerImpl extends WorkflowHandler {
  @Inject CanaryWorkflowYamlHandler canaryWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

  @Override
  public List<Yaml> getPhases(Workflow workflow) {
    CanaryWorkflowYaml canaryWorkflowYaml = canaryWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return canaryWorkflowYaml.getPhases();
  }

  @Override
  public List<GraphNode> getSteps(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return getSteps(orchestrationWorkflow.getWorkflowPhases(), orchestrationWorkflow.getPreDeploymentSteps(),
        orchestrationWorkflow.getPostDeploymentSteps());
  }

  @Override
  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.PIPELINE_TEMPLATE;
  }

  @Override
  public JsonNode getTemplateSpec(Workflow workflow) {
    CanaryWorkflowYaml canaryWorkflowYaml = canaryWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    PhaseStep.Yaml prePhase = PhaseStep.Yaml.builder()
                                  .stepSkipStrategies(canaryWorkflowYaml.getPreDeploymentStepSkipStrategy())
                                  .stepsInParallel(orchestrationWorkflow.getPreDeploymentSteps().isStepsInParallel())
                                  .steps(canaryWorkflowYaml.getPreDeploymentSteps())
                                  .build();
    PhaseStep.Yaml postPhase = PhaseStep.Yaml.builder()
                                   .stepSkipStrategies(canaryWorkflowYaml.getPostDeploymentStepSkipStrategy())
                                   .stepsInParallel(orchestrationWorkflow.getPostDeploymentSteps().isStepsInParallel())
                                   .steps(canaryWorkflowYaml.getPostDeploymentSteps())
                                   .build();
    return buildMultiStagePipelineTemplate(
        stepMapperFactory, prePhase, canaryWorkflowYaml.getPhases(), postPhase, canaryWorkflowYaml.getRollbackPhases());
  }
}
