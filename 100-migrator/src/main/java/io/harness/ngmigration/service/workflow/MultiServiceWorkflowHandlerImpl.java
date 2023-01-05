/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.step.StepMapperFactory;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.MultiServiceOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.service.impl.yaml.handler.workflow.MultiServiceWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

public class MultiServiceWorkflowHandlerImpl extends WorkflowHandler {
  @Inject MultiServiceWorkflowYamlHandler multiServiceWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

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

  PhaseStep getPreDeploymentPhase(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return orchestrationWorkflow.getPreDeploymentSteps();
  }

  PhaseStep getPostDeploymentPhase(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    return orchestrationWorkflow.getPostDeploymentSteps();
  }

  @Override
  public JsonNode getTemplateSpec(Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow) {
    return buildMultiStagePipelineTemplate(migratedEntities, stepMapperFactory, workflow);
  }
}
