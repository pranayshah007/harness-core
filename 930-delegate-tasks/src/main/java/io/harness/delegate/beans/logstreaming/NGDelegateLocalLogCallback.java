/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;

import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class NGDelegateLocalLogCallback implements LogCallback {
  @Override
  public void saveExecutionLog(String line) {
    log.info(line);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel) {
    if (logLevel == null) {
      logLevel = LogLevel.INFO;
    }
    switch (logLevel) {
      case WARN:
        log.warn(line);
      case DEBUG:
        log.debug(line);
      case FATAL:
      case ERROR:
        log.error(line);
      case INFO:
      default:
        log.info(line);
    }
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    saveExecutionLog(line, logLevel);
  }
}
