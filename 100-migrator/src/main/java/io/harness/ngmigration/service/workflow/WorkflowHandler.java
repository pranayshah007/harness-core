/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.getRollbackPhases;
import static io.harness.when.beans.WhenConditionStatus.SUCCESS;

import static software.wings.sm.StepType.AWS_NODE_SELECT;
import static software.wings.sm.StepType.AZURE_NODE_SELECT;
import static software.wings.sm.StepType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES;
import static software.wings.sm.StepType.DC_NODE_SELECT;

import io.harness.beans.InputSetValidatorType;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.HarnessForConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.steps.customstage.CustomStageConfig;
import io.harness.steps.customstage.CustomStageNode;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.wait.WaitStepNode;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.beans.workflow.StepSkipStrategy.Scope;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

public abstract class WorkflowHandler {
  public static final String INPUT_EXPRESSION = "<+input>";
  private static final Set<String> loopingEnablers = Sets.newHashSet(DC_NODE_SELECT.name(),
      CUSTOM_DEPLOYMENT_FETCH_INSTANCES.getName(), AWS_NODE_SELECT.name(), AZURE_NODE_SELECT.getName());

  @Inject private StepMapperFactory stepMapperFactory;
  public List<CgEntityId> getReferencedEntities(StepMapperFactory stepMapperFactory, Workflow workflow) {
    List<GraphNode> steps = MigratorUtility.getSteps(workflow);
    Map<String, String> stepIdToServiceIdMap = getStepIdToServiceIdMap(workflow);
    List<CgEntityId> referencedEntities = new ArrayList<>();
    if (StringUtils.isNotBlank(workflow.getServiceId())) {
      referencedEntities.add(
          CgEntityId.builder().id(workflow.getServiceId()).type(NGMigrationEntityType.SERVICE).build());
    }
    if (EmptyPredicate.isEmpty(steps)) {
      return referencedEntities;
    }
    referencedEntities.addAll(
        steps.stream()
            .map(step
                -> stepMapperFactory.getStepMapper(step.getType())
                       .getReferencedEntities(workflow.getAccountId(), step, stepIdToServiceIdMap))
            .filter(EmptyPredicate::isNotEmpty)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
    return referencedEntities;
  }

  public TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.STAGE_TEMPLATE;
  }

  boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    List<WorkflowPhase> phases1 = getPhases(workflow1);
    List<WorkflowPhase> rollbackPhases1 = getRollbackPhases(workflow1);
    PhaseStep pre1 = getPreDeploymentPhase(workflow1);
    PhaseStep post1 = getPostDeploymentPhase(workflow1);

