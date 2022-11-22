/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.steps.customstage.CustomStageConfig;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.BuildWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.BuildWorkflowYamlHandler;
import software.wings.yaml.workflow.BuildWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  public JsonNode getTemplateSpec(Workflow workflow) {
    List<WorkflowPhase.Yaml> phases = getPhases(workflow);
    List<WorkflowPhase.Yaml> rollbackPhases = getRollbackPhases(workflow);

    // Add all the steps
    List<ExecutionWrapperConfig> steps = getSteps(stepMapperFactory, phases);

    // Add all the steps
    List<ExecutionWrapperConfig> rollingSteps = getSteps(stepMapperFactory, rollbackPhases);

    // Build Stage
    CustomStageConfig customStageConfig =
        CustomStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollingSteps).build())
            .build();

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("type", "Custom")
                                           .put("spec", customStageConfig)
                                           .put("failureStrategies", new ArrayList<>())
                                           .put("variables", getVariables(workflow))
                                           .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }
}
