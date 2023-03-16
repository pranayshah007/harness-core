/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.plancreator.stages.StageElementWrapperConfig;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.service.impl.yaml.handler.workflow.MultiServiceWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

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
  public List<StageElementWrapperConfig> asStages(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow, CaseFormat caseFormat) {
    return getStagesForMultiServiceWorkflow(
        WorkflowMigrationContext.newInstance(entities, migratedEntities, workflow, caseFormat));
  }

  @Override
  public JsonNode getTemplateSpec(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      Workflow workflow, CaseFormat caseFormat) {
    return buildMultiStagePipelineTemplate(
        WorkflowMigrationContext.newInstance(entities, migratedEntities, workflow, caseFormat));
  }
}
