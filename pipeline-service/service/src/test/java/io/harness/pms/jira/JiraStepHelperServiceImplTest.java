/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.jira;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.jira.JiraIssueNG;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class JiraStepHelperServiceImplTest extends CategoryTest {
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks JiraStepHelperServiceImpl jiraStepHelperService;

  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String CONNECTOR = "connector";
  private static final TaskSelectorYaml TASK_SELECTOR_YAML = getTaskSelectorYaml();
  private static final ParameterField TASK_SELECTOR_YAML_PARAMETER =
      ParameterField.createValueField(List.of(TASK_SELECTOR_YAML));

  private static final List<TaskSelector> TASK_SELECTORS =
      TaskSelectorYaml.toTaskSelector(TASK_SELECTOR_YAML_PARAMETER);

  private static final Ambiance AMBIANCE = Ambiance.newBuilder()
                                               .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT)
                                               .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG)
                                               .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT)
                                               .build();
  private static final ConnectorDTO JIRA_CONNECTOR =
      ConnectorDTO.builder()
          .connectorInfo(ConnectorInfoDTO.builder()
                             .identifier(CONNECTOR)
                             .connectorConfig(JiraConnectorDTO.builder()
                                                  .jiraUrl("url")
                                                  .auth(JiraAuthenticationDTO.builder()
                                                            .credentials(JiraUserNamePasswordDTO.builder().build())
                                                            .build())
                                                  .build())
                             .build())
          .build();

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPrepareTestRequest() {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    MockedStatic<NGTimeConversionHelper> aStatic2 = Mockito.mockStatic(NGTimeConversionHelper.class);
    aStatic2.when(() -> NGTimeConversionHelper.convertTimeStringToMilliseconds(any())).thenReturn(0L);
    Mockito.mockStatic(TaskRequestsUtils.class);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    assertThatCode(()
                       -> jiraStepHelperService.prepareTaskRequest(
                           JiraTaskNGParameters.builder(), ambiance, "null", "time", "task", TASK_SELECTORS))
        .isInstanceOf(InvalidRequestException.class);

    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.empty());
    assertThatCode(()
                       -> jiraStepHelperService.prepareTaskRequest(
                           JiraTaskNGParameters.builder(), ambiance, "connectorref", "time", "task", TASK_SELECTORS))
        .isInstanceOf(InvalidRequestException.class);
    aStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Optional.of(
            ConnectorDTO.builder()
                .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(DockerConnectorDTO.builder().build()).build())
                .build()));
    assertThatCode(()
                       -> jiraStepHelperService.prepareTaskRequest(
                           JiraTaskNGParameters.builder(), ambiance, "connectorref", "time", "task", TASK_SELECTORS))
        .isInstanceOf(InvalidRequestException.class);

    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(getConnector(false));
    jiraStepHelperService.prepareTaskRequest(JiraTaskNGParameters.builder(), ambiance, "connectorref",
        new Timestamp(System.currentTimeMillis()).toString(), "task", TASK_SELECTORS);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    ArgumentCaptor<NGAccess> requestArgumentCaptorForNGAccess = ArgumentCaptor.forClass(NGAccess.class);
    verify(secretManagerClientService)
        .getEncryptionDetails(
            requestArgumentCaptorForNGAccess.capture(), requestArgumentCaptorForSecretService.capture());
    assertTrue(requestArgumentCaptorForSecretService.getValue() instanceof JiraConnectorDTO);

    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(getConnector(true));
    jiraStepHelperService.prepareTaskRequest(JiraTaskNGParameters.builder(), ambiance, "connectorref",
        new Timestamp(System.currentTimeMillis()).toString(), "task", TASK_SELECTORS);
    verify(secretManagerClientService, times(2))
        .getEncryptionDetails(
            requestArgumentCaptorForNGAccess.capture(), requestArgumentCaptorForSecretService.capture());
    assertTrue(requestArgumentCaptorForSecretService.getValue() instanceof JiraAuthCredentialsDTO);
    assertThat(requestArgumentCaptorForNGAccess.getValue()).isEqualTo(AmbianceUtils.getNgAccess(ambiance));
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testPrepareTestRequest_CreateStepDelegateSelectors() throws IOException {
    Call mockCall = mock(Call.class);
    doReturn(mockCall).when(connectorResourceClient).get(any(), any(), any(), any());
    doReturn(mockCall).when(mockCall).clone();

    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(JIRA_CONNECTOR)))).when(mockCall).execute();
    doReturn(List.of())
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), any(JiraUserNamePasswordDTO.class));

    TaskRequest taskRequest = jiraStepHelperService.prepareTaskRequest(
        JiraTaskNGParameters.builder(), AMBIANCE, CONNECTOR, "10m", "task", TASK_SELECTORS);
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getSelector())
        .isEqualTo(TASK_SELECTOR_YAML.getDelegateSelectors());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getOrigin())
        .isEqualTo(TASK_SELECTOR_YAML.getOrigin());
    verify(connectorResourceClient).get(CONNECTOR, ACCOUNT, ORG, PROJECT);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testPrepareTestRequest_UpdateStepDelegateSelectors() throws IOException {
    Call mockCall = mock(Call.class);
    doReturn(mockCall).when(connectorResourceClient).get(any(), any(), any(), any());
    doReturn(mockCall).when(mockCall).clone();

    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(JIRA_CONNECTOR)))).when(mockCall).execute();
    doReturn(List.of())
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), any(JiraUserNamePasswordDTO.class));

    TaskRequest taskRequest = jiraStepHelperService.prepareTaskRequest(
        JiraTaskNGParameters.builder(), AMBIANCE, CONNECTOR, "10m", "task", TASK_SELECTORS);
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getSelector())
        .isEqualTo(TASK_SELECTOR_YAML.getDelegateSelectors());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getOrigin())
        .isEqualTo(TASK_SELECTOR_YAML.getOrigin());
    verify(connectorResourceClient).get(CONNECTOR, ACCOUNT, ORG, PROJECT);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPrepareStepResponse() throws Exception {
    StepResponse stepResponse =
        jiraStepHelperService.prepareStepResponse(() -> JiraTaskNGResponse.builder().issue(new JiraIssueNG()).build());
    assertEquals(stepResponse.getStatus(), Status.SUCCEEDED);
  }

  private Optional<ConnectorDTO> getConnector(boolean updatedYaml) {
    if (updatedYaml) {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder()
              .connectorType(ConnectorType.JIRA)
              .connectorConfig(JiraConnectorDTO.builder()
                                   .jiraUrl("url")
                                   .username("username")
                                   .passwordRef(SecretRefData.builder().build())
                                   .auth(JiraAuthenticationDTO.builder()
                                             .authType(JiraAuthType.USER_PASSWORD)
                                             .credentials(JiraUserNamePasswordDTO.builder()
                                                              .username("username")
                                                              .passwordRef(SecretRefData.builder().build())
                                                              .build())
                                             .build())
                                   .build())
              .build();
      return Optional.of(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build());
    }
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.JIRA)
                                            .connectorConfig(JiraConnectorDTO.builder()
                                                                 .jiraUrl("url")
                                                                 .username("username")
                                                                 .passwordRef(SecretRefData.builder().build())
                                                                 .build())
                                            .build();
    return Optional.of(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build());
  }
  private static TaskSelectorYaml getTaskSelectorYaml() {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml();
    taskSelectorYaml.setOrigin("step");
    taskSelectorYaml.setDelegateSelectors("step-selector");
    return taskSelectorYaml;
  }
}
