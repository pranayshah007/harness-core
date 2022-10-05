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