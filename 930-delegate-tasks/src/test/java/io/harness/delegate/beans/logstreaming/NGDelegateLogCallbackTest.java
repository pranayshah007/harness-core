/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGDelegateLogCallbackTest extends CategoryTest implements MockableTestMixin {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ITaskProgressClient taskProgressClient;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testSendTaskProgressUpdate() {
    // should not throw Exception and should log command unit progress map
    commandUnitsProgress.getCommandUnitProgressMap().put(
        "key1", CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build());
    NGDelegateLogCallback ngDelegateLogCallback =
        new NGDelegateLogCallback(logStreamingTaskClient, null, false, commandUnitsProgress);
    doThrow(new RuntimeException("failed")).when(taskProgressClient).sendTaskProgressUpdate(any());

    ngDelegateLogCallback.sendTaskProgressUpdate(taskProgressClient);
    verify(taskProgressClient, Mockito.times(1)).sendTaskProgressUpdate(any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testUpdateCommandUnitProgressMap() {
    NGDelegateLogCallback ngDelegateLogCallback =
        new NGDelegateLogCallback(logStreamingTaskClient, "unit1", false, commandUnitsProgress);
    assertThat(commandUnitsProgress.getCommandUnitProgressMap()).isEmpty();

    Instant now = Instant.now();
    boolean change = ngDelegateLogCallback.updateCommandUnitProgressMap(
        CommandExecutionStatus.RUNNING, now, commandUnitsProgress.getCommandUnitProgressMap());
    assertThat(change).isEqualTo(true);
    CommandUnitProgress commandUnitProgress =
        CommandUnitProgress.builder().status(CommandExecutionStatus.RUNNING).startTime(now.toEpochMilli()).build();
    assertThat(commandUnitsProgress.getCommandUnitProgressMap().get("unit1")).isEqualTo(commandUnitProgress);

    change = ngDelegateLogCallback.updateCommandUnitProgressMap(
        CommandExecutionStatus.RUNNING, now, commandUnitsProgress.getCommandUnitProgressMap());
    assertThat(change).isEqualTo(false);
    assertThat(commandUnitsProgress.getCommandUnitProgressMap().get("unit1")).isEqualTo(commandUnitProgress);

    change = ngDelegateLogCallback.updateCommandUnitProgressMap(
        CommandExecutionStatus.SUCCESS, now, commandUnitsProgress.getCommandUnitProgressMap());
    commandUnitProgress = CommandUnitProgress.builder()
                              .status(CommandExecutionStatus.SUCCESS)
                              .startTime(now.toEpochMilli())
                              .endTime(now.toEpochMilli())
                              .build();
    assertThat(change).isEqualTo(true);
    assertThat(commandUnitsProgress.getCommandUnitProgressMap().get("unit1")).isEqualTo(commandUnitProgress);

    change = ngDelegateLogCallback.updateCommandUnitProgressMap(
        CommandExecutionStatus.RUNNING, now, commandUnitsProgress.getCommandUnitProgressMap());
    assertThat(change).isEqualTo(false);
    assertThat(commandUnitsProgress.getCommandUnitProgressMap().get("unit1")).isEqualTo(commandUnitProgress);

    change = ngDelegateLogCallback.updateCommandUnitProgressMap(
        CommandExecutionStatus.SUCCESS, now, commandUnitsProgress.getCommandUnitProgressMap());
    assertThat(change).isEqualTo(false);
    assertThat(commandUnitsProgress.getCommandUnitProgressMap().get("unit1")).isEqualTo(commandUnitProgress);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWhenClientNull() {
    // should not throw Exception
    commandUnitsProgress.getCommandUnitProgressMap().put(
        "key1", CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build());
    NGDelegateLogCallback ngDelegateLogCallback = new NGDelegateLogCallback(null, null, false, commandUnitsProgress);

    ngDelegateLogCallback.saveExecutionLog("dummy", LogLevel.ERROR, CommandExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWhenTerminalStatus() {
    NGDelegateLogCallback ngDelegateLogCallback = new NGDelegateLogCallback(logStreamingTaskClient, null, false, null);

    ngDelegateLogCallback.saveExecutionLog("dummy", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    verify(logStreamingTaskClient, Mockito.times(1)).writeLogLine(any(), eq(null));
    verify(logStreamingTaskClient, Mockito.times(1)).closeStream(eq(null));
    verifyNoMoreInteractions(logStreamingTaskClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWhenExplicitlyCloseStream() {
    NGDelegateLogCallback ngDelegateLogCallback = new NGDelegateLogCallback(logStreamingTaskClient, null, false, null);

    ngDelegateLogCallback.saveExecutionLog("dummy", LogLevel.ERROR, CommandExecutionStatus.RUNNING, true);
    verify(logStreamingTaskClient, Mockito.times(1)).writeLogLine(any(), eq(null));
    verify(logStreamingTaskClient, Mockito.times(1)).closeStream(eq(null));
    verifyNoMoreInteractions(logStreamingTaskClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWhenExplicitlyNotCloseStreamAndTerminalStatus() {
    when(logStreamingTaskClient.obtainTaskProgressClient()).thenReturn(null);
    when(logStreamingTaskClient.obtainTaskProgressExecutor()).thenReturn(executorService);
    NGDelegateLogCallback ngDelegateLogCallback =
        new NGDelegateLogCallback(logStreamingTaskClient, "unit1", false, commandUnitsProgress);
    ngDelegateLogCallback.saveExecutionLog("error occurred", LogLevel.ERROR, CommandExecutionStatus.FAILURE, false);
    verify(logStreamingTaskClient, Mockito.times(1)).writeLogLine(any(), eq("unit1"));
    verify(logStreamingTaskClient, Mockito.times(0)).closeStream(any());
    verify(logStreamingTaskClient, Mockito.times(1)).obtainTaskProgressClient();
    verify(logStreamingTaskClient, Mockito.times(1)).obtainTaskProgressExecutor();
    verifyNoMoreInteractions(logStreamingTaskClient);
  }
}
