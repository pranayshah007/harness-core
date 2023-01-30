/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.logging.LogCallback;

import org.slf4j.Logger;

public class LogWrapper {
  final Logger log;

  public LogWrapper(Logger log) {
    this.log = log;
  }

  public void info(LogCallback logCallback, String msg, Object... params) {
    info(logCallback, msg, false, params);
  }

  public void infoBold(LogCallback logCallback, String msg, Object... params) {
    info(logCallback, msg, true, params);
  }

  public void info(LogCallback logCallback, String msg, boolean isBold, Object... params) {
    String formatted = format(msg, params);
    log.info(formatted);
    if (isBold) {
      logCallback.saveExecutionLog(color(formatted, White, Bold), INFO);
    } else {
      logCallback.saveExecutionLog(formatted);
    }
  }

  public void warn(LogCallback logCallback, String msg, Object... params) {
    String formatted = format(msg, params);
    log.warn(formatted);
    logCallback.saveExecutionLog(color(formatted, Yellow, Bold), INFO);
  }
}
