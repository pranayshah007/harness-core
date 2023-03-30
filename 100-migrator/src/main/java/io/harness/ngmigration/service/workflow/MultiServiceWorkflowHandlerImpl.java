/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.stages.StageElementWrapperConfig;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.service.impl.yaml.handler.workflow.MultiServiceWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;

public class MultiServiceWorkflowHandlerImpl extends WorkflowHandler {
  @Inject MultiServiceWorkflowYamlHandler multiServiceWorkflowYamlHandler;

  @Override
  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.PIPELINE_TEMPLATE;
  }

  PhaseStep getPreDeploymentPhase(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return orchestrationWorkflow.getPreDeploymentSteps();
  }

  PhaseStep getPostDeploymentPhase(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return orchestrationWorkflow.getPostDeploymentSteps();
  }

  @Override
  public List<StageElementWrapperConfig> asStages(MigrationContext migrationContext, Workflow workflow) {
    return getStagesForMultiServiceWorkflow(
        migrationContext, WorkflowMigrationContext.newInstance(migrationContext, workflow));
  }

  @Override
  public JsonNode getTemplateSpec(MigrationContext migrationContext, Workflow workflow) {
    return buildMultiStagePipelineTemplate(
        migrationContext, WorkflowMigrationContext.newInstance(migrationContext, workflow));
  }
}