    List<WorkflowPhase> phases2 = getPhases(workflow2);
    List<WorkflowPhase> rollbackPhases2 = getRollbackPhases(workflow2);
    PhaseStep pre2 = getPreDeploymentPhase(workflow2);
    PhaseStep post2 = getPostDeploymentPhase(workflow2);

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
        .filter(variable -> variable.getType() != VariableType.ENTITY)
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
        true, false, INPUT_EXPRESSION, variable.getValue(), validator, true);
  }

  public List<StageElementWrapperConfig> asStages(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow) {
    throw new NotImplementedException("Getting stages is only supported for multi service workflows right now");
  }

  public abstract JsonNode getTemplateSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow);

  List<WorkflowPhase> getPhases(Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return EmptyPredicate.isNotEmpty(orchestrationWorkflow.getWorkflowPhases())
        ? orchestrationWorkflow.getWorkflowPhases()
        : Collections.emptyList();
  }

  PhaseStep getPreDeploymentPhase(Workflow workflow) {
    return null;
  }

  PhaseStep getPostDeploymentPhase(Workflow workflow) {
    return null;
  }

  public Map<String, String> getStepIdToServiceIdMap(Workflow workflow) {
    Map<String, String> result = new HashMap<>();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
    result.putAll(getStepIdToServiceIdMap(phases));
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(workflow);
    result.putAll(getStepIdToServiceIdMap(rollbackPhases));
    return result;
  }

  private Map<String, String> getStepIdToServiceIdMap(List<WorkflowPhase> phases) {
    Map<String, String> result = new HashMap<>();
    if (isNotEmpty(phases)) {
      for (WorkflowPhase phase : phases) {
        String serviceId = phase.getServiceId();
        List<PhaseStep> phaseSteps = phase.getPhaseSteps();
        if (isNotEmpty(phaseSteps)) {
          for (PhaseStep phaseStep : phaseSteps) {
            List<GraphNode> steps = phaseStep.getSteps();
            if (isNotEmpty(steps)) {
              steps.forEach(s -> result.put(s.getId(), serviceId));
            }
          }
        }
      }
    }
    return result;
  }

  List<ExecutionWrapperConfig> getStepGroups(WorkflowMigrationContext context, WorkflowPhase phase) {
    List<PhaseStep> phaseSteps = phase != null ? phase.getPhaseSteps() : Collections.emptyList();
    List<ExecutionWrapperConfig> stepGroups = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(phaseSteps)) {
      stepGroups = phaseSteps.stream()
                       .filter(phaseStep -> EmptyPredicate.isNotEmpty(phaseStep.getSteps()))
                       .map(phaseStep -> getStepGroup(context, phase, phaseStep))
                       .filter(Objects::nonNull)
                       .collect(Collectors.toList());
    }
    return stepGroups;
  }

  boolean areSimilarPhase(StepMapperFactory stepMapperFactory, WorkflowPhase phase1, WorkflowPhase phase2) {
    List<PhaseStep> phase1Steps = phase1 != null ? phase1.getPhaseSteps() : Collections.emptyList();
    List<PhaseStep> phase2Steps = phase2 != null ? phase2.getPhaseSteps() : Collections.emptyList();

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

  boolean areSimilarPhaseStep(StepMapperFactory stepMapperFactory, PhaseStep phaseStep1, PhaseStep phaseStep2) {
    if ((phaseStep1 == null && phaseStep2 != null) || (phaseStep1 != null && phaseStep2 == null)) {
      return false;
    }

    if (phaseStep1 == null) {
      return true;
    }

    List<GraphNode> stepYamls1 =
        EmptyPredicate.isNotEmpty(phaseStep1.getSteps()) ? phaseStep1.getSteps() : Collections.emptyList();
    List<GraphNode> stepYamls2 =
        EmptyPredicate.isNotEmpty(phaseStep2.getSteps()) ? phaseStep2.getSteps() : Collections.emptyList();

    if (stepYamls2.size() != stepYamls1.size()) {
      return false;
    }

    for (int i = 0; i < stepYamls1.size(); ++i) {
      GraphNode stepYaml1 = stepYamls1.get(i);
      GraphNode stepYaml2 = stepYamls2.get(i);
      if (!stepMapperFactory.areSimilar(stepYaml1, stepYaml2)) {
        return false;
      }
    }

    return true;
  }

  ExecutionWrapperConfig getStepGroup(WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep) {
    List<GraphNode> stepYamls = phaseStep.getSteps();

    List<ExecutionWrapperConfig> steps = getStepWrappers(context, phase, phaseStep, stepYamls);
    if (EmptyPredicate.isEmpty(steps)) {
      return null;
    }
    if (phaseStep.isStepsInParallel()) {
      steps =
          Collections.singletonList(ExecutionWrapperConfig.builder().parallel(JsonPipelineUtils.asTree(steps)).build());
    }
    StepWhenCondition when = null;
    List<StepSkipStrategy> cgSkipConditions = phaseStep.getStepSkipStrategies();
    if (EmptyPredicate.isNotEmpty(cgSkipConditions)
        && cgSkipConditions.stream().anyMatch(skip -> Scope.ALL_STEPS.equals(skip.getScope()))) {
      StepSkipStrategy strategy =
          cgSkipConditions.stream().filter(skip -> Scope.ALL_STEPS.equals(skip.getScope())).findFirst().get();
      when = StepWhenCondition.builder()
                 .condition(wrapNot(strategy.getAssertionExpression()))
                 .stageStatus(SUCCESS)
                 .build();
    }

    List<ExecutionWrapperConfig> allSteps = new ArrayList<>();

    // Handle Wait Interval
    Integer waitInterval = phaseStep.getWaitInterval();
    if (waitInterval != null && waitInterval > 0) {
      WaitStepNode waitStepNode = MigratorUtility.getWaitStepNode("Wait", waitInterval, false);
      ExecutionWrapperConfig waitStep =
          ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(waitStepNode)).build();
      allSteps.add(waitStep);
    }
    allSteps.addAll(steps);

    return ExecutionWrapperConfig.builder()
        .stepGroup(JsonPipelineUtils.asTree(StepGroupElementConfig.builder()
                                                .identifier(MigratorUtility.generateIdentifier(phaseStep.getName()))
                                                .name(MigratorUtility.generateName(phaseStep.getName()))
                                                .steps(allSteps)
                                                .skipCondition(null)
                                                .when(ParameterField.createValueField(when))
                                                .failureStrategies(null)
                                                .build()))
        .build();
  }

  List<ExecutionWrapperConfig> getStepWrappers(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, List<GraphNode> stepYamls) {
    if (EmptyPredicate.isEmpty(stepYamls)) {
      return Collections.emptyList();
    }
    MigratorExpressionUtils.render(context.getEntities(), context.getMigratedEntities(), phaseStep,
        getExpressions(phase, context.getStepExpressionFunctors()));
    List<StepSkipStrategy> cgSkipConditions = phaseStep.getStepSkipStrategies();
    Map<String, String> skipStrategies = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(cgSkipConditions)
        && cgSkipConditions.stream().noneMatch(skip -> Scope.ALL_STEPS.equals(skip.getScope()))) {
      cgSkipConditions.stream()
          .filter(skip
              -> EmptyPredicate.isNotEmpty(skip.getStepIds()) && StringUtils.isNotBlank(skip.getAssertionExpression()))
          .forEach(skip -> skip.getStepIds().forEach(step -> skipStrategies.put(step, skip.getAssertionExpression())));
    }
    List<ExecutionWrapperConfig> stepWrappers = new ArrayList<>();
    boolean addLoopingStrategy = false;
    for (GraphNode stepYaml : stepYamls) {
      JsonNode stepNodeJson = getStepElementConfig(
          context, phase, phaseStep, stepYaml, skipStrategies.getOrDefault(stepYaml.getId(), null), addLoopingStrategy);
      if (stepNodeJson != null) {
        ExecutionWrapperConfig build = ExecutionWrapperConfig.builder().step(stepNodeJson).build();
        stepWrappers.add(build);
        if (!addLoopingStrategy && shouldAddLoopingInNextSteps(stepYaml.getType())) {
          addLoopingStrategy = true;
        }
      }
    }
    return stepWrappers;
  }

  private boolean shouldAddLoopingInNextSteps(String type) {
    return loopingEnablers.contains(type);
  }

  JsonNode getStepElementConfig(WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep,
      GraphNode step, String skipCondition, boolean addLoopingStrategy) {
    StepMapper stepMapper = stepMapperFactory.getStepMapper(step.getType());
    Map<String, Object> expressions = getExpressions(phase, context.getStepExpressionFunctors());
    if (StringUtils.isNotBlank(skipCondition)) {
      skipCondition = (String) MigratorExpressionUtils.render(
          context.getEntities(), context.getMigratedEntities(), skipCondition, expressions);
    }
    MigratorExpressionUtils.render(context.getEntities(), context.getMigratedEntities(), step, expressions);
    List<StepExpressionFunctor> expressionFunctors = stepMapper.getExpressionFunctor(context, phase, phaseStep, step);
    if (isNotEmpty(expressionFunctors)) {
      context.getStepExpressionFunctors().addAll(expressionFunctors);
    }
    TemplateStepNode templateStepNode = stepMapper.getTemplateSpec(context, step);
    if (templateStepNode != null) {
      return JsonPipelineUtils.asTree(templateStepNode);
    }
    AbstractStepNode stepNode = stepMapper.getSpec(context, step);
    if (stepNode == null) {
      return null;
    }
    if (StringUtils.isNotBlank(skipCondition)) {
      stepNode.setWhen(ParameterField.createValueField(
          StepWhenCondition.builder().condition(wrapNot(skipCondition)).stageStatus(SUCCESS).build()));
    }
    if (addLoopingStrategy && stepMapper.loopingSupported()) {
      stepNode.setStrategy(ParameterField.createValueField(
          StrategyConfig.builder()
              .repeat(HarnessForConfig.builder()
                          .items(ParameterField.createValueField(Arrays.asList("<+stage.output.hosts>")))
                          .build())
              .build()));
    }
    return JsonPipelineUtils.asTree(stepNode);
  }

  private Map<String, Object> getExpressions(WorkflowPhase phase, List<StepExpressionFunctor> functors) {
    Map<String, Object> expressions = new HashMap<>();

    for (StepExpressionFunctor functor : functors) {
      functor.setCurrentStageIdentifier(MigratorUtility.generateIdentifier(phase.getName()));
      expressions.put(functor.getCgExpression(), functor);
    }
    return expressions;
  }

  private ParameterField<String> wrapNot(String condition) {
    if (StringUtils.isBlank(condition)) {
      return ParameterField.ofNull();
    }
    return ParameterField.createValueField("!(" + condition + ")");
  }

  // We can infer the type based on the service, infra & sometimes based on the steps used.
  ServiceDefinitionType inferServiceDefinitionType(WorkflowMigrationContext context) {
    List<GraphNode> steps = MigratorUtility.getSteps(context.getWorkflow());
    return inferServiceDefinitionType(context, steps);
  }

  ServiceDefinitionType inferServiceDefinitionType(WorkflowMigrationContext context, WorkflowPhase phase) {
    List<GraphNode> steps = MigratorUtility.getStepsFromPhases(Collections.singletonList(phase));
    return inferServiceDefinitionType(context, steps);
  }

  ServiceDefinitionType inferServiceDefinitionType(WorkflowMigrationContext context, List<GraphNode> steps) {
    OrchestrationWorkflowType workflowType = context.getWorkflow().getOrchestration().getOrchestrationWorkflowType();
    ServiceDefinitionType defaultType = workflowType.equals(OrchestrationWorkflowType.BASIC)
        ? ServiceDefinitionType.SSH
        : ServiceDefinitionType.KUBERNETES;
    if (EmptyPredicate.isEmpty(steps)) {
      return defaultType;
    }
    return steps.stream()
        .map(step -> stepMapperFactory.getStepMapper(step.getType()).inferServiceDef(context, step))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(defaultType);
  }

  ParameterField<Map<String, Object>> getRuntimeInput() {
    return ParameterField.createExpressionField(true, "<+input>", null, false);
  }

  DeploymentStageConfig getDeploymentStageConfig(WorkflowMigrationContext context,
      ServiceDefinitionType serviceDefinitionType, WorkflowPhase phase, WorkflowPhase rollbackPhase) {
    List<ExecutionWrapperConfig> stepGroups = getStepGroups(context, phase);
    if (EmptyPredicate.isEmpty(stepGroups)) {
      return null;
    }
    List<ExecutionWrapperConfig> rollbackSteps = getStepGroups(context, rollbackPhase);
    return getDeploymentStageConfig(serviceDefinitionType, stepGroups, rollbackSteps);
  }

  DeploymentStageConfig getDeploymentStageConfig(ServiceDefinitionType serviceDefinitionType,
      List<ExecutionWrapperConfig> steps, List<ExecutionWrapperConfig> rollbackSteps) {
    if (EmptyPredicate.isEmpty(steps)) {
      AbstractStepNode waitStepNode = MigratorUtility.getWaitStepNode("Wait", 60, true);
      ExecutionWrapperConfig waitStep =
          ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(waitStepNode)).build();
      steps = Collections.singletonList(waitStep);
    }
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
                         .infrastructureDefinitions(ParameterField.createExpressionField(true, "<+input>", null, false))
                         .build())
        .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
        .build();
  }

  DeploymentStageConfig getDeploymentStageConfig(WorkflowMigrationContext context, List<ExecutionWrapperConfig> steps,
      List<ExecutionWrapperConfig> rollbackSteps) {
    return getDeploymentStageConfig(inferServiceDefinitionType(context), steps, rollbackSteps);
  }

  public static ParameterField<List<FailureStrategyConfig>> getDefaultFailureStrategy() {
    FailureStrategyConfig failureStrategyConfig =
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Lists.newArrayList(NGFailureType.ALL_ERRORS))
                           .action(AbortFailureActionConfig.builder().build())
                           .build())
            .build();
    return ParameterField.createValueField(Collections.singletonList(failureStrategyConfig));
  }

  JsonNode getDeploymentStageTemplateSpec(WorkflowMigrationContext context) {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    List<ExecutionWrapperConfig> rollbackSteps = new ArrayList<>();
    List<WorkflowPhase> phases = getPhases(context.getWorkflow());
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(context.getWorkflow());

    // Add all the steps
    if (EmptyPredicate.isNotEmpty(phases)) {
      steps.addAll(phases.stream()
                       .flatMap(phase -> getStepGroups(context, phase).stream())
                       .filter(Objects::nonNull)
                       .collect(Collectors.toList()));
    }

    // Add all the rollback steps
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      rollbackSteps.addAll(rollbackPhases.stream()
                               .flatMap(phase -> getStepGroups(context, phase).stream())
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList()));
    }

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("type", "Deployment")
                                           .put("spec", getDeploymentStageConfig(context, steps, rollbackSteps))
                                           .put("failureStrategies", getDefaultFailureStrategy())
                                           .put("variables", getVariables(context.getWorkflow()))
                                           .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }

  List<ExecutionWrapperConfig> getSteps(WorkflowMigrationContext context, List<WorkflowPhase> phases) {
    if (EmptyPredicate.isEmpty(phases)) {
      return Collections.emptyList();
    }
    return phases.stream()
        .flatMap(phase -> getStepGroups(context, phase).stream())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  JsonNode getCustomStageTemplateSpec(WorkflowMigrationContext context) {
    Workflow workflow = context.getWorkflow();
    List<WorkflowPhase> phases = getPhases(workflow);
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(workflow);

    // Add all the steps
    List<ExecutionWrapperConfig> steps = getSteps(context, phases);

    if (EmptyPredicate.isEmpty(steps)) {
      AbstractStepNode waitStepNode = MigratorUtility.getWaitStepNode("Wait", 60, true);
      ExecutionWrapperConfig waitStep =
          ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(waitStepNode)).build();
      steps = Collections.singletonList(waitStep);
    }

    // Add all the steps
    List<ExecutionWrapperConfig> rollbackSteps = getSteps(context, rollbackPhases);

    // Build Stage
    CustomStageConfig customStageConfig =
        CustomStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(steps).rollbackSteps(rollbackSteps).build())
            .build();

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("type", "Custom")
                                           .put("spec", customStageConfig)
                                           .put("failureStrategies", getDefaultFailureStrategy())
                                           .put("variables", getVariables(workflow))
                                           .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }

  // This is for multi-service only
  StageElementWrapperConfig buildCustomStage(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep) {
    ExecutionWrapperConfig wrapper = getStepGroup(context, phase, phaseStep);
    if (wrapper == null) {
      return null;
    }
    CustomStageConfig customStageConfig =
        CustomStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(Collections.singletonList(wrapper)).build())
            .build();
    CustomStageNode customStageNode = new CustomStageNode();
    customStageNode.setName(phase.getName());
    customStageNode.setIdentifier(MigratorUtility.generateIdentifier(phase.getName()));
    customStageNode.setCustomStageConfig(customStageConfig);
    customStageNode.setFailureStrategies(getDefaultFailureStrategy());
    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(customStageNode)).build();
  }

  // This is for multi service only
  StageElementWrapperConfig buildDeploymentStage(WorkflowMigrationContext context,
      ServiceDefinitionType serviceDefinitionType, WorkflowPhase phase, WorkflowPhase rollbackPhase) {
    DeploymentStageConfig stageConfig = getDeploymentStageConfig(context, serviceDefinitionType, phase, rollbackPhase);
    if (stageConfig == null) {
      return null;
    }
    DeploymentStageNode stageNode = new DeploymentStageNode();
    stageNode.setName(phase.getName());
    stageNode.setIdentifier(MigratorUtility.generateIdentifier(phase.getName()));
    stageNode.setDeploymentStageConfig(stageConfig);
    stageNode.setFailureStrategies(getDefaultFailureStrategy());
    if (EmptyPredicate.isNotEmpty(phase.getVariableOverrides())) {
      stageNode.setVariables(phase.getVariableOverrides()
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

  JsonNode buildCanaryStageTemplate(WorkflowMigrationContext context) {
    Workflow workflow = context.getWorkflow();
    PhaseStep prePhaseStep = getPreDeploymentPhase(workflow);
    List<WorkflowPhase> phases = getPhases(workflow);
    PhaseStep postPhaseStep = getPostDeploymentPhase(workflow);
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(workflow);

    final String PHASE_NAME = "DUMMY";
    List<ExecutionWrapperConfig> stepGroupWrappers = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(prePhaseStep.getSteps())) {
      prePhaseStep.setName("Pre Deployment");
      WorkflowPhase prePhase = WorkflowPhaseBuilder.aWorkflowPhase()
                                   .name(PHASE_NAME)
                                   .phaseSteps(Collections.singletonList(prePhaseStep))
                                   .build();
      List<ExecutionWrapperConfig> stage = getStepGroups(context, prePhase);
      if (EmptyPredicate.isNotEmpty(stage)) {
        stepGroupWrappers.addAll(stage);
      }
    }

    if (EmptyPredicate.isNotEmpty(phases)) {
      for (WorkflowPhase phase : phases) {
        String prefix = phase.getName();
        phase.setName(PHASE_NAME);
        stepGroupWrappers.addAll(phase.getPhaseSteps()
                                     .stream()
                                     .peek(phaseStep -> phaseStep.setName(prefix + "-" + phaseStep.getName()))
                                     .map(phaseStep -> getStepGroup(context, phase, phaseStep))
                                     .filter(Objects::nonNull)
                                     .collect(Collectors.toList()));
      }
    }

    if (EmptyPredicate.isNotEmpty(postPhaseStep.getSteps())) {
      postPhaseStep.setName("Post Deployment");
      WorkflowPhase postPhase = WorkflowPhaseBuilder.aWorkflowPhase()
                                    .name(PHASE_NAME)
                                    .phaseSteps(Collections.singletonList(postPhaseStep))
                                    .build();
      List<ExecutionWrapperConfig> stage = getStepGroups(context, postPhase);
      if (EmptyPredicate.isNotEmpty(stage)) {
        stepGroupWrappers.addAll(stage);
      }
    }

    List<ExecutionWrapperConfig> rollbackStepGroupWrappers = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      Collections.reverse(rollbackPhases);
      for (WorkflowPhase phase : rollbackPhases) {
        String prefix = phase.getName();
        phase.setName(PHASE_NAME);
        rollbackStepGroupWrappers.addAll(phase.getPhaseSteps()
                                             .stream()
                                             .peek(phaseStep -> phaseStep.setName(prefix + "-" + phaseStep.getName()))
                                             .map(phaseStep -> getStepGroup(context, phase, phaseStep))
                                             .filter(Objects::nonNull)
                                             .collect(Collectors.toList()));
      }
    }

    Map<String, Object> templateSpec =
        ImmutableMap.<String, Object>builder()
            .put("type", "Deployment")
            .put("spec", getDeploymentStageConfig(context, stepGroupWrappers, rollbackStepGroupWrappers))
            .put("failureStrategies", getDefaultFailureStrategy())
            .put("variables", getVariables(context.getWorkflow()))
            .build();
    return JsonPipelineUtils.asTree(templateSpec);
  }

  List<StageElementWrapperConfig> getStagesForMultiServiceWorkflow(WorkflowMigrationContext context) {
    Workflow workflow = context.getWorkflow();
    PhaseStep prePhaseStep = getPreDeploymentPhase(workflow);
    List<WorkflowPhase> phases = getPhases(workflow);
    PhaseStep postPhaseStep = getPostDeploymentPhase(workflow);
    List<WorkflowPhase> rollbackPhases = getRollbackPhases(workflow);

    List<StageElementWrapperConfig> stages = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(prePhaseStep.getSteps())) {
      WorkflowPhase prePhase = WorkflowPhaseBuilder.aWorkflowPhase().name("Pre Deployment").build();
      prePhaseStep.setName("Pre Deployment");
      StageElementWrapperConfig stage = buildCustomStage(context, prePhase, prePhaseStep);
      if (stage != null) {
        stages.add(stage);
      }
    }

    final Map<String, WorkflowPhase> rollbackPhaseMap = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(rollbackPhases)) {
      rollbackPhaseMap.putAll(
          rollbackPhases.stream().collect(Collectors.toMap(WorkflowPhase::getPhaseNameForRollback, phase -> phase)));
    }

    if (EmptyPredicate.isNotEmpty(phases)) {
      stages.addAll(phases.stream()
                        .map(phase
                            -> buildDeploymentStage(context, inferServiceDefinitionType(context, phase), phase,
                                rollbackPhaseMap.getOrDefault(phase.getName(), null)))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(postPhaseStep.getSteps())) {
      WorkflowPhase postPhase = WorkflowPhaseBuilder.aWorkflowPhase().name("Post Deployment").build();
      postPhaseStep.setName("Post Deployment");
      StageElementWrapperConfig stage = buildCustomStage(context, postPhase, postPhaseStep);
      if (stage != null) {
        stages.add(stage);
      }
    }

    // Note: If there are no stages in the generated template then we add a dummy stage which has a step that is always
    // skipped
    if (EmptyPredicate.isEmpty(stages)) {
      AbstractStepNode waitStepNode = MigratorUtility.getWaitStepNode("Wait", 60, true);
      ExecutionWrapperConfig waitStep =
          ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(waitStepNode)).build();
      CustomStageConfig customStageConfig =
          CustomStageConfig.builder()
              .execution(ExecutionElementConfig.builder().steps(Collections.singletonList(waitStep)).build())
              .build();
      CustomStageNode customStageNode = new CustomStageNode();
      customStageNode.setName("Always Skipped");
      customStageNode.setIdentifier(MigratorUtility.generateIdentifier("Always Skipped"));
      customStageNode.setCustomStageConfig(customStageConfig);
      customStageNode.setFailureStrategies(getDefaultFailureStrategy());
      stages.add(StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(customStageNode)).build());
    }

    return stages;
  }

  JsonNode buildMultiStagePipelineTemplate(WorkflowMigrationContext context) {
    List<StageElementWrapperConfig> stages = getStagesForMultiServiceWorkflow(context);
    PipelineInfoConfig pipelineInfoConfig =
        PipelineInfoConfig.builder().stages(stages).variables(getVariables(context.getWorkflow())).build();
    return JsonPipelineUtils.asTree(pipelineInfoConfig);
  }
}
