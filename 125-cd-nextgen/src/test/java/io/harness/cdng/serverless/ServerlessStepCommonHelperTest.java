/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessDeployResult;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.exception.GeneralException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessStepCommonHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  @Mock private EngineExpressionService engineExpressionService;

  @Mock private OutcomeService outcomeService;
  @Mock private ServerlessAwsLambdaDeployStep serverlessAwsLambdaDeployStep;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock private ServerlessStepHelper serverlessStepHelper;

  @Spy @InjectMocks private ServerlessStepCommonHelper serverlessStepCommonHelper;

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
    TaskChainResponse expectedTaskChainResponse =
        TaskChainResponse.builder().chainEnd(true).passThroughData(serverlessExecutionPassThroughData).build();
    Optional<Pair<String, String>> manifestFilePathContent = Optional.of(Pair.of("a", "b"));
    doReturn(manifestFilePathContent).when(serverlessStepHelper).getManifestFileContent(any(), any());
    doReturn(expectedTaskChainResponse)
        .when(serverlessAwsLambdaDeployStep)
        .executeServerlessTask(any(), any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = serverlessStepCommonHelper.executeNextLink(serverlessAwsLambdaDeployStep,
        ambiance, stepElementParameters, passThroughData, () -> responseData, serverlessStepHelper);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse).isEqualTo(expectedTaskChainResponse);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void queueServerlessTaskTest() {
    // Not written because not sure how to test prepareCDTaskRequest
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
    doReturn(manifestFileContent).when(engineExpressionService).renderExpression(ambiance, manifestFileContent);
    assertThat(serverlessStepCommonHelper.renderManifestContent(ambiance, manifestFileContent))
        .isEqualTo(manifestFileContent);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void renderManifestContentTestWhenManifestFileContentEmpty() {
    String manifestFileContent = "";
    assertThat(serverlessStepCommonHelper.renderManifestContent(ambiance, manifestFileContent))
        .isEqualTo(manifestFileContent);
  }
}
