/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AsgConfigurationManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgLaunchTemplateManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgScalingPolicyManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgScheduledUpdateGroupActionManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployRequest;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.exception.GeneralException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
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

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.Collections;
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
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class AsgStepCommonHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
          .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
          .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
          .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
          .build();
  private final AsgRollingDeployStepParameters asgSpecParameters = AsgRollingDeployStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters =
          StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  private final String content = "content";
  @Mock private OutcomeService outcomeService;

  @Mock private StepHelper stepHelper;
  @Mock private AsgStepExecutor asgStepExecutor;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private LogCallback logCallback;
  @Mock private FileStoreService fileStoreService;
  @Mock private AsgCanaryDeployStep asgCanaryDeployStep;
  @Mock private StepUtils stepUtils;

  @Spy @InjectMocks private AsgStepCommonHelper asgStepCommonHelper;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void startChainLinkTest() {
    HarnessStore harnessStoreConfig =
            HarnessStore.builder()
                    .files(ParameterField.<List<String>>builder().value(Arrays.asList("Asg/sample/asg.yaml")).build())
                    .build();
    ManifestOutcome launchTemplateManifestOutcome =
            AsgLaunchTemplateManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome configurationManifestOutcome =
            AsgConfigurationManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingPolicyManifestOutcome =
            AsgScalingPolicyManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scheduledUpdateGroupActionManifestOutcome =
            AsgScheduledUpdateGroupActionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("lauchTemplate", launchTemplateManifestOutcome);
    manifestOutcomeMap.put("configuration", configurationManifestOutcome);
    manifestOutcomeMap.put("scalingPolicy", scalingPolicyManifestOutcome);
    manifestOutcomeMap.put("scheduledUpdate", scheduledUpdateGroupActionManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(asgStepCommonHelper).resolveAsgManifestsOutcome(ambiance);
    doReturn(logCallback)
            .when(asgStepCommonHelper)
            .getLogCallback(AsgCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(asgStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(TaskRequest.newBuilder().build());

    TaskChainResponse taskChainResponse =
            asgStepCommonHelper.startChainLink(asgStepExecutor, ambiance, stepElementParameters);

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    AsgExecutionPassThroughData asgExecutionPassThroughData =
            (AsgExecutionPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(asgExecutionPassThroughData.getInfrastructure()).isEqualTo(AsgInfrastructureOutcome.builder().build());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void startChainLinkLocalStoreEcsCanaryDeployStepTest() {
    HarnessStore harnessStoreConfig =
            HarnessStore.builder()
                    .files(ParameterField.<List<String>>builder().value(Arrays.asList("Asg/sample/asg.yaml")).build())
                    .build();
    ManifestOutcome launchTemplateManifestOutcome =
            AsgLaunchTemplateManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome configurationManifestOutcome =
            AsgConfigurationManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scalingPolicyManifestOutcome =
            AsgScalingPolicyManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();
    ManifestOutcome scheduledUpdateGroupActionManifestOutcome =
            AsgScheduledUpdateGroupActionManifestOutcome.builder().identifier("sadf").store(harnessStoreConfig).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = new HashMap<>();
    manifestOutcomeMap.put("lauchTemplate", launchTemplateManifestOutcome);
    manifestOutcomeMap.put("configuration", configurationManifestOutcome);
    manifestOutcomeMap.put("scalingPolicy", scalingPolicyManifestOutcome);
    manifestOutcomeMap.put("scheduledUpdate", scheduledUpdateGroupActionManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeMap);
    doReturn(manifestsOutcome).when(asgStepCommonHelper).resolveAsgManifestsOutcome(ambiance);
    doReturn(logCallback)
            .when(asgStepCommonHelper)
            .getLogCallback(AsgCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    doNothing().when(asgStepCommonHelper).validateManifestsOutcome(ambiance, manifestsOutcome);
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    InfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());

    Optional<FileStoreNodeDTO> manifestFile = Optional.of(FileNodeDTO.builder().content(content).build());
    doReturn(manifestFile).when(fileStoreService).getWithChildrenByPath(any(), any(), any(), any(), anyBoolean());
    doReturn(content).when(engineExpressionService).renderExpression(any(), any());

    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(unitProgressData)
            .when(asgStepCommonHelper)
            .getCommandUnitProgressData(AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    asgStepCommonHelper.startChainLink(asgCanaryDeployStep, ambiance, stepElementParameters);

    AsgExecutionPassThroughData executionPassThroughData = AsgExecutionPassThroughData.builder()
            .infrastructure(infrastructureOutcome)
            .lastActiveUnitProgressData(unitProgressData)
            .build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList(content));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList(content));

    AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .asgStoreManifestsContent(asgStoreManifestsContent)
            .build();

    verify(asgCanaryDeployStep)
            .executeAsgTask(
                    ambiance, stepElementParameters, executionPassThroughData, unitProgressData, asgStepExecutorParams);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskExceptionTest() throws Exception {
    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    AsgExecutionPassThroughData asgExecutionPassThroughData =
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    StepResponse stepResponse =
            asgStepCommonHelper.handleTaskException(ambiance, asgExecutionPassThroughData, new GeneralException("ex"));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureTypes(0)).isEqualTo(APPLICATION_FAILURE);
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskThrowExceptionTest() throws Exception {
    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    AsgExecutionPassThroughData asgExecutionPassThroughData =
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    UnitProgressData unitProgressData =
            UnitProgressDataMapper.toUnitProgressData(CommandUnitsProgress.builder().build());
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(new GeneralException("ex"));
    StepResponse stepResponse = asgStepCommonHelper.handleTaskException(
            ambiance, asgExecutionPassThroughData, new TaskNGDataException(unitProgressData, sanitizedException));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void queueAsgTaskTest() {
    AsgCanaryDeployRequest asgCommandRequest = AsgCanaryDeployRequest.builder().commandName("command").build();
    AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData =
            AsgPrepareRollbackDataPassThroughData.builder().build();

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(TaskRequest.newBuilder().build());

    TaskChainResponse taskChainResponse = asgStepCommonHelper.queueAsgTask(stepElementParameters, asgCommandRequest,
            ambiance, asgPrepareRollbackDataPassThroughData, false, TaskType.AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG);

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
  }
}
