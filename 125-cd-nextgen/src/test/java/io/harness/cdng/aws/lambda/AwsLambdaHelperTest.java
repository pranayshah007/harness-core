/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.lambda.AwsLambdaCommandUnitConstants;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStepParameters;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsLambdaAliasDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.AwsLambdaDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.lambda.AwsLambda;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaS3ArtifactConfig;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaCommandResponse;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaDeployResponse;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse.AwsLambdaPrepareRollbackResponseBuilder;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitcommon.GitFetchFilesResult;
import io.harness.delegate.task.gitcommon.GitTaskNGResponse;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(CDP)
public class AwsLambdaHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final AwsLambdaDeployStepParameters awsLambdaDeploySpecParameters =
      AwsLambdaDeployStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                                  .spec(awsLambdaDeploySpecParameters)
                                                                  .timeout(ParameterField.createValueField("10m"))
                                                                  .build();
  private final InfrastructureOutcome infrastructureOutcome = AwsLambdaInfrastructureOutcome.builder().build();

  private final S3ArtifactOutcome s3ArtifactOutcome = S3ArtifactOutcome.builder().build();
  private final ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(s3ArtifactOutcome).build();
  private final UnitProgressData unitProgressData = UnitProgressData.builder().build();

  private final String FILE_PATH = "Lambda/createFunction.json";
  private final String ID = "id";
  private final String MANIFEST_CONTENT = "conetent";

  @Mock private LogCallback logCallback;
  @Mock private FileStoreService fileStoreService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private OutcomeService outcomeService;
  @Mock private AwsLambdaEntityHelper awsLambdaEntityHelper;
  @Mock private TaskRequestsUtils TaskRequestsUtils;
  @Mock private StepHelper stepHelper;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks @Spy AwsLambdaHelper awsLambdaHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkHarnessStoreTask() {
    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList(FILE_PATH)).build())
            .build();
    ManifestOutcome manifestOutcome =
        AwsLambdaDefinitionManifestOutcome.builder().identifier(ID).store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put(ID, manifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doNothing().when(awsLambdaHelper).validateManifestsOutcome(ambiance, manifestsOutcome);

    doReturn(logCallback)
        .when(awsLambdaHelper)
        .getLogCallback(AwsLambdaCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    AwsLambdaFunctionsInfraConfig awsLambdaInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    doReturn(awsLambdaInfraConfig).when(awsLambdaHelper).getInfraConfig(any(), any());

    OptionalOutcome optionalArtifactOutcome = OptionalOutcome.builder().found(true).outcome(artifactsOutcome).build();
    doReturn(optionalArtifactOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    Optional<FileStoreNodeDTO> manifestFile = Optional.of(FileNodeDTO.builder().content(MANIFEST_CONTENT).build());
    doReturn(manifestFile).when(fileStoreService).getWithChildrenByPath(any(), any(), any(), any(), anyBoolean());
    doReturn(MANIFEST_CONTENT).when(engineExpressionService).renderExpression(any(), any());
    doReturn(unitProgressData)
        .when(awsLambdaHelper)
        .getCommandUnitProgressData(
            AwsLambdaCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);
    TaskChainResponse taskChainResponse = awsLambdaHelper.startChainLink(ambiance, stepElementParameters);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    verify(awsLambdaHelper)
        .queueTask(eq(stepElementParameters), any(), eq(TaskType.AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_TASK_NG),
            eq(ambiance), any(), eq(false));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void
  testHandlePrepareRollbackDataResponseSkipsRollbackIfFunctionConfigurationIsEmptyOrFirstDeploymentIsTrue() {
    AwsLambdaPrepareRollbackResponseBuilder awsLambdaPrepareRollbackResponseBuilder =
        AwsLambdaPrepareRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData);

    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList(FILE_PATH)).build())
            .build();
    ManifestOutcome manifestOutcome =
        AwsLambdaDefinitionManifestOutcome.builder().identifier(ID).store(harnessStoreConfig).build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData =
        AwsLambdaStepPassThroughData.builder()
            .manifestsOutcomes(Arrays.asList(manifestOutcome))
            .manifestFileContentsMap(Map.of(ID, Arrays.asList("v1:k1")))
            .build();
    OptionalOutcome optionalArtifactOutcome = OptionalOutcome.builder().found(true).outcome(artifactsOutcome).build();
    doReturn(optionalArtifactOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    doReturn(TaskChainResponse.builder().chainEnd(true).build())
        .when(awsLambdaHelper)
        .executeTask(ambiance, stepElementParameters, awsLambdaStepPassThroughData, unitProgressData);

    // First deployment is false and functionConfiguration is null
    awsLambdaHelper.handlePrepareRollbackDataResponse(
        awsLambdaPrepareRollbackResponseBuilder.build(), ambiance, stepElementParameters, awsLambdaStepPassThroughData);
    Mockito.verify(executionSweepingOutputService, Mockito.times(0)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testHandlePrepareRollbackDataResponse() {
    AwsLambdaPrepareRollbackResponseBuilder awsLambdaPrepareRollbackResponseBuilder =
        AwsLambdaPrepareRollbackResponse.builder()
            .functionConfiguration("FunctionArn")
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData);

    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList(FILE_PATH)).build())
            .build();
    ManifestOutcome manifestOutcome =
        AwsLambdaDefinitionManifestOutcome.builder().identifier(ID).store(harnessStoreConfig).build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData =
        AwsLambdaStepPassThroughData.builder()
            .manifestsOutcomes(Arrays.asList(manifestOutcome))
            .manifestFileContentsMap(Map.of(ID, Arrays.asList("v1:k1")))
            .build();
    OptionalOutcome optionalArtifactOutcome = OptionalOutcome.builder().found(true).outcome(artifactsOutcome).build();
    doReturn(optionalArtifactOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    doReturn(TaskChainResponse.builder().chainEnd(true).build())
        .when(awsLambdaHelper)
        .executeTask(ambiance, stepElementParameters, awsLambdaStepPassThroughData, unitProgressData);

    awsLambdaHelper.handlePrepareRollbackDataResponse(
        awsLambdaPrepareRollbackResponseBuilder.build(), ambiance, stepElementParameters, awsLambdaStepPassThroughData);
    Mockito.verify(executionSweepingOutputService, Mockito.times(1)).consume(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInfraConfigTest() {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = mock(AwsLambdaFunctionsInfraConfig.class);
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaEntityHelper).getInfraConfig(any(), any());
    assertThat(awsLambdaHelper.getInfraConfig(infrastructureOutcome, ambiance))
        .isEqualTo(awsLambdaFunctionsInfraConfig);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getAwsLambdaManifestOutcomeTest() {
    AwsLambdaAliasDefinitionManifestOutcome awsLambdaAliasDefinitionManifestOutcome =
        mock(AwsLambdaAliasDefinitionManifestOutcome.class);
    AwsLambdaDefinitionManifestOutcome awsLambdaDefinitionManifestOutcome =
        mock(AwsLambdaDefinitionManifestOutcome.class);
    when(awsLambdaDefinitionManifestOutcome.getType()).thenReturn(ManifestType.AwsLambdaFunctionDefinition);
    when(awsLambdaAliasDefinitionManifestOutcome.getType()).thenReturn(ManifestType.AwsLambdaFunctionAliasDefinition);
    assertThat(awsLambdaHelper
                   .getAwsLambdaManifestOutcome(
                       Arrays.asList(awsLambdaAliasDefinitionManifestOutcome, awsLambdaDefinitionManifestOutcome))
                   .size())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeNextLinkTestWhenGitFetchResponseAsFailure() throws Exception {
    GitTaskNGResponse gitTaskNGResponse = GitTaskNGResponse.builder().taskStatus(TaskStatus.FAILURE).build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = AwsLambdaStepPassThroughData.builder().build();
    assertThat(
        awsLambdaHelper
            .executeNextLink(ambiance, stepElementParameters, awsLambdaStepPassThroughData, () -> gitTaskNGResponse)
            .getPassThroughData())
        .isInstanceOf(AwsLambdaStepExceptionPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeNextLinkTestWhenGitFetchResponseAsSuccess() throws Exception {
    String identifier = "iden";
    String content = "content";
    String identifier1 = "iden";
    String content1 = "content";
    AwsLambdaDefinitionManifestOutcome awsLambdaDefinitionManifestOutcome =
        AwsLambdaDefinitionManifestOutcome.builder().identifier(identifier1).build();
    GitFile gitFile = GitFile.builder().fileContent(content).build();
    GitFetchFilesResult gitFetchFilesResult =
        GitFetchFilesResult.builder().identifier(identifier).files(Arrays.asList(gitFile)).build();
    Map<String, List<String>> harnessManifest = Map.of(identifier1, Arrays.asList(content1));
    GitTaskNGResponse gitTaskNGResponse = GitTaskNGResponse.builder()
                                              .unitProgressData(unitProgressData)
                                              .gitFetchFilesResults(Arrays.asList(gitFetchFilesResult))
                                              .taskStatus(TaskStatus.SUCCESS)
                                              .build();
    AwsLambdaInfrastructureOutcome awsLambdaInfrastructureOutcome = AwsLambdaInfrastructureOutcome.builder().build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData =
        AwsLambdaStepPassThroughData.builder()
            .infrastructureOutcome(awsLambdaInfrastructureOutcome)
            .manifestFileContentsMap(harnessManifest)
            .unitProgressData(unitProgressData)
            .manifestsOutcomes(Arrays.asList(awsLambdaDefinitionManifestOutcome))
            .build();
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaEntityHelper).getInfraConfig(any(), any());
    ArtifactOutcome artifactOutcome = S3ArtifactOutcome.builder().build();
    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ArtifactsOutcome.builder().primary(artifactOutcome).build())
                 .build())
        .when(outcomeService)
        .resolveOptional(any(), any());
    AwsLambdaArtifactConfig awsLambdaArtifactConfig = AwsLambdaS3ArtifactConfig.builder().build();
    doReturn(awsLambdaArtifactConfig).when(awsLambdaEntityHelper).getAwsLambdaArtifactConfig(any(), any());

    AwsLambdaSpecParameters awsLambdaSpecParameters = AwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .spec(awsLambdaSpecParameters)
                                                      .build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    assertThat(
        awsLambdaHelper
            .executeNextLink(ambiance, stepElementParameters, awsLambdaStepPassThroughData, () -> gitTaskNGResponse)
            .getPassThroughData())
        .isInstanceOf(AwsLambdaStepPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeNextLinkTestWhenPrepareRollbackResponseAsFailure() throws Exception {
    AwsLambdaPrepareRollbackResponse awsLambdaPrepareRollbackResponse =
        AwsLambdaPrepareRollbackResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = AwsLambdaStepPassThroughData.builder().build();
    assertThat(awsLambdaHelper
                   .executeNextLink(ambiance, stepElementParameters, awsLambdaStepPassThroughData,
                       () -> awsLambdaPrepareRollbackResponse)
                   .getPassThroughData())
        .isInstanceOf(AwsLambdaStepExceptionPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeNextLinkTestWhenPrepareResponseAsSuccess() throws Exception {
    String identifier1 = "iden";
    String content1 = "content";
    AwsLambdaDefinitionManifestOutcome awsLambdaDefinitionManifestOutcome =
        AwsLambdaDefinitionManifestOutcome.builder().identifier(identifier1).build();
    Map<String, List<String>> harnessManifest = Map.of(identifier1, Arrays.asList(content1));
    AwsLambdaPrepareRollbackResponse awsLambdaPrepareRollbackResponse =
        AwsLambdaPrepareRollbackResponse.builder()
            .unitProgressData(unitProgressData)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .manifestContent(content1)
            .build();
    AwsLambdaInfrastructureOutcome awsLambdaInfrastructureOutcome = AwsLambdaInfrastructureOutcome.builder().build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData =
        AwsLambdaStepPassThroughData.builder()
            .infrastructureOutcome(awsLambdaInfrastructureOutcome)
            .manifestFileContentsMap(harnessManifest)
            .unitProgressData(unitProgressData)
            .manifestsOutcomes(Arrays.asList(awsLambdaDefinitionManifestOutcome))
            .build();
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaEntityHelper).getInfraConfig(any(), any());
    ArtifactOutcome artifactOutcome = S3ArtifactOutcome.builder().build();
    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ArtifactsOutcome.builder().primary(artifactOutcome).build())
                 .build())
        .when(outcomeService)
        .resolveOptional(any(), any());
    AwsLambdaArtifactConfig awsLambdaArtifactConfig = AwsLambdaS3ArtifactConfig.builder().build();
    doReturn(awsLambdaArtifactConfig).when(awsLambdaEntityHelper).getAwsLambdaArtifactConfig(any(), any());

    AwsLambdaSpecParameters awsLambdaSpecParameters = AwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .spec(awsLambdaSpecParameters)
                                                      .build();
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    assertThat(awsLambdaHelper
                   .executeNextLink(ambiance, stepElementParameters, awsLambdaStepPassThroughData,
                       () -> awsLambdaPrepareRollbackResponse)
                   .getPassThroughData())
        .isInstanceOf(AwsLambdaStepPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeNextLinkTestWhenException() throws Exception {
    AwsLambdaPrepareRollbackResponse awsLambdaPrepareRollbackResponse =
        AwsLambdaPrepareRollbackResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = AwsLambdaStepPassThroughData.builder().build();
    assertThat(awsLambdaHelper
                   .executeNextLink(ambiance, stepElementParameters, awsLambdaStepPassThroughData,
                       () -> awsLambdaPrepareRollbackResponse)
                   .getPassThroughData())
        .isInstanceOf(AwsLambdaStepExceptionPassThroughData.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerInstanceInfoTest() {
    AwsLambda awsLambda = AwsLambda.builder().build();
    AwsLambdaCommandResponse awsLambdaCommandResponse = AwsLambdaDeployResponse.builder()
                                                            .awsLambda(awsLambda)
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .build();
    ServerInstanceInfo serverInstanceInfo = mock(ServerInstanceInfo.class);
    Mockito.mockStatic(AwsLambdaToServerInstanceInfoMapper.class);
    when(AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfo(any(), any(), any())).thenReturn(serverInstanceInfo);
    AwsLambdaInfraConfig awsLambdaInfraConfig = AwsLambdaFunctionsInfraConfig.builder().region("region").build();
    assertThat(awsLambdaHelper.getServerInstanceInfo(awsLambdaCommandResponse, awsLambdaInfraConfig, "infraKey").size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleStepExceptionFailure() {
    AwsLambdaStepExceptionPassThroughData awsLambdaStepExceptionPassThroughData =
        AwsLambdaStepExceptionPassThroughData.builder().unitProgressData(unitProgressData).build();
    assertThat(awsLambdaHelper.handleStepExceptionFailure(awsLambdaStepExceptionPassThroughData))
        .isInstanceOf(StepResponse.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void generateStepResponseTestWhenFailure() {
    AwsLambda awsLambda = AwsLambda.builder().build();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    AwsLambdaCommandResponse awsLambdaCommandResponse = AwsLambdaDeployResponse.builder()
                                                            .awsLambda(awsLambda)
                                                            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                            .build();
    assertThat(
        awsLambdaHelper.generateStepResponse(awsLambdaCommandResponse, stepResponseBuilder, ambiance).getStatus())
        .isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void generateStepResponseTestWhenSuccess() {
    AwsLambda awsLambda = AwsLambda.builder().build();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    AwsLambdaCommandResponse awsLambdaCommandResponse = AwsLambdaDeployResponse.builder()
                                                            .awsLambda(awsLambda)
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .build();
    assertThat(
        awsLambdaHelper.generateStepResponse(awsLambdaCommandResponse, stepResponseBuilder, ambiance).getStatus())
        .isEqualTo(Status.SUCCEEDED);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleStepExceptionFailureTest() throws Exception {
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = AwsLambdaStepPassThroughData.builder().build();
    Exception exception = new Exception("exception");
    assertThat(
        awsLambdaHelper.handleStepFailureException(ambiance, awsLambdaStepPassThroughData, exception).getStatus())
        .isEqualTo(Status.FAILED);
  }
}
