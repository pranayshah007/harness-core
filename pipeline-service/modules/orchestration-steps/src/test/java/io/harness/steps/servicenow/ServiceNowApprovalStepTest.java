/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.eraro.ErrorCode.APPROVAL_REJECTION;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
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
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalOutCome;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalSpecParameters;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStep;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServiceNowApprovalStepTest extends CategoryTest {
  public static final String TICKET_NUMBER = "TICKET_NUMBER";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String PROBLEM = "PROBLEM";
  public static final String INSTANCE_ID = "INSTANCE_ID";
  public static final String CHANGE_WINDOW_START = "CHANGE_WINDOW_START";
  public static final String CHANGE_WINDOW_END = "CHANGE_WINDOW_END";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ApprovalInstanceService approvalInstanceService;
  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @InjectMocks private ServiceNowApprovalStep serviceNowApprovalStep;
  private ILogStreamingStepClient logStreamingStepClient;

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteAsync() {
    Ambiance ambiance = buildAmbiance();
    assertThatThrownBy(()
                           -> serviceNowApprovalStep.executeAsync(
                               ambiance, getStepElementParameters("", PROBLEM, CONNECTOR), null, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> serviceNowApprovalStep.executeAsync(
                               ambiance, getStepElementParameters(TICKET_NUMBER, PROBLEM, ""), null, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> serviceNowApprovalStep.executeAsync(
                               ambiance, getStepElementParameters(TICKET_NUMBER, " ", CONNECTOR), null, null))
        .isInstanceOf(InvalidRequestException.class);

    StepElementParameters parameters = getStepElementParameters(TICKET_NUMBER, PROBLEM, CONNECTOR);
    doAnswer(invocationOnMock -> {
      ServiceNowApprovalInstance instance = invocationOnMock.getArgument(0, ServiceNowApprovalInstance.class);
      instance.setId(INSTANCE_ID);
      return instance;
    })
        .when(approvalInstanceService)
        .save(any());
    assertThat(serviceNowApprovalStep.executeAsync(ambiance, parameters, null, null).getCallbackIds(0))
        .isEqualTo(INSTANCE_ID);
    ArgumentCaptor<ApprovalInstance> approvalInstanceArgumentCaptor = ArgumentCaptor.forClass(ApprovalInstance.class);
    verify(approvalInstanceService).save(approvalInstanceArgumentCaptor.capture());
    assertThat(approvalInstanceArgumentCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceArgumentCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    ServiceNowApprovalInstance instance = (ServiceNowApprovalInstance) approvalInstanceArgumentCaptor.getValue();
    assertThat(instance.getTicketNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(instance.getTicketType()).isEqualTo(PROBLEM);
    assertThat(instance.getConnectorRef()).isEqualTo(CONNECTOR);
    assertThat(instance.getChangeWindow().getStartField()).isEqualTo(CHANGE_WINDOW_START);
    assertThat(instance.getChangeWindow().getEndField()).isEqualTo(CHANGE_WINDOW_END);
    verify(logStreamingStepClient, times(4)).openStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters(TICKET_NUMBER, PROBLEM, CONNECTOR);

    ServiceNowApprovalResponseData responseData =
        ServiceNowApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    ServiceNowApprovalInstance approvalInstance = ServiceNowApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.FAILED);
    approvalInstance.setErrorMessage("error");
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    assertThatThrownBy(()
                           -> serviceNowApprovalStep.handleAsyncResponse(
                               ambiance, parameters, Collections.singletonMap("key", responseData)))
        .isInstanceOf(ApprovalStepNGException.class)
        .hasMessage("error");
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSuccess() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters(TICKET_NUMBER, PROBLEM, CONNECTOR);

    ServiceNowApprovalResponseData responseData =
        ServiceNowApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    ServiceNowApprovalInstance approvalInstance = ServiceNowApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.APPROVED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        serviceNowApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(ServiceNowApprovalOutCome.class);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseRejected() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters(TICKET_NUMBER, PROBLEM, CONNECTOR);

    ServiceNowApprovalResponseData responseData =
        ServiceNowApprovalResponseData.builder().instanceId(INSTANCE_ID).build();
    ServiceNowApprovalInstance approvalInstance = ServiceNowApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.REJECTED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        serviceNowApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.APPROVAL_REJECTED);
    assertThat(response.getFailureInfo().getFailureData(0).getFailureTypesList())
        .containsExactly(FailureType.APPROVAL_REJECTION);
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("Approval Step has been Rejected");
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo(APPROVAL_REJECTION.name());
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(ServiceNowApprovalOutCome.class);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters(TICKET_NUMBER, PROBLEM, CONNECTOR);
    serviceNowApprovalStep.handleAbort(ambiance, parameters, null);
    verify(approvalInstanceService).abortByNodeExecutionId(any());
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetStepParametersClass() {
    assertThat(serviceNowApprovalStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
  private StepElementParameters getStepElementParameters(String ticketNumber, String ticketType, String connector) {
    return StepElementParameters.builder()
        .type("SERVICENOW_APPROVAL")
        .spec(ServiceNowApprovalSpecParameters.builder()
                  .ticketNumber(ParameterField.<String>builder().value(ticketNumber).build())
                  .connectorRef(ParameterField.<String>builder().value(connector).build())
                  .ticketType(ParameterField.<String>builder().value(ticketType).build())
                  .changeWindowSpec(ServiceNowChangeWindowSpec.builder()
                                        .startField(ParameterField.<String>builder().value(CHANGE_WINDOW_START).build())
                                        .endField(ParameterField.<String>builder().value(CHANGE_WINDOW_END).build())
                                        .build())
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
