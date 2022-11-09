/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsCanaryDeleteDataOutcome;
import io.harness.cdng.ecs.beans.EcsCanaryDeployOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsCanaryDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsCanaryDeployStepParameters ecsSpecParameters = EcsCanaryDeployStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  private static final String canarySuffix = "Canary";

  @Mock private EcsStepHelperImpl ecsStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Spy private EcsStepCommonHelper ecsStepCommonHelper;

  @Spy @InjectMocks private EcsCanaryDeployStep ecsCanaryDeployStep;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsGitFetchPassThroughData)
                                               .build();
    doReturn(taskChainResponse1)
        .when(ecsStepCommonHelper)
        .executeNextLinkCanary(any(), any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = ecsCanaryDeployStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, ecsGitFetchPassThroughData, () -> responseData);
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsGitFetchPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsGitFetchPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeEcsTaskTest() {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsStepExecutorParams ecsStepExecutorParams = EcsStepExecutorParams.builder()
                                                      .ecsTaskDefinitionManifestContent("taskDef")
                                                      .ecsServiceDefinitionManifestContent("service")
                                                      .build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();

    doReturn(taskChainResponse1).when(ecsStepCommonHelper).queueEcsTask(any(), any(), any(), any(), anyBoolean());
    EcsCanaryDeleteDataOutcome ecsCanaryDeleteDataOutcome =
        EcsCanaryDeleteDataOutcome.builder()
            .ecsServiceNameSuffix(canarySuffix)
            .createServiceRequestBuilderString(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .build();
    doReturn("output")
        .when(executionSweepingOutputService)
        .consume(ambiance, OutcomeExpressionConstants.ECS_CANARY_DELETE_DATA_OUTCOME, ecsCanaryDeleteDataOutcome,
            StepOutcomeGroup.STEP.name());
    TaskChainResponse taskChainResponse = ecsCanaryDeployStep.executeEcsTask(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);

    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsPrepareRollbackDataPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsPrepareRollbackDataPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextEcsGitFetchFailurePassThroughDataTest() throws Exception {
    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        EcsGitFetchFailurePassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMsg("error")
            .build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();

    StepResponse stepResponse = ecsCanaryDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsGitFetchFailurePassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder().setErrorMessage(ecsGitFetchFailurePassThroughData.getErrorMsg()).build());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextEcsStepExceptionPassThroughDataTest() throws Exception {
    EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
        EcsStepExceptionPassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();

    StepResponse stepResponse = ecsCanaryDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsStepExceptionPassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();
    ResponseData responseData =
        EcsCanaryDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsCanaryDeployResult(EcsCanaryDeployResult.builder().canaryServiceName("service").build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ThrowingSupplier<ResponseData> responseDataSupplier = () -> responseData;
    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    EcsCanaryDeployOutcome ecsCanaryDeployOutcome =
        EcsCanaryDeployOutcome.builder().canaryServiceName("service").build();
    doReturn(StepResponse.StepOutcome.builder()
                 .name(OutcomeExpressionConstants.OUTPUT)
                 .outcome(ecsCanaryDeployOutcome)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos((EcsCanaryDeployResponse) responseDataSupplier.get(), "infraKey");
    StepResponse stepResponse = ecsCanaryDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().stream().findFirst().get();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(ecsCanaryDeployOutcome);
    assertThat(stepOutcome.getName()).isEqualTo("output");
  }
}
