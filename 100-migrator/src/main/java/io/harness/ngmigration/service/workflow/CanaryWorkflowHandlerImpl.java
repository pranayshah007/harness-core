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
import static io.harness.beans.OrchestrationWorkflowType.MULTI_SERVICE;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.ng.core.template.TemplateEntityType.PIPELINE_TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityType.STAGE_TEMPLATE;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import java.util.Set;

public class CanaryWorkflowHandlerImpl extends WorkflowHandler {
  private static final Set<OrchestrationWorkflowType> ROLLING_WORKFLOW_TYPES =
      Sets.newHashSet(BASIC, BLUE_GREEN, ROLLING);

  @Override
  public TemplateEntityType getTemplateType(Workflow workflow) {
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (workflowType != OrchestrationWorkflowType.MULTI_SERVICE) {
      return STAGE_TEMPLATE;
    }
    return shouldCreateStageTemplate(workflow) ? STAGE_TEMPLATE : PIPELINE_TEMPLATE;
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
  public JsonNode getTemplateSpec(MigrationContext migrationContext, Workflow workflow) {
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    WorkflowMigrationContext context = WorkflowMigrationContext.newInstance(migrationContext, workflow);
    if (ROLLING_WORKFLOW_TYPES.contains(workflowType)) {
      return getDeploymentStageTemplateSpec(migrationContext, context);
    }
    if (workflowType == BUILD) {
      return getCustomStageTemplateSpec(migrationContext, context);
    }
    if (workflowType == MULTI_SERVICE) {
      return buildMultiStagePipelineTemplate(migrationContext, context);
    }
    return buildCanaryStageTemplate(migrationContext, context);
  }
}
