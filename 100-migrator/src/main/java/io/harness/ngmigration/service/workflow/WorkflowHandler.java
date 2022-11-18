/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.InputSetValidatorType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
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
import software.wings.yaml.workflow.StepYaml;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public interface WorkflowHandler {
  default TemplateEntityType getTemplateType(Workflow workflow) {
    return TemplateEntityType.STAGE_TEMPLATE;
  }

  default List<NGVariable> getVariables(Workflow workflow) {
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

  default JsonNode getTemplateSpec(Workflow workflow) {
    return null;
  }

  List<Yaml> getPhases(Workflow workflow);

  List<GraphNode> getSteps(Workflow workflow);

  default List<GraphNode> getSteps(
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

  default List<GraphNode> getStepsFromPhases(List<WorkflowPhase> phases) {
    return phases.stream()
        .filter(phase -> isNotEmpty(phase.getPhaseSteps()))
        .flatMap(phase -> phase.getPhaseSteps().stream())
        .filter(phaseStep -> isNotEmpty(phaseStep.getSteps()))
        .flatMap(phaseStep -> phaseStep.getSteps().stream())
        .collect(Collectors.toList());
  }

  default ExecutionWrapperConfig getSteps(StepMapperFactory stepMapperFactory, WorkflowPhase.Yaml phase) {
    List<PhaseStep.Yaml> phaseSteps = phase.getPhaseSteps();
    List<ExecutionWrapperConfig> currSteps = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(phaseSteps)) {
      currSteps = phaseSteps.stream()
                      .filter(phaseStep -> EmptyPredicate.isNotEmpty(phaseStep.getSteps()))
                      .flatMap(phaseStep -> phaseStep.getSteps().stream())
                      .map(phaseStep
                          -> ExecutionWrapperConfig.builder()
                                 .step(JsonPipelineUtils.asTree(getStepElementConfig(stepMapperFactory, phaseStep)))
                                 .build())
                      .collect(Collectors.toList());
    }
    return ExecutionWrapperConfig.builder()
        .stepGroup(JsonPipelineUtils.asTree(StepGroupElementConfig.builder()
                                                .identifier(MigratorUtility.generateIdentifier(phase.getName()))
                                                .name(phase.getName())
                                                .steps(currSteps)
                                                .skipCondition(null)
                                                .when(null)
                                                .failureStrategies(null)
                                                .build()))
        .build();
  }

  default AbstractStepNode getStepElementConfig(StepMapperFactory stepMapperFactory, StepYaml step) {
    StepMapper stepMapper = stepMapperFactory.getStepMapper(step.getType());
    return stepMapper.getSpec(step);
  }
}
