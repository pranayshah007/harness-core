/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.exception.ServiceNowException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.ApprovalProgressData;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDC)
public class ServiceNowApprovalHelperServiceImplTest extends CategoryTest {
  @Mock private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private String publisherName = "publisherName";
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;

  @Mock private ApprovalInstanceService approvalInstanceService;
  ServiceNowApprovalHelperService serviceNowApprovalHelperService;
  @Mock ILogStreamingStepClient iLogStreamingStepClient;
  private MockedStatic<NGRestUtils> ngRestUtilsMockedStatic;
  private MockedStatic<TaskRequestsUtils> taskRequestsUtilsMockedStatic;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  @Before
  public void setUp() {
    ngRestUtilsMockedStatic = Mockito.mockStatic(NGRestUtils.class);
    taskRequestsUtilsMockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    serviceNowApprovalHelperService = spy(new ServiceNowApprovalHelperServiceImpl(connectorResourceClient,
        pmsGitSyncHelper, logStreamingStepClientFactory, secretManagerClient, ngDelegate2TaskExecutor, kryoSerializer,
        publisherName, waitNotifyEngine, approvalInstanceService));
  }

  @After
  public void cleanup() {
    ngRestUtilsMockedStatic.close();
    taskRequestsUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandlePollingEvent() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    doReturn(iLogStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);
    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("__TASK_ID__");
    doNothing().when(waitNotifyEngine).progressOn(any(), any());

    ServiceNowApprovalInstance instance = getServiceNowApprovalInstance(ambiance);
    taskRequestsUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Collections.EMPTY_LIST);
    doReturn(ServiceNowConnectorDTO.builder()
                 .username("USERNAME")
                 .serviceNowUrl("url")
                 .passwordRef(SecretRefData.builder().build())
                 .build())
        .when(serviceNowApprovalHelperService)
        .getServiceNowConnector(eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any());
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("task".getBytes());

    ArgumentCaptor<TaskDetails> taskDetailsArgumentCaptor = ArgumentCaptor.forClass(TaskDetails.class);
    when(TaskRequestsUtils.prepareTaskRequest(
             any(), taskDetailsArgumentCaptor.capture(), anyList(), anyList(), any(), anyBoolean()))
        .thenReturn(null);

    serviceNowApprovalHelperService.handlePollingEvent(null, instance);

    ArgumentCaptor<TaskRequest> captor = ArgumentCaptor.forClass(TaskRequest.class);
    verify(ngDelegate2TaskExecutor, times(1)).queueTask(any(), captor.capture(), any());
    assertThat(taskDetailsArgumentCaptor.getValue().getType().getType())
        .isEqualTo(TaskType.SERVICENOW_TASK_NG.toString());
    assertThat(taskDetailsArgumentCaptor.getValue().getKryoParameters()).isNotEmpty();
    ArgumentCaptor<NGAccessWithEncryptionConsumer> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(NGAccessWithEncryptionConsumer.class);
    verify(secretManagerClient).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(
        requestArgumentCaptorForSecretService.getValue().getDecryptableEntity() instanceof ServiceNowConnectorDTO)
        .isTrue();
    verify(waitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(waitNotifyEngine)
        .progressOn("id",
            ApprovalProgressData.builder()
                .latestDelegateTaskId("__TASK_ID__")
                .taskName("ServiceNow Task: Get Ticket")
                .build());

    // since auth object is present, then decrypt-able entity will be ServiceNowAuthCredentialsDTO
    doReturn(ServiceNowConnectorDTO.builder()
                 .username("username")
                 .serviceNowUrl("url")
                 .passwordRef(SecretRefData.builder().build())
                 .auth(ServiceNowAuthenticationDTO.builder()
                           .authType(ServiceNowAuthType.USER_PASSWORD)
                           .credentials(ServiceNowUserNamePasswordDTO.builder()
                                            .username("username")
                                            .passwordRef(SecretRefData.builder().build())
                                            .build())
                           .build())
                 .build())
        .when(serviceNowApprovalHelperService)
        .getServiceNowConnector(eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any());
    serviceNowApprovalHelperService.handlePollingEvent(null, instance);
    verify(secretManagerClient, times(2)).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(
        requestArgumentCaptorForSecretService.getValue().getDecryptableEntity() instanceof ServiceNowAuthCredentialsDTO)
        .isTrue();

    // when progress update fails
    doThrow(new RuntimeException()).when(waitNotifyEngine).progressOn(any(), any());
    assertThatCode(() -> serviceNowApprovalHelperService.handlePollingEvent(null, instance)).doesNotThrowAnyException();
    verify(ngDelegate2TaskExecutor, times(3)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
    verify(waitNotifyEngine, times(3)).waitForAllOn(any(), any(), any());
    verify(waitNotifyEngine, times(3))
        .progressOn("id",
            ApprovalProgressData.builder()
                .latestDelegateTaskId("__TASK_ID__")
                .taskName("ServiceNow Task: Get Ticket")
                .build());

    // when task id is empty, progress update shouldn't be called

    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("  ");
    serviceNowApprovalHelperService.handlePollingEvent(null, instance);
    verify(ngDelegate2TaskExecutor, times(4)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
    verify(waitNotifyEngine, times(4)).waitForAllOn(any(), any(), any());
    verifyNoMoreInteractions(waitNotifyEngine);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetConnector() {
    Optional<ConnectorDTO> connectorDTO = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(ServiceNowConnectorDTO.builder().build()).build())
            .build());
    Optional<ConnectorDTO> connectorDTO1 = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build())
            .build());
    ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(connectorDTO);
    serviceNowApprovalHelperService.getServiceNowConnector(accountId, orgIdentifier, projectIdentifier, "connectorRef");
    when(NGRestUtils.getResponse(any())).thenReturn(connectorDTO1);
    assertThatThrownBy(()
                           -> serviceNowApprovalHelperService.getServiceNowConnector(
                               accountId, orgIdentifier, projectIdentifier, "connectorRef"))
        .isInstanceOf(ServiceNowException.class);
    when(NGRestUtils.getResponse(null)).thenReturn(Optional.empty());
    assertThatThrownBy(()
                           -> serviceNowApprovalHelperService.getServiceNowConnector(
                               accountId, orgIdentifier, projectIdentifier, "connectorRef"))
        .isInstanceOf(ServiceNowException.class);
  }

  private ServiceNowApprovalInstance getServiceNowApprovalInstance(Ambiance ambiance) {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    ServiceNowApprovalInstance instance =
        ServiceNowApprovalInstance.builder()
            .ticketNumber("ticketNumber")
            .ticketType("PROBLEM")
            .delegateSelectors(ParameterField.createValueField(Collections.singletonList(taskSelectorYaml)))
            .connectorRef("connectorRed")
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder().criteriaSpecDTO(KeyValuesCriteriaSpecDTO.builder().build()).build())
            .build();
    instance.setAmbiance(ambiance);
    instance.setId("id");
    instance.setType(ApprovalType.SERVICENOW_APPROVAL);
    return instance;
  }
}
