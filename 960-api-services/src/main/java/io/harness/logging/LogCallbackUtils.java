/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import groovy.util.logging.Slf4j;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
@Slf4j
public class LogCallbackUtils {
  public void saveExecutionLogSafely(LogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }

  public void saveExecutionLogSafely(LogCallback logCallback, String line, LogLevel logLevel) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, logLevel);
    }
  }

  public void saveExecutionLogSafely(
      LogCallback logCallback, String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, logLevel, commandExecutionStatus);
    }
  }
}
