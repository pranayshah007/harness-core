/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.jira;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.exception.HarnessJiraException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.ApprovalProgressData;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.waiter.WaitNotifyEngine;

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

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class JiraApprovalHelperServiceImplTest extends CategoryTest {
  @Mock private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private String publisherName = "publisherName";
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  JiraApprovalHelperServiceImpl jiraApprovalHelperService;
  @Mock ILogStreamingStepClient iLogStreamingStepClient;
  private MockedStatic<NGRestUtils> aStatic;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  @Before
  public void setUp() {
    aStatic = Mockito.mockStatic(NGRestUtils.class);
    jiraApprovalHelperService = spy(new JiraApprovalHelperServiceImpl(ngDelegate2TaskExecutor, connectorResourceClient,
        kryoSerializer, secretManagerClient, waitNotifyEngine, logStreamingStepClientFactory, publisherName,
        pmsGitSyncHelper, null, approvalInstanceService));
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = BRIJESH)
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

    JiraApprovalInstance instance = getJiraApprovalInstance(ambiance);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Collections.EMPTY_LIST);
    doReturn(JiraConnectorDTO.builder()
                 .username("USERNAME")
                 .jiraUrl("url")
                 .passwordRef(SecretRefData.builder().build())
                 .build())
        .when(jiraApprovalHelperService)
        .getJiraConnector(eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any());
    ArgumentCaptor<NGAccessWithEncryptionConsumer> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(NGAccessWithEncryptionConsumer.class);
    jiraApprovalHelperService.handlePollingEvent(null, instance);

    verify(ngDelegate2TaskExecutor, times(1)).queueTask(any(), any(), any());
    verify(secretManagerClient).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertTrue(requestArgumentCaptorForSecretService.getValue().getDecryptableEntity() instanceof JiraConnectorDTO);
    verify(waitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(waitNotifyEngine)
        .progressOn("id",
            ApprovalProgressData.builder()
                .latestDelegateTaskId("__TASK_ID__")
                .taskName("Jira Task: Get Issue")
                .build());

    // since auth object is present, then decrypt-able entity will be JiraAuthCredentialsDTO
    doReturn(JiraConnectorDTO.builder()
                 .username("username")
                 .jiraUrl("url")
                 .passwordRef(SecretRefData.builder().build())
                 .auth(JiraAuthenticationDTO.builder()
                           .authType(JiraAuthType.USER_PASSWORD)
                           .credentials(JiraUserNamePasswordDTO.builder()
                                            .username("username")
                                            .passwordRef(SecretRefData.builder().build())
                                            .build())
                           .build())
                 .build())
        .when(jiraApprovalHelperService)
        .getJiraConnector(eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any());
    jiraApprovalHelperService.handlePollingEvent(null, instance);
    verify(secretManagerClient, times(2)).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertTrue(
        requestArgumentCaptorForSecretService.getValue().getDecryptableEntity() instanceof JiraAuthCredentialsDTO);

    // when progress update fails
    doThrow(new RuntimeException()).when(waitNotifyEngine).progressOn(any(), any());
    assertThatCode(() -> jiraApprovalHelperService.handlePollingEvent(null, instance)).doesNotThrowAnyException();
    verify(ngDelegate2TaskExecutor, times(3)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
    verify(waitNotifyEngine, times(3)).waitForAllOn(any(), any(), any());
    verify(waitNotifyEngine, times(3))
        .progressOn("id",
            ApprovalProgressData.builder()
                .latestDelegateTaskId("__TASK_ID__")
                .taskName("Jira Task: Get Issue")
                .build());

    // when task id is empty, progress update shouldn't be called

    when(ngDelegate2TaskExecutor.queueTask(any(), any(), eq(Duration.ofSeconds(0)))).thenReturn("  ");
    jiraApprovalHelperService.handlePollingEvent(null, instance);
    verify(ngDelegate2TaskExecutor, times(4)).queueTask(any(), any(), eq(Duration.ofSeconds(0)));
    verify(waitNotifyEngine, times(4)).waitForAllOn(any(), any(), any());
    verifyNoMoreInteractions(waitNotifyEngine);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetConnector() {
    Optional<ConnectorDTO> connectorDTO = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(JiraConnectorDTO.builder().build()).build())
            .build());
    Optional<ConnectorDTO> connectorDTO1 = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build())
            .build());
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(connectorDTO);
    jiraApprovalHelperService.getJiraConnector(accountId, orgIdentifier, projectIdentifier, "connectorRef");
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(connectorDTO1);
    assertThatThrownBy(
        () -> jiraApprovalHelperService.getJiraConnector(accountId, orgIdentifier, projectIdentifier, "connectorRef"))
        .isInstanceOf(HarnessJiraException.class);
    aStatic.when(() -> NGRestUtils.getResponse(null)).thenReturn(Optional.empty());
    assertThatThrownBy(
        () -> jiraApprovalHelperService.getJiraConnector(accountId, orgIdentifier, projectIdentifier, "connectorRef"))
        .isInstanceOf(HarnessJiraException.class);
  }

  private JiraApprovalInstance getJiraApprovalInstance(Ambiance ambiance) {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    JiraApprovalInstance instance =
        JiraApprovalInstance.builder()
            .issueKey("issueKey")
            .delegateSelectors(ParameterField.createValueField(Collections.singletonList(taskSelectorYaml)))
            .connectorRef("connectorRed")
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder().criteriaSpecDTO(KeyValuesCriteriaSpecDTO.builder().build()).build())
            .build();
    instance.setAmbiance(ambiance);
    instance.setId("id");
    instance.setType(ApprovalType.JIRA_APPROVAL);
    return instance;
  }
}
