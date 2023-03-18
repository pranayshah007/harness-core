/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepInfo;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepNode;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsParameterField;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsParameterFieldType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.JenkinsStepFunctor;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.WorkflowPhase;
import software.wings.sm.State;
import software.wings.sm.states.JenkinsState;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class JenkinsStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.JENKINS_BUILD;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    JenkinsState state = new JenkinsState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    JenkinsState state = (JenkinsState) getState(graphNode);
    JenkinsBuildStepNode stepNode = new JenkinsBuildStepNode();
    baseSetup(graphNode, stepNode, context.getIdentifierCaseFormat());

    List<JenkinsParameterField> jobParams = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(state.getJobParameters())) {
      jobParams = state.getJobParameters()
                      .stream()
                      .map(param
                          -> JenkinsParameterField.builder()
                                 .name(param.getKey())
                                 .value(ParameterField.createValueField(param.getValue()))
                                 .type(ParameterField.createValueField(JenkinsParameterFieldType.STRING))
                                 .build())
                      .collect(Collectors.toList());
    }

    String jobName = "<+input>";
    JenkinsBuildStepInfo stepInfo =
        JenkinsBuildStepInfo.builder()
            .unstableStatusAsSuccess(state.isUnstableSuccess())
            .useConnectorUrlForJobExecution(true)
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .connectorRef(ParameterField.createValueField("<+input>"))
            .jobParameter(ParameterField.createValueField(jobParams))
            .jobName(ParameterField.createValueField(jobName))
            .build();
    stepNode.setJenkinsBuildStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    // We can parameterize almost everything in Jenkins step. So customers could templatize
    return true;
  }

  @Override
  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    String sweepingOutputName = getSweepingOutputName(graphNode);
    if (StringUtils.isEmpty(sweepingOutputName)) {
      return Collections.emptyList();
    }
    return Lists.newArrayList(String.format("context.%s", sweepingOutputName), String.format("%s", sweepingOutputName))
        .stream()
        .map(exp
            -> StepOutput.builder()
                   .stageIdentifier(
                       MigratorUtility.generateIdentifier(phase.getName(), context.getIdentifierCaseFormat()))
                   .stepIdentifier(
                       MigratorUtility.generateIdentifier(graphNode.getName(), context.getIdentifierCaseFormat()))
                   .stepGroupIdentifier(
                       MigratorUtility.generateIdentifier(phaseStep.getName(), context.getIdentifierCaseFormat()))
                   .expression(exp)
                   .build())
        .map(JenkinsStepFunctor::new)
        .collect(Collectors.toList());
  }
}
