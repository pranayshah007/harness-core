/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("PMD.AvoidStringBufferField")
@OwnedBy(CDP)
@Slf4j
public class TerraformCliErrorLogOutputStream extends ErrorLogOutputStream {
  private static final Pattern TF_UNDERLINED_LOG_LINE_PATTERN = Pattern.compile("\\u001B\\[4m(.*?)\\u001B\\[0m");
  private static final Pattern TF_LOG_LINE_PATTERN =
      Pattern.compile("\\[(?:TRACE|DEBUG|INFO|WARN|ERROR|CRITICAL)\\]\\s?(.+?)?:");
  private static final Predicate<String> cliErrorPredicate =
      logLine -> isNotEmpty(logLine) && !TF_LOG_LINE_PATTERN.matcher(logLine).find();

  public TerraformCliErrorLogOutputStream(LogCallback executionLogCallback, boolean skipColorLogs) {
    this.executionLogCallback = executionLogCallback;
    this.skipColorLogs = skipColorLogs;
  }
  private final LogCallback executionLogCallback;
  private StringBuilder errorLogs;

  private boolean skipColorLogs;

  @Override
  protected void processLine(String line) {
    if (cliErrorPredicate.test(line)) {
      String processedLine = line;
      try {
        Matcher matcher = TF_UNDERLINED_LOG_LINE_PATTERN.matcher(line);
        List<String> matches = matcher.results().map(MatchResult::group).collect(Collectors.toList());
        List<String> sanitizedMatches =
            matches.stream().map(match -> match.replaceAll("\\u001B\\[(4|0)m", "")).collect(Collectors.toList());
        processedLine =
            StringUtils.replaceEach(line, matches.toArray(new String[] {}), sanitizedMatches.toArray(new String[] {}));
      } catch (Exception e) {
        log.warn("Failed to sanitize log line: {}", e.getMessage());
      }

      if (errorLogs == null) {
        errorLogs = new StringBuilder();
      }
      executionLogCallback.saveExecutionLog(processedLine, LogLevel.ERROR, skipColorLogs);

      log.error(processedLine);
      errorLogs.append(' ').append(processedLine);
    }
  }

  @Override
  public String getError() {
    if (errorLogs != null) {
      return errorLogs.toString().trim();
    }
    return null;
  }
}
