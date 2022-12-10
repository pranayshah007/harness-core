/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ngmigration.service.step.StepMapperFactory;

import software.wings.beans.BuildWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.BuildWorkflowYamlHandler;
import software.wings.yaml.workflow.BuildWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;

public class BuildWorkflowHandlerImpl extends WorkflowHandler {
  @Inject BuildWorkflowYamlHandler buildWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

  @Override
  public List<Yaml> getRollbackPhases(Workflow workflow) {
    BuildWorkflowYaml buildWorkflowYaml = buildWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return buildWorkflowYaml.getRollbackPhases();
  }

  @Override
  public List<Yaml> getPhases(Workflow workflow) {
    BuildWorkflowYaml buildWorkflowYaml = buildWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return buildWorkflowYaml.getPhases();
  }

  @Override
  public List<GraphNode> getSteps(Workflow workflow) {
    BuildWorkflow orchestrationWorkflow = (BuildWorkflow) workflow.getOrchestrationWorkflow();
    return getSteps(orchestrationWorkflow.getWorkflowPhases(), orchestrationWorkflow.getPreDeploymentSteps(),
        orchestrationWorkflow.getPostDeploymentSteps());
  }

  @Override
  public boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    return areSimilar(stepMapperFactory, workflow1, workflow2);
  }

  @Override
  public JsonNode getTemplateSpec(Workflow workflow) {
    return getCustomStageTemplateSpec(workflow, stepMapperFactory);
  }
}
