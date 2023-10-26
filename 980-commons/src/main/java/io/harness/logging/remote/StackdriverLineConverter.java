/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Severity;
import java.util.Map;

public class StackdriverLineConverter implements LineConverter<LogEntry> {
  @Override
  public LogEntry convert(final Map<String, ?> line) {
    final var level = (String) line.remove(SEVERITY);
    final var timestamp = Long.parseLong((String) line.remove(TIMESTAMP));
    return LogEntry.newBuilder(Payload.JsonPayload.of(line))
        .setSeverity(getSeverity(level))
        .setTimestamp(timestamp)
        .build();
  }

  private static Severity getSeverity(final String level) {
    if (isEmpty(level)) {
      return Severity.INFO;
    }

    switch (level) {
      case "ERROR":
        return Severity.ERROR;
      case "TRACE":
      case "DEBUG":
        return Severity.DEBUG;
      case "WARN":
        return Severity.WARNING;
      case "INFO":
      default:
        return Severity.INFO;
    }
  }
}
