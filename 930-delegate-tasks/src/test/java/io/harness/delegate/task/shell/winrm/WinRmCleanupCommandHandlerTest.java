/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.Silent.class)
public class WinRmCleanupCommandHandlerTest {
  @Mock ShellExecutorFactoryNG shellExecutorFactory;
  @InjectMocks private WinRmCleanupCommandHandler handler;

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleAllArgumentsNull() {
    assertThatThrownBy(() -> handler.handle(null, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testHandleCommandUnitNull() {
    WinrmTaskParameters commandTaskParameters = mock(WinrmTaskParameters.class);
    assertThatThrownBy(() -> handler.handle(commandTaskParameters, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testHandle() {
    NgCleanupCommandUnit cleanupCommandUnit = mock(NgCleanupCommandUnit.class);
    WinrmTaskParameters commandTaskParameters = mock(WinrmTaskParameters.class);
    ScriptProcessExecutor executor = mock(ScriptProcessExecutor.class);
    when(shellExecutorFactory.getExecutor(any(ShellExecutorConfig.class), any(), any(), eq(true))).thenReturn(executor);
    LogCallback logCallback = mock(LogCallback.class);
    when(executor.getLogCallback()).thenReturn(logCallback);
    ExecuteCommandResponse result = handler.handle(commandTaskParameters, cleanupCommandUnit, null, null, null);
    assertThat(result.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
