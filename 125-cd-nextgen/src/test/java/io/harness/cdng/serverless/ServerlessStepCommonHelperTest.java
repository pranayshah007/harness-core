/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.beans.ServerlessAwsLambdaStepExecutorParams;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExecutorParams;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.beans.serverless.ServerlessDeployResult;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactoryArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessEcrArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessS3ArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessS3FetchFileConfig;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.delegate.task.serverless.request.ServerlessS3FetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse;
import io.harness.exception.GeneralException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class ServerlessStepCommonHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private static final String PRIMARY_ARTIFACT_PATH_FOR_ARTIFACTORY = "<+artifact.path>";
  private static final String PRIMARY_ARTIFACT_PATH_FOR_S3 = "<+artifact.path>";
  private static final String PRIMARY_ARTIFACT_PATH_FOR_ECR = "<+artifact.image>";
  private static final String ARTIFACT_ACTUAL_PATH = "harnessArtifact/artifactFile";
  private static final String SIDECAR_ARTIFACT_PATH_PREFIX = "<+artifacts.sidecars.";
  private static final String SIDECAR_ARTIFACT_FILE_NAME_PREFIX = "harnessArtifact/sidecar-artifact-";

  @Mock private EngineExpressionService engineExpressionService;
  @Mock private OutcomeService outcomeService;
  @Mock private ServerlessAwsLambdaDeployStep serverlessAwsLambdaDeployStep;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock private ServerlessStepHelper serverlessStepHelper;
  @Mock private TaskRequestsUtils TaskRequestsUtils;
  @Mock private StepHelper stepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @Spy @InjectMocks private ServerlessStepCommonHelper serverlessStepCommonHelper;

  final ServerlessAwsLambdaDeployStepParameters serverlessSpecParameters =
      ServerlessAwsLambdaDeployStepParameters.infoBuilder().build();
  final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                          .spec(serverlessSpecParameters)
                                                          .timeout(ParameterField.createValueField("10m"))
                                                          .build();

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkTest() {
    String folderPath = "asdf/";
    GitStoreConfig gitStoreConfig = GitStore.builder()
                                        .connectorRef(ParameterField.createValueField("sfad"))
                                        .folderPath(ParameterField.createValueField(folderPath))
                                        .build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("safdsd", manifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);

    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doNothing().when(serverlessStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());

    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(manifestOutcome).when(serverlessStepHelper).getServerlessManifestOutcome(manifestsOutcome.values());

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doNothing().when(serverlessStepCommonHelper).validateManifest(any(), any(), any());
    doReturn("dfs").when(serverlessStepHelper).getConfigOverridePath(manifestOutcome);
    doReturn(connectorInfoDTO).when(serverlessEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(gitStoreDelegateConfig)
        .when(serverlessStepCommonHelper)
        .getGitStoreDelegateConfig(
            gitStoreConfig, connectorInfoDTO, manifestOutcome, Arrays.asList(folderPath), ambiance);

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse =
          serverlessStepCommonHelper.startChainLink(ambiance, stepElementParameters, serverlessStepHelper);

      aStatic.verify(
          () -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()), times(1));

      ServerlessStepPassThroughData serverlessStepPassThroughData =
          (ServerlessStepPassThroughData) taskChainResponse.getPassThroughData();

      assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getIdentifier()).isEqualTo("sadf");
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getStore()).isEqualTo(gitStoreConfig);
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getType())
          .isEqualTo("ServerlessAwsLambda");
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkS3FetchTaskTest() {
    String folderPath = "asdf/";
    S3StoreConfig s3StoreConfig = S3StoreConfig.builder()
                                      .connectorRef(ParameterField.createValueField("sfad"))
                                      .folderPath(ParameterField.createValueField(folderPath))
                                      .build();
    ParameterField<String> configOverridePath = ParameterField.<String>builder().value("serverlessConfig.yaml").build();
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder()
                                          .identifier("sadf")
                                          .store(s3StoreConfig)
                                          .configOverridePath(configOverridePath)
                                          .build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("safdsd", manifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(serverlessStepCommonHelper).resolveServerlessManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doNothing().when(serverlessStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    S3StoreDelegateConfig s3StoreDelegateConfig = S3StoreDelegateConfig.builder().build();
    doNothing().when(serverlessStepCommonHelper).validateManifest(any(), any(), any());
    doReturn("dfs").when(serverlessStepHelper).getConfigOverridePath(manifestOutcome);
    doReturn(connectorInfoDTO).when(serverlessEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(s3StoreDelegateConfig)
        .when(serverlessStepCommonHelper)
        .getS3StoreDelegateConfig(ambiance, s3StoreConfig, manifestOutcome);

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());
      ServerlessStepHelper serverlessStepHelper = new ServerlessAwsLambdaStepHelper();
      TaskChainResponse taskChainResponse =
          serverlessStepCommonHelper.startChainLink(ambiance, stepElementParameters, serverlessStepHelper);

      ServerlessS3FetchFileConfig serverlessS3FetchFileConfig = ServerlessS3FetchFileConfig.builder()
                                                                    .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                    .identifier(manifestOutcome.getIdentifier())
                                                                    .manifestType(manifestOutcome.getType())
                                                                    .configOverridePath(configOverridePath.getValue())
                                                                    .succeedIfFileNotFound(false)
                                                                    .build();
      String accountId = AmbianceUtils.getAccountId(ambiance);
      ServerlessS3FetchRequest serverlessS3FetchRequest = ServerlessS3FetchRequest.builder()
                                                              .accountId(accountId)
                                                              .serverlessS3FetchFileConfig(serverlessS3FetchFileConfig)
                                                              .shouldOpenLogStream(true)
                                                              .build();
      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.SERVERLESS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {serverlessS3FetchRequest})
                                    .build();

      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));

      ServerlessStepPassThroughData serverlessStepPassThroughData =
          (ServerlessStepPassThroughData) taskChainResponse.getPassThroughData();

      assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getIdentifier())
          .isEqualTo(manifestOutcome.getIdentifier());
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getStore())
          .isEqualTo(manifestOutcome.getStore());
      assertThat(serverlessStepPassThroughData.getServerlessManifestOutcome().getType())
          .isEqualTo("ServerlessAwsLambda");
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void queueServerlessTaskTest() {
    ServerlessDeployRequest serverlessCommandRequest = ServerlessDeployRequest.builder()
                                                           .accountId("accountId")
                                                           .commandName("commandName")
                                                           .timeoutIntervalInMin(10)
                                                           .manifestContent("content")
                                                           .build();
    ServerlessExecutionPassThroughData executionPassThroughData = ServerlessExecutionPassThroughData.builder().build();
    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      serverlessStepCommonHelper.queueServerlessTask(
          stepElementParameters, serverlessCommandRequest, ambiance, executionPassThroughData, false);
      aStatic.verify(
          () -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()), times(1));
    }
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void resolveServerlessManifestsOutcomeNotFoundTest() {
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).outcome(null).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    serverlessStepCommonHelper.resolveServerlessManifestsOutcome(ambiance);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void resolveServerlessManifestsOutcomeFoundTest() {
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    assertThat(serverlessStepCommonHelper.resolveServerlessManifestsOutcome(ambiance)).isEqualTo(manifestsOutcome);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getGitStoreDelegateConfigTest() {
    String folderPath = "asdf/";
    GitStoreConfig gitStoreConfig = GitStore.builder()
                                        .connectorRef(ParameterField.createValueField("sfad"))
                                        .folderPath(ParameterField.createValueField(folderPath))
                                        .build();
    ManifestOutcome manifestOutcome = ServerlessAwsLambdaManifestOutcome.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doNothing().when(serverlessStepCommonHelper).validateManifest(any(), any(), any());
    doReturn(connectorInfoDTO).when(serverlessEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(gitStoreDelegateConfig)
        .when(serverlessStepCommonHelper)
        .getGitStoreDelegateConfig(
            gitStoreConfig, connectorInfoDTO, manifestOutcome, Arrays.asList(folderPath), ambiance);
    assertThat(serverlessStepCommonHelper.getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
        .isEqualTo(gitStoreDelegateConfig);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void startChainTest() {
    String folderPath = "asdf/";
    GitStoreConfig gitStoreConfig = GitStore.builder()
                                        .connectorRef(ParameterField.createValueField("sfad"))
                                        .folderPath(ParameterField.createValueField(folderPath))
                                        .build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("safdsd", manifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);

    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doNothing().when(serverlessStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());

    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(manifestOutcome).when(serverlessStepHelper).getServerlessManifestOutcome(manifestsOutcome.values());

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doNothing().when(serverlessStepCommonHelper).validateManifest(any(), any(), any());
    doReturn("dfs").when(serverlessStepHelper).getConfigOverridePath(manifestOutcome);
    doReturn(connectorInfoDTO).when(serverlessEntityHelper).getConnectorInfoDTO(any(), any());
    doReturn(gitStoreDelegateConfig)
        .when(serverlessStepCommonHelper)
        .getGitStoreDelegateConfig(
            gitStoreConfig, connectorInfoDTO, manifestOutcome, Arrays.asList(folderPath), ambiance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @SneakyThrows
  @Category(UnitTests.class)
  public void executeNextLinkWhenGitFailureTest() {
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    PassThroughData passThroughData = ServerlessStepPassThroughData.builder().build();
    ResponseData responseData = ServerlessGitFetchResponse.builder().taskStatus(TaskStatus.FAILURE).build();
    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(ServerlessGitFetchFailurePassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @SneakyThrows
  @Category(UnitTests.class)
  public void executeNextLinkWhenExecutionFailureDueToManifestNotFoundTest() {
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    PassThroughData passThroughData = ServerlessStepPassThroughData.builder().build();
    ResponseData responseData = ServerlessGitFetchResponse.builder()
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .filesFromMultipleRepo(new HashMap<>())
                                    .build();
    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(ServerlessStepExceptionPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @SneakyThrows
  @Category(UnitTests.class)
  public void executeNextLinkSuccessTest() {
    StoreConfig storeConfig = GitStore.builder().build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("adsf").store(storeConfig).build();
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    PassThroughData passThroughData = ServerlessStepPassThroughData.builder()
                                          .serverlessManifestOutcome(manifestOutcome)
                                          .infrastructureOutcome(infrastructureOutcome)
                                          .build();
    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<>();
    fetchFilesResultMap.put("adsf",
        FetchFilesResult.builder()
            .files(Arrays.asList(GitFile.builder().fileContent("safddsaf").filePath("sdafsda/").build()))
            .build());
    ResponseData responseData =
        ServerlessGitFetchResponse.builder()
            .taskStatus(TaskStatus.SUCCESS)
            .filesFromMultipleRepo(fetchFilesResultMap)
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder().build();
    TaskChainResponse expectedTaskChainResponse = TaskChainResponse.builder()
                                                      .chainEnd(false)
                                                      .passThroughData(serverlessExecutionPassThroughData)
                                                      .taskRequest(TaskRequest.newBuilder().build())
                                                      .build();
    Optional<Pair<String, String>> manifestFilePathContent = Optional.of(Pair.of("a", "b"));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    doReturn(manifestFilePathContent).when(serverlessStepHelper).getManifestFileContent(any(), any());
    doReturn(expectedTaskChainResponse)
        .when(serverlessAwsLambdaDeployStep)
        .executeServerlessPrepareRollbackTask(any(), any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse).isEqualTo(expectedTaskChainResponse);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @SneakyThrows
  @Category(UnitTests.class)
  public void executeNextLinkS3FetchResponseTest() {
    StoreConfig storeConfig = S3StoreConfig.builder().build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("adsf").store(storeConfig).build();
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    PassThroughData passThroughData = ServerlessStepPassThroughData.builder()
                                          .serverlessManifestOutcome(manifestOutcome)
                                          .infrastructureOutcome(infrastructureOutcome)
                                          .build();
    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<>();
    fetchFilesResultMap.put("adsf",
        FetchFilesResult.builder()
            .files(Arrays.asList(GitFile.builder().fileContent("safddsaf").filePath("sdafsda/").build()))
            .build());
    ResponseData responseData =
        ServerlessGitFetchResponse.builder()
            .taskStatus(TaskStatus.SUCCESS)
            .filesFromMultipleRepo(fetchFilesResultMap)
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder().build();
    TaskChainResponse expectedTaskChainResponse = TaskChainResponse.builder()
                                                      .chainEnd(false)
                                                      .passThroughData(serverlessExecutionPassThroughData)
                                                      .taskRequest(TaskRequest.newBuilder().build())
                                                      .build();
    Optional<Pair<String, String>> manifestFilePathContent = Optional.of(Pair.of("a", "b"));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    doReturn(manifestFilePathContent).when(serverlessStepHelper).getManifestFileContent(any(), any());
    doReturn(expectedTaskChainResponse)
        .when(serverlessAwsLambdaDeployStep)
        .executeServerlessPrepareRollbackTask(any(), any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse).isEqualTo(expectedTaskChainResponse);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleGitTaskFailureTest() {
    ServerlessGitFetchFailurePassThroughData serverlessGitFetchFailurePassThroughData =
        ServerlessGitFetchFailurePassThroughData.builder()
            .errorMsg("error")
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    StepResponse stepResponse =
        serverlessStepCommonHelper.handleGitTaskFailure(serverlessGitFetchFailurePassThroughData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo()).isEqualTo(FailureInfo.newBuilder().setErrorMessage("error").build());
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleStepExceptionFailureTest() {
    ServerlessStepExceptionPassThroughData serverlessGitFetchFailurePassThroughData =
        ServerlessStepExceptionPassThroughData.builder()
            .errorMessage("error")
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    StepResponse stepResponse =
        serverlessStepCommonHelper.handleStepExceptionFailure(serverlessGitFetchFailurePassThroughData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("error");
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypes(0))
        .isEqualTo(FailureType.APPLICATION_FAILURE);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getLevel())
        .isEqualTo(io.harness.eraro.Level.ERROR.name());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(GENERAL_ERROR.name());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @SneakyThrows
  @Category(UnitTests.class)
  public void handleTaskExceptionFailureTest() {
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    ServerlessExecutionPassThroughData serverlessGitFetchFailurePassThroughData =
        ServerlessExecutionPassThroughData.builder()
            .infrastructure(infrastructureOutcome)
            .lastActiveUnitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    StepResponse stepResponse = serverlessStepCommonHelper.handleTaskException(
        ambiance, serverlessGitFetchFailurePassThroughData, new Exception());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypes(0))
        .isEqualTo(FailureType.APPLICATION_FAILURE);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getLevel())
        .isEqualTo(io.harness.eraro.Level.ERROR.name());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(GENERAL_ERROR.name());
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  @SneakyThrows
  public void handleTaskExceptionFailureWhenTaskNGDataExceptionTest() {
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    ServerlessExecutionPassThroughData serverlessGitFetchFailurePassThroughData =
        ServerlessExecutionPassThroughData.builder()
            .infrastructure(infrastructureOutcome)
            .lastActiveUnitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .build();
    serverlessStepCommonHelper.handleTaskException(ambiance, serverlessGitFetchFailurePassThroughData,
        new TaskNGDataException(UnitProgressData.builder().build(), new Exception()));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleServerlessPrepareRollbackDataResponseCommandExecutionStatusFailureTest() throws Exception {
    StoreConfig storeConfig = GitStore.builder().build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("adsf").store(storeConfig).build();
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    PassThroughData passThroughData = ServerlessStepPassThroughData.builder()
                                          .serverlessManifestOutcome(manifestOutcome)
                                          .infrastructureOutcome(infrastructureOutcome)
                                          .build();

    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<>();
    fetchFilesResultMap.put("adsf",
        FetchFilesResult.builder()
            .files(Arrays.asList(GitFile.builder().fileContent("safddsaf").filePath("sdafsda/").build()))
            .build());
    ResponseData responseData = ServerlessPrepareRollbackDataResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                    .errorMessage("error")
                                    .unitProgressData(UnitProgressData.builder().build())
                                    .build();

    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleServerlessPrepareRollbackDataResponseCommandExecutionStatusSuccessTest() throws Exception {
    StoreConfig storeConfig = GitStore.builder().build();
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("adsf").store(storeConfig).build();
    InfrastructureOutcome infrastructureOutcome = ServerlessAwsLambdaInfrastructureOutcome.builder().build();
    ServerlessStepPassThroughData passThroughData = ServerlessStepPassThroughData.builder()
                                                        .serverlessManifestOutcome(manifestOutcome)
                                                        .infrastructureOutcome(infrastructureOutcome)
                                                        .build();

    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<>();
    fetchFilesResultMap.put("adsf",
        FetchFilesResult.builder()
            .files(Arrays.asList(GitFile.builder().fileContent("safddsaf").filePath("sdafsda/").build()))
            .build());
    ServerlessAwsLambdaPrepareRollbackDataResult serverlessRollbackDataResult =
        ServerlessAwsLambdaPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .previousVersionTimeStamp("123")
            .build();
    ServerlessPrepareRollbackDataResponse responseData =
        ServerlessPrepareRollbackDataResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .errorMessage("error")
            .serverlessPrepareRollbackDataResult(serverlessRollbackDataResult)
            .unitProgressData(UnitProgressData.builder().build())
            .build();

    ServerlessFetchFileOutcome serverlessFetchFileOutcome = ServerlessFetchFileOutcome.builder()
                                                                .manifestFileOverrideContent("manifestFileOverride")
                                                                .manifestFilePathContent(Pair.of("a", "b"))
                                                                .build();
    OptionalSweepingOutput serverlessFetchFileOptionalOutput =
        OptionalSweepingOutput.builder().output(serverlessFetchFileOutcome).found(true).build();
    doReturn(serverlessFetchFileOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.SERVERLESS_FETCH_FILE_OUTCOME));

    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder()
            .infrastructure(passThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(responseData.getUnitProgressData())
            .build();
    ServerlessStepExecutorParams serverlessStepExecutorParams =
        ServerlessAwsLambdaStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .manifestFilePathContent(serverlessFetchFileOutcome.getManifestFilePathContent())
            .manifestFileOverrideContent(serverlessFetchFileOutcome.getManifestFileOverrideContent())
            .build();
    TaskChainResponse taskChainRes =
        TaskChainResponse.builder().chainEnd(false).taskRequest(TaskRequest.newBuilder().build()).build();
    doReturn(taskChainRes)
        .when(serverlessAwsLambdaDeployStep)
        .executeServerlessTask(passThroughData.getServerlessManifestOutcome(), ambiance, stepElementParameters,
            serverlessExecutionPassThroughData, responseData.getUnitProgressData(), serverlessStepExecutorParams);

    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getFunctionInstanceInfoWhenDeployResponse() {
    ServerlessDeployResult serverlessDeployResult = ServerlessAwsLambdaDeployResult.builder().build();
    ServerlessCommandResponse serverlessCommandResponse =
        ServerlessDeployResponse.builder().serverlessDeployResult(serverlessDeployResult).build();
    List<ServerInstanceInfo> serverInstanceInfoList =
        Arrays.asList(ServerlessAwsLambdaServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfoList)
        .when(serverlessStepHelper)
        .getServerlessDeployFunctionInstanceInfo(serverlessDeployResult, "infraStructureKey");
    assertThat(serverlessStepCommonHelper.getFunctionInstanceInfo(
                   serverlessCommandResponse, serverlessStepHelper, "infraStructureKey"))
        .isEqualTo(serverInstanceInfoList);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void resolveArtifactsOutcomeWhenArtifactOutcomeOptionsFoundAndPrimaryAlsoFoundTest() {
    ArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().build();
    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(artifactOutcome).build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(artifactsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    assertThat(serverlessStepCommonHelper.resolveArtifactsOutcome(ambiance)).isEqualTo(Optional.of(artifactOutcome));
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void resolveArtifactsOutcomeWhenArtifactOutcomeOptionsNotFound() {
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    assertThat(serverlessStepCommonHelper.resolveArtifactsOutcome(ambiance)).isEqualTo(Optional.empty());
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void resolveArtifactsOutcomeWhenArtifactOutcomeOptionsFoundAndPrimaryNotFoundTest() {
    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(artifactsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    assertThat(serverlessStepCommonHelper.resolveArtifactsOutcome(ambiance)).isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void renderManifestContentTestWhenManifestFileContentNotEmpty() {
    String manifestFileContent = "dsfa";
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder().build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig);
    doReturn(manifestFileContent).when(engineExpressionService).renderExpression(ambiance, manifestFileContent);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(manifestFileContent);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void
  renderManifestContentTestWhenManifestFileContentNotEmptyAndContainsPrimaryArtifactoryReplacementExpression() {
    String manifestFileContent = PRIMARY_ARTIFACT_PATH_FOR_ARTIFACTORY;
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder().build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig);
    doReturn(ARTIFACT_ACTUAL_PATH).when(engineExpressionService).renderExpression(ambiance, ARTIFACT_ACTUAL_PATH);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(ARTIFACT_ACTUAL_PATH);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void renderManifestContentTestWhenManifestFileContentNotEmptyAndContainsPrimaryEcrReplacementExpression() {
    String manifestFileContent = PRIMARY_ARTIFACT_PATH_FOR_ECR;
    String replacedContent = "448640225317.dkr.ecr.us-east-1.amazonaws.com/test-docker-2:latest";
    ServerlessArtifactConfig serverlessArtifactConfig =
        ServerlessEcrArtifactConfig.builder().image(replacedContent).build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig);
    doReturn(replacedContent).when(engineExpressionService).renderExpression(ambiance, replacedContent);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(replacedContent);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void
  renderManifestContentTestWhenManifestFileContentNotEmptyAndContainsPrimaryAndSecondaryReplacementExpression() {
    String manifestFileContent = "image: " + PRIMARY_ARTIFACT_PATH_FOR_ECR + "\nimage: " + SIDECAR_ARTIFACT_PATH_PREFIX
        + "sidecar1>\n path: " + SIDECAR_ARTIFACT_PATH_PREFIX + "sidecar2>";
    String image = "448640225317.dkr.ecr.us-east-1.amazonaws.com/test-docker-2:latest";
    String image1 = "443440225317.dkr.ecr.us-east-1.amazonaws.com/test-docker:latest";
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessEcrArtifactConfig.builder().image(image).build();
    ServerlessArtifactConfig serverlessArtifactConfig1 = ServerlessEcrArtifactConfig.builder().image(image1).build();
    String replacedManifestContent =
        "image: " + image + "\nimage: " + image1 + "\n path: " + SIDECAR_ARTIFACT_FILE_NAME_PREFIX + "sidecar2";
    ServerlessArtifactConfig serverlessArtifactConfig2 = ServerlessArtifactoryArtifactConfig.builder().build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig1);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig2);
    doReturn(replacedManifestContent).when(engineExpressionService).renderExpression(ambiance, replacedManifestContent);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(replacedManifestContent);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void renderManifestContentTestWhenManifestFileContentEmpty() {
    String manifestFileContent = "";
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder().build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(manifestFileContent);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getManifestDefaultFileNameServerlessStepUtilsTest() {
    assertThat(serverlessStepCommonHelper.getManifestDefaultFileName("filePath/serverless.yaml"))
        .isEqualTo("serverless.yaml");
    assertThat(serverlessStepCommonHelper.getManifestDefaultFileName("filePath/serverless.yml"))
        .isEqualTo("serverless.yml");
    assertThat(serverlessStepCommonHelper.getManifestDefaultFileName("filePath/serverless.json"))
        .isEqualTo("serverless.json");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void renderManifestContentTestWhenManifestFileContentNotEmptyAndContainsPrimaryS3ReplacementExpression() {
    String manifestFileContent = PRIMARY_ARTIFACT_PATH_FOR_S3;
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessS3ArtifactConfig.builder().build();
    Map<String, ServerlessArtifactConfig> sidecarArtifactMap = new HashMap<>();
    sidecarArtifactMap.put("sidecar1", serverlessArtifactConfig);
    sidecarArtifactMap.put("sidecar2", serverlessArtifactConfig);
    doReturn(ARTIFACT_ACTUAL_PATH).when(engineExpressionService).renderExpression(ambiance, ARTIFACT_ACTUAL_PATH);
    assertThat(serverlessStepCommonHelper.renderManifestContent(
                   ambiance, manifestFileContent, serverlessArtifactConfig, sidecarArtifactMap))
        .isEqualTo(ARTIFACT_ACTUAL_PATH);
  }
}