/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static software.wings.beans.TaskType.AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackResult;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgBlueGreenRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final String accountId = "test-account";

  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder()
          .spec(AsgBlueGreenRollbackStepParameters.infoBuilder()
                    .asgBlueGreenCreateServiceFnq("deployFqn")
                    .asgBlueGreenSwapTargetGroupsFnq("swapServiceFqn")
                    .build())
          .timeout(ParameterField.createValueField("10m"))
          .build();

  UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

  public static final String ASG_ROLLING_ROLLBACK_COMMAND_NAME = "AsgBlueGreenRollback";

  public static final String ASG_BLUE_GREEN_DEPLOY_STEP_MISSING = "Blue Green Deploy step is not configured.";
  private static final String amiImageName = "ami123";
  private static final String asgName = "asg";
  private static final String prodAsgName = "prodAsg";
  private static final AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();

  private static Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
  private static AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData;
  private static AsgInfrastructureOutcome asgInfrastructureOutcome;
  private static StepInputPackage inputPackage;
  private static AsgLoadBalancerConfig asgLoadBalancerConfig;
  private static AsgBlueGreenExecutionPassThroughData asgBlueGreenExecutionPassThroughData;
  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgBlueGreenRollbackStep asgBlueGreenRollbackStep;
  @Spy private InstanceInfoService instanceInfoService;
  @Spy private CDStepHelper cdStepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Spy private OutcomeService outcomeService;
  @Mock AccountService accountService;
  @Mock StepHelper stepHelper;

  @BeforeClass
  public static void setup() throws IOException {
    inputPackage = StepInputPackage.builder().build();

    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    asgInfrastructureOutcome = AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build();

    asgBlueGreenExecutionPassThroughData = AsgBlueGreenExecutionPassThroughData.blueGreenBuilder()
                                               .loadBalancerConfig(asgLoadBalancerConfig)
                                               .asgName(asgName)
                                               .asgManifestFetchData(AsgManifestFetchData.builder().build())
                                               .firstDeployment(false)
                                               .infrastructure(asgInfrastructureOutcome)
                                               .build();

    asgPrepareRollbackDataPassThroughData = AsgPrepareRollbackDataPassThroughData.builder()
                                                .infrastructureOutcome(asgInfrastructureOutcome)
                                                .asgStoreManifestsContent(asgStoreManifestsContent)
                                                .build();

    asgLoadBalancerConfig = AsgLoadBalancerConfig.builder()
                                .loadBalancer("lb")
                                .prodListenerArn("prodLis")
                                .prodListenerRuleArn("prodArn")
                                .stageListenerArn("stageLis")
                                .stageListenerRuleArn("stageArn")
                                .stageTargetGroupArnsList(List.of("stageTarget"))
                                .prodTargetGroupArnsList(List.of("prodTarget"))
                                .build();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    ResponseData responseData = AsgBlueGreenRollbackResponse.builder()
                                    .unitProgressData(unitProgressData)
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .asgBlueGreenRollbackResult(AsgBlueGreenRollbackResult.builder().build())
                                    .build();

    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    doReturn(asgInfrastructureOutcome).when(outcomeService).resolve(any(), any());
    AsgServerInstanceInfo asgServerInstanceInfo = AsgServerInstanceInfo.builder().build();
    doReturn(List.of(asgServerInstanceInfo)).when(asgStepCommonHelper).getServerInstanceInfos(any(), any(), any());
    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    doReturn(AccountDTO.builder().name("abcd").build()).when(accountService).getAccount(any());
    doReturn(asgStoreManifestsContent).when(stepHelper).sendRollbackTelemetryEvent(any(), any(), any());
    StepResponse stepResponse = asgBlueGreenRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> (AsgCommandResponse) responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgBlueGreenRollbackOutcome.class);
    assertThat(outcomeMap.get(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME))
        .isInstanceOf(DeploymentInfoOutcome.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTest() {
    doReturn(OptionalSweepingOutput.builder()
                 .output(AsgBlueGreenPrepareRollbackDataOutcome.builder()
                             .loadBalancer("lb")
                             .prodListenerArn("prodLis")
                             .prodListenerRuleArn("prodArn")
                             .stageListenerArn("stageLis")
                             .stageListenerRuleArn("stageArn")
                             .stageTargetGroupArnsList(List.of("stageTarget"))
                             .prodTargetGroupArnsList(List.of("prodTarget"))
                             .prodAsgName(prodAsgName)
                             .stageAsgManifestDataForRollback(asgStoreManifestsContent)
                             .prodAsgManifestDataForRollback(asgStoreManifestsContent)
                             .build())
                 .found(true)
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                ((AsgBlueGreenRollbackStepParameters) stepElementParameters.getSpec()).getAsgBlueGreenDeployFnq() + "."
                + OutcomeExpressionConstants.ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME)));

    doReturn(OptionalSweepingOutput.builder()
                 .output(AsgBlueGreenDeployOutcome.builder()
                             .stageAsg(AutoScalingGroupContainer.builder().autoScalingGroupName(asgName).build())
                             .build())
                 .found(true)
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                ((AsgBlueGreenRollbackStepParameters) stepElementParameters.getSpec()).getAsgBlueGreenDeployFnq() + "."
                + OutcomeExpressionConstants.ASG_BLUE_GREEN_DEPLOY_OUTCOME)));

    doReturn(OptionalSweepingOutput.builder()
                 .output(AsgBlueGreenSwapServiceOutcome.builder()
                             .trafficShifted(true)
                             .prodAsg(AutoScalingGroupContainer.builder().autoScalingGroupName(prodAsgName).build())
                             .stageAsg(AutoScalingGroupContainer.builder().autoScalingGroupName(asgName).build())
                             .build())
                 .found(true)
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                ((AsgBlueGreenRollbackStepParameters) stepElementParameters.getSpec()).getAsgBlueGreenSwapServiceFnq()
                + "." + OutcomeExpressionConstants.ASG_BLUE_GREEN_SWAP_SERVICE_OUTCOME)));

    AsgInfrastructureOutcome asgInfrastructureOutcome1 =
        AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build();

    doReturn(asgInfrastructureOutcome1).when(outcomeService).resolve(any(), any());
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(asgInfrastructureOutcome1).build();

    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(true)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(asgExecutionPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    asgBlueGreenRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    AsgBlueGreenRollbackRequest asgBlueGreenRollbackRequest =
        AsgBlueGreenRollbackRequest.builder()
            .commandName(ASG_ROLLING_ROLLBACK_COMMAND_NAME)
            .asgInfraConfig(asgInfraConfig)
            .accountId(accountId)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgLoadBalancerConfig(asgLoadBalancerConfig)
            .prodAsgName(prodAsgName)
            .prodAsgManifestsDataForRollback(asgStoreManifestsContent)
            .stageAsgName(asgName)
            .stageAsgManifestsDataForRollback(asgStoreManifestsContent)
            .servicesSwapped(true)
            .build();

    verify(asgStepCommonHelper)
        .queueAsgTask(
            eq(stepElementParameters), any(), eq(ambiance), any(), eq(true), eq(AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG));
  }
}
