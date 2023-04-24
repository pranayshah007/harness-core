/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira;

import static io.harness.eraro.ErrorCode.APPROVAL_REJECTION;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ApprovalStepNGException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.jira.JiraApprovalOutcome;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;
import io.harness.steps.approval.step.jira.JiraApprovalStep;
import io.harness.steps.approval.step.jira.beans.JiraApprovalResponseData;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;

import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class JiraApprovalStepTest extends CategoryTest {
  public static final String TICKET_NUMBER = "TICKET_NUMBER";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String INSTANCE_ID = "INSTANCE_ID";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ApprovalInstanceService approvalInstanceService;

  @InjectMocks private JiraApprovalStep jiraApprovalStep;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteAsync() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();
    doAnswer(invocationOnMock -> {
      JiraApprovalInstance instance = invocationOnMock.getArgument(0, JiraApprovalInstance.class);
      instance.setId(INSTANCE_ID);
      return instance;
    })
        .when(approvalInstanceService)
        .save(any());
    assertThat(jiraApprovalStep.executeAsync(ambiance, parameters, null, null).getCallbackIds(0))
        .isEqualTo(INSTANCE_ID);
    ArgumentCaptor<ApprovalInstance> approvalInstanceArgumentCaptor = ArgumentCaptor.forClass(ApprovalInstance.class);
    verify(approvalInstanceService).save(approvalInstanceArgumentCaptor.capture());
    assertThat(approvalInstanceArgumentCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceArgumentCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    JiraApprovalInstance instance = (JiraApprovalInstance) approvalInstanceArgumentCaptor.getValue();
    assertThat(instance.getIssueKey()).isEqualTo(TICKET_NUMBER);
    assertThat(instance.getConnectorRef()).isEqualTo(CONNECTOR);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    JiraApprovalResponseData responseData = JiraApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    JiraApprovalInstance approvalInstance = JiraApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.FAILED);
    approvalInstance.setErrorMessage("error");
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    assertThatThrownBy(
        () -> jiraApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData)))
        .isInstanceOf(ApprovalStepNGException.class)
        .hasMessage("error");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSuccess() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    JiraApprovalResponseData responseData = JiraApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    JiraApprovalInstance approvalInstance = JiraApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.APPROVED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        jiraApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(JiraApprovalOutcome.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseRejected() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    JiraApprovalResponseData responseData = JiraApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    JiraApprovalInstance approvalInstance = JiraApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.REJECTED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        jiraApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.APPROVAL_REJECTED);
    assertThat(response.getFailureInfo().getFailureData(0).getFailureTypesList())
        .containsExactly(FailureType.APPROVAL_REJECTION);
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("Approval Step has been Rejected");
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo(APPROVAL_REJECTION.name());
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(JiraApprovalOutcome.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();
    jiraApprovalStep.handleAbort(ambiance, parameters, null);
    verify(approvalInstanceService).expireByNodeExecutionId(null);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetStepParametersClass() {
    assertThat(jiraApprovalStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  private StepElementParameters getStepElementParameters() {
    return StepElementParameters.builder()
        .type("JIRA_APPROVAL")
        .spec(JiraApprovalSpecParameters.builder()
                  .issueKey(ParameterField.<String>builder().value(TICKET_NUMBER).build())
                  .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                  .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }
}
