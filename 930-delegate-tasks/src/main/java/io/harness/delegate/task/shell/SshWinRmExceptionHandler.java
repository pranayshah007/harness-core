/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.SSH_INVALID_CREDENTIALS_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.SSH_INVALID_CREDENTIALS_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.TASK_TIMEOUT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.TASK_TIMEOUT_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.TASK_TIMEOUT_HINT;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;

@OwnedBy(CDP)
@UtilityClass
public class SshWinRmExceptionHandler {
  private static final String SSH = "SSH";
  private static final String WIN_RM = "WinRm";
  private static final String INVALID_CREDENTIALS = "invalid credentials";
  private static final String TASK_TIMEOUT = "Task is time out";

  public TaskNGDataException handle(Exception exception, Logger log, CommandUnitsProgress commandUnitsProgress,
      boolean isSsh, ILogStreamingTaskClient logStreamingTaskClient,
      SshWinRmLogCallbackProviderFactory logCallbackProviderFactory) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
    log.error("Exception in processing command task", sanitizedException);

    if (exception instanceof TimeoutException || exception instanceof InterruptedException) {
      return wrapToTaskNGDataException(NestedExceptionUtils.hintWithExplanationException(TASK_TIMEOUT_HINT,
                                           TASK_TIMEOUT_EXPLANATION, new TimeoutException(TASK_TIMEOUT_FAILED)),
          commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
    }

    return isSsh ? handleSsh(exception, sanitizedException, commandUnitsProgress, logStreamingTaskClient,
               logCallbackProviderFactory, log)
                 : handleWinRm(exception, sanitizedException, commandUnitsProgress, logStreamingTaskClient,
                     logCallbackProviderFactory, log);
  }

  private TaskNGDataException handleSsh(Exception exception, Exception sanitizedException,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient logStreamingTaskClient,
      SshWinRmLogCallbackProviderFactory logCallbackProviderFactory, Logger log) {
    if (exception instanceof WingsException) {
      WingsException wingsException = (WingsException) exception;
      if (wingsException.getCode() == ErrorCode.INVALID_CREDENTIAL) {
        return wrapToTaskNGDataException(
            NestedExceptionUtils.hintWithExplanationException(format(SSH_INVALID_CREDENTIALS_HINT, SSH),
                format(SSH_INVALID_CREDENTIALS_EXPLANATION, SSH),
                new InvalidRequestException(sanitizedException.getMessage(), USER)),
            commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
      }

      return wrapToTaskNGDataException(
          sanitizedException, commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
    }

    return wrapToTaskNGDataException(
        sanitizedException, commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
  }

  private TaskNGDataException handleWinRm(Exception exception, Exception sanitizedException,
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient logStreamingTaskClient,
      SshWinRmLogCallbackProviderFactory logCallbackProviderFactory, Logger log) {
    if (exception instanceof IllegalStateException) {
      if (exception.getMessage().toLowerCase().contains(INVALID_CREDENTIALS)) {
        return wrapToTaskNGDataException(
            NestedExceptionUtils.hintWithExplanationException(format(SSH_INVALID_CREDENTIALS_HINT, WIN_RM),
                format(SSH_INVALID_CREDENTIALS_EXPLANATION, WIN_RM),
                new InvalidRequestException(sanitizedException.getMessage(), USER)),
            commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
      }

      return wrapToTaskNGDataException(
          sanitizedException, commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
    }

    return wrapToTaskNGDataException(
        sanitizedException, commandUnitsProgress, logStreamingTaskClient, logCallbackProviderFactory, log);
  }

  private TaskNGDataException wrapToTaskNGDataException(Exception exception, CommandUnitsProgress commandUnitsProgress,
      ILogStreamingTaskClient logStreamingTaskClient, SshWinRmLogCallbackProviderFactory logCallbackProviderFactory,
      Logger log) {
    Map<String, CommandUnitProgress> commandUnitProgressMap = commandUnitsProgress.getCommandUnitProgressMap();

    if (EmptyPredicate.isNotEmpty(commandUnitProgressMap)) {
      NgSshWinRmLogCallbackProvider logCallbackProvider =
          (NgSshWinRmLogCallbackProvider) logCallbackProviderFactory.createNg(
              logStreamingTaskClient, commandUnitsProgress);

      commandUnitProgressMap.entrySet()
          .stream()
          .filter(Objects::nonNull)
          .filter(entry -> CommandExecutionStatus.RUNNING == entry.getValue().getStatus())
          .forEach(entry -> {
            saveLogErrorAndCloseStream(entry.getKey(), logCallbackProvider, exception.getMessage(), log);
          });
    }

    return new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), exception);
  }

  private void saveLogErrorAndCloseStream(
      String commandUnitName, NgSshWinRmLogCallbackProvider logCallbackProvider, String message, Logger log) {
    try {
      LogCallback logCallback = logCallbackProvider.obtainLogCallback(commandUnitName);
      logCallback.saveExecutionLog(String.format("Failed: [%s].", message), LogLevel.ERROR, FAILURE);
    } catch (Exception e) {
      log.error("Failed to save execution log for command unit {}", commandUnitName, e);
    }
  }
}
