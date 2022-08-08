/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cli;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class CliHelper {
  @Nonnull
  public CliResponse executeCliCommand(String command, long timeoutInMillis, Map<String, String> envVariables,
      String directory, LogCallback executionLogCallback) throws IOException, InterruptedException, TimeoutException {
    return executeCliCommand(
        command, timeoutInMillis, envVariables, directory, executionLogCallback, command, new EmptyLogOutputStream());
  }

  @Nonnull
  public CliResponse executeCliCommand(String command, long timeoutInMillis, Map<String, String> envVariables,
      String directory, LogCallback executionLogCallback, String loggingCommand, LogOutputStream logOutputStream)
      throws IOException, InterruptedException, TimeoutException {
    executionLogCallback.saveExecutionLog(loggingCommand, LogLevel.INFO, RUNNING);

    StringBuilder errorLogs = new StringBuilder();
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .environment(CollectionUtils.emptyIfNull(envVariables))
                                          .directory(new File(directory))
                                          .redirectOutput(logOutputStream)
                                          .redirectError(new LogOutputStream() {
                                            @Override
                                            protected void processLine(String line) {
                                              log.error(line);
                                              executionLogCallback.saveExecutionLog(line, LogLevel.ERROR);
                                              errorLogs.append(' ').append(line);
                                            }
                                          });

    ProcessResult processResult = processExecutor.execute();
    CommandExecutionStatus status = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
    return CliResponse.builder()
        .commandExecutionStatus(status)
        .output(processResult.outputUTF8())
        .error(errorLogs.toString().trim())
        .exitCode(processResult.getExitValue())
        .build();
  }
}
