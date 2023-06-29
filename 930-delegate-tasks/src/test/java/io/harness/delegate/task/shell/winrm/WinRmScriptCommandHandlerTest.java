/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;

import software.wings.core.winrm.executors.WinRmExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class WinRmScriptCommandHandlerTest {
  @Mock private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private Map<String, Object> taskContext;
  @Mock private ShellExecutorFactoryNG shellExecutorFactory;

  @InjectMocks private WinRmScriptCommandHandler winRmScriptCommandHandler;

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldExecuteScriptCommandWithWinRmExecutor() {
    String command = "command";
    List<String> outputVariables = List.of("variable");
    List<String> secretOutputVariables = List.of("secretVariable");
    ScriptCommandUnit scriptCommandUnit =
        ScriptCommandUnit.builder().script("echo HELLO").scriptType(ScriptType.BASH).command(command).build();
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    WinrmTaskParameters winrmTaskParameters = WinrmTaskParameters.builder()
                                                  .commandUnits(Arrays.asList(scriptCommandUnit))
                                                  .executeOnDelegate(false)
                                                  .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
                                                  .disableWinRMCommandEncodingFFSet(true)
                                                  .outputVariables(outputVariables)
                                                  .secretOutputVariables(secretOutputVariables)
                                                  .build();
    WinRmExecutor executor = mock(WinRmExecutor.class);
    when(winRmExecutorFactoryNG.getExecutor(any(), anyBoolean(), anyBoolean(), any(), any())).thenReturn(executor);
    when(executor.executeCommandString(command, outputVariables, secretOutputVariables, null))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    CommandExecutionStatus result = winRmScriptCommandHandler
                                        .handle(winrmTaskParameters, scriptCommandUnit, iLogStreamingTaskClient,
                                            CommandUnitsProgress.builder().build(), taskContext)
                                        .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testShouldExecuteScriptCommandWithWinRmExecutorOnDelegate() {
    String command = "command";
    List<String> outputVariables = List.of("variable");
    List<String> secretOutputVariables = List.of("secretVariable");
    ScriptCommandUnit scriptCommandUnit =
        ScriptCommandUnit.builder().script("echo HELLO").scriptType(ScriptType.POWERSHELL).command(command).build();
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    WinrmTaskParameters winrmTaskParameters = WinrmTaskParameters.builder()
                                                  .commandUnits(Arrays.asList(scriptCommandUnit))
                                                  .executeOnDelegate(true)
                                                  .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
                                                  .disableWinRMCommandEncodingFFSet(true)
                                                  .outputVariables(outputVariables)
                                                  .secretOutputVariables(secretOutputVariables)
                                                  .build();
    ScriptProcessExecutor executor = mock(ScriptProcessExecutor.class);
    when(shellExecutorFactory.getExecutor(any(), any(), any(), anyBoolean())).thenReturn(executor);
    when(executor.executeCommandString(command, outputVariables, secretOutputVariables, null))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    when(executor.getLogCallback()).thenReturn(mock(LogCallback.class));
    CommandExecutionStatus result = winRmScriptCommandHandler
                                        .handle(winrmTaskParameters, scriptCommandUnit, iLogStreamingTaskClient,
                                            CommandUnitsProgress.builder().build(), taskContext)
                                        .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
