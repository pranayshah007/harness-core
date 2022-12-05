/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.Condition;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpec;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.custom.CustomApprovalStepInfo;
import io.harness.steps.approval.step.custom.CustomApprovalStepNode;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo.HarnessApprovalStepInfoBuilder;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfo;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.jira.JiraApprovalStepInfo;
import io.harness.steps.approval.step.jira.JiraApprovalStepInfo.JiraApprovalStepInfoBuilder;
import io.harness.steps.approval.step.jira.JiraApprovalStepNode;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepInfo;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepNode;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams;
import software.wings.sm.State;
import software.wings.sm.states.ApprovalState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ApprovalStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    ApprovalState state = (ApprovalState) getState(stepYaml);
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
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ApprovalState state = new ApprovalState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    ApprovalState state = (ApprovalState) getState(stepYaml);

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

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    ApprovalState state1 = (ApprovalState) getState(stepYaml1);
    ApprovalState state2 = (ApprovalState) getState(stepYaml2);
    // As long as the types match we can call them similar. Because it is easy to create step templates & customize
    return state1.getApprovalStateType() == state2.getApprovalStateType();
  }

  private HarnessApprovalStepNode buildHarnessApproval(ApprovalState state) {
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();
    baseSetup(state, harnessApprovalStepNode);

    HarnessApprovalStepInfoBuilder harnessApprovalStepInfoBuilder =
        HarnessApprovalStepInfo.builder().includePipelineExecutionHistory(ParameterField.createValueField(true));

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
    JiraApprovalStepNode stepNode = new JiraApprovalStepNode();
    baseSetup(state, stepNode);

    JiraApprovalParams approvalParams = state.getApprovalStateParams().getJiraApprovalParams();
    CriteriaSpecWrapper approval = getRuntimeJexl();

    if (StringUtils.isNoneBlank(approvalParams.getApprovalField(), approvalParams.getApprovalValue())) {
      approval = getKeyValueCriteria(approvalParams.getApprovalField(), approvalParams.getApprovalValue());
    }

    CriteriaSpecWrapper rejection = getRuntimeJexl();
    if (StringUtils.isNoneBlank(approvalParams.getRejectionField(), approvalParams.getRejectionValue())) {
      rejection = getKeyValueCriteria(approvalParams.getRejectionField(), approvalParams.getRejectionValue());
    }

    JiraApprovalStepInfoBuilder stepInfoBuilder =
        JiraApprovalStepInfo.builder()
            .connectorRef(MigratorUtility.RUNTIME_INPUT)
            .issueKey(ParameterField.createValueField(approvalParams.getIssueId()))
            .projectKey(approvalParams.getProject())
            .approvalCriteria(approval)
            .rejectionCriteria(rejection);

    stepNode.setJiraApprovalStepInfo(stepInfoBuilder.build());
    return stepNode;
  }

  private ServiceNowApprovalStepNode buildServiceNowApproval(ApprovalState state) {
    ServiceNowApprovalStepNode stepNode = new ServiceNowApprovalStepNode();
    baseSetup(state, stepNode);

    ServiceNowApprovalParams approvalParams = state.getApprovalStateParams().getServiceNowApprovalParams();

    ServiceNowApprovalStepInfo stepInfo =
        ServiceNowApprovalStepInfo.builder()
            .connectorRef(MigratorUtility.RUNTIME_INPUT)
            .delegateSelectors(null)
            .ticketNumber(ParameterField.createValueField(approvalParams.getIssueNumber()))
            .ticketType(ParameterField.createValueField(approvalParams.getTicketType().getDisplayName()))
            .changeWindow(null)
            .approvalCriteria(null)
            .rejectionCriteria(null)
            .build();
    stepNode.setServiceNowApprovalStepInfo(stepInfo);
    return stepNode;
  }

  private CustomApprovalStepNode buildCustomApproval(ApprovalState state) {
    CustomApprovalStepNode stepNode = new CustomApprovalStepNode();
    baseSetup(state, stepNode);

    ShellScriptApprovalParams approvalParams = state.getApprovalStateParams().getShellScriptApprovalParams();

    CriteriaSpecWrapper approval = getKeyValueCriteria("HARNESS_APPROVAL_STATUS", "APPROVED");
    CriteriaSpecWrapper rejection = getKeyValueCriteria("HARNESS_APPROVAL_STATUS", "REJECTED");

    CustomApprovalStepInfo stepInfo =
        CustomApprovalStepInfo.builder()
            .source(ShellScriptSourceWrapper.builder()
                        .spec(ShellScriptInlineSource.builder()
                                  .script(ParameterField.createValueField(approvalParams.getScriptString()))
                                  .build())
                        .type("Inline")
                        .build())
            .scriptTimeout(ParameterField.createValueField(Timeout.builder().timeoutString("10m").build()))
            .retryInterval(ParameterField.createValueField(
                Timeout.builder().timeoutInMillis(approvalParams.getRetryInterval()).build()))
            .outputVariables(Collections.emptyList())
            .environmentVariables(Collections.emptyList())
            .shell(ShellType.Bash)
            .delegateSelectors(MigratorUtility.getDelegateSelectors(approvalParams.fetchDelegateSelectors()))
            .approvalCriteria(approval)
            .rejectionCriteria(rejection)
            .build();

    stepNode.setCustomApprovalStepInfo(stepInfo);
    return stepNode;
  }

  private static CriteriaSpecWrapper getRuntimeJexl() {
    CriteriaSpecWrapper criteria = new CriteriaSpecWrapper();
    criteria.setType(CriteriaSpecType.JEXL);
    criteria.setCriteriaSpec(JexlCriteriaSpec.builder().expression(MigratorUtility.RUNTIME_INPUT).build());
    return criteria;
  }

  private static CriteriaSpecWrapper getKeyValueCriteria(String key, String value) {
    CriteriaSpecWrapper criteria = new CriteriaSpecWrapper();
    criteria.setType(CriteriaSpecType.KEY_VALUES);
    criteria.setCriteriaSpec(
        KeyValuesCriteriaSpec.builder()
            .conditions(Collections.singletonList(Condition.builder()
                                                      .key(key)
                                                      .operator(Operator.EQ)
                                                      .value(ParameterField.createValueField(value))
                                                      .build()))
            .build());
    return criteria;
  }
}
