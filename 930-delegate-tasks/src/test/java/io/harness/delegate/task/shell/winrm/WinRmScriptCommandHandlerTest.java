package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
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
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;

import software.wings.core.winrm.executors.WinRmExecutor;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class WinRmScriptCommandHandlerTest {
  @Mock private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;

  @InjectMocks private WinRmScriptCommandHandler winRmScriptCommandHandler;

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldExecuteScriptCommandWithWinRmExecutor() {
    String command = "command";
    List<String> outputVariables = Arrays.asList("variable");
    ScriptCommandUnit scriptCommandUnit =
        ScriptCommandUnit.builder().script("echo HELLO").scriptType(ScriptType.BASH).command(command).build();
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    WinrmTaskParameters winrmTaskParameters = WinrmTaskParameters.builder()
                                                  .commandUnits(Arrays.asList(scriptCommandUnit))
                                                  .executeOnDelegate(true)
                                                  .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
                                                  .disableWinRMCommandEncodingFFSet(true)
                                                  .outputVariables(outputVariables)
                                                  .build();
    WinRmExecutor executor = mock(WinRmExecutor.class);
    when(winRmExecutorFactoryNG.getExecutor(any(), anyBoolean(), any(), any())).thenReturn(executor);
    when(executor.executeCommandString(command, outputVariables))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    CommandExecutionStatus result = winRmScriptCommandHandler.handle(
        winrmTaskParameters, scriptCommandUnit, iLogStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
