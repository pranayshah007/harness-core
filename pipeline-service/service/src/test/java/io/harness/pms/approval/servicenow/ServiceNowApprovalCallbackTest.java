/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.servicenow;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.ServiceNowException;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.misc.TicketNG;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.servicenow.evaluation.ServiceNowCriteriaEvaluator;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class ServiceNowApprovalCallbackTest extends CategoryTest {
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  private static String approvalInstanceId = "approvalInstanceId";
  private static String issueKey = "issueKey";
  @Mock ILogStreamingStepClient iLogStreamingStepClient;
  @Mock NGErrorHelper ngErrorHelper;
  @InjectMocks private ServiceNowApprovalCallback serviceNowApprovalCallback;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  @Before
  public void setUp() {
    serviceNowApprovalCallback = spy(ServiceNowApprovalCallback.builder().build());
    on(serviceNowApprovalCallback).set("approvalInstanceService", approvalInstanceService);
    on(serviceNowApprovalCallback).set("logStreamingStepClientFactory", logStreamingStepClientFactory);
    on(serviceNowApprovalCallback).set("kryoSerializer", kryoSerializer);
    on(serviceNowApprovalCallback).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    on(serviceNowApprovalCallback).set("ngErrorHelper", ngErrorHelper);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testPush() {
    try (MockedStatic<ServiceNowCriteriaEvaluator> mockStatic = Mockito.mockStatic(ServiceNowCriteriaEvaluator.class)) {
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(true);
      on(serviceNowApprovalCallback).set("approvalInstanceId", approvalInstanceId);
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .build();
      ServiceNowApprovalInstance instance = getServiceNowApprovalInstance(ambiance);
      Map<String, ResponseData> response = new HashMap<>();
      response.put("data", BinaryResponseData.builder().build());
      Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();
      fields.put("dummyField", ServiceNowFieldValueNG.builder().build());
      doReturn(ServiceNowTaskNGResponse.builder().ticket(ServiceNowTicketNG.builder().fields(fields).build()).build())
          .when(kryoSerializer)
          .asInflatedObject(any());
      doReturn(iLogStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
      doReturn(instance).when(approvalInstanceService).get(approvalInstanceId);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(1))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.EXPIRED), nullable(TicketNG.class));
      verify(approvalInstanceService, times(1))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.APPROVED), nullable(TicketNG.class));

      instance.setDeadline(Long.MAX_VALUE);

      // testing the case when approval criteria met and change window criteria met
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(true);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(2))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.APPROVED), nullable(TicketNG.class));

      // testing the case when approval criteria met and change window criteria not met
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(false);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(0)).finalizeStatus((String) any(), (ApprovalStatus) any(), (String) any());

      when(ngErrorHelper.getErrorSummary(any()))
          .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(false);
      serviceNowApprovalCallback.push(response);

      JexlCriteriaSpecDTO rejectionCriteria = JexlCriteriaSpecDTO.builder().build();
      instance.setRejectionCriteria(CriteriaSpecWrapperDTO.builder().criteriaSpecDTO(rejectionCriteria).build());
      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), eq(rejectionCriteria))).thenReturn(true);
      serviceNowApprovalCallback.push(response);
      instance.getApprovalCriteria().setCriteriaSpecDTO(JexlCriteriaSpecDTO.builder().expression("1==2").build());
      rejectionCriteria.setExpression("b==b");
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService)
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.REJECTED), nullable(TicketNG.class));

      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any()))
          .thenThrow(new ApprovalStepNGException("error", true));
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService)
          .finalizeStatus(approvalInstanceId, ApprovalStatus.FAILED,
              "Fatal error evaluating approval/rejection/change window criteria: error");

      // Testing the case when approval criteria not available
      instance.setApprovalCriteria(null);
      assertThatThrownBy(() -> serviceNowApprovalCallback.push(response)).isInstanceOf(ServiceNowException.class);

      doReturn(ServiceNowTaskNGResponse.builder().build()).when(kryoSerializer).asInflatedObject(any());
      serviceNowApprovalCallback.push(response);

      // To test case of error in kryo serialization; also validate that in such case log callback
      // is called with a non-terminal command execution status
      doReturn(ErrorNotifyResponseData.builder().build()).when(kryoSerializer).asInflatedObject(any());

      try (MockedConstruction<NGLogCallback> mocked = mockConstruction(NGLogCallback.class)) {
        serviceNowApprovalCallback.push(response);
        NGLogCallback logCallback = mocked.constructed().get(0);
        verify(logCallback).saveExecutionLog(any(), eq(LogLevel.ERROR));
      }

      // To throw exception while casting the response to ResponseData and catch the exception
      doReturn(null).when(kryoSerializer).asInflatedObject(any());
      serviceNowApprovalCallback.push(response);
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPushUsingKryoWithoutReference() {
    try (MockedStatic<ServiceNowCriteriaEvaluator> mockStatic = Mockito.mockStatic(ServiceNowCriteriaEvaluator.class)) {
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(true);
      on(serviceNowApprovalCallback).set("approvalInstanceId", approvalInstanceId);
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .build();
      ServiceNowApprovalInstance instance = getServiceNowApprovalInstance(ambiance);
      Map<String, ResponseData> response = new HashMap<>();

      Map<String, ServiceNowFieldValueNG> fields = new HashMap<>();
      fields.put("dummyField", ServiceNowFieldValueNG.builder().build());
      response.put("data", BinaryResponseData.builder().usingKryoWithoutReference(true).build());
      doReturn(ServiceNowTaskNGResponse.builder().ticket(ServiceNowTicketNG.builder().fields(fields).build()).build())
          .when(referenceFalseKryoSerializer)
          .asInflatedObject(any());

      doReturn(iLogStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
      doReturn(instance).when(approvalInstanceService).get(approvalInstanceId);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(1))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.EXPIRED), nullable(TicketNG.class));
      verify(approvalInstanceService, times(1))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.APPROVED), nullable(TicketNG.class));

      instance.setDeadline(Long.MAX_VALUE);

      // testing the case when approval criteria met and change window criteria met
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(true);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(2))
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.APPROVED), nullable(TicketNG.class));

      // testing the case when approval criteria met and change window criteria not met
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(true);
      mockStatic.when(() -> ServiceNowCriteriaEvaluator.validateWithinChangeWindow(any(), any(), any()))
          .thenReturn(false);
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService, times(0)).finalizeStatus((String) any(), (ApprovalStatus) any(), (String) any());

      when(ngErrorHelper.getErrorSummary(any()))
          .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any())).thenReturn(false);
      serviceNowApprovalCallback.push(response);

      JexlCriteriaSpecDTO rejectionCriteria = JexlCriteriaSpecDTO.builder().build();
      instance.setRejectionCriteria(CriteriaSpecWrapperDTO.builder().criteriaSpecDTO(rejectionCriteria).build());
      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), eq(rejectionCriteria))).thenReturn(true);
      serviceNowApprovalCallback.push(response);
      instance.getApprovalCriteria().setCriteriaSpecDTO(JexlCriteriaSpecDTO.builder().expression("1==2").build());
      rejectionCriteria.setExpression("b==b");
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService)
          .finalizeStatus(eq(approvalInstanceId), eq(ApprovalStatus.REJECTED), nullable(TicketNG.class));

      when(ServiceNowCriteriaEvaluator.evaluateCriteria(any(), any()))
          .thenThrow(new ApprovalStepNGException("error", true));
      serviceNowApprovalCallback.push(response);
      verify(approvalInstanceService)
          .finalizeStatus(approvalInstanceId, ApprovalStatus.FAILED,
              "Fatal error evaluating approval/rejection/change window criteria: error");

      // Testing the case when approval criteria not available
      instance.setApprovalCriteria(null);
      assertThatThrownBy(() -> serviceNowApprovalCallback.push(response)).isInstanceOf(ServiceNowException.class);

      doReturn(ServiceNowTaskNGResponse.builder().build()).when(referenceFalseKryoSerializer).asInflatedObject(any());
      serviceNowApprovalCallback.push(response);
      // To test case of error in kryo serialization
      doReturn(ErrorNotifyResponseData.builder().build()).when(referenceFalseKryoSerializer).asInflatedObject(any());
      serviceNowApprovalCallback.push(response);
      // To throw exception while casting the response to ResponseData and catch the exception
      doReturn(null).when(referenceFalseKryoSerializer).asInflatedObject(any());
      serviceNowApprovalCallback.push(response);

      // Case when issue key is invalid
      instance.setDeadline(Long.MAX_VALUE);
      doReturn(ServiceNowTaskNGResponse.builder().ticket(ServiceNowTicketNG.builder().build()).build())
          .when(referenceFalseKryoSerializer)
          .asInflatedObject(any());
      try (MockedConstruction<NGLogCallback> mocked = mockConstruction(NGLogCallback.class)) {
        serviceNowApprovalCallback.push(response);
        NGLogCallback logCallback = mocked.constructed().get(0);
        verify(logCallback)
            .saveExecutionLog(
                LogHelper.color(String.format("Failed to fetch ticket. Ticket number might be invalid: %s", issueKey),
                    LogColor.Red),
                LogLevel.ERROR);
      }
    }
  }

  private ServiceNowApprovalInstance getServiceNowApprovalInstance(Ambiance ambiance) {
    ServiceNowApprovalInstance instance =
        ServiceNowApprovalInstance.builder()
            .approvalCriteria(CriteriaSpecWrapperDTO.builder()
                                  .criteriaSpecDTO(JexlCriteriaSpecDTO.builder().expression("1==1").build())
                                  .build())
            .build();
    instance.setAmbiance(ambiance);
    instance.setType(ApprovalType.SERVICENOW_APPROVAL);
    instance.setId(approvalInstanceId);
    instance.setTicketNumber(issueKey);
    return instance;
  }
}
