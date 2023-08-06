/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSshWinrmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.shell.CommandTaskResponse;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.ApiServiceBeansKryoRegister;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class CommandStepTest extends CategoryTest {
  @Mock private SshCommandStepHelper sshCommandStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private OutcomeService outcomeService;

  @Mock private CDStepHelper cdStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ThrowingSupplier exceptionThrowingSupplier;
  @Captor private ArgumentCaptor<List<ServerInstanceInfo>> serverInstanceInfoListCaptor;

  @InjectMocks private CommandStep commandStep;
  @Spy private CommandTaskDataFactory commandTaskDataFactory;

  private final String accountId = "accountId";
  private final String infraKey = "INFRAKEY";
  private final String localhost = "localhost";
  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
  private final CommandStepParameters commandStepParameters =
      CommandStepParameters.infoBuilder()
          .host(ParameterField.createValueField(localhost))
          .commandUnits(Arrays.asList(CommandUnitWrapper.builder()
                                          .type("Script")
                                          .spec(ScriptCommandUnitSpec.builder().build())
                                          .name("test")
                                          .build()))
          .build();

  private final CommandStepParameters winrmCommandStepParameters =
      CommandStepParameters.infoBuilder()
          .host(ParameterField.createValueField(localhost))
          .commandUnits(Arrays.asList(CommandUnitWrapper.builder().type("WinRm").name("winrm-svc").build()))
          .build();

  private final CommandStepParameters commandStepParametersNoHosts =
      CommandStepParameters.infoBuilder()
          .commandUnits(Arrays.asList(CommandUnitWrapper.builder()
                                          .type("Script")
                                          .spec(ScriptCommandUnitSpec.builder().build())
                                          .name("winrm-svc")
                                          .build()))
          .build();
  private SshCommandTaskParameters sshCommandTaskParameters =
      SshCommandTaskParameters.builder()
          .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
          .executeOnDelegate(false)
          .accountId(accountId)
          .commandUnits(Arrays.asList(NgInitCommandUnit.builder().build(),
              ScriptCommandUnit.builder().name("test").build(), NgCleanupCommandUnit.builder().build()))
          .build();
  KryoSerializer serializer = new KryoSerializer(
      new HashSet<>(Arrays.asList(DelegateTasksBeansKryoRegister.class, ApiServiceBeansKryoRegister.class)));
  private byte[] serializedParams = serializer.asDeflatedBytes(sshCommandTaskParameters);

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    doReturn(sshCommandTaskParameters)
        .when(sshCommandStepHelper)
        .buildCommandTaskParameters(eq(ambiance), eq(commandStepParameters), any());
    doReturn(EnvironmentType.ALL).when(stepHelper).getEnvironmentType(ambiance);
    doReturn(serializedParams).when(kryoSerializer).asDeflatedBytes(sshCommandTaskParameters);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();

    TaskRequest taskRequest =
        commandStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());
    verify(sshCommandStepHelper, times(1)).buildCommandTaskParameters(ambiance, commandStepParameters, "30m");
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.COMMAND_TASK_NG.getDisplayName());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getKryoParameters())
        .isEqualTo(ByteString.copyFrom(serializedParams));
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testExecuteTaskWithTimeOutGreaterThan30Minutes() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(winrmCommandStepParameters)
                                                            .timeout(ParameterField.createValueField("45m"))
                                                            .build();
    doReturn(sshCommandTaskParameters)
        .when(sshCommandStepHelper)
        .buildCommandTaskParameters(eq(ambiance), eq(winrmCommandStepParameters), any());

    TaskRequest taskRequest =
        commandStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());
    verify(sshCommandStepHelper, times(1)).buildCommandTaskParameters(ambiance, winrmCommandStepParameters, "45m");
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.COMMAND_TASK_NG.getDisplayName());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getExecutionTimeout().getSeconds())
        .isEqualTo(2700);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextSuccessPdc() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();
    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(PdcInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    CommandTaskResponse commandTaskResponse =
        CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).unitProgressData(unitProgressData).build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    verify(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
    List<ServerInstanceInfo> serverInstanceInfoList = serverInstanceInfoListCaptor.getValue();
    assertThat(serverInstanceInfoList).hasSize(1);
    assertThat(((PdcServerInstanceInfo) serverInstanceInfoList.get(0)).getHost()).isEqualTo(localhost);
    assertThat(((PdcServerInstanceInfo) serverInstanceInfoList.get(0)).getInfrastructureKey()).isEqualTo(infraKey);
    assertThat(((PdcServerInstanceInfo) serverInstanceInfoList.get(0)).getServiceType()).isEqualTo(ServiceSpecType.SSH);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextSuccessAzure() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();
    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(SshWinRmAzureInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    CommandTaskResponse commandTaskResponse =
        CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).unitProgressData(unitProgressData).build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    verify(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
    List<ServerInstanceInfo> serverInstanceInfoList = serverInstanceInfoListCaptor.getValue();
    assertThat(serverInstanceInfoList).hasSize(1);
    assertThat(((AzureSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getHost()).isEqualTo(localhost);
    assertThat(((AzureSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getInfrastructureKey())
        .isEqualTo(infraKey);
    assertThat(((AzureSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getServiceType())
        .isEqualTo(ServiceSpecType.SSH);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextSuccessAws() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();
    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(SshWinRmAwsInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    CommandTaskResponse commandTaskResponse =
        CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).unitProgressData(unitProgressData).build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    verify(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
    List<ServerInstanceInfo> serverInstanceInfoList = serverInstanceInfoListCaptor.getValue();
    assertThat(serverInstanceInfoList).hasSize(1);
    assertThat(((AwsSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getHost()).isEqualTo(localhost);
    assertThat(((AwsSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getInfrastructureKey())
        .isEqualTo(infraKey);
    assertThat(((AwsSshWinrmServerInstanceInfo) serverInstanceInfoList.get(0)).getServiceType())
        .isEqualTo(ServiceSpecType.SSH);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextFailure() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(PdcInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    CommandTaskResponse commandTaskResponse = CommandTaskResponse.builder()
                                                  .status(CommandExecutionStatus.FAILURE)
                                                  .errorMessage("Something went wrong")
                                                  .unitProgressData(unitProgressData)
                                                  .build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Something went wrong");
    verify(instanceInfoService, times(0)).saveServerInstancesIntoSweepingOutput(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextErrorFromDelegate() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();

    List<UnitProgress> unitProgresses =
        Arrays.asList(UnitProgress.newBuilder().setStatus(UnitStatus.RUNNING).setUnitName("Init").build());

    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(PdcInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    doReturn(StepResponse.builder()
                 .status(Status.FAILED)
                 .failureInfo(FailureInfo.newBuilder().build())
                 .unitProgressList(unitProgresses)
                 .build())
        .when(sshCommandStepHelper)
        .handleTaskException(eq(ambiance), any(), any());
    doThrow(new RuntimeException("Failed to execute the task")).when(exceptionThrowingSupplier).get();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, exceptionThrowingSupplier);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    verify(instanceInfoService, times(0)).saveServerInstancesIntoSweepingOutput(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteTaskNoHost() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParametersNoHosts)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();

    assertThatThrownBy(
        () -> commandStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Host information is missing in Command Step. Please make sure the looping strategy (repeat) is provided.");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextCustomDeployment() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();
    doReturn(ServiceStepOutcome.builder().type(ServiceSpecType.CUSTOM_DEPLOYMENT).build())
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(CustomDeploymentInfrastructureOutcome.builder().infrastructureKey(infraKey).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    CommandTaskResponse commandTaskResponse =
        CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).unitProgressData(unitProgressData).build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    verify(instanceInfoService, times(0))
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
  }
}
