/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model.kubeconfig;

import static io.harness.k8s.K8sConstants.AUTH_PLUGIN_VERSION_COMMAND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class KubeConfigAuthPluginHelper {
  private static final int TIMEOUT_IN_MINUTES = 1;

  public static boolean isAuthPluginBinaryAvailable(String binaryName) {
    String authPluginVersionCommand = AUTH_PLUGIN_VERSION_COMMAND.replace("${AUTH_PLUGIN_BINARY}", binaryName);
    if (!runCommand(authPluginVersionCommand)) {
      log.warn(String.format("Command failed: %s \n", authPluginVersionCommand));
      if (!runCommand(binaryName + " --version")) {
        log.warn(String.format("binary not found in the environment: %s \n", binaryName));
        return false;
      }
    }
    return true;
  }

  private static boolean runCommand(final String command) {
    try {
      return executeShellCommand(command);
    } catch (final Exception e) {
      log.error("Failed running command: {}", command, ExceptionMessageSanitizer.sanitizeException(e));
      return false;
    }
  }

  private static boolean executeShellCommand(String command)
      throws IOException, InterruptedException, TimeoutException {
    final ProcessExecutor processExecutor = new ProcessExecutor()
                                                .timeout(TIMEOUT_IN_MINUTES, TimeUnit.MINUTES)
                                                .directory(null)
                                                .command("/bin/bash", "-c", command)
                                                .readOutput(true)
                                                .redirectOutput(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String line) {
                                                    log.info(line);
                                                  }
                                                })
                                                .redirectError(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String line) {
                                                    log.error(line);
                                                  }
                                                });

    final ProcessResult result = processExecutor.execute();

    if (result.getExitValue() == 0) {
      log.info(result.outputUTF8());
      return true;
    } else {
      log.error(result.outputUTF8());
      return false;
    }
  }
}
