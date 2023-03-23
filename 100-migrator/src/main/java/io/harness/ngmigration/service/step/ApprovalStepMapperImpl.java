/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.ApprovalFunctor;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.Condition;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpec;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec;
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

import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.approval.ConditionalOperator;
import software.wings.beans.approval.Criteria;
import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams;
import software.wings.sm.State;
import software.wings.sm.states.ApprovalState;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class ApprovalStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
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

  public AbstractStepNode getSpec(PipelineStageElement pipelineStageElement, CaseFormat caseFormat) {
    Map<String, Object> properties = emptyIfNull(pipelineStageElement.getProperties());
    ApprovalState state = new ApprovalState(pipelineStageElement.getName());
    state.parseProperties(properties);
    return getSpec(state, caseFormat);
  }

  private AbstractStepNode getSpec(ApprovalState state, CaseFormat caseFormat) {
    switch (state.getApprovalStateType()) {
      case JIRA:
        return buildJiraApproval(state, caseFormat);
      case USER_GROUP:
        return buildHarnessApproval(state, caseFormat);
      case SHELL_SCRIPT:
        return buildCustomApproval(state, caseFormat);
      case SERVICENOW:
        return buildServiceNowApproval(state, caseFormat);
      default:
        throw new IllegalStateException("Unsupported Approval Type");
    }
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    ApprovalState state = new ApprovalState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    ApprovalState state = (ApprovalState) getState(graphNode);
    return getSpec(state, context.getIdentifierCaseFormat());
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    ApprovalState state1 = (ApprovalState) getState(stepYaml1);
    ApprovalState state2 = (ApprovalState) getState(stepYaml2);
    // As long as the types match we can call them similar. Because it is easy to create step templates & customize

    if (state1.getApprovalStateType() != state2.getApprovalStateType()) {
      return false;
    }
    if (state1.getApprovalStateType() == ApprovalStateType.USER_GROUP) {
      Set<NameValuePair> variables1 = new HashSet<>(emptyIfNull(state1.getVariables()));
      Set<NameValuePair> variables2 = new HashSet<>(emptyIfNull(state2.getVariables()));
      return variables1.equals(variables2);
    }
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
        .map(ApprovalFunctor::new)
        .collect(Collectors.toList());
  }

  private HarnessApprovalStepNode buildHarnessApproval(ApprovalState state, CaseFormat caseFormat) {
    HarnessApprovalStepNode harnessApprovalStepNode = new HarnessApprovalStepNode();
    baseSetup(state, harnessApprovalStepNode, caseFormat);

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

  private JiraApprovalStepNode buildJiraApproval(ApprovalState state, CaseFormat caseFormat) {
    JiraApprovalStepNode stepNode = new JiraApprovalStepNode();
    baseSetup(state, stepNode, caseFormat);

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

  private CriteriaSpecWrapper getServiceNowCriteriaSpecWrapper(Criteria criteria) {
    if (criteria == null) {
      return null;
    }
    CriteriaSpecWrapper criteriaSpecWrapper = new CriteriaSpecWrapper();
    criteriaSpecWrapper.setType(CriteriaSpecType.KEY_VALUES);
    List<Condition> conditions =
        criteria.fetchConditions()
            .entrySet()
            .stream()
            .map(entry
                -> Condition.builder()
                       .key(entry.getKey())
                       .operator(Operator.IN)
                       .value(ParameterField.createValueField(String.join(",", entry.getValue())))
                       .build())
            .collect(Collectors.toList());
    KeyValuesCriteriaSpec spec =
        KeyValuesCriteriaSpec.builder()
            .matchAnyCondition(ParameterField.createValueField(ConditionalOperator.OR.equals(criteria.getOperator())))
            .conditions(conditions)
            .build();
    criteriaSpecWrapper.setCriteriaSpec(spec);
    return criteriaSpecWrapper;
  }

  private ServiceNowApprovalStepNode buildServiceNowApproval(ApprovalState state, CaseFormat caseFormat) {
    ServiceNowApprovalStepNode stepNode = new ServiceNowApprovalStepNode();
    baseSetup(state, stepNode, caseFormat);
    ServiceNowApprovalParams approvalParams = state.getApprovalStateParams().getServiceNowApprovalParams();
    ServiceNowChangeWindowSpec changeWindow = null;
    if (approvalParams.isChangeWindowPresent()) {
      changeWindow = ServiceNowChangeWindowSpec.builder()
                         .startField(ParameterField.createValueField(approvalParams.getChangeWindowStartField()))
                         .endField(ParameterField.createValueField(approvalParams.getChangeWindowEndField()))
                         .build();
    }
    ServiceNowApprovalStepInfo stepInfo =
        ServiceNowApprovalStepInfo.builder()
            .connectorRef(MigratorUtility.RUNTIME_INPUT)
            .delegateSelectors(null)
            .ticketNumber(ParameterField.createValueField(approvalParams.getIssueNumber()))
            .ticketType(ParameterField.createValueField(approvalParams.getTicketType().getDisplayName()))
            .changeWindow(changeWindow)
            .approvalCriteria(getServiceNowCriteriaSpecWrapper(approvalParams.getApproval()))
            .rejectionCriteria(getServiceNowCriteriaSpecWrapper(approvalParams.getRejection()))
            .build();
    stepNode.setServiceNowApprovalStepInfo(stepInfo);
    return stepNode;
  }

  private CustomApprovalStepNode buildCustomApproval(ApprovalState state, CaseFormat caseFormat) {
    CustomApprovalStepNode stepNode = new CustomApprovalStepNode();
    baseSetup(state, stepNode, caseFormat);

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
            .retryInterval(MigratorUtility.getTimeout(approvalParams.getRetryInterval()))
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
