/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import static io.harness.ngmigration.service.MigratorUtility.RUNTIME_INPUT;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.jira.beans.JiraField;
import io.harness.steps.jira.create.JiraCreateStepInfo;
import io.harness.steps.jira.create.JiraCreateStepNode;
import io.harness.steps.jira.update.JiraUpdateStepInfo;
import io.harness.steps.jira.update.JiraUpdateStepNode;
import io.harness.steps.jira.update.beans.TransitionTo;

import software.wings.sm.State;
import software.wings.sm.states.collaboration.JiraCreateUpdate;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraCreateUpdateStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    JiraCreateUpdate state = (JiraCreateUpdate) getState(stepYaml);
    switch (state.getJiraAction()) {
      case UPDATE_TICKET:
        return StepSpecTypeConstants.JIRA_UPDATE;
      case CREATE_TICKET:
        return StepSpecTypeConstants.JIRA_CREATE;
      default:
        throw new IllegalStateException("Unsupported Approval Type");
    }
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    JiraCreateUpdate state = new JiraCreateUpdate(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    JiraCreateUpdate state = (JiraCreateUpdate) getState(stepYaml);
    switch (state.getJiraAction()) {
      case UPDATE_TICKET:
        return buildUpdate(state);
      case CREATE_TICKET:
        return buildCreate(state);
      default:
        throw new IllegalStateException("Unsupported Approval Type");
    }
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    JiraCreateUpdate state1 = (JiraCreateUpdate) getState(stepYaml1);
    JiraCreateUpdate state2 = (JiraCreateUpdate) getState(stepYaml2);
    if (!state2.getJiraAction().equals(state1.getJiraAction())) {
      return false;
    }
    return true;
  }

  private JiraCreateStepNode buildCreate(JiraCreateUpdate state) {
    JiraCreateStepNode stepNode = new JiraCreateStepNode();
    baseSetup(state, stepNode);
    JiraCreateStepInfo stepInfo = JiraCreateStepInfo.builder()
                                      .connectorRef(RUNTIME_INPUT)
                                      .projectKey(ParameterField.createValueField(state.getProject()))
                                      .issueType(ParameterField.createValueField(state.getIssueType()))
                                      .fields(getFields(state))
                                      .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
                                      .build();

    stepNode.setJiraCreateStepInfo(stepInfo);
    return stepNode;
  }

  private static List<JiraField> getFields(JiraCreateUpdate state) {
    if (EmptyPredicate.isEmpty(state.getCustomFieldsMap())) {
      return Collections.emptyList();
    }
    return state.getCustomFieldsMap()
        .entrySet()
        .stream()
        .map(entry
            -> JiraField.builder()
                   .name(entry.getKey())
                   .value(ParameterField.createValueField(entry.getValue().getFieldValue()))
                   .build())
        .collect(Collectors.toList());
  }

  private JiraUpdateStepNode buildUpdate(JiraCreateUpdate state) {
    JiraUpdateStepNode stepNode = new JiraUpdateStepNode();
    baseSetup(state, stepNode);
    JiraUpdateStepInfo stepInfo =
        JiraUpdateStepInfo.builder()
            .connectorRef(RUNTIME_INPUT)
            .issueKey(ParameterField.createValueField(state.getIssueId()))
            .transitionTo(TransitionTo.builder().status(ParameterField.createValueField(state.getStatus())).build())
            .fields(getFields(state))
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .build();
    stepNode.setJiraUpdateStepInfo(stepInfo);
    return stepNode;
  }
}
