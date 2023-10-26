/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.logging.client.StackdriverLogLine;

import com.google.logging.type.LogSeverity;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoggingServiceLineConverter implements LineConverter<StackdriverLogLine> {
  private final Supplier<Map<String, String>> labelSupplier;

  @Override
  public StackdriverLogLine convert(final Map<String, ?> line) {
    final var level = (String) line.remove(SEVERITY);
    final var timestamp = Long.parseLong((String) line.remove(TIMESTAMP));

    return new StackdriverLogLine(line, labelSupplier.get(), getSeverity(level), timestamp);
  }

  private static int getSeverity(final String level) {
    if (isEmpty(level)) {
      return LogSeverity.INFO_VALUE;
    }

    switch (level) {
      case "ERROR":
        return LogSeverity.ERROR_VALUE;
      case "TRACE":
      case "DEBUG":
        return LogSeverity.DEBUG_VALUE;
      case "WARN":
        return LogSeverity.WARNING_VALUE;
      case "INFO":
      default:
        return LogSeverity.INFO_VALUE;
    }
  }
}
