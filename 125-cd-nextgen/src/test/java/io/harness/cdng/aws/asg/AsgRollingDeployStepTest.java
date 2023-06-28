/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResponse;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResult;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgRollingDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final AsgRollingDeployStepParameters asgSpecParameters =
      AsgRollingDeployStepParameters.infoBuilder()
          .skipMatching(ParameterField.createValueField(true))
          .useAlreadyRunningInstances(ParameterField.createValueField(false))
          .instanceWarmup(ParameterField.createValueField(50))
          .minimumHealthyPercentage(ParameterField.createValueField(40))
          .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

  private static final String ASG_ROLLING_DEPLOY_COMMAND_NAME = "AsgRollingDeploy";
  private static final String ASG_ROLLING_PREPARE_ROLLBACK_DATA_COMMAND_NAME = "AsgRollingPrepareRollbackData";
  private static final String amiImageName = "ami123";
  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgRollingDeployStep asgRollingDeployStep;
  @Spy private InstanceInfoService instanceInfoService;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder()
            .infrastructure(AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();

    AsgPrepareRollbackDataResult asgPrepareRollbackDataResult =
        AsgPrepareRollbackDataResult.builder()
            .asgName("asg")
            .asgManifestsDataForRollback(asgStoreManifestsContent)
            .build();

    ResponseData responseData = AsgPrepareRollbackDataResponse.builder()
                                    .asgPrepareRollbackDataResult(asgPrepareRollbackDataResult)
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .build();

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(true)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgExecutionPassThroughData)
                                               .build();

    doReturn(taskChainResponse1).when(asgStepCommonHelper).executeNextLinkRolling(any(), any(), any(), any(), any());

    TaskChainResponse taskChainResponse = asgRollingDeployStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, asgExecutionPassThroughData, () -> responseData);
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AsgExecutionPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(asgExecutionPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeAsgTaskTest() {
    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    AsgStepExecutorParams asgStepExecutorParams =
        AsgStepExecutorParams.builder().asgStoreManifestsContent(asgStoreManifestsContent).build();

    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(true)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgExecutionPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    doReturn(amiImageName).when(asgStepCommonHelper).getAmiImageId(any());

    TaskChainResponse taskChainResponse = asgRollingDeployStep.executeAsgTask(
        ambiance, stepElementParameters, asgExecutionPassThroughData, unitProgressData, asgStepExecutorParams);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AsgExecutionPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(asgExecutionPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    AsgRollingDeployRequest asgRollingDeployRequest =
        AsgRollingDeployRequest.builder()
            .commandName(ASG_ROLLING_DEPLOY_COMMAND_NAME)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .accountId(accountId)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgStoreManifestsContent(asgStepExecutorParams.getAsgStoreManifestsContent())
            .skipMatching(ParameterFieldHelper.getBooleanParameterFieldValue(asgSpecParameters.getSkipMatching()))
            .useAlreadyRunningInstances(
                ParameterFieldHelper.getBooleanParameterFieldValue(asgSpecParameters.getUseAlreadyRunningInstances()))
            .instanceWarmup(ParameterFieldHelper.getIntegerParameterFieldValue(asgSpecParameters.getInstanceWarmup()))
            .minimumHealthyPercentage(
                ParameterFieldHelper.getIntegerParameterFieldValue(asgSpecParameters.getMinimumHealthyPercentage()))
            .amiImageId(amiImageName)
            .build();

    verify(asgStepCommonHelper)
        .queueAsgTask(stepElementParameters, asgRollingDeployRequest, ambiance, asgExecutionPassThroughData, true,
            TaskType.AWS_ASG_ROLLING_DEPLOY_TASK_NG);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeAsgPrepareRollbackTaskTest() {
    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData =
        AsgPrepareRollbackDataPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgPrepareRollbackDataPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    TaskChainResponse taskChainResponse = asgRollingDeployStep.executeAsgPrepareRollbackDataTask(
        ambiance, stepElementParameters, asgPrepareRollbackDataPassThroughData, unitProgressData);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AsgPrepareRollbackDataPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(asgPrepareRollbackDataPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    AsgPrepareRollbackDataRequest asgPrepareRollbackDataRequest =
        AsgPrepareRollbackDataRequest.builder()
            .commandName(ASG_ROLLING_PREPARE_ROLLBACK_DATA_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .asgStoreManifestsContent(asgPrepareRollbackDataPassThroughData.getAsgStoreManifestsContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();

    verify(asgStepCommonHelper)
        .queueAsgTask(stepElementParameters, asgPrepareRollbackDataRequest, ambiance,
            asgPrepareRollbackDataPassThroughData, false, TaskType.AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextAsgStepExceptionPassThroughDataTest() throws Exception {
    AsgStepExceptionPassThroughData asgStepExceptionPassThroughData =
        AsgStepExceptionPassThroughData.builder().unitProgressData(unitProgressData).errorMessage("error").build();
    ResponseData responseData = AsgCanaryDeployResponse.builder().build();

    StepResponse stepResponse = asgRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgStepExceptionPassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    AsgInfrastructureOutcome asgInfrastructureOutcome =
        AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build();
    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(asgInfrastructureOutcome).build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(asgInfrastructureOutcome, ambiance);

    ResponseData responseData =
        AsgRollingDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .asgRollingDeployResult(
                AsgRollingDeployResult.builder()
                    .autoScalingGroupContainer(AutoScalingGroupContainer.builder().autoScalingGroupName("asg").build())
                    .asgStoreManifestsContent(asgStoreManifestsContent)
                    .build())
            .unitProgressData(unitProgressData)
            .errorMessage("error")
            .build();

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse = asgRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgExecutionPassThroughData, () -> responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgRollingDeployOutcome.class);
    assertThat(outcomeMap.get(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME))
        .isInstanceOf(DeploymentInfoOutcome.class);
  }
}