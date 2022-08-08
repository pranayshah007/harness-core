/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessResult;

public abstract class AbstractExecutable implements Executable {
  @Override
  public ServerlessCliResponse execute(String directory, OutputStream output, OutputStream error, boolean printCommand,
      long timeoutInMillis, Map<String, String> envVariables)
      throws IOException, TimeoutException, InterruptedException {
    String command = this.command();
    if (printCommand) {
      writeCommandToOutput(command, output);
    }
    ProcessResult processResult =
        ServerlessUtils.executeScript(directory, command, output, error, timeoutInMillis, envVariables);
    return ServerlessCliResponse.builder()
        .commandExecutionStatus(processResult.getExitValue() == 0 ? SUCCESS : FAILURE)
        .output(processResult.outputUTF8())
        .build();
  }

  public static Optional<String> getPrintableCommand(String command) {
    int index = command.indexOf("serverless ");
    if (index != -1) {
      return Optional.of(command.substring(index));
    }
    return Optional.empty();
  }

  private void writeCommandToOutput(String command, OutputStream output) throws IOException {
    Optional<String> printCommandOption = getPrintableCommand(command);
    if (printCommandOption.isPresent()) {
      String printCommand = "\n" + printCommandOption.get() + "\n\n";
      output.write(printCommand.getBytes(StandardCharsets.UTF_8));
    }
  }
}
