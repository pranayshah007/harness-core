/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.TailFilePatternDto;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshInitCommandHandlerTest extends CategoryTest {
  static final String PRE_INIT_CMD = "mkdir -p /tmp/test";
  static final String PRINT_ENV = "printenv";

  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock ScriptSshExecutor scriptSshExecutor;
  @Mock ScriptProcessExecutor scriptProcessExecutor;
  @Mock SshScriptExecutorFactory sshScriptExecutorFactory;
  @Mock Map<String, Object> taskContext;
  @Mock LogCallback logCallback;

  @Inject @InjectMocks final SshInitCommandHandler sshInitCommandHandler = new SshInitCommandHandler();

  static final String TAIL_FILE_PATTERN = "some pattern";
  final List<TailFilePatternDto> tailFilePatterns =
      Arrays.asList(TailFilePatternDto.builder().filePath("some path").pattern(TAIL_FILE_PATTERN).build());

  final NgCommandUnit initCommandUnit = NgInitCommandUnit.builder().build();
  final NgCommandUnit scriptNoTailCommandUnit =
      ScriptCommandUnit.builder().script("echo test").workingDirectory("/test").build();
  final NgCommandUnit scriptWithTailCommandUnit =
      ScriptCommandUnit.builder().script("harness_utils_start_tail_log_verification").workingDirectory("/test").build();
  final NgCommandUnit scriptWithTailFileCommandUnit =
      ScriptCommandUnit.builder().script("test").workingDirectory("/test").tailFilePatterns(tailFilePatterns).build();
  final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = {VITALIE, ACASIAN})
  @Category(UnitTests.class)
  public void testPrepareScriptFails() {
    CommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                           .executeOnDelegate(false)
                                           .executionId("test")
                                           .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                           .commandUnits(Arrays.asList(initCommandUnit))
                                           .build();

    doReturn(scriptSshExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptSshExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.FAILURE);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testPrepareScriptFailsOnDelegate() {
    CommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                           .executeOnDelegate(true)
                                           .executionId("test")
                                           .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                           .commandUnits(Arrays.asList(initCommandUnit))
                                           .build();

    doReturn(scriptProcessExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptProcessExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.FAILURE);
    when(scriptProcessExecutor.getLogCallback()).thenReturn(logCallback);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = {VITALIE, ACASIAN})
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithoutTail() {
    SshCommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                              .executeOnDelegate(false)
                                              .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                              .executionId("test")
                                              .commandUnits(Arrays.asList(initCommandUnit, scriptNoTailCommandUnit))
                                              .build();

    doReturn(scriptSshExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptSshExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptSshExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();
    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("# set session");
    assertThat(scriptCommandUnit.getCommand()).doesNotContain("harness_utils_wait_for_tail_log_verification");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithoutTailOnDelegate() {
    SshCommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                              .executeOnDelegate(true)
                                              .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                              .executionId("test")
                                              .commandUnits(Arrays.asList(initCommandUnit, scriptNoTailCommandUnit))
                                              .build();

    doReturn(scriptProcessExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptProcessExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.getLogCallback()).thenReturn(logCallback);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();
    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("# set session");
    assertThat(scriptCommandUnit.getCommand()).doesNotContain("harness_utils_wait_for_tail_log_verification");
  }

  @Test
  @Owner(developers = {VITALIE, ACASIAN})
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithTail() {
    SshCommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                              .executeOnDelegate(false)
                                              .executionId("test")
                                              .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                              .commandUnits(Arrays.asList(initCommandUnit, scriptWithTailCommandUnit))
                                              .build();

    doReturn(scriptSshExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptSshExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptSshExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();

    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_start_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_wait_for_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).doesNotContain("filePatterns=");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithTailOnDelegate() {
    SshCommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                              .executeOnDelegate(true)
                                              .executionId("test")
                                              .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
                                              .commandUnits(Arrays.asList(initCommandUnit, scriptWithTailCommandUnit))
                                              .build();

    doReturn(scriptProcessExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptProcessExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.getLogCallback()).thenReturn(logCallback);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();

    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_start_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_wait_for_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).doesNotContain("filePatterns=");
  }

  @Test
  @Owner(developers = {VITALIE, ACASIAN})
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithTailPattern() {
    SshCommandTaskParameters parameters =
        SshCommandTaskParameters.builder()
            .executeOnDelegate(false)
            .executionId("test")
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .commandUnits(Arrays.asList(initCommandUnit, scriptWithTailFileCommandUnit))
            .build();

    doReturn(scriptSshExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptSshExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptSshExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();

    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_start_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_wait_for_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("filePatterns=");
    assertThat(scriptCommandUnit.getCommand()).contains(TAIL_FILE_PATTERN);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testPrepareScriptSuccessWithTailPatternOnDelegate() {
    SshCommandTaskParameters parameters =
        SshCommandTaskParameters.builder()
            .executeOnDelegate(true)
            .executionId("test")
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder().build())
            .commandUnits(Arrays.asList(initCommandUnit, scriptWithTailFileCommandUnit))
            .build();

    doReturn(scriptProcessExecutor).when(sshScriptExecutorFactory).getExecutor(any());
    when(scriptProcessExecutor.executeCommandString(PRE_INIT_CMD, true)).thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.executeCommandString(eq(PRINT_ENV), any(StringBuffer.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    when(scriptProcessExecutor.getLogCallback()).thenReturn(logCallback);

    CommandExecutionStatus status =
        sshInitCommandHandler
            .handle(parameters, initCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) parameters.getCommandUnits()
                                              .stream()
                                              .filter(cu -> NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType()))
                                              .findFirst()
                                              .get();

    assertThat(scriptCommandUnit.getCommand()).contains(scriptCommandUnit.getScript());
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_start_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("harness_utils_wait_for_tail_log_verification()");
    assertThat(scriptCommandUnit.getCommand()).contains("filePatterns=");
    assertThat(scriptCommandUnit.getCommand()).contains(TAIL_FILE_PATTERN);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidArguments() {
    assertThatThrownBy(()
                           -> sshInitCommandHandler.handle(WinrmTaskParameters.builder().build(), initCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");

    assertThatThrownBy(
        ()
            -> sshInitCommandHandler.handle(SshCommandTaskParameters.builder().build(),
                NgCleanupCommandUnit.builder().build(), logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testEvaluateCommandUnitsVariables() {
    String copyDestinationPathVariable = "$cpDestinationPath";
    String copyDestinationPath = "copyDestinationPath";
    String scriptWorkingDirVariable = "$tmpWorkingDir";
    String scriptWorkingDir = "scriptWorkingDir";
    String downloadDestinationPathVariable = "$downloadDestinationPath";
    String downloadDestinationPath = "downloadDestinationPath";
    SshExecutorFactoryContext mockContext = mock(SshExecutorFactoryContext.class);

    NgInitCommandUnit initCommandUnit = NgInitCommandUnit.builder().build();
    ScriptCommandUnit scriptCommandUnit =
        ScriptCommandUnit.builder().workingDirectory(scriptWorkingDirVariable).build();
    CopyCommandUnit copyCommandUnit = CopyCommandUnit.builder().destinationPath(copyDestinationPathVariable).build();
    NgDownloadArtifactCommandUnit downloadArtifactCommandUnit =
        NgDownloadArtifactCommandUnit.builder().destinationPath(downloadDestinationPathVariable).build();
    NgCleanupCommandUnit cleanupCommandUnit = NgCleanupCommandUnit.builder().build();

    when(mockContext.evaluateVariable(eq(scriptWorkingDirVariable))).thenReturn(scriptWorkingDir);
    when(mockContext.evaluateVariable(eq(copyDestinationPathVariable))).thenReturn(copyDestinationPath);
    when(mockContext.evaluateVariable(eq(downloadDestinationPathVariable))).thenReturn(downloadDestinationPath);

    List<NgCommandUnit> ngCommandUnits =
        List.of(initCommandUnit, scriptCommandUnit, copyCommandUnit, downloadArtifactCommandUnit, cleanupCommandUnit);
    sshInitCommandHandler.evaluateCommandUnitsVariables(mockContext, ngCommandUnits);

    assertThat(ngCommandUnits.size()).isEqualTo(5);
    assertThat(ngCommandUnits.get(0)).isInstanceOf(NgInitCommandUnit.class);

    assertThat(ngCommandUnits.get(1)).isInstanceOf(ScriptCommandUnit.class);
    ScriptCommandUnit evaluatedScriptCommandUnit = (ScriptCommandUnit) ngCommandUnits.get(1);
    assertThat(evaluatedScriptCommandUnit.getWorkingDirectory()).isEqualTo(scriptWorkingDir);

    assertThat(ngCommandUnits.get(2)).isInstanceOf(CopyCommandUnit.class);
    CopyCommandUnit evaluatedCopyCommandUnit = (CopyCommandUnit) ngCommandUnits.get(2);
    assertThat(evaluatedCopyCommandUnit.getDestinationPath()).isEqualTo(copyDestinationPath);

    assertThat(ngCommandUnits.get(3)).isInstanceOf(NgDownloadArtifactCommandUnit.class);
    NgDownloadArtifactCommandUnit evaluatedDownloadArtifactCommandUnit =
        (NgDownloadArtifactCommandUnit) ngCommandUnits.get(3);
    assertThat(evaluatedDownloadArtifactCommandUnit.getDestinationPath()).isEqualTo(downloadDestinationPath);

    assertThat(ngCommandUnits.get(4)).isInstanceOf(NgCleanupCommandUnit.class);
  }
}
