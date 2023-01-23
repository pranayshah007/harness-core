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
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowStepSupportStatus;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.sm.State;
import software.wings.sm.states.JenkinsState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JenkinsStepMapperImpl implements StepMapper {
  @Override
  public WorkflowStepSupportStatus stepSupportStatus(GraphNode graphNode) {
    return WorkflowStepSupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.JENKINS_BUILD;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    JenkinsState state = new JenkinsState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    JenkinsState state = (JenkinsState) getState(graphNode);
    JenkinsBuildStepNode stepNode = new JenkinsBuildStepNode();
    baseSetup(graphNode, stepNode);

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
}
