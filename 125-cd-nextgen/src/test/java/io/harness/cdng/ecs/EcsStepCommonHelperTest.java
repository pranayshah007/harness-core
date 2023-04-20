/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.SAINATH;

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsManifestsContent;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsRunTaskManifestsContent;
import io.harness.cdng.ecs.beans.EcsRunTaskS3FileConfigs;
import io.harness.cdng.ecs.beans.EcsS3FetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsS3FetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsS3ManifestFileConfigs;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.EcsScalableTargetDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsScalingPolicyDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsServiceDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
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
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;
import io.harness.delegate.task.ecs.request.EcsCanaryDeployRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchRunTaskResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchRunTaskResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
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
  private EcsStepHelperImpl ecsStepHelper = new EcsStepHelperImpl();
  private final String content = "content";

  @Mock private OutcomeService outcomeService;
  @Mock private EcsEntityHelper ecsEntityHelper;
  @Mock private EcsStepUtils ecsStepUtils;
  @Mock private StepHelper stepHelper;
  @Mock private EcsStepExecutor ecsStepExecutor;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private LogCallback logCallback;
  @Mock private FileStoreService fileStoreService;
  @Mock private EcsBlueGreenCreateServiceStep ecsBlueGreenCreateServiceStep;
  @Mock private EcsRollingDeployStep ecsRollingDeployStep;
  @Mock private EcsCanaryDeployStep ecsCanaryDeployStep;
  @Mock private TaskRequestsUtils TaskRequestsUtils;
  @Mock private CDExpressionResolver cdExpressionResolver;

  @Spy @InjectMocks private EcsStepCommonHelper ecsStepCommonHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkTest() {
    GitStoreConfig gitStoreConfig = GitStore.builder()
                                        .connectorRef(ParameterField.createValueField("sfad"))
                                        .folderPath(ParameterField.createValueField("folderPath"))
                                        .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(gitStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().build();
    doReturn(connectorInfoDTO).when(ecsEntityHelper).getConnectorInfoDTO(any(), any());
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder().build();
    doReturn(gitStoreDelegateConfig).when(ecsStepCommonHelper).getGitStoreDelegateConfig(any(), any(), any());

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse =
          ecsStepCommonHelper.startChainLink(ecsStepExecutor, ambiance, stepElementParameters, ecsStepHelper);

      aStatic.verify(
          () -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()), times(1));

      EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
          (EcsGitFetchPassThroughData) taskChainResponse.getPassThroughData();
      assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
      assertThat(ecsGitFetchPassThroughData.getInfrastructureOutcome())
          .isEqualTo(EcsInfrastructureOutcome.builder().build());
      assertNull(ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs());
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkAllS3StoreTest() {
    S3StoreConfig s3StoreConfig = S3StoreConfig.builder()
                                      .connectorRef(ParameterField.createValueField("sfad"))
                                      .paths(ParameterField.createValueField(Arrays.asList("folderPath")))
                                      .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(s3StoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(s3StoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(s3StoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(s3StoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).connectorType(ConnectorType.AWS).build();
    doReturn(connectorInfoDTO).when(ecsEntityHelper).getConnectorInfoDTO(any(), any());
    S3StoreDelegateConfig s3StoreDelegateConfig = S3StoreDelegateConfig.builder().build();
    doReturn(s3StoreDelegateConfig)
        .when(ecsStepCommonHelper)
        .getS3StoreDelegateConfig(eq(s3StoreConfig), any(), eq(ambiance));

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse =
          ecsStepCommonHelper.startChainLink(ecsStepExecutor, ambiance, stepElementParameters, ecsStepHelper);

      aStatic.verify(
          () -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()), times(1));

      EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
          (EcsS3FetchPassThroughData) taskChainResponse.getPassThroughData();

      assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
      assertThat(ecsS3FetchPassThroughData.getInfrastructureOutcome()).isEqualTo(infrastructureOutcome);
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkLocalStoreExceptionTest() {
    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList("Ecs/sample/ecs.yaml")).build())
            .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    ecsStepCommonHelper.startChainLink(ecsStepExecutor, ambiance, stepElementParameters, ecsStepHelper);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkLocalStoreEcsRollingDeployStepTest() {
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(unitProgressData)
        .when(ecsStepCommonHelper)
        .getCommandUnitProgressData(EcsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList("Ecs/sample/ecs.yaml")).build())
            .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    Optional<FileStoreNodeDTO> manifestFile = Optional.of(FileNodeDTO.builder().content("content").build());
    doReturn(manifestFile).when(fileStoreService).getWithChildrenByPath(any(), any(), any(), any(), anyBoolean());
    doReturn(content).when(engineExpressionService).renderExpression(any(), any());

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(any(), any());

    ecsStepCommonHelper.startChainLink(ecsRollingDeployStep, ambiance, stepElementParameters, ecsStepHelper);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(infrastructureOutcome)
            .ecsTaskDefinitionManifestContent(content)
            .ecsServiceDefinitionManifestContent(content)
            .ecsScalableTargetManifestContentList(Arrays.asList(content))
            .ecsScalingPolicyManifestContentList(Arrays.asList(content))
            .build();

    verify(ecsRollingDeployStep)
        .executeEcsPrepareRollbackTask(
            ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkLocalStoreEcsBlueGreenCreateServiceStepTest() {
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(unitProgressData)
        .when(ecsStepCommonHelper)
        .getCommandUnitProgressData(EcsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList("Ecs/sample/ecs.yaml")).build())
            .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    Optional<FileStoreNodeDTO> manifestFile = Optional.of(FileNodeDTO.builder().content("content").build());
    doReturn(manifestFile).when(fileStoreService).getWithChildrenByPath(any(), any(), any(), any(), anyBoolean());

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(any(), any());

    ecsStepCommonHelper.startChainLink(ecsBlueGreenCreateServiceStep, ambiance, stepElementParameters, ecsStepHelper);

    verify(ecsBlueGreenCreateServiceStep)
        .executeEcsPrepareRollbackTask(eq(ambiance), eq(stepElementParameters), any(), eq(unitProgressData));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkLocalStoreEcsCanaryDeployStepTest() {
    HarnessStore harnessStoreConfig =
        HarnessStore.builder()
            .files(ParameterField.<List<String>>builder().value(Arrays.asList("Ecs/sample/ecs.yaml")).build())
            .build();
    ManifestOutcome taskManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome serviceManifestOutcome =
        EcsServiceDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalableManifestOutcome =
        EcsScalableTargetDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingManifestOutcome =
        EcsScalingPolicyDefinitionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("task", taskManifestOutcome);
    manifestOutcomeMap.put("service", serviceManifestOutcome);
    manifestOutcomeMap.put("scalable", scalableManifestOutcome);
    manifestOutcomeMap.put("scaling", scalingManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(ecsStepCommonHelper).resolveEcsManifestsOutcome(ambiance);
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(ecsStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    Optional<FileStoreNodeDTO> manifestFile = Optional.of(FileNodeDTO.builder().content(content).build());
    doReturn(manifestFile).when(fileStoreService).getWithChildrenByPath(any(), any(), any(), any(), anyBoolean());
    doReturn(content).when(engineExpressionService).renderExpression(any(), any());

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(any(), any());

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(unitProgressData)
        .when(ecsStepCommonHelper)
        .getCommandUnitProgressData(EcsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    ecsStepCommonHelper.startChainLink(ecsCanaryDeployStep, ambiance, stepElementParameters, ecsStepHelper);

    EcsExecutionPassThroughData executionPassThroughData = EcsExecutionPassThroughData.builder()
                                                               .infrastructure(infrastructureOutcome)
                                                               .lastActiveUnitProgressData(unitProgressData)
                                                               .build();

    EcsStepExecutorParams ecsStepExecutorParams = EcsStepExecutorParams.builder()
                                                      .shouldOpenFetchFilesLogStream(false)
                                                      .ecsTaskDefinitionManifestContent(content)
                                                      .ecsServiceDefinitionManifestContent(content)
                                                      .ecsScalableTargetManifestContentList(Arrays.asList(content))
                                                      .ecsScalingPolicyManifestContentList(Arrays.asList(content))
                                                      .build();

    verify(ecsCanaryDeployStep)
        .executeEcsTask(
            ambiance, stepElementParameters, executionPassThroughData, unitProgressData, ecsStepExecutorParams);
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
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkRolling(ecsStepExecutor, ambiance, stepElementParameters,
        ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(gitFile.getFileContent())
            .ecsServiceDefinitionManifestContent(gitFile.getFileContent())
            .ecsScalableTargetManifestContentList(Arrays.asList(gitFile.getFileContent()))
            .ecsScalingPolicyManifestContentList(Arrays.asList(gitFile.getFileContent()))
            .build();
    verify(ecsStepExecutor)
        .executeEcsPrepareRollbackTask(ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData,
            ecsGitFetchResponse.getUnitProgressData());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsGitFetchResponseS3ConfigFileTest() throws Exception {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region("us-east-1").bucketName("bucket").build();

    EcsS3FetchFileConfig ecsS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                    .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                    .succeedIfFileNotFound(false)
                                                    .build();

    EcsS3ManifestFileConfigs ecsS3ManifestFileConfigs =
        EcsS3ManifestFileConfigs.builder()
            .ecsS3ServiceDefinitionFileConfig(ecsS3FetchFileConfig)
            .ecsS3ScalableTargetFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .ecsS3ScalingPolicyFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .build();

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().ecsS3ManifestFileConfigs(ecsS3ManifestFileConfigs).build();

    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRolling(ecsStepExecutor, ambiance,
          stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      EcsS3FetchRequest ecsS3FetchRequest =
          EcsS3FetchRequest.builder()
              .accountId(accountId)
              .ecsServiceDefinitionS3FetchFileConfig(ecsS3ManifestFileConfigs.getEcsS3ServiceDefinitionFileConfig())
              .ecsScalableTargetS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalableTargetFileConfigs())
              .ecsScalingPolicyS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalingPolicyFileConfigs())
              .shouldOpenLogStream(false)
              .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {ecsS3FetchRequest})
                                    .build();
      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));

      assertThat(((EcsS3FetchPassThroughData) taskChainResponse.getPassThroughData())
                     .getEcsOtherStoreContents()
                     .getEcsTaskDefinitionFileContent())
          .isEqualTo(fetchFilesResult.getFiles().get(0).getFileContent());
    }
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

    doReturn("sakdj").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkRolling(ecsRollingDeployStep, ambiance, stepElementParameters,
        ecsPrepareRollbackDataPassThroughData, () -> responseData, ecsStepHelper);

    EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse = (EcsPrepareRollbackDataResponse) responseData;
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsPrepareRollbackDataPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsPrepareRollbackDataResponse.getUnitProgressData())
            .build();
    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(
                ecsPrepareRollbackDataPassThroughData.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(
                ecsPrepareRollbackDataPassThroughData.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(
                ecsPrepareRollbackDataPassThroughData.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(
                ecsPrepareRollbackDataPassThroughData.getEcsScalingPolicyManifestContentList())
            .build();
    verify(ecsRollingDeployStep)
        .executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
            ecsPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
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
  public void executeNextLinkBlueGreenResponseEcsGitFetchResponseTest() throws Exception {
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().targetGroupArnKey("harnessKey").build();

    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn("sakdj").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkBlueGreen(
        ecsBlueGreenCreateServiceStep, ambiance, stepElementParameters, ecsGitFetchPassThroughData, () -> responseData);

    EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(gitFile.getFileContent())
            .ecsServiceDefinitionManifestContent(gitFile.getFileContent())
            .ecsScalableTargetManifestContentList(Arrays.asList())
            .ecsScalingPolicyManifestContentList(Arrays.asList())
            .targetGroupArnKey(ecsGitFetchPassThroughData.getTargetGroupArnKey())
            .build();
    verify(ecsBlueGreenCreateServiceStep)
        .executeEcsPrepareRollbackTask(ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData,
            ecsGitFetchResponse.getUnitProgressData());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkBlueGreenEcsGitFetchResponseS3ConfigFileTest() throws Exception {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region("us-east-1").bucketName("bucket").build();

    EcsS3FetchFileConfig ecsS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                    .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                    .succeedIfFileNotFound(false)
                                                    .build();

    EcsS3ManifestFileConfigs ecsS3ManifestFileConfigs =
        EcsS3ManifestFileConfigs.builder()
            .ecsS3ServiceDefinitionFileConfig(ecsS3FetchFileConfig)
            .ecsS3ScalableTargetFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .ecsS3ScalingPolicyFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .build();

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().ecsS3ManifestFileConfigs(ecsS3ManifestFileConfigs).build();

    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkBlueGreen(
          ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData, () -> responseData);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      EcsS3FetchRequest ecsS3FetchRequest =
          EcsS3FetchRequest.builder()
              .accountId(accountId)
              .ecsServiceDefinitionS3FetchFileConfig(ecsS3ManifestFileConfigs.getEcsS3ServiceDefinitionFileConfig())
              .ecsScalableTargetS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalableTargetFileConfigs())
              .ecsScalingPolicyS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalingPolicyFileConfigs())
              .shouldOpenLogStream(false)
              .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {ecsS3FetchRequest})
                                    .build();
      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));

      assertThat(((EcsS3FetchPassThroughData) taskChainResponse.getPassThroughData())
                     .getEcsOtherStoreContents()
                     .getEcsTaskDefinitionFileContent())
          .isEqualTo(fetchFilesResult.getFiles().get(0).getFileContent());
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkCanaryEcsGitFetchResponseTest() throws Exception {
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent(content).build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsScalableTargetFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .ecsScalingPolicyFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkCanary(ecsStepExecutor, ambiance, stepElementParameters,
        ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsGitFetchResponse.getUnitProgressData())
            .build();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(gitFile.getFileContent())
            .ecsServiceDefinitionManifestContent(gitFile.getFileContent())
            .ecsScalableTargetManifestContentList(Arrays.asList(gitFile.getFileContent()))
            .ecsScalingPolicyManifestContentList(Arrays.asList(gitFile.getFileContent()))
            .build();
    verify(ecsStepExecutor)
        .executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
            ecsGitFetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkCanaryEcsGitFetchResponseS3ConfigFileTest() throws Exception {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region("us-east-1").bucketName("bucket").build();

    EcsS3FetchFileConfig ecsS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                    .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                    .succeedIfFileNotFound(false)
                                                    .build();

    EcsS3ManifestFileConfigs ecsS3ManifestFileConfigs =
        EcsS3ManifestFileConfigs.builder()
            .ecsS3ServiceDefinitionFileConfig(ecsS3FetchFileConfig)
            .ecsS3ScalableTargetFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .ecsS3ScalingPolicyFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
            .build();

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().ecsS3ManifestFileConfigs(ecsS3ManifestFileConfigs).build();

    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();
    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkCanary(ecsStepExecutor, ambiance,
          stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      EcsS3FetchRequest ecsS3FetchRequest =
          EcsS3FetchRequest.builder()
              .accountId(accountId)
              .ecsServiceDefinitionS3FetchFileConfig(ecsS3ManifestFileConfigs.getEcsS3ServiceDefinitionFileConfig())
              .ecsScalableTargetS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalableTargetFileConfigs())
              .ecsScalingPolicyS3FetchFileConfigs(ecsS3ManifestFileConfigs.getEcsS3ScalingPolicyFileConfigs())
              .shouldOpenLogStream(false)
              .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {ecsS3FetchRequest})
                                    .build();
      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));

      assertThat(((EcsS3FetchPassThroughData) taskChainResponse.getPassThroughData())
                     .getEcsOtherStoreContents()
                     .getEcsTaskDefinitionFileContent())
          .isEqualTo(fetchFilesResult.getFiles().get(0).getFileContent());
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkBlueGreenResponseEcsBlueGreenPrepareRollbackDataResponseTest() throws Exception {
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .ecsTaskDefinitionManifestContent("taskDef")
            .ecsServiceDefinitionManifestContent("serDef")
            .ecsScalableTargetManifestContentList(Arrays.asList("scalable"))
            .ecsScalingPolicyManifestContentList(Arrays.asList("policy"))
            .build();

    EcsLoadBalancerConfig ecsLoadBalancerConfig = EcsLoadBalancerConfig.builder()
                                                      .prodTargetGroupArn("prodTargetGroupArn")
                                                      .prodListenerRuleArn("prodListenerRuleArn")
                                                      .stageTargetGroupArn("stageTargetGroupArn")
                                                      .stageListenerRuleArn("stageListenerRuleArn")
                                                      .stageListenerArn("stageListenerArn")
                                                      .build();
    EcsBlueGreenPrepareRollbackDataResult ecsPrepareRollbackDataResult =
        EcsBlueGreenPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .registerScalableTargetRequestBuilderStrings(Arrays.asList("adbd"))
            .registerScalingPolicyRequestBuilderStrings(Arrays.asList("acdh"))
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    ResponseData responseData = EcsBlueGreenPrepareRollbackDataResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .ecsBlueGreenPrepareRollbackDataResult(ecsPrepareRollbackDataResult)
                                    .build();

    TaskChainResponse taskChainResponseMock = TaskChainResponse.builder()
                                                  .chainEnd(false)
                                                  .taskRequest(TaskRequest.newBuilder().build())
                                                  .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                                  .build();

    doReturn("sakdj").when(executionSweepingOutputService).consume(any(), any(), any(), any());

    doReturn(taskChainResponseMock)
        .when(ecsBlueGreenCreateServiceStep)
        .executeEcsTask(any(), any(), any(), any(), any());

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkBlueGreen(ecsBlueGreenCreateServiceStep,
        ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, () -> responseData);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    assertThat(((EcsPrepareRollbackDataPassThroughData) taskChainResponse.getPassThroughData())
                   .getEcsTaskDefinitionManifestContent())
        .isEqualTo(ecsPrepareRollbackDataPassThroughData.getEcsTaskDefinitionManifestContent());
    assertThat(((EcsPrepareRollbackDataPassThroughData) taskChainResponse.getPassThroughData())
                   .getEcsServiceDefinitionManifestContent())
        .isEqualTo(ecsPrepareRollbackDataPassThroughData.getEcsServiceDefinitionManifestContent());
    assertThat(((EcsPrepareRollbackDataPassThroughData) taskChainResponse.getPassThroughData())
                   .getEcsScalableTargetManifestContentList())
        .isEqualTo(ecsPrepareRollbackDataPassThroughData.getEcsScalableTargetManifestContentList());
    assertThat(((EcsPrepareRollbackDataPassThroughData) taskChainResponse.getPassThroughData())
                   .getEcsScalingPolicyManifestContentList())
        .isEqualTo(ecsPrepareRollbackDataPassThroughData.getEcsScalingPolicyManifestContentList());
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsS3FetchResponseTest() throws Exception {
    EcsManifestsContent ecsOtherStoreContent = EcsManifestsContent.builder().build();
    EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
        EcsS3FetchPassThroughData.builder().ecsOtherStoreContents(ecsOtherStoreContent).build();
    ResponseData responseData = EcsS3FetchResponse.builder()
                                    .ecsS3TaskDefinitionContent(content)
                                    .ecsS3ServiceDefinitionContent(content)
                                    .ecsS3ScalableTargetContents(Arrays.asList(content))
                                    .ecsS3ScalingPolicyContents(Arrays.asList(content))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkRolling(ecsRollingDeployStep, ambiance, stepElementParameters,
        ecsS3FetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .ecsTaskDefinitionManifestContent(ecsS3FetchResponse.getEcsS3TaskDefinitionContent())
            .ecsServiceDefinitionManifestContent(ecsS3FetchResponse.getEcsS3ServiceDefinitionContent())
            .ecsScalableTargetManifestContentList(ecsS3FetchResponse.getEcsS3ScalableTargetContents())
            .ecsScalingPolicyManifestContentList(ecsS3FetchResponse.getEcsS3ScalingPolicyContents())
            .build();
    verify(ecsRollingDeployStep)
        .executeEcsPrepareRollbackTask(ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData,
            ecsS3FetchResponse.getUnitProgressData());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsS3FetchResponseOtherStoreTest() throws Exception {
    EcsManifestsContent ecsOtherStoreContent = EcsManifestsContent.builder()
                                                   .ecsTaskDefinitionFileContent(content)
                                                   .ecsServiceDefinitionFileContent(content)
                                                   .ecsScalableTargetManifestContentList(Arrays.asList(content))
                                                   .ecsScalingPolicyManifestContentList(Arrays.asList(content))
                                                   .build();

    EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
        EcsS3FetchPassThroughData.builder().ecsOtherStoreContents(ecsOtherStoreContent).build();
    ResponseData responseData = EcsS3FetchResponse.builder()
                                    .ecsS3ScalableTargetContents(Arrays.asList(content))
                                    .ecsS3ScalingPolicyContents(Arrays.asList(content))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkRolling(ecsRollingDeployStep, ambiance, stepElementParameters,
        ecsS3FetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .ecsTaskDefinitionManifestContent(ecsOtherStoreContent.getEcsTaskDefinitionFileContent())
            .ecsServiceDefinitionManifestContent(ecsOtherStoreContent.getEcsServiceDefinitionFileContent())
            .ecsScalableTargetManifestContentList(Arrays.asList(content, content))
            .ecsScalingPolicyManifestContentList(Arrays.asList(content, content))
            .build();
    verify(ecsRollingDeployStep)
        .executeEcsPrepareRollbackTask(ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData,
            ecsS3FetchResponse.getUnitProgressData());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRollingEcsS3FetchResponseFailureTest() throws Exception {
    EcsManifestsContent ecsOtherStoreContent = EcsManifestsContent.builder().build();
    EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
        EcsS3FetchPassThroughData.builder().ecsOtherStoreContents(ecsOtherStoreContent).build();
    ResponseData responseData =
        EcsS3FetchResponse.builder().errorMessage("error").taskStatus(TaskStatus.FAILURE).build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRolling(ecsRollingDeployStep, ambiance,
        stepElementParameters, ecsS3FetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
    EcsS3FetchFailurePassThroughData ecsS3FetchFailurePassThroughData =
        EcsS3FetchFailurePassThroughData.builder()
            .errorMsg(ecsS3FetchResponse.getErrorMessage())
            .unitProgressData(ecsS3FetchResponse.getUnitProgressData())
            .build();

    assertTrue(taskChainResponse.isChainEnd());
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsS3FetchFailurePassThroughData);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkCanaryEcsS3FetchResponseTest() throws Exception {
    EcsManifestsContent ecsOtherStoreContent = EcsManifestsContent.builder().build();
    EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
        EcsS3FetchPassThroughData.builder().ecsOtherStoreContents(ecsOtherStoreContent).build();
    ResponseData responseData = EcsS3FetchResponse.builder()
                                    .ecsS3TaskDefinitionContent(content)
                                    .ecsS3ServiceDefinitionContent(content)
                                    .ecsS3ScalableTargetContents(Arrays.asList(content))
                                    .ecsS3ScalingPolicyContents(Arrays.asList(content))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkCanary(ecsCanaryDeployStep, ambiance, stepElementParameters,
        ecsS3FetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsS3FetchResponse.getUnitProgressData())
            .build();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsS3FetchResponse.getEcsS3TaskDefinitionContent())
            .ecsServiceDefinitionManifestContent(ecsS3FetchResponse.getEcsS3ServiceDefinitionContent())
            .ecsScalableTargetManifestContentList(ecsS3FetchResponse.getEcsS3ScalableTargetContents())
            .ecsScalingPolicyManifestContentList(ecsS3FetchResponse.getEcsS3ScalingPolicyContents())
            .build();

    verify(ecsCanaryDeployStep)
        .executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
            ecsS3FetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkBlueGreenEcsS3FetchResponseTest() throws Exception {
    EcsManifestsContent ecsOtherStoreContent =
        EcsManifestsContent.builder().ecsServiceDefinitionFileContent(content).build();
    EcsS3FetchPassThroughData ecsS3FetchPassThroughData = EcsS3FetchPassThroughData.builder()
                                                              .ecsOtherStoreContents(ecsOtherStoreContent)
                                                              .otherStoreTargetGroupArnKey("otherKey")
                                                              .build();
    ResponseData responseData = EcsS3FetchResponse.builder()
                                    .ecsS3TaskDefinitionContent(content)
                                    .ecsS3ScalableTargetContents(Arrays.asList(content))
                                    .ecsS3ScalingPolicyContents(Arrays.asList(content))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkBlueGreen(
        ecsBlueGreenCreateServiceStep, ambiance, stepElementParameters, ecsS3FetchPassThroughData, () -> responseData);

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .ecsTaskDefinitionManifestContent(ecsS3FetchResponse.getEcsS3TaskDefinitionContent())
            .ecsServiceDefinitionManifestContent(ecsOtherStoreContent.getEcsServiceDefinitionFileContent())
            .ecsScalableTargetManifestContentList(ecsS3FetchResponse.getEcsS3ScalableTargetContents())
            .ecsScalingPolicyManifestContentList(ecsS3FetchResponse.getEcsS3ScalingPolicyContents())
            .targetGroupArnKey(ecsS3FetchPassThroughData.getOtherStoreTargetGroupArnKey())
            .build();
    verify(ecsBlueGreenCreateServiceStep)
        .executeEcsPrepareRollbackTask(ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData,
            ecsS3FetchResponse.getUnitProgressData());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void queueEcsTaskTest() {
    EcsCanaryDeployRequest ecsCommandRequest = EcsCanaryDeployRequest.builder().commandName("command").build();
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse = ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsCommandRequest,
          ambiance, ecsPrepareRollbackDataPassThroughData, false, TaskType.ECS_COMMAND_TASK_NG);

      aStatic.verify(
          () -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()), times(1));

      assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
      assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    }
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

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void startChainLinkRunTaskS3StoreTest() {
    S3StoreConfig s3StoreConfig = S3StoreConfig.builder()
                                      .connectorRef(ParameterField.createValueField("sfad"))
                                      .paths(ParameterField.createValueField(Arrays.asList("folderPath")))
                                      .build();
    ParameterField<StoreConfigWrapper> storeConfigWrapper =
        ParameterField.<StoreConfigWrapper>builder()
            .value(StoreConfigWrapper.builder().spec(s3StoreConfig).build())
            .build();
    EcsRunTaskStepParameters ecsRunTaskStepParameters = EcsRunTaskStepParameters.infoBuilder()
                                                            .taskDefinition(storeConfigWrapper)
                                                            .runTaskRequestDefinition(storeConfigWrapper)
                                                            .build();
    StepElementParameters stepParameters = StepElementParameters.builder().spec(ecsRunTaskStepParameters).build();
    doReturn(logCallback)
        .when(ecsStepCommonHelper)
        .getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    InfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).connectorType(ConnectorType.AWS).build();
    doReturn(connectorInfoDTO).when(ecsEntityHelper).getConnectorInfoDTO(any(), any());
    S3StoreDelegateConfig s3StoreDelegateConfig = S3StoreDelegateConfig.builder().build();
    doReturn(s3StoreDelegateConfig)
        .when(ecsStepCommonHelper)
        .getS3StoreDelegateConfig(eq(s3StoreConfig), any(), eq(ambiance));

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      EcsStepHelperImpl ecsStepHelperImpl = new EcsStepHelperImpl();
      ecsStepCommonHelper.startChainLinkEcsRunTask(ecsStepExecutor, ambiance, stepParameters, ecsStepHelperImpl);

      EcsS3FetchFileConfig runTaskDefinitionS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                                    .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                    .succeedIfFileNotFound(false)
                                                                    .build();

      EcsS3FetchFileConfig runTaskRequestDefinitionS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                                           .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                           .succeedIfFileNotFound(false)
                                                                           .build();

      EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest =
          EcsS3FetchRunTaskRequest.builder()
              .runTaskDefinitionS3FetchFileConfig(runTaskDefinitionS3FetchFileConfig)
              .runTaskRequestDefinitionS3FetchFileConfig(runTaskRequestDefinitionS3FetchFileConfig)
              .shouldOpenLogStream(false)
              .build();
      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {ecsS3FetchRunTaskRequest})
                                    .build();
      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));
    }
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRunTaskEcsS3FetchResponseTest() throws Exception {
    EcsRunTaskManifestsContent ecsOtherStoreRunTaskContent =
        EcsRunTaskManifestsContent.builder().runTaskDefinitionFileContent(content).build();
    EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
        EcsS3FetchPassThroughData.builder().ecsOtherStoreRunTaskContent(ecsOtherStoreRunTaskContent).build();
    ResponseData responseData = EcsS3FetchRunTaskResponse.builder()
                                    .runTaskRequestDefinitionFileContent(content)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();

    doReturn(content).when(engineExpressionService).renderExpression(any(), eq(content));

    ecsStepCommonHelper.executeNextLinkRunTask(
        ecsStepExecutor, ambiance, stepElementParameters, ecsS3FetchPassThroughData, () -> responseData, ecsStepHelper);

    EcsS3FetchRunTaskResponse ecsS3FetchRunTaskResponse = (EcsS3FetchRunTaskResponse) responseData;

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsOtherStoreRunTaskContent.getRunTaskDefinitionFileContent())
            .ecsRunTaskRequestDefinitionManifestContent(
                ecsS3FetchRunTaskResponse.getRunTaskRequestDefinitionFileContent())
            .build();

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsS3FetchRunTaskResponse.getUnitProgressData())
            .build();
    verify(ecsStepExecutor)
        .executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
            ecsS3FetchRunTaskResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkRunTaskEcsGitFetchResponseS3ConfigFilesTest() throws Exception {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region("us-east-1").bucketName("bucket").build();

    EcsS3FetchFileConfig runTaskRequestDefinitionS3FetchFileConfig = EcsS3FetchFileConfig.builder()
                                                                         .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                         .succeedIfFileNotFound(false)
                                                                         .build();

    EcsRunTaskS3FileConfigs ecsRunTaskS3FileConfigs =
        EcsRunTaskS3FileConfigs.builder()
            .runTaskRequestDefinitionS3FetchFileConfig(runTaskRequestDefinitionS3FetchFileConfig)
            .build();

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().ecsRunTaskS3FileConfigs(ecsRunTaskS3FileConfigs).build();

    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent(content).build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchRunTaskResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();
    try (MockedStatic<TaskRequestsUtils> aStatic = mockStatic(TaskRequestsUtils.class)) {
      when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(TaskRequest.newBuilder().build());

      TaskChainResponse taskChainResponse = ecsStepCommonHelper.executeNextLinkRunTask(ecsStepExecutor, ambiance,
          stepElementParameters, ecsGitFetchPassThroughData, () -> responseData, ecsStepHelper);

      EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest =
          EcsS3FetchRunTaskRequest.builder()
              .runTaskRequestDefinitionS3FetchFileConfig(runTaskRequestDefinitionS3FetchFileConfig)
              .shouldOpenLogStream(false)
              .build();
      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                    .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                    .parameters(new Object[] {ecsS3FetchRunTaskRequest})
                                    .build();
      aStatic.verify(
          ()
              -> TaskRequestsUtils.prepareCDTaskRequest(any(), eq(taskData), any(), any(), any(), any(), any()),
          times(1));

      assertThat(((EcsS3FetchPassThroughData) taskChainResponse.getPassThroughData())
                     .getEcsOtherStoreRunTaskContent()
                     .getRunTaskDefinitionFileContent())
          .isEqualTo(ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent());
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetEcsGitFetchRunTaskFileConfigWithNonGit() {
    StoreConfig storeConfig = HarnessStore.builder().build();
    ManifestOutcome manifestOutcome = EcsTaskDefinitionManifestOutcome.builder().store(storeConfig).build();
    ecsStepCommonHelper.getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, null, null);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetGitFetchFileRunTaskResponse() {
    TaskRequest taskRequest = TaskRequest.getDefaultInstance();
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    try (MockedStatic<TaskRequestsUtils> stepUtilsStaticMock = mockStatic(TaskRequestsUtils.class)) {
      stepUtilsStaticMock
          .when(() -> TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(taskRequest);
      TaskChainResponse taskChainResponse = ecsStepCommonHelper.getGitFetchFileRunTaskResponse(
          ambiance, false, stepElementParameters, ecsGitFetchPassThroughData, null, null);
      assertThat(taskChainResponse.getTaskRequest()).isEqualTo(taskRequest);
      assertThat(taskChainResponse.isChainEnd()).isFalse();
      assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsGitFetchPassThroughData);
    }
  }
}
