/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.exception.DataException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class TaskExceptionUtils {
  private static final Set<Class<? extends Throwable>> HARNESS_META_EXCEPTIONS =
      ImmutableSet.of(HintException.class, ExplanationException.class, DataException.class);

  public void handleExceptionCommandUnits(
      CommandUnitsProgress unitsProgress, Function<String, LogCallback> logCallbackGetter, Throwable exception) {
    Map<String, CommandUnitProgress> commandUnitProgressMap = unitsProgress.getCommandUnitProgressMap();

    if (EmptyPredicate.isNotEmpty(commandUnitProgressMap)) {
      commandUnitProgressMap.entrySet()
          .stream()
          .filter(Objects::nonNull)
          .filter(entry -> CommandExecutionStatus.RUNNING == entry.getValue().getStatus())
          .forEach(entry -> {
            Throwable failureCause = filterOutExceptions(exception, TaskExceptionUtils.HARNESS_META_EXCEPTIONS);
            String message = ExceptionUtils.getMessage(failureCause);
            saveLogErrorAndCloseStream(entry.getKey(), logCallbackGetter, message);
          });
    }
  }

  public Throwable filterOutExceptions(Throwable throwable, Set<Class<? extends Throwable>> exceptions) {
    if (throwable.getCause() == null) {
      return throwable;
    }

    if (exceptions.stream().anyMatch(clazz -> clazz.isAssignableFrom(throwable.getClass()))) {
      return filterOutExceptions(throwable.getCause(), exceptions);
    }

    return throwable;
  }

  private void saveLogErrorAndCloseStream(
      String commandUnitName, Function<String, LogCallback> logCallbackGetter, String message) {
    try {
      LogCallback logCallback = logCallbackGetter.apply(commandUnitName);
      logCallback.saveExecutionLog(String.format("Failed: [%s].", message), LogLevel.ERROR, FAILURE);
    } catch (Exception e) {
      log.error("Failed to save execution log for command unit {}", commandUnitName, e);
    }
  }
}
