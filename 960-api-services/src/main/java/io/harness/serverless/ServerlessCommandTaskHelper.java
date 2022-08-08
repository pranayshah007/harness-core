/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
@UtilityClass
public class ServerlessCommandTaskHelper {
  public static LogOutputStream getExecutionLogOutputStream(LogCallback executionLogCallback, LogLevel logLevel) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(line, logLevel);
      }
    };
  }

  public static ServerlessCliResponse executeCommand(AbstractExecutable command, String workingDirectory,
      LogCallback executionLogCallback, boolean printCommand, long timeoutInMillis, Map<String, String> envVariables)
      throws InterruptedException, TimeoutException, IOException {
    try (LogOutputStream logOutputStream = getExecutionLogOutputStream(executionLogCallback, INFO);
         LogOutputStream logErrorStream = getExecutionLogOutputStream(executionLogCallback, ERROR)) {
      return command.execute(
          workingDirectory, logOutputStream, logErrorStream, printCommand, timeoutInMillis, envVariables);
    }
  }
}
