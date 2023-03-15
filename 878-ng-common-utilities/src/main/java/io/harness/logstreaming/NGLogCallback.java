/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.time.Instant;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGLogCallback implements LogCallback {
  private final ILogStreamingStepClient logStreamingClient;
  private final String logSuffix;

  public NGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance, String logSuffix,
      boolean shouldOpenStream) {
    this.logStreamingClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    this.logSuffix = logSuffix;
    if (shouldOpenStream) {
      logStreamingClient.openStream(logSuffix);
    }
  }

  @Override
  public void saveExecutionLog(String line) {
    saveExecutionLog(line, LogLevel.INFO);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel) {
    saveExecutionLog(line, logLevel, CommandExecutionStatus.RUNNING);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    Instant now = Instant.now();
    LogLine logLine = LogLine.builder().message(line).level(logLevel).timestamp(now).build();
    logStreamingClient.writeLogLine(logLine, logSuffix);
    boolean terminalStatus = CommandExecutionStatus.isTerminalStatus(commandExecutionStatus);
    if (terminalStatus) {
      logStreamingClient.closeStream(logSuffix);
    }
  }

  public String getLogBaseKey() {
    if (this.logStreamingClient != null) {
      LogStreamingStepClientImpl logStreamingStepClient = (LogStreamingStepClientImpl) this.logStreamingClient;
      return logStreamingStepClient.getBaseLogKey();
    }
    return "";
  }
}
