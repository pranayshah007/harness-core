/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.EcsScalableTargetDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsScalingPolicyDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsServiceDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.request.EcsCanaryDeployRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.GeneralException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class EcsStepCommonHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsRollingDeployStepParameters ecsSpecParameters = EcsRollingDeployStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  @Mock private OutcomeService outcomeService;
  @Mock private EcsStepHelperImpl ecsStepHelper;
  @Mock private EcsEntityHelper ecsEntityHelper;
  @Mock private EcsStepUtils ecsStepUtils;
  @Mock private StepHelper stepHelper;
  @Mock private EcsStepExecutor ecsStepExecutor;
  @Mock private EcsRollingDeployStep ecsRollingDeployStep;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private LogCallback logCallback;

  @Spy @InjectMocks private EcsStepCommonHelper ecsStepCommonHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkTest() {
    GitStoreConfig gitStoreConfig = GitStore.builder()
                                        .connectorRef(ParameterField.createValueField("sfad"))
                                        .folderPath(ParameterField.createValueField("folderPath"))
                                        .build();
    ManifestOutcome manifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("safdsd", manifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    List<ManifestOutcome> manifestOutcomes =
        Arrays.asList(manifestOutcome, serviceManifestOutcome, scalableManifestOutcome, scalingManifestOutcome);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    doReturn(manifestOutcomes).when(ecsStepHelper).getEcsManifestOutcome(manifestsOutcome.values());
    doReturn(manifestOutcome).when(ecsStepHelper).getEcsTaskDefinitionManifestOutcome(manifestOutcomes);
    doReturn(serviceManifestOutcome).when(ecsStepHelper).getEcsServiceDefinitionManifestOutcome(manifestOutcomes);
    doReturn(Arrays.asList(scalableManifestOutcome))
        .when(ecsStepHelper)
        .getManifestOutcomesByType(manifestOutcomes, ManifestType.EcsScalableTargetDefinition);
    doReturn(Arrays.asList(scalingManifestOutcome))
        .when(ecsStepHelper)
        .getManifestOutcomesByType(manifestOutcomes, ManifestType.EcsScalingPolicyDefinition);
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    doReturn(connectorInfoDTO).when(ecsEntityHelper).getConnectorInfoDTO(any(), any());
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(gitStoreDelegateConfig).when(ecsStepCommonHelper).getGitStoreDelegateConfig(any(), any(), any());

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskChainResponse taskChainResponse =
        ecsStepCommonHelper.startChainLink(ecsStepExecutor, ambiance, stepElementParameters, ecsStepHelper);

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        (EcsGitFetchPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(ecsGitFetchPassThroughData.getInfrastructureOutcome())
        .isEqualTo(EcsInfrastructureOutcome.builder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsGitFetchResponseTest() throws Exception {
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsScalableTargetFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .ecsScalingPolicyFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();
    doReturn(taskChainResponse1).when(ecsStepExecutor).executeEcsPrepareRollbackTask(any(), any(), any(), any());

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRolling(ecsStepExecutor, ambiance,
        stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsPrepareRollbackDataPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsPrepareRollbackDataResponseTest() throws Exception {
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .ecsTaskDefinitionManifestContent("taskDef")
            .ecsServiceDefinitionManifestContent("serDef")
            .ecsScalableTargetManifestContentList(Arrays.asList("scalable"))
            .ecsScalingPolicyManifestContentList(Arrays.asList("policy"))
            .build();

    EcsPrepareRollbackDataResult ecsPrepareRollbackDataResult =
        EcsPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .registerScalableTargetRequestBuilderStrings(Arrays.asList("adbd"))
            .registerScalingPolicyRequestBuilderStrings(Arrays.asList("acdh"))
            .build();
    ResponseData responseData = EcsPrepareRollbackDataResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .ecsPrepareRollbackDataResult(ecsPrepareRollbackDataResult)
                                    .build();

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();

    doReturn(taskChainResponse1).when(ecsRollingDeployStep).executeEcsTask(any(), any(), any(), any(), any());

    doReturn("sakdj").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRolling(ecsRollingDeployStep, ambiance,
        stepElementParameters, ecsPrepareRollbackDataPassThroughData, () -> responseData, ecsStepHelper);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsPrepareRollbackDataPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingTaskFailureTest() throws Exception {
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .errorMessage("error")
                                    .taskStatus(TaskStatus.FAILURE)
                                    .build();

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRolling(ecsStepExecutor, ambiance,
        stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        (EcsGitFetchFailurePassThroughData) taskChainResponse.getPassThroughData();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsGitFetchFailurePassThroughData.class);
    assertThat(ecsGitFetchFailurePassThroughData.getErrorMsg()).isEqualTo("error");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkCanaryEcsGitFetchResponseTest() throws Exception {
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsScalableTargetFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .ecsScalingPolicyFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();
    doReturn(taskChainResponse1).when(ecsStepExecutor).executeEcsTask(any(), any(), any(), any(), any());

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkCanary(ecsStepExecutor, ambiance,
        stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsPrepareRollbackDataPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void queueEcsTaskTest() {
    EcsCanaryDeployRequest ecsCommandRequest = EcsCanaryDeployRequest.builder().commandName("command").build();
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.queueEcsTask(
        stepElementParameters, ecsCommandRequest, ambiance, ecsPrepareRollbackDataPassThroughData, false);

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskExceptionTest() throws Exception {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    StepResponse stepResponse =
        ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, new GeneralException("ex"));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureTypes(0)).isEqualTo(APPLICATION_FAILURE);
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskThrowExceptionTest() throws Exception {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    UnitProgressData unitProgressData =
        UnitProgressDataMapper.toUnitProgressData(CommandUnitsProgress.builder().build());
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(new GeneralException("ex"));
    StepResponse stepResponse = ecsStepCommonHelper.handleTaskException(
        ambiance, ecsExecutionPassThroughData, new TaskNGDataException(unitProgressData, sanitizedException));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerInstanceInfoEcsRollingDeployResponseTest() {
    String InfraKey = "infraKey";
    EcsTask ecsTask = EcsTask.builder().clusterArn("arn").launchType("FARGATE").build();
    EcsRollingDeployResult ecsRollingDeployResult =
        EcsRollingDeployResult.builder().region("us-east-1").ecsTasks(Arrays.asList(ecsTask)).build();
    EcsRollingDeployResponse ecsRollingDeployResponse =
        EcsRollingDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .ecsRollingDeployResult(ecsRollingDeployResult)
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    EcsServerInstanceInfo serverInstanceInfo =
        (EcsServerInstanceInfo) ecsStepCommonHelper.getServerInstanceInfos(ecsRollingDeployResponse, InfraKey).get(0);
    assertThat(serverInstanceInfo.getClusterArn()).isEqualTo("arn");
    assertThat(serverInstanceInfo.getLaunchType()).isEqualTo("FARGATE");
    assertThat(serverInstanceInfo.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerInstanceInfoEcsRollingRollbackResponseTest() {
    String InfraKey = "infraKey";
    EcsTask ecsTask = EcsTask.builder().clusterArn("arn").launchType("FARGATE").build();
    EcsRollingRollbackResult ecsRollingRollbackResult =
        EcsRollingRollbackResult.builder().region("us-east-1").ecsTasks(Arrays.asList(ecsTask)).build();
    EcsRollingRollbackResponse ecsRollingRollbackResponse =
        EcsRollingRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .ecsRollingRollbackResult(ecsRollingRollbackResult)
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    EcsServerInstanceInfo serverInstanceInfo =
        (EcsServerInstanceInfo) ecsStepCommonHelper.getServerInstanceInfos(ecsRollingRollbackResponse, InfraKey).get(0);
    assertThat(serverInstanceInfo.getClusterArn()).isEqualTo("arn");
    assertThat(serverInstanceInfo.getLaunchType()).isEqualTo("FARGATE");
    assertThat(serverInstanceInfo.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerInstanceInfoEcsCanaryDeployResponseTest() {
    String InfraKey = "infraKey";
    EcsTask ecsTask = EcsTask.builder().clusterArn("arn").launchType("FARGATE").build();
    EcsCanaryDeployResult ecsCanaryDeployResult =
        EcsCanaryDeployResult.builder().region("us-east-1").ecsTasks(Arrays.asList(ecsTask)).build();
    EcsCanaryDeployResponse ecsCanaryDeployResponse =
        EcsCanaryDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .ecsCanaryDeployResult(ecsCanaryDeployResult)
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    EcsServerInstanceInfo serverInstanceInfo =
        (EcsServerInstanceInfo) ecsStepCommonHelper.getServerInstanceInfos(ecsCanaryDeployResponse, InfraKey).get(0);
    assertThat(serverInstanceInfo.getClusterArn()).isEqualTo("arn");
    assertThat(serverInstanceInfo.getLaunchType()).isEqualTo("FARGATE");
    assertThat(serverInstanceInfo.getRegion()).isEqualTo("us-east-1");
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerInstanceInfoExceptionTest() {
    String InfraKey = "infraKey";
    EcsCanaryDeleteResponse ecsCanaryDeleteResponse = EcsCanaryDeleteResponse.builder().build();
    ecsStepCommonHelper.getServerInstanceInfos(ecsCanaryDeleteResponse, InfraKey);
  }
}
