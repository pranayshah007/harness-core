/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.FILIP;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.winrm.WinRmConfigAuthEnhancer;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;

import software.wings.core.winrm.executors.WinRmExecutor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.NotImplementedException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WinRmShellScriptTaskNGTest extends CategoryTest {
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;
  @Mock WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock ShellExecutorFactoryNG shellExecutorFactory;
  @Mock ScriptProcessExecutor executor;
  @Mock WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock WinRmExecutor winRmExecutor;

  @InjectMocks
  WinRmShellScriptTaskNG winRmShellScriptTask =
      new WinRmShellScriptTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(),
          logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(shellExecutorFactory.getExecutor(any(), any(), any())).thenReturn(executor);

    when(winRmExecutorFactoryNG.getExecutor(any(), anyBoolean(), any(), any())).thenReturn(winRmExecutor);
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testShouldAcceptOnlyTaskParams() {
    winRmShellScriptTask.run(new Object[] {});
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldReturnFailureWhenExceptionIsThrown() {
    // given
    TaskParameters parameters = WinRmShellScriptTaskParametersNG.builder().executeOnDelegate(false).build();

    // when
    DelegateResponseData responseData = winRmShellScriptTask.run(parameters);

    // then
    Assertions.assertThat(responseData)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(ShellScriptTaskResponseNG.class))
        .extracting(ShellScriptTaskResponseNG::getStatus)
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldReturnSuccessWhenExecutedOnDelegate() {
    // given
    TaskParameters parameters = WinRmShellScriptTaskParametersNG.builder()
                                    .executeOnDelegate(true)
                                    .outputVars(emptyList())
                                    .secretOutputVars(emptyList())
                                    .build();

    when(executor.executeCommandString(any(), anyList()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    when(executor.executeCommandString(any(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());

    // when
    DelegateResponseData responseData = winRmShellScriptTask.run(parameters);

    // then
    Assertions.assertThat(responseData)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(ShellScriptTaskResponseNG.class))
        .extracting(ShellScriptTaskResponseNG::getStatus)
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldReturnFailureWhenExecutorReturnFailureOnDelegate() {
    // given
    TaskParameters parameters =
        WinRmShellScriptTaskParametersNG.builder().executeOnDelegate(true).outputVars(emptyList()).build();

    when(executor.executeCommandString(any(), anyList()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build());

    // when
    DelegateResponseData responseData = winRmShellScriptTask.run(parameters);

    // then
    Assertions.assertThat(responseData)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(ShellScriptTaskResponseNG.class))
        .extracting(ShellScriptTaskResponseNG::getStatus)
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldReturnSuccessWhenExecutedOnRemote() {
    // given
    TaskParameters parameters = WinRmShellScriptTaskParametersNG.builder()
                                    .executeOnDelegate(false)
                                    .outputVars(emptyList())
                                    .secretOutputVars(emptyList())
                                    .script("echo test")
                                    .build();

    when(winRmExecutor.executeCommandString(anyString())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(winRmExecutor.executeCommandString(any(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());

    // when
    DelegateResponseData responseData = winRmShellScriptTask.run(parameters);

    // then
    Assertions.assertThat(responseData)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(ShellScriptTaskResponseNG.class))
        .extracting(ShellScriptTaskResponseNG::getStatus)
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void shouldReturnFailureWhenExecutorReturnFailureOnRemote() {
    // given
    TaskParameters parameters =
        WinRmShellScriptTaskParametersNG.builder().executeOnDelegate(false).outputVars(emptyList()).build();

    when(winRmExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(winRmExecutor.executeCommandString(anyString(), anyList()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build());

    // when
    DelegateResponseData responseData = winRmShellScriptTask.run(parameters);

    // then
    Assertions.assertThat(responseData)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(ShellScriptTaskResponseNG.class))
        .extracting(ShellScriptTaskResponseNG::getStatus)
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }
}