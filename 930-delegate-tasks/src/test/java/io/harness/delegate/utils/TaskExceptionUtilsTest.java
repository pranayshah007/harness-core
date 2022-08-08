/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.exception.DataException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class TaskExceptionUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private Function<String, LogCallback> logCallbackGetter;
  @Mock private LogCallback logCallback;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFilterOutExceptions() {
    final Set<Class<? extends Throwable>> exceptionsToFilter =
        ImmutableSet.of(HintException.class, DataException.class);
    final Exception exception = new TaskNGDataException(
        null, new HintException("Hint", new TaskNGDataException(null, new RuntimeException("Actual exception"))));

    Throwable result = TaskExceptionUtils.filterOutExceptions(exception, exceptionsToFilter);
    assertThat(result).isInstanceOf(RuntimeException.class);
    assertThat(result.getMessage()).isEqualTo("Actual exception");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleExceptionCommandUnits() {
    final LinkedHashMap<String, CommandUnitProgress> commandUnitsProgressMap = new LinkedHashMap<>();
    final CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitsProgressMap).build();
    final Exception actualException = new RuntimeException("Actual exception");
    final Exception exception =
        new TaskNGDataException(null, new HintException("Hint", new ExplanationException("Expl", actualException)));

    doReturn(logCallback).when(logCallbackGetter).apply(anyString());

    commandUnitsProgressMap.put("Test1", CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build());
    commandUnitsProgressMap.put("Test2", CommandUnitProgress.builder().status(CommandExecutionStatus.FAILURE).build());
    commandUnitsProgressMap.put("Test3", CommandUnitProgress.builder().status(CommandExecutionStatus.RUNNING).build());
    commandUnitsProgressMap.put("Test4", CommandUnitProgress.builder().status(CommandExecutionStatus.RUNNING).build());

    TaskExceptionUtils.handleExceptionCommandUnits(commandUnitsProgress, logCallbackGetter, exception);

    verify(logCallbackGetter).apply("Test3");
    verify(logCallbackGetter).apply("Test4");
    verifyNoMoreInteractions(logCallbackGetter);
    verify(logCallback, times(2))
        .saveExecutionLog(
            String.format("Failed: [%s].", ExceptionUtils.getMessage(actualException)), LogLevel.ERROR, FAILURE);
  }
}