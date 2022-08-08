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
        String command = "list_descendants ()\n"
            + "{\n"
            + "  local children=$(ps -ef | grep $1 | awk '{print $2}')\n"
            + "  kill -9 ${children[0]}\n"
            + "\n"
            + "  for (( c=1; c<${#children[@]}-1 ; c++ ))\n"
            + "  do\n"
            + "    list_descendants ${children[c]}\n"
            + "  done\n"
            + "}\n"
            + "\n"
            + "list_descendants $(ps -ef | grep -m1 " + fileName + " | awk '{print $2}')";
        log.info("Kill child processes command: {}", command);
        outputStream.write(command.getBytes(Charset.forName("UTF-8")));
        processExecutor.command("/bin/bash", killFileName);
        processExecutor.execute();
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
