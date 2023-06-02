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
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;

@OwnedBy(CDP)
@UtilityClass
public class SshWinRmExceptionHandler {
  private static final String SSH = "SSH";
  private static final String WIN_RM = "WinRm";
  private static final String INVALID_CREDENTIALS = "invalid credentials";

  public TaskNGDataException handle(
      Exception exception, Logger log, CommandUnitsProgress commandUnitsProgress, boolean isSsh) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
    log.error("Exception in processing command task", sanitizedException);

    return isSsh ? handleSsh(exception, sanitizedException, commandUnitsProgress)
                 : handleWinRm(exception, sanitizedException, commandUnitsProgress);
  }

  private TaskNGDataException handleSsh(
      Exception exception, Exception sanitizedException, CommandUnitsProgress commandUnitsProgress) {
    if (exception instanceof WingsException) {
      WingsException wingsException = (WingsException) exception;
      if (wingsException.getCode() == ErrorCode.INVALID_CREDENTIAL) {
        return wrapToTaskNGDataException(
            NestedExceptionUtils.hintWithExplanationException(format(SSH_INVALID_CREDENTIALS_HINT, SSH),
                format(SSH_INVALID_CREDENTIALS_EXPLANATION, SSH),
                new InvalidRequestException(sanitizedException.getMessage(), USER)),
            commandUnitsProgress);
      }

      return wrapToTaskNGDataException(sanitizedException, commandUnitsProgress);
    }

    return wrapToTaskNGDataException(sanitizedException, commandUnitsProgress);
  }

  private TaskNGDataException handleWinRm(
      Exception exception, Exception sanitizedException, CommandUnitsProgress commandUnitsProgress) {
    if (exception instanceof IllegalStateException) {
      if (exception.getMessage().toLowerCase().contains(INVALID_CREDENTIALS)) {
        return wrapToTaskNGDataException(
            NestedExceptionUtils.hintWithExplanationException(format(SSH_INVALID_CREDENTIALS_HINT, WIN_RM),
                format(SSH_INVALID_CREDENTIALS_EXPLANATION, WIN_RM),
                new InvalidRequestException(sanitizedException.getMessage(), USER)),
            commandUnitsProgress);
      }

      return wrapToTaskNGDataException(sanitizedException, commandUnitsProgress);
    }

    return wrapToTaskNGDataException(sanitizedException, commandUnitsProgress);
  }

  private TaskNGDataException wrapToTaskNGDataException(
      Exception exception, CommandUnitsProgress commandUnitsProgress) {
    return new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), exception);
  }
}
