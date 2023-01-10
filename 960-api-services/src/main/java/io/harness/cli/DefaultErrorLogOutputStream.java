/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("PMD.AvoidStringBufferField")
@OwnedBy(CDP)
@Slf4j
public class DefaultErrorLogOutputStream extends ErrorLogOutputStream {
  public DefaultErrorLogOutputStream(LogCallback executionLogCallback) {
    this.executionLogCallback = executionLogCallback;
  }
  LogCallback executionLogCallback;
  StringBuilder errorLogs;

  @Override
  protected void processLine(String line) {
    if (errorLogs == null) {
      errorLogs = new StringBuilder();
    }
    log.error(line);
    executionLogCallback.saveExecutionLog(line, LogLevel.ERROR);
    errorLogs.append(' ').append(line);
  }

  @Override
  public String getError() {
    if (errorLogs != null) {
      return errorLogs.toString().trim();
    }
    return null;
  }
}
