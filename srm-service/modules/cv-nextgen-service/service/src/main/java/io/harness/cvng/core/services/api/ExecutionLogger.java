/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;

import java.util.List;

public interface ExecutionLogger {
  default void info(String message) {
    this.log(LogLevel.INFO, message);
  }
  default void warn(String message) { // TODO change others too
    this.log(LogLevel.INFO, message);
  }
  default void error(String message, String... messages) {
    this.log(LogLevel.ERROR, message, messages);
  }

  void log(LogLevel logLevel, String message, String... messages);

  void log(LogLevel logLevel, List<CVNGLogTag> cvngLogTags, String message, String... messages);
}
