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
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
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
import io.harness.steps.wait.WaitStepInfo;
import io.harness.steps.wait.WaitStepNode;
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
  public static final String INPUT_EXPRESSION = "<+input>";

  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.STAGE_TEMPLATE;
  }

  public abstract boolean areSimilar(Workflow workflow1, Workflow workflow2);

  boolean areSimilar(StepMapperFactory stepMapperFactory, Workflow workflow1, Workflow workflow2) {
    List<WorkflowPhase.Yaml> phases1 = getPhases(workflow1);
    List<WorkflowPhase.Yaml> rollbackPhases1 = getRollbackPhases(workflow1);
    PhaseStep.Yaml pre1 = getPreDeploymentPhase(workflow1);
    PhaseStep.Yaml post1 = getPostDeploymentPhase(workflow1);

    List<WorkflowPhase.Yaml> phases2 = getPhases(workflow2);
    List<WorkflowPhase.Yaml> rollbackPhases2 = getRollbackPhases(workflow2);
    PhaseStep.Yaml pre2 = getPreDeploymentPhase(workflow2);
    PhaseStep.Yaml post2 = getPostDeploymentPhase(workflow2);

    if (!areSimilarPhaseStep(stepMapperFactory, pre1, pre2)) {
      return false;
    }

    if (!areSimilarPhaseStep(stepMapperFactory, post1, post2)) {
      return false;
    }

    if (phases1.size() != phases2.size()) {
      return false;
    }

    if (rollbackPhases1.size() != rollbackPhases2.size()) {
      return false;
    }

    for (int i = 0; i < phases1.size(); ++i) {
      if (!areSimilarPhase(stepMapperFactory, phases1.get(i), phases2.get(i))) {
        return false;
      }
    }

    for (int i = 0; i < rollbackPhases1.size(); ++i) {
      if (!areSimilarPhase(stepMapperFactory, rollbackPhases2.get(i), rollbackPhases1.get(i))) {
        return false;
      }
    }

    return true;
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

    if (StringUtils.isBlank(variable.getValue())) {
      return ParameterField.createExpressionField(true, INPUT_EXPRESSION, validator, true);
    }

    return ParameterField.createFieldWithDefaultValue(
        true, true, INPUT_EXPRESSION, variable.getValue(), validator, true);
  }

  public abstract JsonNode getTemplateSpec(Workflow workflow);

  abstract List<Yaml> getRollbackPhases(Workflow workflow);

  abstract List<Yaml> getPhases(Workflow workflow);

  PhaseStep.Yaml getPreDeploymentPhase(Workflow workflow) {
    return null;
  }

  PhaseStep.Yaml getPostDeploymentPhase(Workflow workflow) {
    return null;
  }

  public abstract List<GraphNode> getSteps(Workflow workflow);

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

  boolean areSimilarPhase(StepMapperFactory stepMapperFactory, WorkflowPhase.Yaml phase1, WorkflowPhase.Yaml phase2) {
    List<PhaseStep.Yaml> phase1Steps = phase1 != null ? phase1.getPhaseSteps() : Collections.emptyList();
    List<PhaseStep.Yaml> phase2Steps = phase2 != null ? phase2.getPhaseSteps() : Collections.emptyList();

    if (phase1Steps.size() != phase2Steps.size()) {
      return false;
    }

    for (int i = 0; i < phase1Steps.size(); ++i) {
      if (!areSimilarPhaseStep(stepMapperFactory, phase1Steps.get(i), phase2Steps.get(i))) {
        return false;
      }
    }
    return true;
  }

  boolean areSimilarPhaseStep(
      StepMapperFactory stepMapperFactory, PhaseStep.Yaml phaseStep1, PhaseStep.Yaml phaseStep2) {
    if ((phaseStep1 == null && phaseStep2 != null) || (phaseStep1 != null && phaseStep2 == null)) {
      return false;
    }

    if (phaseStep1 == null) {
      return true;
    }

    List<StepYaml> stepYamls1 =
        EmptyPredicate.isNotEmpty(phaseStep1.getSteps()) ? phaseStep1.getSteps() : Collections.emptyList();
    List<StepYaml> stepYamls2 =
        EmptyPredicate.isNotEmpty(phaseStep2.getSteps()) ? phaseStep2.getSteps() : Collections.emptyList();

    if (stepYamls2.size() != stepYamls1.size()) {
      return false;
    }

    for (int i = 0; i < stepYamls1.size(); ++i) {
      StepYaml stepYaml1 = stepYamls1.get(i);
      StepYaml stepYaml2 = stepYamls2.get(i);
      if (!stepMapperFactory.areSimilar(stepYaml1, stepYaml2)) {
        return false;
      }
    }

    return true;
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

    List<ExecutionWrapperConfig> allSteps = new ArrayList<>();

    // Handle Wait Interval
    Integer waitInterval = phaseStep.getWaitInterval();
    if (waitInterval != null && waitInterval > 0) {
      WaitStepNode waitStepNode = new WaitStepNode();
      waitStepNode.setName("Wait");
      waitStepNode.setIdentifier("wait");
      waitStepNode.setWaitStepInfo(
          WaitStepInfo.infoBuilder().duration(MigratorUtility.getTimeout(waitInterval * 1000)).build());
      ExecutionWrapperConfig waitStep =
          ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(waitStepNode)).build();
      allSteps.add(waitStep);
    }
    allSteps.addAll(steps);

    return ExecutionWrapperConfig.builder()
        .stepGroup(JsonPipelineUtils.asTree(StepGroupElementConfig.builder()
                                                .identifier(MigratorUtility.generateIdentifier(phaseStep.getName()))
                                                .name(phaseStep.getName())
                                                .steps(allSteps)
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
            -> getStepElementConfig(stepMapperFactory, stepYaml, skipStrategies.getOrDefault(stepYaml.getName(), null)))
        .filter(Objects::nonNull)
        .map(stepNode -> ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(stepNode)).build())
        .collect(Collectors.toList());
  }

  AbstractStepNode getStepElementConfig(StepMapperFactory stepMapperFactory, StepYaml step, String skipCondition) {
    StepMapper stepMapper = stepMapperFactory.getStepMapper(step.getType());
    MigratorExpressionUtils.render(step, new HashMap<>());
    AbstractStepNode stepNode = stepMapper.getSpec(step);
    if (stepNode == null) {
      return null;
    }
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

  JsonNode getCustomStageTemplateSpec(Workflow workflow, StepMapperFactory stepMapperFactory) {
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
    if (EmptyPredicate.isNotEmpty(phase.getServiceVariableOverrides())) {
      stageNode.setVariables(phase.getServiceVariableOverrides()
                                 .stream()
                                 .filter(sv -> StringUtils.isNotBlank(sv.getName()))
                                 .map(sv
                                     -> StringNGVariable.builder()
                                            .name(sv.getName())
                                            .type(NGVariableType.STRING)
                                            .value(ParameterField.createValueField(
                                                StringUtils.isNotBlank(sv.getValue()) ? sv.getValue() : ""))
                                            .build())
                                 .collect(Collectors.toList()));
    }
    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(stageNode)).build();
  }

  JsonNode buildMultiStagePipelineTemplate(StepMapperFactory stepMapperFactory, Workflow workflow) {
    PhaseStep.Yaml prePhase = getPreDeploymentPhase(workflow);
    List<WorkflowPhase.Yaml> phases = getPhases(workflow);
    PhaseStep.Yaml postPhase = getPostDeploymentPhase(workflow);
    List<WorkflowPhase.Yaml> rollbackPhases = getRollbackPhases(workflow);

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

    PipelineInfoConfig pipelineInfoConfig =
        PipelineInfoConfig.builder().stages(stages).variables(getVariables(workflow)).build();
    return JsonPipelineUtils.asTree(pipelineInfoConfig);
  }
}
