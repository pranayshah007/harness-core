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
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.step.StepMapperFactory;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.service.impl.yaml.handler.workflow.CanaryWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CanaryWorkflowHandlerImpl extends WorkflowHandler {
  private static final Set<OrchestrationWorkflowType> ROLLING_WORKFLOW_TYPES =
      Sets.newHashSet(BASIC, BLUE_GREEN, ROLLING);

  @Inject CanaryWorkflowYamlHandler canaryWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

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
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (ROLLING_WORKFLOW_TYPES.contains(workflowType)) {
      return getDeploymentStageTemplateSpec(migratedEntities, workflow, stepMapperFactory);
    }
    if (workflowType == BUILD) {
      return getCustomStageTemplateSpec(migratedEntities, workflow, stepMapperFactory);
    }
    return buildMultiStagePipelineTemplate(migratedEntities, stepMapperFactory, workflow);
  }
}
