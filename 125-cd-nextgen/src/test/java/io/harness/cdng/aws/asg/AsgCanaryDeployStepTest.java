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

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpec;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
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
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgCanaryDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final ParameterField<Integer> count = ParameterField.createValueField(1);
  private final CapacitySpec spec = CountCapacitySpec.builder().count(count).build();
  private final Capacity instanceSelection = Capacity.builder().spec(spec).build();
  private final AsgCanaryDeployStepParameters asgSpecParameters =
      AsgCanaryDeployStepParameters.infoBuilder().instanceSelection(instanceSelection).build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();
  private static final String amiImageName = "ami123";

  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgCanaryDeployStep asgCanaryDeployStep;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Spy private InstanceInfoService instanceInfoService;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeAsgTaskTest() {
    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    AsgStepExecutorParams asgStepExecutorParams =
        AsgStepExecutorParams.builder().asgStoreManifestsContent(asgStoreManifestsContent).build();

    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(infrastructureOutcome, ambiance);
    doReturn(asgStoreManifestsContent).when(asgStepCommonHelper).buildManifestContentMap(any(), any());
    doReturn(amiImageName).when(asgStepCommonHelper).getAmiImageId(any());

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(true)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgExecutionPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    TaskChainResponse taskChainResponse = asgCanaryDeployStep.executeAsgTask(
        ambiance, stepElementParameters, asgExecutionPassThroughData, unitProgressData, asgStepExecutorParams);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AsgExecutionPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(asgExecutionPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextAsgStepExceptionPassThroughDataTest() throws Exception {
    AsgStepExceptionPassThroughData asgStepExceptionPassThroughData =
        AsgStepExceptionPassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ResponseData responseData = AsgCanaryDeployResponse.builder().build();

    StepResponse stepResponse = asgCanaryDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgStepExceptionPassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder()
            .infrastructure(AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();

    ResponseData responseData =
        AsgCanaryDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .asgCanaryDeployResult(
                AsgCanaryDeployResult.builder()
                    .autoScalingGroupContainer(AutoScalingGroupContainer.builder().autoScalingGroupName("asg").build())
                    .build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();

    AutoScalingGroupContainer autoScalingGroupContainer =
        AutoScalingGroupContainer.builder().autoScalingGroupName("asg").build();
    AsgCanaryDeployOutcome asgCanaryDeployOutcome =
        AsgCanaryDeployOutcome.builder().asg(autoScalingGroupContainer).build();

    AsgInfrastructureOutcome asgInfrastructureOutcome =
        AsgInfrastructureOutcome.builder().infrastructureKey("abcd").build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());
    AsgServerInstanceInfo asgServerInstanceInfo = AsgServerInstanceInfo.builder().build();
    doReturn(List.of(asgServerInstanceInfo)).when(asgStepCommonHelper).getServerInstanceInfos(any(), any(), any());

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse = asgCanaryDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgExecutionPassThroughData, () -> responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgCanaryDeployOutcome.class);
    assertThat(outcomeMap.get(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME))
        .isInstanceOf(DeploymentInfoOutcome.class);
  }
}
