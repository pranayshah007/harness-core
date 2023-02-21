package io.harness.aws.sam;

import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

public class AwsSamCommandHelper {
  public static LogOutputStream getExecutionLogOutputStream(LogCallback executionLogCallback, LogLevel logLevel) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(line, logLevel);
      }
    };
  }

  public static AwsSamCliResponse executeCommand(AwsSamAbstractExecutable command, String workingDirectory,
                                                     LogCallback executionLogCallback, boolean printCommand, long timeoutInMillis, Map<String, String> envVariables)
          throws InterruptedException, TimeoutException, IOException {
    try (LogOutputStream logOutputStream = getExecutionLogOutputStream(executionLogCallback, INFO);
         LogOutputStream logErrorStream = getExecutionLogOutputStream(executionLogCallback, ERROR)) {
      return command.execute(
              workingDirectory, logOutputStream, logErrorStream, printCommand, timeoutInMillis, envVariables);
    }
  }
}
