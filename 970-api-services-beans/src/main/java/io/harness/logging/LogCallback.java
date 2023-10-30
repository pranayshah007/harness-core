/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

public interface LogCallback {
  void saveExecutionLog(String line);

  void saveExecutionLog(String line, LogLevel logLevel);

  default void saveExecutionLog(String line, LogLevel logLevel, boolean skipColoringLog) {
    saveExecutionLog(line, logLevel);
  }

  default void saveExecutionLog(
      String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus, boolean closeLogStream) {
    saveExecutionLog(line, logLevel, commandExecutionStatus);
  }

  void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus);

  default void dispatchLogs() {}

  default void close(CommandExecutionStatus commandExecutionStatus) {}
}
