/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.ssh.CommandHandler.RESOLVED_ENV_VARIABLES_KEY;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshScriptCommandHandlerTest extends CategoryTest {
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock ScriptSshExecutor scriptSshExecutor;
  @Mock ScriptProcessExecutor scriptProcessExecutor;
  @Mock SshScriptExecutorFactory sshScriptExecutorFactory;
  @Mock Map<String, Object> taskContext;
  @Mock LogCallback logCallback;

  final String COMMAND = "echo test";
  final SSHKeySpecDTO SSH_KEY_SPEC = SSHKeySpecDTO.builder().build();
  final List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

  final NgCommandUnit scriptCommandUnit =
      ScriptCommandUnit.builder().name("test").command(COMMAND).workingDirectory("/test").build();
  final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  final List<String> outputVariables = List.of("var");
  final List<String> secretOutputVariables = List.of("secretVar");

  @Inject @InjectMocks final SshScriptCommandHandler sshScriptCommandHandler = new SshScriptCommandHandler();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(Collections.emptyMap()).when(taskContext).get(RESOLVED_ENV_VARIABLES_KEY);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldExecuteCommandWithSshExecutor() {
    doReturn(scriptSshExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptSshExecutor.executeCommandString(COMMAND, outputVariables, secretOutputVariables, null))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());

    CommandExecutionStatus status =
        sshScriptCommandHandler
            .handle(getParameters(false), scriptCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<SshExecutorFactoryContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(SshExecutorFactoryContext.class);
    verify(sshScriptExecutorFactory).getExecutor(contextArgumentCaptor.capture());
    assertContextData(contextArgumentCaptor.getValue(), false);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldExecuteCommandWithScriptProcessExecutorOnDelegate() {
    doReturn(scriptProcessExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptProcessExecutor.executeCommandString(COMMAND, outputVariables, secretOutputVariables, null))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    when(scriptProcessExecutor.getLogCallback()).thenReturn(logCallback);

    CommandExecutionStatus status =
        sshScriptCommandHandler
            .handle(getParameters(true), scriptCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<SshExecutorFactoryContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(SshExecutorFactoryContext.class);
    verify(sshScriptExecutorFactory).getExecutor(contextArgumentCaptor.capture());
    assertContextData(contextArgumentCaptor.getValue(), true);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidArguments() {
    assertThatThrownBy(()
                           -> sshScriptCommandHandler.handle(WinrmTaskParameters.builder().build(), scriptCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");

    assertThatThrownBy(()
                           -> sshScriptCommandHandler.handle(getParameters(false), NgInitCommandUnit.builder().build(),
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  private CommandTaskParameters getParameters(boolean onDelegate) {
    return SshCommandTaskParameters.builder()
        .accountId("testAccount")
        .executeOnDelegate(onDelegate)
        .executionId("testExecution")
        .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder()
                                    .encryptionDataDetails(encryptedDataDetailList)
                                    .sshKeySpecDto(SSH_KEY_SPEC)
                                    .build())
        .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
        .commandUnits(Arrays.asList(scriptCommandUnit))
        .outputVariables(outputVariables)
        .secretOutputVariables(secretOutputVariables)
        .host("host")
        .build();
  }

  private void assertContextData(SshExecutorFactoryContext context, boolean onDelegate) {
    assertThat(context).isNotNull();
    assertThat(context.getAccountId()).isEqualTo("testAccount");
    assertThat(context.getExecutionId()).isEqualTo("testExecution");
    assertThat(context.getWorkingDirectory()).isEqualTo("/test");
    assertThat(context.getCommandUnitName()).isEqualTo("test");
    assertThat(context.getCommandUnitsProgress()).isEqualTo(commandUnitsProgress);
    assertThat(context.isExecuteOnDelegate()).isEqualTo(onDelegate);
    assertThat(context.getEncryptedDataDetailList()).isEqualTo(encryptedDataDetailList);
    assertThat(context.getSshKeySpecDTO()).isEqualTo(SSH_KEY_SPEC);
    assertThat(context.getHost()).isEqualTo("host");
    assertThat(context.getILogStreamingTaskClient()).isEqualTo(logStreamingTaskClient);
  }
}
