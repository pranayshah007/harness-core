/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutionData;

import software.wings.beans.ConnectionType;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.core.winrm.executors.WinRmExecutor;
import software.wings.core.winrm.executors.WinRmExecutorFactory;
import software.wings.service.intfc.security.EncryptionService;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ShellScriptTaskHandlerTest {
  // CLASS UNDER TEST
  @InjectMocks private ShellScriptTaskHandler handler;

  // DEPENDENCIES
  @Mock private WinRmExecutorFactory winrmExecutorFactory;
  @Mock private EncryptionService encryptionService;
  @Mock private WinRmSessionConfig winRmSessionConfig;

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotApplyTimeoutWhenNull() {
    handler.applyTimeoutOnSessionConfig(null, winRmSessionConfig);
    verify(winRmSessionConfig, never()).setTimeout(ArgumentMatchers.any());
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldApplyTimeoutWhenAvailable() {
    handler.applyTimeoutOnSessionConfig(1010L, winRmSessionConfig);
    verify(winRmSessionConfig).setTimeout(1010);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldApplyTimeoutWhenHigherThenIntegerMaxValue() {
    long timeout = Integer.MAX_VALUE;
    handler.applyTimeoutOnSessionConfig(++timeout, winRmSessionConfig);
    verify(winRmSessionConfig).setTimeout(Integer.MAX_VALUE);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void handleTimeoutOnWinRMSession() throws IOException {
    final ShellScriptParameters parameters = ShellScriptParameters.builder()
                                                 .outputVars(null)
                                                 .secretOutputVars(null)
                                                 .executeOnDelegate(false)
                                                 .script("Get-Date;")
                                                 .connectionType(ConnectionType.WINRM)
                                                 .sshTimeOut(1410)
                                                 .build();

    final ShellScriptParameters spyParameters = Mockito.spy(parameters);
    doReturn(winRmSessionConfig).when(spyParameters).winrmSessionConfig(encryptionService);

    WinRmExecutor executor = Mockito.mock(WinRmExecutor.class);
    when(winrmExecutorFactory.getExecutor(winRmSessionConfig, false, false, false)).thenReturn(executor);
    ExecuteCommandResponse response = ExecuteCommandResponse.builder()
                                          .commandExecutionData(ShellExecutionData.builder().build())
                                          .status(CommandExecutionStatus.SUCCESS)
                                          .build();
    when(executor.executeCommandString(anyString(), any(List.class), any(List.class), eq(1410L))).thenReturn(response);

    final CommandExecutionResult result = handler.handle(spyParameters);
    assertThat(result).isNotNull();
    verify(winRmSessionConfig).setTimeout(1410);
  }
}
