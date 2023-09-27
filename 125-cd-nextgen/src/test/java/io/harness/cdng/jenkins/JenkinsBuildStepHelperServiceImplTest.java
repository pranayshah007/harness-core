/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildOutcome;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildSpecParameters;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepHelperServiceImpl;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class JenkinsBuildStepHelperServiceImplTest extends CategoryTest {
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks JenkinsBuildStepHelperServiceImpl jenkinsBuildStepHelperService;

  private final Ambiance ambiance =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "accountId")
          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
          .putSetupAbstractions("projectIdentifier", "projectIdentifier")
          .setMetadata(
              ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
          .build();

  private final String connectorRef = "connectorref";
  private final ConnectorDTO jenkinsConnector =
      ConnectorDTO.builder()
          .connectorInfo(ConnectorInfoDTO.builder()
                             .identifier(connectorRef)
                             .connectorConfig(JenkinsConnectorDTO.builder()
                                                  .jenkinsUrl("https://jenkins.com")
                                                  .auth(JenkinsAuthenticationDTO.builder().build())
                                                  .build())
                             .build())
          .build();
  ArtifactTaskExecutionResponse artifactTaskExecutionResponse;
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testPrepareTestRequest() throws IOException {
    Call mockCall = mock(Call.class);
    doReturn(mockCall).when(connectorResourceClient).get(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(jenkinsConnector)))).when(mockCall).execute();
    doReturn(List.of())
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), any(JenkinsAuthCredentialsDTO.class));

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml();
    taskSelectorYaml.setDelegateSelectors("step-selector");
    taskSelectorYaml.setOrigin("step");

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .timeout(ParameterField.createValueField("10m"))
            .spec(JenkinsBuildSpecParameters.builder()
                      .connectorRef(ParameterField.createValueField("connectorref"))
                      .delegateSelectors(ParameterField.createValueField(List.of(taskSelectorYaml)))
                      .build())
            .build();
    TaskChainResponse taskChainResponse = jenkinsBuildStepHelperService.queueJenkinsBuildTask(
        JenkinsArtifactDelegateRequest.builder(), ambiance, stepElementParameters);

    TaskRequest taskRequest = taskChainResponse.getTaskRequest();

    TaskSelector selectors = taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0);
    Assertions.assertThat(selectors.getSelector()).isEqualTo(taskSelectorYaml.getDelegateSelectors());
    Assertions.assertThat(selectors.getOrigin()).isEqualTo(taskSelectorYaml.getOrigin());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPrepareTestRequestError() {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    MockedStatic<NGTimeConversionHelper> aStatic2 = Mockito.mockStatic(NGTimeConversionHelper.class);
    aStatic2.when(() -> NGTimeConversionHelper.convertTimeStringToMilliseconds(any())).thenReturn(0L);
    Mockito.mockStatic(StepUtils.class);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .timeout(ParameterField.createValueField("10m"))
            .spec(JenkinsBuildSpecParameters.builder()
                      .connectorRef(ParameterField.createValueField("connectorref"))
                      .build())
            .build();
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.empty());
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.queueJenkinsBuildTask(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, stepElementParameters))
        .isInstanceOf(InvalidRequestException.class);
    aStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Optional.of(
            ConnectorDTO.builder()
                .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(DockerConnectorDTO.builder().build()).build())
                .build()));
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.queueJenkinsBuildTask(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, stepElementParameters))
        .isInstanceOf(InvalidRequestException.class);
    aStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Optional.of(
            ConnectorDTO.builder()
                .connectorInfo(
                    ConnectorInfoDTO.builder().connectorConfig(JenkinsConnectorDTO.builder().build()).build())
                .build()));
    // when(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl()).thenReturn(any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = mock(ArtifactTaskExecutionResponse.class);
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.queueJenkinsBuildTask(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, stepElementParameters))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPrepareStepResponse() throws Exception {
    StepResponse stepResponse = jenkinsBuildStepHelperService.prepareStepResponse(
        ()
            -> ArtifactTaskResponse.builder()
                   .artifactTaskExecutionResponse(
                       ArtifactTaskExecutionResponse.builder()
                           .jenkinsBuildTaskNGResponse(
                               JenkinsBuildTaskNGResponse.builder()
                                   .jobUrl("https://jenkins.dev.harness.io/job/Automation QA/3578/")
                                   .queuedBuildUrl("https://jenkins.dev.harness.io/job/Automation QA/3578/")
                                   .executionStatus(ExecutionStatus.FAILED)
                                   .build())
                           .build())
                   .build());
    assertEquals(stepResponse.getStatus(), Status.FAILED);
    assertEquals(stepResponse.getStepOutcomes().size(), 1);
    assertThat(stepResponse.getStepOutcomes().stream().findAny().get().getOutcome())
        .isInstanceOf(JenkinsBuildOutcome.class);
    JenkinsBuildOutcome outcome =
        (JenkinsBuildOutcome) stepResponse.getStepOutcomes().stream().findAny().get().getOutcome();
    assertEquals(outcome.getJobUrl(), "https://jenkins.dev.harness.io/job/Automation%20QA/3578/");
    assertEquals(outcome.getQueuedBuildUrl(), "https://jenkins.dev.harness.io/job/Automation%20QA/3578/");

    stepResponse = jenkinsBuildStepHelperService.prepareStepResponse(
        ()
            -> ArtifactTaskResponse.builder()
                   .artifactTaskExecutionResponse(
                       ArtifactTaskExecutionResponse.builder()
                           .jenkinsBuildTaskNGResponse(
                               JenkinsBuildTaskNGResponse.builder()
                                   .jobUrl("https://jenkins.dev.harness.io/job/AutomationQA/3578/")
                                   .queuedBuildUrl("https://jenkins.dev.harness.io/job/AutomationQA/3578/")
                                   .executionStatus(ExecutionStatus.SUCCESS)
                                   .build())
                           .build())
                   .build());
    assertEquals(stepResponse.getStatus(), Status.SUCCEEDED);
    assertEquals(stepResponse.getStepOutcomes().size(), 1);
    assertThat(stepResponse.getStepOutcomes().stream().findAny().get().getOutcome())
        .isInstanceOf(JenkinsBuildOutcome.class);
    outcome = (JenkinsBuildOutcome) stepResponse.getStepOutcomes().stream().findAny().get().getOutcome();
    assertEquals(outcome.getJobUrl(), "https://jenkins.dev.harness.io/job/AutomationQA/3578/");
    assertEquals(outcome.getQueuedBuildUrl(), "https://jenkins.dev.harness.io/job/AutomationQA/3578/");
  }
}
