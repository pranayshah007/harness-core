/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.S3UrlStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationCreateStackPassThroughData;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationInheritOutput;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;
import software.wings.sm.states.provision.S3UriParser;

import com.amazonaws.services.s3.AmazonS3URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({StepUtils.class})
@RunWith(MockitoJUnitRunner.class)
public class CloudformationStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private S3UriParser s3UriParser;
  @Mock private StepHelper stepHelper;
  @Mock private CloudformationStepExecutor cloudformationStepExecutor;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private final CloudformationStepHelper cloudformationStepHelper = new CloudformationStepHelper();
  private static final String TAGS = "[\n"
      + "  {\n"
      + "    \"tag\": \"tag1\",\n"
      + "    \"tag2\": \"tag3\"\n"
      + "  }\n"
      + "]"
      + "";
  private static final String PARAMETERS_FILE_CONTENT = "[\n"
      + "  {\n"
      + "    \"ParameterKey\": \"AlarmEMail\",\n"
      + "    \"ParameterValue\": \"nasser.gonzalez@harness.io\"\n"
      + "  }\n"
      + "]";

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRenderValue() {
    Ambiance ambiance = getAmbiance();
    String expression = "expression";
    cloudformationStepHelper.renderValue(ambiance, expression);
    verify(engineExpressionService).renderExpression(ambiance, expression);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithoutAwsConnector() {
    StepElementParameters stepElementParameters = createStepParametersWithGit(false);
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(AppDynamicsConnectorDTO.builder().build()).build();
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);
  }

  /*
  File templates stored in git

   */
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGitAndNoTags() {
    StepElementParameters stepElementParameters = createStepParametersWithGit(false);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build();
    ConnectorInfoDTO gitConnectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build())
            .build();
    doReturn(awsConnectorDTO).doReturn(gitConnectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    MockedStatic mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskChainResponse response =
        cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    mockedStatic.close();
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(gitFetchRequest).isExactlyInstanceOf(GitFetchRequest.class);
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(3);
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(0).getIdentifier()).isEqualTo("test-identifier");
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(1).getIdentifier()).isEqualTo("test-identifier2");
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(2).getIdentifier()).isEqualTo("templateFile");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.GIT_FETCH_NEXT_GEN_TASK.name());
    assertThat(response.getTaskRequest()).isNotNull();
    assertThat(response.getPassThroughData()).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGitAnTags() {
    StepElementParameters stepElementParameters = createStepParametersWithGit(true);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build();
    ConnectorInfoDTO gitConnectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(GitConfigDTO.builder().gitConnectionType(GitConnectionType.REPO).build())
            .build();
    doReturn(awsConnectorDTO).doReturn(gitConnectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();

    doReturn(sshKeySpecDTO).when(gitConfigAuthenticationInfoHelper).getSSHKey(any(), any(), any(), any());
    List<EncryptedDataDetail> apiEncryptedDataDetails = new ArrayList<>();
    doReturn(apiEncryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    MockedStatic mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskChainResponse response =
        cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    mockedStatic.close();
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(gitFetchRequest).isExactlyInstanceOf(GitFetchRequest.class);
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(4);
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(0).getIdentifier()).isEqualTo("test-identifier");
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(1).getIdentifier()).isEqualTo("test-identifier2");
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(2).getIdentifier()).isEqualTo("templateFile");
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().get(3).getIdentifier()).isEqualTo("tagsFile");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.GIT_FETCH_NEXT_GEN_TASK.name());
    assertThat(response.getTaskRequest()).isNotNull();
    assertThat(response.getPassThroughData()).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithS3AndNoTags() {
    StepElementParameters stepElementParameters = createStepParametersWithS3(false);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(new ArrayList<>()).when(secretManagerClientService).getEncryptionDetails(any(), any());
    AmazonS3URI s3URI = new AmazonS3URI("s3://bucket/key");
    doReturn(s3URI).when(s3UriParser).parseUrl(anyString());
    MockedStatic mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskChainResponse response =
        cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    mockedStatic.close();
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams =
        (AwsS3FetchFilesTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(awsS3FetchFilesTaskParams).isExactlyInstanceOf(AwsS3FetchFilesTaskParams.class);
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().size()).isEqualTo(2);
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(0).getIdentifier())
        .isEqualTo("test-parameters");
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(1).getIdentifier())
        .isEqualTo("test-parameters2");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.FETCH_S3_FILE_TASK_NG.name());
    assertThat(response.getTaskRequest()).isNotNull();
    assertThat(response.getPassThroughData()).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithS3AndTags() {
    StepElementParameters stepElementParameters = createStepParametersWithS3(true);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(new ArrayList<>()).when(secretManagerClientService).getEncryptionDetails(any(), any());
    AmazonS3URI s3URI = new AmazonS3URI("s3://bucket/key");
    doReturn(s3URI).when(s3UriParser).parseUrl(anyString());
    MockedStatic mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskChainResponse response =
        cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    mockedStatic.close();
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams =
        (AwsS3FetchFilesTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(awsS3FetchFilesTaskParams).isExactlyInstanceOf(AwsS3FetchFilesTaskParams.class);
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().size()).isEqualTo(3);
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(0).getIdentifier()).isEqualTo("tagsFile");
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(1).getIdentifier())
        .isEqualTo("test-parameters");
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(2).getIdentifier())
        .isEqualTo("test-parameters2");

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TaskType.FETCH_S3_FILE_TASK_NG.name());
    assertThat(response.getTaskRequest()).isNotNull();
    assertThat(response.getPassThroughData()).isNotNull();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithInlineAndNoTags() {
    StepElementParameters stepElementParameters = createStepParameterInline(false);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());

    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptor =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);
    cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    doReturn("test-template").when(engineExpressionService).renderExpression(any(), eq("test-template"));
    verify(cloudformationStepExecutor).executeCloudformationTask(any(), any(), taskDataArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptor.getValue().getStackName()).isEqualTo("stack-name");
    reset(cloudformationStepExecutor);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithInlineAndTags() {
    StepElementParameters stepElementParameters = createStepParameterInline(true);
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());

    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptor =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);
    cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    doReturn("test-template").when(engineExpressionService).renderExpression(any(), eq("test-template"));
    verify(cloudformationStepExecutor).executeCloudformationTask(any(), any(), taskDataArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptor.getValue().getStackName()).isEqualTo("stack-name");
    reset(cloudformationStepExecutor);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testStartChainLinkWithS3Template() {
    StepElementParameters stepElementParameters = createStepParameterS3WithNoParameterFiles();
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());

    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptor =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);
    cloudformationStepHelper.startChainLink(cloudformationStepExecutor, getAmbiance(), stepElementParameters);

    verify(cloudformationStepExecutor).executeCloudformationTask(any(), any(), taskDataArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTemplateUrl()).isEqualTo("test-url");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptor.getValue().getStackName()).isEqualTo("stack-name");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkGitNoS3() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    StepElementParameters stepElementParameters = createStepParametersWithGit(false);
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .hasGitFiles(true)
                                                                   .hasS3Files(false)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();

    Map<String, FetchFilesResult> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        FetchFilesResult.builder()
            .files(Collections.singletonList(
                GitFile.builder().fileContent(PARAMETERS_FILE_CONTENT).filePath("file-path").build()))
            .build());
    filesFromMultiRepo.put("tagsFile",
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent(TAGS).filePath("file-path").build()))
            .build());
    filesFromMultiRepo.put("templateFile",
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent("foobar").filePath("file-path").build()))
            .build());
    GitFetchResponse response = GitFetchResponse.builder().filesFromMultipleRepo(filesFromMultiRepo).build();
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptor =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn("foobar").when(engineExpressionService).renderExpression(any(), eq("foobar"));
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParameters, passThroughData, () -> response);

    verify(cloudformationStepExecutor).executeCloudformationTask(any(), any(), taskDataArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTemplateBody()).isEqualTo("foobar");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptor.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptor.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
    assertThat(taskDataArgumentCaptor.getValue().getTags()).isEqualTo(TAGS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkGitNoS3NoTemplate() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .hasGitFiles(true)
                                                                   .hasS3Files(false)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();
    Map<String, FetchFilesResult> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        FetchFilesResult.builder()
            .files(Collections.singletonList(
                GitFile.builder().fileContent(PARAMETERS_FILE_CONTENT).filePath("file-path").build()))
            .build());
    filesFromMultiRepo.put("tagsFile",
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent(TAGS).filePath("file-path").build()))
            .build());
    GitFetchResponse response = GitFetchResponse.builder().filesFromMultipleRepo(filesFromMultiRepo).build();

    // Test now the same scenario but with the template been Inline
    StepElementParameters stepElementParametersInline = createStepParameterInline(false);
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptorInline =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    doReturn("test-template").when(engineExpressionService).renderExpression(any(), eq("test-template"));
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParametersInline, passThroughData, () -> response);

    verify(cloudformationStepExecutor, times(1))
        .executeCloudformationTask(any(), any(), taskDataArgumentCaptorInline.capture(), any());
    assertThat(taskDataArgumentCaptorInline.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptorInline.getValue().getTemplateBody()).isEqualTo("test-template");
    assertThat(taskDataArgumentCaptorInline.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptorInline.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptorInline.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptorInline.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
    assertThat(taskDataArgumentCaptorInline.getValue().getTags()).isEqualTo(TAGS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkGitNoS3TemplateInS3() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .hasGitFiles(true)
                                                                   .hasS3Files(false)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();
    Map<String, FetchFilesResult> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        FetchFilesResult.builder()
            .files(Collections.singletonList(
                GitFile.builder().fileContent(PARAMETERS_FILE_CONTENT).filePath("file-path").build()))
            .build());
    filesFromMultiRepo.put("tagsFile",
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent(TAGS).filePath("file-path").build()))
            .build());
    GitFetchResponse response = GitFetchResponse.builder().filesFromMultipleRepo(filesFromMultiRepo).build();
    StepElementParameters stepElementParametersS3Url = createStepParameterS3WithNoParameterFiles();

    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptorS3Url =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParametersS3Url, passThroughData, () -> response);

    verify(cloudformationStepExecutor, times(1))
        .executeCloudformationTask(any(), any(), taskDataArgumentCaptorS3Url.capture(), any());
    assertThat(taskDataArgumentCaptorS3Url.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptorS3Url.getValue().getTemplateUrl()).isEqualTo("test-url");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptorS3Url.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkS3TemplatesInGit() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    StepElementParameters stepElementParameters = createStepParametersWithS3(false);
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .templateBody("template-from-git")
                                                                   .hasGitFiles(false)
                                                                   .hasS3Files(true)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();
    Map<String, List<S3FileDetailResponse>> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        Collections.singletonList(S3FileDetailResponse.builder().fileContent(PARAMETERS_FILE_CONTENT).build()));
    filesFromMultiRepo.put(
        "tagsFile", Collections.singletonList(S3FileDetailResponse.builder().fileContent(TAGS).build()));
    AwsS3FetchFilesResponse response = AwsS3FetchFilesResponse.builder().s3filesDetails(filesFromMultiRepo).build();
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptor =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    doReturn("template-from-git").when(engineExpressionService).renderExpression(any(), eq("template-from-git"));
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParameters, passThroughData, () -> response);

    verify(cloudformationStepExecutor).executeCloudformationTask(any(), any(), taskDataArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTemplateBody()).isEqualTo("template-from-git");
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptor.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptor.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
    assertThat(taskDataArgumentCaptor.getValue().getTags()).isEqualTo(TAGS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkS3NoTemplatesInGit() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .templateBody("template-from-git")
                                                                   .hasGitFiles(false)
                                                                   .hasS3Files(true)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();
    Map<String, List<S3FileDetailResponse>> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        Collections.singletonList(S3FileDetailResponse.builder().fileContent(PARAMETERS_FILE_CONTENT).build()));
    filesFromMultiRepo.put(
        "tagsFile", Collections.singletonList(S3FileDetailResponse.builder().fileContent(TAGS).build()));
    AwsS3FetchFilesResponse response = AwsS3FetchFilesResponse.builder().s3filesDetails(filesFromMultiRepo).build();
    StepElementParameters stepElementParametersInline = createStepParameterInline(false);
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptorInline =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    doReturn("template-from-git").when(engineExpressionService).renderExpression(any(), eq("template-from-git"));
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParametersInline, passThroughData, () -> response);

    verify(cloudformationStepExecutor, times(1))
        .executeCloudformationTask(any(), any(), taskDataArgumentCaptorInline.capture(), any());
    assertThat(taskDataArgumentCaptorInline.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptorInline.getValue().getTemplateBody()).isEqualTo("template-from-git");
    assertThat(taskDataArgumentCaptorInline.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptorInline.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptorInline.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptorInline.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
    assertThat(taskDataArgumentCaptorInline.getValue().getTags()).isEqualTo(TAGS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void executeNextLinkS3TemplatesInS3URL() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    Map<String, List<S3FileDetailResponse>> filesFromMultiRepo = new HashMap<>();
    filesFromMultiRepo.put("param1",
        Collections.singletonList(S3FileDetailResponse.builder().fileContent(PARAMETERS_FILE_CONTENT).build()));
    filesFromMultiRepo.put(
        "tagsFile", Collections.singletonList(S3FileDetailResponse.builder().fileContent(TAGS).build()));
    AwsS3FetchFilesResponse response = AwsS3FetchFilesResponse.builder().s3filesDetails(filesFromMultiRepo).build();
    CloudFormationCreateStackPassThroughData passThroughDataWithoutTemplate =
        CloudFormationCreateStackPassThroughData.builder()
            .hasGitFiles(false)
            .hasS3Files(true)
            .parametersFilesContent(parameters)
            .build();
    StepElementParameters stepElementParametersS3Url = createStepParameterS3WithNoParameterFiles();
    ArgumentCaptor<CloudformationTaskNGParameters> taskDataArgumentCaptorS3Url =
        ArgumentCaptor.forClass(CloudformationTaskNGParameters.class);

    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(PARAMETERS_FILE_CONTENT)
        .when(engineExpressionService)
        .renderExpression(any(), eq(PARAMETERS_FILE_CONTENT));
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    cloudformationStepHelper.executeNextLink(cloudformationStepExecutor, getAmbiance(), stepElementParametersS3Url,
        passThroughDataWithoutTemplate, () -> response);

    verify(cloudformationStepExecutor, times(1))
        .executeCloudformationTask(any(), any(), taskDataArgumentCaptorS3Url.capture(), any());

    assertThat(taskDataArgumentCaptorS3Url.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptorS3Url.getValue().getTemplateUrl()).isEqualTo("test-url");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getTaskType()).isEqualTo(CloudformationTaskType.CREATE_STACK);
    assertThat(taskDataArgumentCaptorS3Url.getValue().getRegion()).isEqualTo("region");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getParameters().get("AlarmEMail"))
        .isEqualTo("nasser.gonzalez@harness.io");
    assertThat(taskDataArgumentCaptorS3Url.getValue().getTags()).isEqualTo(TAGS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  // This test is going to test the executeNextLink with the param files been stored in an unsupported store
  public void executeNextLinkWithoutS3OrGit() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());

    StepElementParameters stepElementParameters = createStepParameterInline(false);

    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();

    parameters.put("param1", null);

    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .hasGitFiles(false)
                                                                   .hasS3Files(true)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder().build();

    TaskChainResponse response = cloudformationStepHelper.executeNextLink(
        cloudformationStepExecutor, getAmbiance(), stepElementParameters, passThroughData, () -> artifactTaskResponse);
    assertThat(response).isNotNull();
    assertThat(response.isChainEnd()).isTrue();
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  // This test is going to test the executeNextLink with exceptions scenarios
  public void executeNextLinkWithExceptions() throws Exception {
    ConnectorInfoDTO awsConnectorDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
                    .build())
            .build();
    StepElementParameters stepElementParameters = createStepParametersWithS3(false);
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("param1", null);
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .hasGitFiles(false)
                                                                   .hasS3Files(true)
                                                                   .parametersFilesContent(parameters)
                                                                   .build();
    AwsS3FetchFilesResponse awsS3FetchFilesResponse =
        AwsS3FetchFilesResponse.builder()
            .unitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(Collections.singletonList(
                        UnitProgress.newBuilder().setUnitName("name").setStatus(UnitStatus.FAILURE).build()))
                    .build())
            .build();
    doReturn(awsConnectorDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(UnitProgressData.builder()
                 .unitProgresses(Collections.singletonList(
                     UnitProgress.newBuilder().setUnitName("name").setStatus(UnitStatus.FAILURE).build()))
                 .build())
        .when(cdStepHelper)
        .completeUnitProgressData(any(), any(), any());
    TaskChainResponse response = cloudformationStepHelper.executeNextLink(cloudformationStepExecutor, getAmbiance(),
        stepElementParameters, passThroughData, () -> awsS3FetchFilesResponse);
    assertThat(response).isNotNull();
    assertThat(response.isChainEnd()).isTrue();
    StepExceptionPassThroughData stepExceptionPassThroughData =
        (StepExceptionPassThroughData) response.getPassThroughData();
    assertThat(stepExceptionPassThroughData.getUnitProgressData().getUnitProgresses().get(0).getStatus())
        .isEqualTo(UnitStatus.FAILURE);
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void saveCloudformationInheritInputTest() {
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .repoName(ParameterField.createValueField("repoName"))
                      .paths(ParameterField.createValueField(Collections.singletonList("path1")))
                      .branch(ParameterField.createValueField("branch1"))
                      .gitFetchType(FetchType.BRANCH)
                      .connectorRef(ParameterField.createValueField("test-connector"))
                      .build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec2.setStore(storeConfigWrapper);
    templateFileSpec.setStore(storeConfigWrapper);
    CloudformationCreateStackStepConfigurationParameters config =
        CloudformationCreateStackStepConfigurationParameters.builder()
            .connectorRef(ParameterField.createValueField("aws-connector"))
            .region(ParameterField.createValueField("region"))
            .stackName(ParameterField.createValueField("stack-name"))
            .parameters(parametersFileSpecs)
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Remote)
                              .build())
            .build();

    ArgumentCaptor<CloudFormationInheritOutput> dataCaptor = ArgumentCaptor.forClass(CloudFormationInheritOutput.class);
    cloudformationStepHelper.saveCloudFormationInheritOutput(config, "id", getAmbiance(), true);
    verify(executionSweepingOutputService).consume(any(), any(), dataCaptor.capture(), any());
    assertThat(dataCaptor.getValue()).isNotNull();
    assertThat(dataCaptor.getValue().getStackName()).isEqualTo("stack-name");
    assertThat(dataCaptor.getValue().getRegion()).isEqualTo("region");
    assertThat(dataCaptor.getValue().getConnectorRef()).isEqualTo("aws-connector");
  }

  @Test()
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getSavedCloudformationInheritInputTest() {
    CloudFormationInheritOutput cloudFormationInheritOutput = CloudFormationInheritOutput.builder()
                                                                  .stackName("stack-name")
                                                                  .connectorRef("aws-connector")
                                                                  .region("region")
                                                                  .roleArn("role-arn")
                                                                  .build();

    doReturn(OptionalSweepingOutput.builder().output(cloudFormationInheritOutput).found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());
    CloudFormationInheritOutput output =
        cloudformationStepHelper.getSavedCloudFormationInheritOutput("id", getAmbiance());
    assertThat(output).isNotNull();
    assertThat(output.getStackName()).isEqualTo("stack-name");
    assertThat(output.getConnectorRef()).isEqualTo("aws-connector");
    assertThat(output.getRegion()).isEqualTo("region");
    assertThat(output.getRoleArn()).isEqualTo("role-arn");
  }

  @Test()
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getCloudformationConfig() {
    Ambiance ambiance = getAmbiance();
    Map<String, Object> parameterOverrides = Collections.singletonMap("name", ParameterField.createValueField("value"));
    List<String> capabilities = Collections.singletonList("capability");
    List<String> stackStatusesToMarkAsSuccess = Collections.singletonList("CREATE_COMPLETE");
    LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("params", Collections.singletonList(PARAMETERS_FILE_CONTENT));
    CloudFormationCreateStackPassThroughData passThroughData = CloudFormationCreateStackPassThroughData.builder()
                                                                   .templateBody("templateBody")
                                                                   .templateUrl("templateUrl")
                                                                   .parametersFilesContent(parameters)
                                                                   .tags(TAGS)
                                                                   .build();
    CloudformationCreateStackStepConfigurationParameters configuration =
        CloudformationCreateStackStepConfigurationParameters.builder()
            .region(ParameterField.createValueField("region"))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .parameterOverrides(parameterOverrides)
            .stackName(ParameterField.createValueField("stackName"))
            .roleArn(ParameterField.createValueField("roleArn"))
            .capabilities(ParameterField.createValueField(capabilities))
            .skipOnStackStatuses(ParameterField.createValueField(stackStatusesToMarkAsSuccess))
            .build();
    CloudformationCreateStackStepParameters stepParameters =
        CloudformationCreateStackStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provisionerIdentifier"))
            .configuration(configuration)
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    CloudformationConfig cloudformationConfig =
        cloudformationStepHelper.getCloudformationConfig(ambiance, stepElementParameters, passThroughData);

    assertThat(cloudformationConfig).isNotNull();
    assertThat(cloudformationConfig.getAccountId()).isEqualTo("account");
    assertThat(cloudformationConfig.getOrgId()).isEqualTo("org");
    assertThat(cloudformationConfig.getProjectId()).isEqualTo("project");
    assertThat(cloudformationConfig.getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(cloudformationConfig.getProvisionerIdentifier()).isEqualTo("provisionerIdentifier");
    assertThat(cloudformationConfig.getTemplateBody()).isEqualTo("templateBody");
    assertThat(cloudformationConfig.getTemplateUrl()).isEqualTo("templateUrl");
    assertThat(cloudformationConfig.getParametersFiles()).isEqualTo(parameters);
    assertThat(cloudformationConfig.getParameterOverrides().get("name")).isEqualTo("value");
    assertThat(cloudformationConfig.getStackName()).isEqualTo("stackName");
    assertThat(cloudformationConfig.getTags()).isEqualTo(TAGS);
    assertThat(cloudformationConfig.getConnectorRef()).isEqualTo("connectorRef");
    assertThat(cloudformationConfig.getRegion()).isEqualTo("region");
    assertThat(cloudformationConfig.getRoleArn()).isEqualTo("roleArn");
    assertThat(cloudformationConfig.getCapabilities()).isEqualTo(capabilities);
    assertThat(cloudformationConfig.getStackStatusesToMarkAsSuccess()).isEqualTo(stackStatusesToMarkAsSuccess);
  }

  @Test()
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getCloudformationConfigForPipelineRollback() {
    Ambiance ambiance =
        getAmbiance()
            .toBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build())
            .setOriginalStageExecutionIdForRollbackMode("original_exec_id")
            .build();
    CloudFormationCreateStackPassThroughData passThroughData =
        CloudFormationCreateStackPassThroughData.builder().build();
    CloudformationCreateStackStepConfigurationParameters configuration =
        CloudformationCreateStackStepConfigurationParameters.builder().build();
    CloudformationCreateStackStepParameters stepParameters =
        CloudformationCreateStackStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provisionerIdentifier"))
            .configuration(configuration)
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    doReturn(TAGS).when(engineExpressionService).renderExpression(any(), eq(TAGS));

    CloudformationConfig cloudformationConfig =
        cloudformationStepHelper.getCloudformationConfig(ambiance, stepElementParameters, passThroughData);

    assertThat(cloudformationConfig).isNotNull();
    assertThat(cloudformationConfig.getAccountId()).isEqualTo("account");
    assertThat(cloudformationConfig.getOrgId()).isEqualTo("org");
    assertThat(cloudformationConfig.getProjectId()).isEqualTo("project");
    assertThat(cloudformationConfig.getStageExecutionId()).isEqualTo("original_exec_id");
  }

  private StepElementParameters createStepParametersWithS3(boolean tags) {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    RemoteCloudformationTagsFileSpec tagsFileSpec = new RemoteCloudformationTagsFileSpec();

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .type(StoreConfigType.S3URL)
            .spec(S3UrlStoreConfig.builder()
                      .urls(ParameterField.createValueField(Collections.singletonList("url1")))
                      .region(ParameterField.createValueField("region"))
                      .connectorRef(ParameterField.createValueField("test-connector"))
                      .build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec.setIdentifier("test-parameters");
    parametersFileSpec2.setStore(storeConfigWrapper);
    parametersFileSpec2.setIdentifier("test-parameters2");
    templateFileSpec.setStore(storeConfigWrapper);
    tagsFileSpec.setStore(storeConfigWrapper);

    parameters.setConfiguration(
        CloudformationCreateStackStepConfigurationParameters.builder()
            .tags(tags
                    ? CloudformationTags.builder().type(CloudformationTagsFileTypes.Remote).spec(tagsFileSpec).build()
                    : null)
            .region(ParameterField.createValueField("region"))
            .stackName(ParameterField.createValueField("stack-name"))
            .parameters(parametersFileSpecs)
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Remote)
                              .build())
            .build());
    return StepElementParameters.builder().spec(parameters).build();
  }

  private StepElementParameters createStepParameterInline(boolean tags) {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    InlineCloudformationTemplateFileSpec templateFileSpec = new InlineCloudformationTemplateFileSpec();
    InlineCloudformationTagsFileSpec tagsFileSpec = new InlineCloudformationTagsFileSpec();

    templateFileSpec.setTemplateBody(ParameterField.createValueField("test-template"));
    tagsFileSpec.setContent(ParameterField.createValueField(TAGS));
    parameters.setConfiguration(
        CloudformationCreateStackStepConfigurationParameters.builder()
            .tags(tags
                    ? CloudformationTags.builder().type(CloudformationTagsFileTypes.Inline).spec(tagsFileSpec).build()
                    : null)
            .region(ParameterField.createValueField("region"))
            .stackName(ParameterField.createValueField("stack-name"))
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Inline)
                              .build())
            .build());
    return StepElementParameters.builder().spec(parameters).build();
  }

  private StepElementParameters createStepParameterS3WithNoParameterFiles() {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    S3UrlCloudformationTemplateFileSpec templateFileSpec = new S3UrlCloudformationTemplateFileSpec();
    templateFileSpec.setTemplateUrl(ParameterField.createValueField("test-url"));
    parameters.setConfiguration(CloudformationCreateStackStepConfigurationParameters.builder()
                                    .region(ParameterField.createValueField("region"))
                                    .stackName(ParameterField.createValueField("stack-name"))
                                    .templateFile(CloudformationTemplateFile.builder()
                                                      .spec(templateFileSpec)
                                                      .type(CloudformationTemplateFileTypes.S3Url)
                                                      .build())
                                    .build());
    return StepElementParameters.builder().spec(parameters).build();
  }

  private StepElementParameters createStepParametersWithGit(boolean tags) {
    CloudformationCreateStackStepParameters parameters = new CloudformationCreateStackStepParameters();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    CloudformationParametersFileSpec parametersFileSpec2 = new CloudformationParametersFileSpec();
    LinkedHashMap<String, CloudformationParametersFileSpec> parametersFileSpecs = new LinkedHashMap<>();
    parametersFileSpecs.put("var1", parametersFileSpec);
    parametersFileSpecs.put("var2", parametersFileSpec2);
    RemoteCloudformationTagsFileSpec tagsFileSpec = new RemoteCloudformationTagsFileSpec();

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .type(StoreConfigType.GIT)
            .spec(GithubStore.builder()
                      .repoName(ParameterField.createValueField("repoName"))
                      .paths(ParameterField.createValueField(Collections.singletonList("path1")))
                      .branch(ParameterField.createValueField("branch1"))
                      .gitFetchType(FetchType.BRANCH)
                      .connectorRef(ParameterField.createValueField("test-connector"))
                      .build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec.setIdentifier("test-identifier");
    parametersFileSpec2.setStore(storeConfigWrapper);
    parametersFileSpec2.setIdentifier("test-identifier2");
    templateFileSpec.setStore(storeConfigWrapper);
    tagsFileSpec.setStore(storeConfigWrapper);
    parameters.setConfiguration(
        CloudformationCreateStackStepConfigurationParameters.builder()
            .tags(tags
                    ? CloudformationTags.builder().type(CloudformationTagsFileTypes.Remote).spec(tagsFileSpec).build()
                    : null)
            .parameters(parametersFileSpecs)
            .region(ParameterField.createValueField("region"))
            .stackName(ParameterField.createValueField("stack-name"))
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Remote)
                              .build())
            .build());
    return StepElementParameters.builder().spec(parameters).build();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetParametersFromJson() {
    String parametersJson =
        "[{\"ParameterKey\": \"name\",\"ParameterValue\": \"value\"},{\"ParameterKey\": \"name2\",\"ParameterValue\": \"value2\"}]";
    doReturn(parametersJson).when(engineExpressionService).renderExpression(any(), any());

    Map<String, String> parameters = cloudformationStepHelper.getParametersFromJson(getAmbiance(), parametersJson);

    assertThat(parameters.get("name")).isEqualTo("value");
    assertThat(parameters.get("name2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetParametersFromJsonMalformedJson() {
    String parametersJson =
        "[{\"ParameterKey\": \"name\",\"ParameterValue: \"value\"},{\"ParameterKey\": \"name2\",\"ParameterValue\": \"value2\"}]";
    doReturn(parametersJson).when(engineExpressionService).renderExpression(any(), any());

    assertThatThrownBy(() -> cloudformationStepHelper.getParametersFromJson(getAmbiance(), parametersJson))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Failed to Deserialize json");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFailureResponse() {
    List<UnitProgress> unitProgresses = new ArrayList<>();
    String errorMessage = "errorMessage";

    StepResponse stepResponse = cloudformationStepHelper.getFailureResponse(unitProgresses, errorMessage);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(errorMessage);
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
