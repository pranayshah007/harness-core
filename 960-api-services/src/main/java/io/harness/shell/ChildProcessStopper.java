/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.filesystem.FileIo.deleteFileIfExists;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stop.ProcessStopper;

@Slf4j
public class ChildProcessStopper implements ProcessStopper {
  String fileName;
  File workingDirectory;
  private String killFileName;
  private ProcessExecutor processExecutor;

  public ChildProcessStopper(String fileName, File workingDirectory, ProcessExecutor processExecutor) {
    this.fileName = fileName;
    this.killFileName = "kill-" + fileName;
    this.workingDirectory = workingDirectory;
    this.processExecutor = processExecutor;
  }

  public void stop(Process process) {
    File scriptFile = new File(workingDirectory, killFileName);
    try {
      try (FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
        String command = "list_descendants () {\n"
            + "    echo \"Parent process id: $1\"\n"
            + "    local -a children=()\n"
            + "    read -ra children <<< \"$(ps -ef | grep -v grep | grep $1 | awk '{print $2}' | tr '\\n' ' ')\"\n"
            + "    echo \"Number of children processes: ${#children[@]}\"\n"
            + "    local c\n"
            + "\n"
            + "    for (( c=1; c<${#children[@]} ; c++ ))\n"
            + "    do\n"
            + "        list_descendants ${children[c]}\n"
            + "    done\n"
            + "\n"
            + "    first_child=${children[0]}\n"
            + "    if kill -0 $first_child > /dev/null 2>&1; then\n"
            + "        if kill -9 $first_child; then\n"
            + "            echo \"Termination successful for PID: $first_child\"\n"
            + "        else\n"
            + "            echo \"Failed to terminate PID: $first_child\"\n"
            + "        fi\n"
            + "    else\n"
            + "        echo \"Process with PID $first_child is not running. Skipping termination.\"\n"
            + "    fi\n"
            + "}\n"
            + "\n"
            + "list_descendants $(ps -ef | grep -v grep | grep -m1 " + fileName + " | awk '{print $2}')";
        log.info("Kill child processes command: {}", command);
        outputStream.write(command.getBytes(Charset.forName("UTF-8")));
        processExecutor.command("/bin/bash", killFileName);
        ProcessResult result = processExecutor.execute();
        log.info("Kill child processes command exited with: {}", result.getExitValue());
      } catch (IOException | InterruptedException | TimeoutException e) {
        log.error("Exception in script execution ", e);
      } finally {
        deleteFileIfExists(scriptFile.getAbsolutePath());
      }
    } catch (IOException e) {
      log.error("Exception in script execution ", e);
    }
    process.destroy();
  }
}
