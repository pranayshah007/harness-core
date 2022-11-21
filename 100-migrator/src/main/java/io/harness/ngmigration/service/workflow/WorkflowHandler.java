/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.when.beans.WhenConditionStatus.SUCCESS;

import io.harness.beans.InputSetValidatorType;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.steps.customstage.CustomStageConfig;
import io.harness.steps.customstage.CustomStageNode;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.beans.workflow.StepSkipStrategy.Scope;
import software.wings.yaml.workflow.StepYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

public abstract class WorkflowHandler {
  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.STAGE_TEMPLATE;
  }

  public List<NGVariable> getVariables(Workflow workflow) {
    List<Variable> variables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (EmptyPredicate.isEmpty(variables)) {
      return Collections.emptyList();
    }
    return variables.stream()
        .map(variable
            -> StringNGVariable.builder()
                   .name(variable.getName())
                   .type(NGVariableType.STRING)
                   .required(variable.isMandatory())
                   .defaultValue(variable.getValue())
                   .value(getVariable(variable))
                   .build())
        .collect(Collectors.toList());
  }

  static ParameterField<String> getVariable(Variable variable) {
    if (variable.isFixed()) {
      return ParameterField.createValueField(variable.getValue());
    }

    InputSetValidator validator = null;
    if (StringUtils.isNotBlank(variable.getAllowedValues())) {
      validator = new InputSetValidator(InputSetValidatorType.ALLOWED_VALUES, variable.getAllowedValues());
    }

    return ParameterField.createFieldWithDefaultValue(true, true, "<+input>", variable.getValue(), validator, true);
  }

  public JsonNode getTemplateSpec(Workflow workflow) {
    return null;
  }

  List<Yaml> getRollbackPhases(Workflow workflow) {
    return null;
  }

  List<Yaml> getPhases(Workflow workflow) {
    return null;
  }

  public List<GraphNode> getSteps(Workflow workflow) {
    return null;
  }

  List<GraphNode> getSteps(
      List<WorkflowPhase> phases, PhaseStep preDeploymentPhaseStep, PhaseStep postDeploymentPhaseStep) {
    List<GraphNode> stepYamls = new ArrayList<>();
    if (postDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(postDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(postDeploymentPhaseStep.getSteps());
    }
    if (preDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(preDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(preDeploymentPhaseStep.getSteps());
    }
    if (EmptyPredicate.isNotEmpty(phases)) {
      stepYamls.addAll(getStepsFromPhases(phases));
    }
    return stepYamls;
  }

  List<GraphNode> getStepsFromPhases(List<WorkflowPhase> phases) {
    return phases.stream()
        .filter(phase -> isNotEmpty(phase.getPhaseSteps()))
        .flatMap(phase -> phase.getPhaseSteps().stream())
        .filter(phaseStep -> isNotEmpty(phaseStep.getSteps()))
        .flatMap(phaseStep -> phaseStep.getSteps().stream())
        .collect(Collectors.toList());
  }

  List<ExecutionWrapperConfig> getStepGroups(StepMapperFactory stepMapperFactory, WorkflowPhase.Yaml phase) {
    List<PhaseStep.Yaml> phaseSteps = phase != null ? phase.getPhaseSteps() : Collections.emptyList();
    List<ExecutionWrapperConfig> stepGroups = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(phaseSteps)) {
      stepGroups = phaseSteps.stream()
                       .filter(phaseStep -> EmptyPredicate.isNotEmpty(phaseStep.getSteps()))
                       .map(phaseStep -> getStepGroup(stepMapperFactory, phaseStep))
                       .filter(Objects::nonNull)
                       .collect(Collectors.toList());
    }
    return stepGroups;
  }

  ExecutionWrapperConfig getStepGroup(StepMapperFactory stepMapperFactory, PhaseStep.Yaml phaseStep) {
    List<StepYaml> stepYamls = phaseStep.getSteps();

    List<ExecutionWrapperConfig> steps = getStepWrappers(stepMapperFactory, phaseStep, stepYamls);
    if (EmptyPredicate.isEmpty(steps)) {
      return null;
    }
    if (phaseStep.isStepsInParallel()) {
      steps =
          Collections.singletonList(ExecutionWrapperConfig.builder().parallel(JsonPipelineUtils.asTree(steps)).build());
    }
    StepWhenCondition when = null;
    List<StepSkipStrategy.Yaml> cgSkipConditions = phaseStep.getStepSkipStrategies();
    if (EmptyPredicate.isNotEmpty(cgSkipConditions)
        && cgSkipConditions.stream().anyMatch(skip -> Scope.ALL_STEPS.name().equals(skip.getScope()))) {
      StepSkipStrategy.Yaml strategy =
          cgSkipConditions.stream().filter(skip -> Scope.ALL_STEPS.name().equals(skip.getScope())).findFirst().get();
      when = StepWhenCondition.builder()
                 .condition(ParameterField.createValueField(strategy.getAssertionExpression()))
                 .stageStatus(SUCCESS)
                 .build();
    }
    return ExecutionWrapperConfig.builder()
        .stepGroup(JsonPipelineUtils.asTree(StepGroupElementConfig.builder()
                                                .identifier(MigratorUtility.generateIdentifier(phaseStep.getName()))
                                                .name(phaseStep.getName())
                                                .steps(steps)
                                                .skipCondition(null)
                                                .when(when)
                                                .failureStrategies(null)
                                                .build()))
        .build();
  }

  List<ExecutionWrapperConfig> getStepWrappers(
      StepMapperFactory stepMapperFactory, PhaseStep.Yaml phaseStep, List<StepYaml> stepYamls) {
    if (EmptyPredicate.isEmpty(stepYamls)) {
      return Collections.emptyList();
    }
    List<StepSkipStrategy.Yaml> cgSkipConditions = phaseStep.getStepSkipStrategies();
    Map<String, String> skipStrategies = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(cgSkipConditions)
        && cgSkipConditions.stream().noneMatch(skip -> Scope.ALL_STEPS.name().equals(skip.getScope()))) {
      cgSkipConditions.stream()
          .filter(skip
              -> EmptyPredicate.isNotEmpty(skip.getSteps()) && StringUtils.isNotBlank(skip.getAssertionExpression()))
          .forEach(skip -> skip.getSteps().forEach(step -> skipStrategies.put(step, skip.getAssertionExpression())));
    }
    return stepYamls.stream()
        .map(stepYaml
            -> ExecutionWrapperConfig.builder()
                   .step(JsonPipelineUtils.asTree(getStepElementConfig(
                       stepMapperFactory, stepYaml, skipStrategies.getOrDefault(stepYaml.getName(), null))))
                   .build())
        .collect(Collectors.toList());
  }

  AbstractStepNode getStepElementConfig(StepMapperFactory stepMapperFactory, StepYaml step, String skipCondition) {
    StepMapper stepMapper = stepMapperFactory.getStepMapper(step.getType());
    AbstractStepNode stepNode = stepMapper.getSpec(step);
    if (StringUtils.isNotBlank(skipCondition)) {
      stepNode.setWhen(StepWhenCondition.builder()
                           .condition(ParameterField.createValueField(skipCondition))
                           .stageStatus(SUCCESS)
                           .build());
    }
    return stepNode;
  }

  // We can infer the type based on the service, infra & sometimes based on the steps used.
  ServiceDefinitionType inferServiceDefinitionType(Workflow workflow) {
    throw new NotImplementedException();
  }

  ParameterField<Map<String, Object>> getRuntimeInput() {
    return ParameterField.createExpressionField(true, "<+input>", null, false);
  }

  DeploymentStageConfig getDeploymentStageConfig(StepMapperFactory stepMapperFactory,
      ServiceDefinitionType serviceDefinitionType, WorkflowPhase.Yaml phase, WorkflowPhase.Yaml rollbackPhase) {
    List<ExecutionWrapperConfig> stepGroups = getStepGroups(stepMapperFactory, phase);
    if (EmptyPredicate.isEmpty(stepGroups)) {
      return null;
    }
    List<ExecutionWrapperConfig> rollbackSteps = getStepGroups(stepMapperFactory, rollbackPhase);
    return getDeploymentStageConfig(serviceDefinitionType, stepGroups, rollbackSteps);
  }

  DeploymentStageConfig getDeploymentStageConfig(ServiceDefinitionType serviceDefinitionType,
      List<ExecutionWrapperConfig> steps, List<ExecutionWrapperConfig> rollbackSteps) {
    return DeploymentStageConfig.builder()
        .deploymentType(serviceDefinitionType)
        .service(ServiceYamlV2.builder()
                     .serviceRef(ParameterField.createValueField("<+input>"))
                     .serviceInputs(getRuntimeInput())
                     .build())
        .environment(EnvironmentYamlV2.builder()
                         .deployToAll(ParameterField.createValueField(false))
                         .environmentRef(ParameterField.createValueField("<+input>"))
                         .environmentInputs(getRuntimeInput())
                         .serviceOverrideInputs(getRuntimeInput())
                         .infrastructureDefinition(ParameterField.createExpressionField(true, "<+input>", null, false))
                         .build())
        .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
        .build();
  }

  DeploymentStageConfig getDeploymentStageConfig(
      Workflow workflow, List<ExecutionWrapperConfig> steps, List<ExecutionWrapperConfig> rollbackSteps) {
    return getDeploymentStageConfig(inferServiceDefinitionType(workflow), steps, rollbackSteps);
  }

  JsonNode getDeploymentStageTemplateSpec(Workflow workflow, StepMapperFactory stepMapperFactory) {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    List<WorkflowPhase.Yaml> phases = getPhases(workflow);
    List<WorkflowPhase.Yaml> rollbackPhases = getRollbackPhases(workflow);

    // Add all the steps
    if (EmptyPredicate.isNotEmpty(phases)) {
      steps.addAll(phases.stream()
                       .flatMap(phase -> getStepGroups(stepMapperFactory, phase).stream())
                       .filter(Objects::nonNull)
                       .collect(Collectors.toList()));
    }

    // Add all the rollback steps
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      rollbackSteps.addAll(rollbackPhases.stream()
                               .flatMap(phase -> getStepGroups(stepMapperFactory, phase).stream())
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList()));
    }

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("type", "Deployment")
                                           .put("spec", getDeploymentStageConfig(workflow, steps, rollbackSteps))
                                           .put("failureStrategies", new ArrayList<>())
                                           .put("variables", getVariables(workflow))
                                           .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }

  List<ExecutionWrapperConfig> getSteps(StepMapperFactory stepMapperFactory, List<WorkflowPhase.Yaml> phases) {
    if (EmptyPredicate.isEmpty(phases)) {
      return Collections.emptyList();
    }
    return phases.stream()
        .flatMap(phase -> getStepGroups(stepMapperFactory, phase).stream())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  StageElementWrapperConfig buildCustomStage(StepMapperFactory stepMapperFactory, PhaseStep.Yaml phaseStep) {
    ExecutionWrapperConfig wrapper = getStepGroup(stepMapperFactory, phaseStep);
    CustomStageConfig customStageConfig =
        CustomStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(Collections.singletonList(wrapper)).build())
            .build();
    CustomStageNode customStageNode = new CustomStageNode();
    customStageNode.setName(phaseStep.getName());
    customStageNode.setIdentifier(MigratorUtility.generateIdentifier(phaseStep.getName()));
    customStageNode.setCustomStageConfig(customStageConfig);
    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(customStageNode)).build();
  }

  // This is for multi service only
  StageElementWrapperConfig buildDeploymentStage(StepMapperFactory stepMapperFactory,
      ServiceDefinitionType serviceDefinitionType, WorkflowPhase.Yaml phase, WorkflowPhase.Yaml rollbackPhase) {
    DeploymentStageConfig stageConfig =
        getDeploymentStageConfig(stepMapperFactory, serviceDefinitionType, phase, rollbackPhase);
    if (stageConfig == null) {
      return null;
    }
    DeploymentStageNode stageNode = new DeploymentStageNode();
    stageNode.setName(phase.getName());
    stageNode.setIdentifier(MigratorUtility.generateIdentifier(phase.getName()));
    stageNode.setDeploymentStageConfig(stageConfig);
    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(stageNode)).build();
  }

  JsonNode buildMultiStagePipelineTemplate(StepMapperFactory stepMapperFactory, PhaseStep.Yaml prePhase,
      List<WorkflowPhase.Yaml> phases, PhaseStep.Yaml postPhase, List<WorkflowPhase.Yaml> rollbackPhases) {
    List<StageElementWrapperConfig> stages = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(prePhase.getSteps())) {
      prePhase.setName("Pre Deployment");
      stages.add(buildCustomStage(stepMapperFactory, prePhase));
    }

    final Map<String, WorkflowPhase.Yaml> rollbackPhaseMap = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      rollbackPhaseMap.putAll(rollbackPhases.stream().collect(
          Collectors.toMap(WorkflowPhase.Yaml::getPhaseNameForRollback, phase -> phase)));
    }

    if (EmptyPredicate.isNotEmpty(phases)) {
      stages.addAll(phases.stream()
                        .map(phase
                            -> buildDeploymentStage(stepMapperFactory, ServiceDefinitionType.KUBERNETES, phase,
                                rollbackPhaseMap.getOrDefault(phase.getName(), null)))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(postPhase.getSteps())) {
      postPhase.setName("Post Deployment");
      stages.add(buildCustomStage(stepMapperFactory, postPhase));
    }

    PipelineInfoConfig pipelineInfoConfig = PipelineInfoConfig.builder().stages(stages).build();
    return JsonPipelineUtils.asTree(pipelineInfoConfig);
  }
}
