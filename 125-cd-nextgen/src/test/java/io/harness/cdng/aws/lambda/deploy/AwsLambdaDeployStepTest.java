/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda.deploy;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.lambda.AwsLambdaHelper;
import io.harness.cdng.aws.lambda.AwsLambdaStepExceptionPassThroughData;
import io.harness.cdng.aws.lambda.AwsLambdaStepPassThroughData;
import io.harness.cdng.aws.lambda.beans.AwsLambdaStepOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.lambda.AwsLambda;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaDeployResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsLambdaDeployStepTest extends CDNGTestBase {
  @Mock private OutcomeService outcomeService;
  @Mock private AwsLambdaHelper awsLambdaHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private AwsLambdaDeployStep awsLambdaDeployStep;

  private final AwsLambdaInfrastructureOutcome infrastructureOutcome = AwsLambdaInfrastructureOutcome.builder()
                                                                           .connectorRef("account.tas")
                                                                           .infrastructureKey("dev-org")
                                                                           .region("dev-space")
                                                                           .build();
  private AwsLambdaDeployStepParameters parameters =
      AwsLambdaDeployStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();

  private final Ambiance ambiance = getAmbiance();

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("AwsLambdaDeploy").spec(parameters).build();
    awsLambdaDeployStep.validateResources(ambiance, stepElementParameters);
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac() {
    TaskChainResponse taskChainResponse = mock(TaskChainResponse.class);
    doReturn(taskChainResponse).when(awsLambdaHelper).startChainLink(any(), any());
    TaskChainResponse taskChainResponse1 = awsLambdaDeployStep.startChainLinkAfterRbac(
        ambiance, StepElementParameters.builder().build(), StepInputPackage.builder().build());
    assertThat(taskChainResponse1).isEqualTo(taskChainResponse);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenSuccess() throws Exception {
    AwsLambdaDeployStepParameters awsLambdaDeployStepParameters = AwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(awsLambdaDeployStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    AwsLambdaStepOutcome awsLambdaStepOutcome = mock(AwsLambdaStepOutcome.class);
    doReturn(awsLambdaStepOutcome).when(awsLambdaHelper).getAwsLambdaStepOutcome(any());

    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        AwsLambdaFunctionsInfraConfig.builder().infraStructureKey("infra").build();
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaHelper).getInfraConfig(any(), any());

    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    doReturn(serverInstanceInfoList).when(awsLambdaHelper).getServerInstanceInfo(any(), any(), any());

    StepOutcome stepOutcome = mock(StepOutcome.class);
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());

    AwsLambda awsLambda = AwsLambda.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    AwsLambdaDeployResponse responseData = AwsLambdaDeployResponse.builder()
                                               .unitProgressData(unitProgressData)
                                               .errorMessage("error")
                                               .awsLambda(awsLambda)
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();
    StepResponse stepResponse =
        awsLambdaDeployStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
            AwsLambdaStepPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build(),
            () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenExceptionInPreviousSubTask() throws Exception {
    AwsLambdaDeployStepParameters awsLambdaDeployStepParameters = AwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(awsLambdaDeployStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    AwsLambdaStepOutcome awsLambdaStepOutcome = mock(AwsLambdaStepOutcome.class);
    doReturn(awsLambdaStepOutcome).when(awsLambdaHelper).getAwsLambdaStepOutcome(any());

    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        AwsLambdaFunctionsInfraConfig.builder().infraStructureKey("infra").build();
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaHelper).getInfraConfig(any(), any());

    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    doReturn(serverInstanceInfoList).when(awsLambdaHelper).getServerInstanceInfo(any(), any(), any());

    StepOutcome stepOutcome = mock(StepOutcome.class);
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());

    AwsLambda awsLambda = AwsLambda.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    AwsLambdaDeployResponse responseData = AwsLambdaDeployResponse.builder()
                                               .unitProgressData(unitProgressData)
                                               .errorMessage("error")
                                               .awsLambda(awsLambda)
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();
    StepResponse stepResponse1 = mock(StepResponse.class);
    doReturn(stepResponse1)
        .when(awsLambdaHelper)
        .handleStepExceptionFailure((AwsLambdaStepExceptionPassThroughData) any());
    StepResponse stepResponse = awsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, AwsLambdaStepExceptionPassThroughData.builder().build(), () -> responseData);
    assertThat(stepResponse).isEqualTo(stepResponse1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenExceptionWhileRetrievingResponse() throws Exception {
    AwsLambdaDeployStepParameters awsLambdaDeployStepParameters = AwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(awsLambdaDeployStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    AwsLambdaStepOutcome awsLambdaStepOutcome = mock(AwsLambdaStepOutcome.class);
    doReturn(awsLambdaStepOutcome).when(awsLambdaHelper).getAwsLambdaStepOutcome(any());

    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        AwsLambdaFunctionsInfraConfig.builder().infraStructureKey("infra").build();
    doReturn(awsLambdaFunctionsInfraConfig).when(awsLambdaHelper).getInfraConfig(any(), any());

    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    doReturn(serverInstanceInfoList).when(awsLambdaHelper).getServerInstanceInfo(any(), any(), any());

    StepOutcome stepOutcome = mock(StepOutcome.class);
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());

    AwsLambda awsLambda = AwsLambda.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    AwsLambdaDeployResponse responseData = AwsLambdaDeployResponse.builder()
                                               .unitProgressData(unitProgressData)
                                               .errorMessage("error")
                                               .awsLambda(awsLambda)
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .build();
    StepResponse stepResponse1 = mock(StepResponse.class);
    doReturn(stepResponse1).when(awsLambdaHelper).handleStepFailureException(any(), any(), any());
    StepResponse stepResponse =
        awsLambdaDeployStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
            AwsLambdaStepPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build(),
            () -> { throw new Exception("exception"); });
    assertThat(stepResponse).isEqualTo(stepResponse1);
  }
}
