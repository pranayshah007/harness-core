/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.ng.core.template.TemplateEntityType.PIPELINE_TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityType.STAGE_TEMPLATE;

import io.harness.beans.OrchestrationWorkflowType;
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
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;

public class CanaryWorkflowHandlerImpl extends WorkflowHandler {
  private static final Set<OrchestrationWorkflowType> ROLLING_WORKFLOW_TYPES =
      Sets.newHashSet(BASIC, BLUE_GREEN, ROLLING);

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
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (ROLLING_WORKFLOW_TYPES.contains(workflowType) || BUILD == workflowType) {
      return STAGE_TEMPLATE;
    }
    return PIPELINE_TEMPLATE;
  }

  @Override
  public boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    return areSimilar(stepMapperFactory, workflow1, workflow2);
  }

  PhaseStep.Yaml getPreDeploymentPhase(Workflow workflow) {
    CanaryWorkflowYaml canaryWorkflowYaml = canaryWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return PhaseStep.Yaml.builder()
        .stepSkipStrategies(canaryWorkflowYaml.getPreDeploymentStepSkipStrategy())
        .stepsInParallel(orchestrationWorkflow.getPreDeploymentSteps().isStepsInParallel())
        .steps(canaryWorkflowYaml.getPreDeploymentSteps())
        .build();
  }

  PhaseStep.Yaml getPostDeploymentPhase(Workflow workflow) {
    CanaryWorkflowYaml canaryWorkflowYaml = canaryWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return PhaseStep.Yaml.builder()
        .stepSkipStrategies(canaryWorkflowYaml.getPostDeploymentStepSkipStrategy())
        .stepsInParallel(orchestrationWorkflow.getPostDeploymentSteps().isStepsInParallel())
        .steps(canaryWorkflowYaml.getPostDeploymentSteps())
        .build();
  }

  @Override
  public JsonNode getTemplateSpec(Workflow workflow) {
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (ROLLING_WORKFLOW_TYPES.contains(workflowType)) {
      return getDeploymentStageTemplateSpec(workflow, stepMapperFactory);
    }
    if (workflowType == BUILD) {
      return getCustomStageTemplateSpec(workflow, stepMapperFactory);
    }
    return buildMultiStagePipelineTemplate(stepMapperFactory, workflow);
  }

  @Override
  List<Yaml> getRollbackPhases(Workflow workflow) {
    CanaryWorkflowYaml canaryWorkflowYaml = canaryWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return canaryWorkflowYaml.getRollbackPhases();
  }
}
