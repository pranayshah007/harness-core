/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.shell.CommandTaskResponse;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.ApiServiceBeansKryoRegister;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.steps.StepHelper;

import software.wings.beans.TaskType;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CommandStepTest extends CategoryTest {
  @Mock private SshCommandStepHelper sshCommandStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private KryoSerializer kryoSerializer;

  @InjectMocks private CommandStep commandStep;

  private final String accountId = "accountId";
  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
  private final CommandStepParameters commandStepParameters =
      CommandStepParameters.infoBuilder()
          .commandUnits(Arrays.asList(
              CommandUnitWrapper.builder()
                  .commandUnit(
                      StepCommandUnit.builder().spec(ScriptCommandUnitSpec.builder().build()).name("test").build())
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
        .buildSshCommandTaskParameters(ambiance, commandStepParameters);
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
    verify(sshCommandStepHelper, times(1)).buildSshCommandTaskParameters(ambiance, commandStepParameters);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getDelegateTaskRequest().getTaskName()).isEqualTo(TaskType.COMMAND_TASK_NG.getDisplayName());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getKryoParameters())
        .isEqualTo(ByteString.copyFrom(serializedParams));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextSuccess() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(commandStepParameters)
                                                            .timeout(ParameterField.createValueField("30m"))
                                                            .build();

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    CommandTaskResponse commandTaskResponse =
        CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).unitProgressData(unitProgressData).build();

    StepResponse stepResponse =
        commandStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> commandTaskResponse);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).containsAll(unitProgresses);
    assertThat(stepResponse.getStepOutcomes()).isEmpty();
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
  }
}
