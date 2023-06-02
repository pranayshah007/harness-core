/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.verification;

import software.wings.beans.dto.CVActivityLog;

public interface CVActivityLogger {
  default void info(String message, long... timestampParams) {
    this.appendLog(CVActivityLog.LogLevel.INFO, message, timestampParams);
  }
  default void warn(String message, long... timestampParams) {
    this.appendLog(CVActivityLog.LogLevel.WARN, message, timestampParams);
  }
  default void error(String message, long... timestampParams) {
    this.appendLog(CVActivityLog.LogLevel.ERROR, message, timestampParams);
  }

  /**
   * use %t in the log message with timestamp params to localize timestamp in the UI.
   * @param logLevel
   * @param message
   * @param timestampParams epoch timestamp in millis
   */
  void appendLog(CVActivityLog.LogLevel logLevel, String message, long... timestampParams);
}
