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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.exception.ServiceNowException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@OwnedBy(CDC)
@PrepareForTest({NGRestUtils.class, StepUtils.class})
public class ServiceNowApprovalHelperServiceImplTest extends CategoryTest {
  @Mock private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private String publisherName = "publisherName";
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  ServiceNowApprovalHelperService serviceNowApprovalHelperService;
  @Mock ILogStreamingStepClient iLogStreamingStepClient;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  @Before
  public void setUp() {
    serviceNowApprovalHelperService = spy(new ServiceNowApprovalHelperServiceImpl(connectorResourceClient,
        pmsGitSyncHelper, logStreamingStepClientFactory, secretManagerClient, ngDelegate2TaskExecutor, kryoSerializer,
        publisherName, waitNotifyEngine));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandlePollingEvent() {
    MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class);
    Mockito.mockStatic(StepUtils.class);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    doReturn(iLogStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(ambiance);

    ServiceNowApprovalInstance instance = getServiceNowApprovalInstance(ambiance);
    mockStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Collections.EMPTY_LIST);
    doReturn(ServiceNowConnectorDTO.builder().build())
        .when(serviceNowApprovalHelperService)
        .getServiceNowConnector(eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any());
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("task".getBytes());

    ArgumentCaptor<TaskDetails> taskDetailsArgumentCaptor = ArgumentCaptor.forClass(TaskDetails.class);
    when(StepUtils.prepareTaskRequest(
             any(), taskDetailsArgumentCaptor.capture(), anyList(), anyList(), any(), anyBoolean()))
        .thenReturn(null);

    serviceNowApprovalHelperService.handlePollingEvent(instance);

    ArgumentCaptor<TaskRequest> captor = ArgumentCaptor.forClass(TaskRequest.class);
    verify(ngDelegate2TaskExecutor, times(1)).queueTask(any(), captor.capture(), any());
    assertThat(taskDetailsArgumentCaptor.getValue().getType().getType())
        .isEqualTo(TaskType.SERVICENOW_TASK_NG.toString());
    assertThat(taskDetailsArgumentCaptor.getValue().getKryoParameters()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetConnector() {
    MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class);

    Optional<ConnectorDTO> connectorDTO = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(ServiceNowConnectorDTO.builder().build()).build())
            .build());
    Optional<ConnectorDTO> connectorDTO1 = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build())
            .build());
    mockStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(connectorDTO);
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
