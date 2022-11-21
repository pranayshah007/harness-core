/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.custom.CustomApprovalStepNode;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo.HarnessApprovalStepInfoBuilder;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfo;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.jira.JiraApprovalStepNode;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepNode;

import software.wings.sm.states.ApprovalState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Map;
import java.util.stream.Collectors;

public class ApprovalStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ApprovalState state = new ApprovalState(stepYaml.getName());
    state.parseProperties(properties);
    switch (state.getApprovalStateType()) {
      case JIRA:
        return StepSpecTypeConstants.JIRA_APPROVAL;
      case USER_GROUP:
        return StepSpecTypeConstants.HARNESS_APPROVAL;
      case SHELL_SCRIPT:
        return StepSpecTypeConstants.CUSTOM_APPROVAL;
      case SERVICENOW:
        return StepSpecTypeConstants.SERVICENOW_APPROVAL;
      default:
        throw new IllegalStateException("Unsupported Approval Type");
    }
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ApprovalState state = new ApprovalState(stepYaml.getName());
    state.parseProperties(properties);

    switch (state.getApprovalStateType()) {
      case JIRA:
        return buildJiraApproval(state);
      case USER_GROUP:
        return buildHarnessApproval(state);
      case SHELL_SCRIPT:
        return buildCustomApproval(state);
      case SERVICENOW:
        return buildServiceNowApproval(state);
      default:
        throw new IllegalStateException("Unsupported Approval Type");
    }
  }

  private HarnessApprovalStepNode buildHarnessApproval(ApprovalState state) {
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();
    baseSetup(state, harnessApprovalStepNode);

    HarnessApprovalStepInfoBuilder harnessApprovalStepInfoBuilder = HarnessApprovalStepInfo.builder();

    harnessApprovalStepInfoBuilder.approvers(
        Approvers.builder()
            .disallowPipelineExecutor(ParameterField.createValueField(false))
            .minimumCount(ParameterField.createValueField(1))
            .userGroups(ParameterField.createExpressionField(true, "<+input>", null, false))
            .build());

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      harnessApprovalStepInfoBuilder.approverInputs(
          state.getVariables()
              .stream()
              .map(pair
                  -> ApproverInputInfo.builder()
                         .name(pair.getName())
                         .defaultValue(ParameterField.createValueField(pair.getValue()))
                         .build())
              .collect(Collectors.toList()));
    }

    harnessApprovalStepNode.setHarnessApprovalStepInfo(harnessApprovalStepInfoBuilder.build());

    return harnessApprovalStepNode;
  }

  private JiraApprovalStepNode buildJiraApproval(ApprovalState state) {
    return null;
  }

  private ServiceNowApprovalStepNode buildServiceNowApproval(ApprovalState state) {
    return null;
  }

  private CustomApprovalStepNode buildCustomApproval(ApprovalState state) {
    return null;
  }
}
