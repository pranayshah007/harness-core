/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.GraphNode;
import software.wings.beans.RollingOrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;
import software.wings.yaml.workflow.RollingWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RollingWorkflowHandlerImpl implements WorkflowHandler {
  @Inject RollingWorkflowYamlHandler rollingWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

  @Override
  public List<Yaml> getPhases(Workflow workflow) {
    RollingWorkflowYaml rollingWorkflowYaml = rollingWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return rollingWorkflowYaml.getPhases();
  }

  @Override
  public List<GraphNode> getSteps(Workflow workflow) {
    RollingOrchestrationWorkflow orchestrationWorkflow =
        (RollingOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return getSteps(orchestrationWorkflow.getWorkflowPhases(), orchestrationWorkflow.getPreDeploymentSteps(),
        orchestrationWorkflow.getPostDeploymentSteps());
  }

  private ParameterField<Map<String, Object>> getRuntimeInput() {
    return ParameterField.createExpressionField(true, "<+input>", null, false);
  }

  //  .failureStrategies(Collections.singletonList(
  //      FailureStrategyConfig.builder()
  //                        .onFailure(OnFailureConfig.builder()
  //                                       .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
  //      .action(StageRollbackFailureActionConfig.builder().build())
  //      .build())
  //      .build()))

  @Override
  public JsonNode getTemplateSpec(Workflow workflow) {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    List<ExecutionWrapperConfig> rollingSteps = new ArrayList<>();
    List<WorkflowPhase.Yaml> phases = getPhases(workflow);

    // Add all the steps
    if (EmptyPredicate.isNotEmpty(phases)) {
      steps.addAll(phases.stream().map(phase -> getSteps(stepMapperFactory, phase)).collect(Collectors.toList()));
    }

    // Build Stage
    DeploymentStageConfig deploymentStageConfig =
        DeploymentStageConfig.builder()
            .deploymentType(inferServiceDefinitionType(workflow))
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField("<+input>"))
                         .serviceInputs(getRuntimeInput())
                         .build())
            .environment(
                EnvironmentYamlV2.builder()
                    .deployToAll(ParameterField.createValueField(false))
                    .environmentRef(ParameterField.createValueField("<+input>"))
                    .environmentInputs(getRuntimeInput())
                    .serviceOverrideInputs(getRuntimeInput())
                    .infrastructureDefinition(ParameterField.createExpressionField(true, "<+input>", null, false))
                    .build())
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollingSteps).build())
            .build();

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("type", "Deployment")
                                           .put("spec", deploymentStageConfig)
                                           .put("failureStrategies", new ArrayList<>())
                                           .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }

  // We can infer the type based on the service, infra & sometimes based on the steps used.
  private ServiceDefinitionType inferServiceDefinitionType(Workflow workflow) {
    // TODO: Deepak Puthraya
    return ServiceDefinitionType.KUBERNETES;
  }
}
