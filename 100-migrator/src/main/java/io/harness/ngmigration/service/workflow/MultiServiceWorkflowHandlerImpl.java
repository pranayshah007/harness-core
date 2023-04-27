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
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.Collectors;

public class MultiServiceWorkflowHandlerImpl extends WorkflowHandler {
  @Override
  public TemplateEntityType getTemplateType(Workflow workflow) {
    return shouldCreateStageTemplate(workflow) ? TemplateEntityType.STAGE_TEMPLATE
                                               : TemplateEntityType.PIPELINE_TEMPLATE;
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
    WorkflowMigrationContext wfContext = WorkflowMigrationContext.newInstance(migrationContext, workflow);
    wfContext.setWorkflowVarsAsPipeline(true);
    List<AbstractStageNode> stages = getStagesForMultiServiceWorkflow(migrationContext, wfContext);
    return stages.stream()
        .map(stage -> StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(stage)).build())
        .collect(Collectors.toList());
  }

  @Override
  public JsonNode getTemplateSpec(MigrationContext migrationContext, Workflow workflow) {
    return buildMultiStagePipelineTemplate(
        migrationContext, WorkflowMigrationContext.newInstance(migrationContext, workflow));
  }
}
