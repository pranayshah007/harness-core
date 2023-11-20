/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.VITALIE;

import static software.wings.beans.TaskType.AWS_ASG_SHIFT_TRAFFIC_TASK_NG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficRequest;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResponse;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResult;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
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

public class AsgShiftTrafficStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final String accountId = "test-account";

  private final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                                  .spec(AsgShiftTrafficStepParameters.infoBuilder()
                                                                            .asgBlueGreenDeployFqn("deployFqn")
                                                                            .weight(ParameterField.createValueField(10))
                                                                            .build())
                                                                  .timeout(ParameterField.createValueField("10m"))
                                                                  .build();

  UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

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
  @Spy @InjectMocks private AsgShiftTrafficStep asgShiftTrafficStep;
  @Spy private InstanceInfoService instanceInfoService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Spy private OutcomeService outcomeService;

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
                                .stageTargetGroupArnsList(List.of("stageTarget"))
                                .prodTargetGroupArnsList(List.of("prodTarget"))
                                .build();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    ResponseData responseData = AsgShiftTrafficResponse.builder()
                                    .unitProgressData(unitProgressData)
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .result(AsgShiftTrafficResult.builder().build())
                                    .build();

    doReturn("").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn(asgInfrastructureOutcome).when(outcomeService).resolve(any(), any());
    AsgServerInstanceInfo asgServerInstanceInfo = AsgServerInstanceInfo.builder().build();
    doReturn(List.of(asgServerInstanceInfo)).when(asgStepCommonHelper).getServerInstanceInfos(any(), any(), any());
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse = asgShiftTrafficStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> (AsgCommandResponse) responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgShiftTrafficOutcome.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTest() {
    doReturn(OptionalSweepingOutput.builder()
                 .output(AsgBlueGreenPrepareRollbackDataOutcome.builder()
                             .loadBalancerConfigs(
                                 Arrays.asList(AwsAsgLoadBalancerConfigYaml.builder()
                                                   .loadBalancer(ParameterField.createValueField("lb"))
                                                   .prodListener(ParameterField.createValueField("prodLis"))
                                                   .prodListenerRuleArn(ParameterField.createValueField("prodArn"))
                                                   .build()))
                             .prodTargetGroupArnListForLoadBalancer(Map.of("lb", List.of("prodTarget")))
                             .stageTargetGroupArnListForLoadBalancer(Map.of("lb", List.of("stageTarget")))
                             .prodAsgName(prodAsgName)
                             .build())
                 .found(true)
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                ((AsgShiftTrafficStepParameters) stepElementParameters.getSpec()).getAsgBlueGreenDeployFqn() + "."
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
                ((AsgShiftTrafficStepParameters) stepElementParameters.getSpec()).getAsgBlueGreenDeployFqn() + "."
                + OutcomeExpressionConstants.ASG_BLUE_GREEN_DEPLOY_OUTCOME)));

    AsgInfrastructureOutcome asgInfrastructureOutcome1 =
        AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build();

    doReturn(asgInfrastructureOutcome1).when(outcomeService).resolve(any(), any());
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());
    doReturn(mock(InfrastructureOutcome.class))
        .when(asgStepCommonHelper)
        .getInfrastructureOutcomeWithUpdatedExpressions(any());

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

    asgShiftTrafficStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    AsgShiftTrafficRequest asgShiftTrafficRequest =
        AsgShiftTrafficRequest.builder()
            .accountId(accountId)
            .commandName(AsgShiftTrafficStep.COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .asgInfraConfig(asgInfraConfig)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .loadBalancers(Arrays.asList(asgLoadBalancerConfig))
            .prodAsgName(prodAsgName)
            .stageAsgName(asgName)
            .downsizeOldAsg(false)
            .weight(10)
            .build();

    verify(asgStepCommonHelper)
        .queueAsgTask(eq(stepElementParameters), eq(asgShiftTrafficRequest), eq(ambiance), any(), eq(true),
            eq(AWS_ASG_SHIFT_TRAFFIC_TASK_NG));
  }
}