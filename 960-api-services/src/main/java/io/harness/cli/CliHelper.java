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
    CliCommandRequest request = CliCommandRequest.builder()
                                    .command(command)
                                    .timeoutInMillis(timeoutInMillis)
                                    .envVariables(envVariables)
                                    .directory(directory)
                                    .logCallback(executionLogCallback)
                                    .loggingCommand(command)
                                    .logOutputStream(new EmptyLogOutputStream())
                                    .errorLogOutputStream(new DefaultErrorLogOutputStream(executionLogCallback))
                                    .secondsToWaitForGracefulShutdown(0)
                                    .build();

    return executeCliCommand(request);
  }

  @Nonnull
  public CliResponse executeCliCommand(String command, long timeoutInMillis, Map<String, String> envVariables,
      String directory, LogCallback executionLogCallback, String loggingCommand, LogOutputStream logOutputStream)
      throws IOException, InterruptedException, TimeoutException {
    CliCommandRequest request = CliCommandRequest.builder()
                                    .command(command)
                                    .timeoutInMillis(timeoutInMillis)
                                    .envVariables(envVariables)
                                    .directory(directory)
                                    .logCallback(executionLogCallback)
                                    .loggingCommand(loggingCommand)
                                    .logOutputStream(logOutputStream)
                                    .errorLogOutputStream(new DefaultErrorLogOutputStream(executionLogCallback))
                                    .secondsToWaitForGracefulShutdown(0)
                                    .build();

    return executeCliCommand(request);
  }

  @Nonnull
  public CliResponse executeCliCommand(CliCommandRequest request)
      throws IOException, InterruptedException, TimeoutException {
    request.getLogCallback().saveExecutionLog(request.getLoggingCommand(), LogLevel.INFO, RUNNING);

    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(request.getTimeoutInMillis(), TimeUnit.MILLISECONDS)
                                          .command("/bin/sh", "-c", request.getCommand())
                                          .readOutput(true)
                                          .environment(CollectionUtils.emptyIfNull(request.getEnvVariables()))
                                          .directory(new File(request.getDirectory()))
                                          .redirectOutput(request.getLogOutputStream())
                                          .redirectError(request.getErrorLogOutputStream());

    if (request.getSecondsToWaitForGracefulShutdown() > 0) {
      // When the thread is interrupted, process executor calls the process destroy method. Process destroy calls
      // sigterm but doesn't wait for the process to be terminated. Since the process is not yet terminated, we don't
      // wait for the process gracefully shutdown and interrupting (killing) the current thread. Once the current thread
      // is killed, we suspect that there is a monitor (probably JVM) that forces process destroy.

      // Since Process destroy doesn't wait for process to terminate after invoking, sigterm will close all existing
      // (stdin, stdout, stderr) streams, and if process during shutdown writes to these streams, process
      // will automatically fail with 141 exit code (which means sigpipe error). Instead of rely on process destroy,
      // we rely on ProcessHandle destroy which invokes sigterm without closing the stream.
      processExecutor.stopper(new GracefulProcessStopper(request.getSecondsToWaitForGracefulShutdown()));
    }

    ProcessResult processResult = processExecutor.execute();
    CommandExecutionStatus status = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
    return CliResponse.builder()
        .command(request.getCommand())
        .commandExecutionStatus(status)
        .output(processResult.outputUTF8())
        .error(request.getErrorLogOutputStream().getError())
        .exitCode(processResult.getExitValue())
        .build();
  }

  @Nonnull
  public ProcessResult executeCommand(String command) throws IOException, InterruptedException, TimeoutException {
    return new ProcessExecutor().commandSplit(command).readOutput(true).execute();
  }
}
