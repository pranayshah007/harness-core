/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model.kubeconfig;

import static io.harness.k8s.K8sConstants.EKS_AUTH_PLUGIN_BINARY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class KubeConfigAuthPluginHelper {
  private static final int TIMEOUT_IN_MINUTES = 1;

  public static boolean isExecAuthPluginBinaryAvailable(String binaryName, LogCallback logCallback) {
    String commandToRun = getCommandToRun(binaryName);
    boolean shouldUseExecFormat = runCommand(binaryName + commandToRun, logCallback, new HashMap<>());

    if (shouldUseExecFormat) {
      saveLogs(
          String.format(
              "%s binary found. Using kubernetes client-go credential plugin mechanism to extend kubectl's authentication",
              binaryName),
          logCallback, LogLevel.INFO);
    }
    return shouldUseExecFormat;
  }

  public static boolean runCommand(final String command, LogCallback logCallback, Map<String, String> environment) {
    try {
      return executeShellCommand(command, logCallback, environment);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      if (logCallback != null) {
        saveLogs(String.format(
                     "Unable to execute command: %s %n %s", command, ExceptionMessageSanitizer.sanitizeException(e)),
            logCallback, LogLevel.WARN);
      }
      return false;
    }
  }

  private static boolean executeShellCommand(String command, LogCallback logCallback, Map<String, String> environment)
      throws IOException, InterruptedException, TimeoutException {
    final ProcessExecutor processExecutor = new ProcessExecutor()
                                                .timeout(TIMEOUT_IN_MINUTES, TimeUnit.MINUTES)
                                                .directory(null)
                                                .command("/bin/bash", "-c", command)
                                                .environment(environment)
                                                .readOutput(true);

    final ProcessResult result = processExecutor.execute();
    if (result.getExitValue() != 0) {
      saveLogs(String.format("Unable to execute command: %s %n %s", command, result.outputUTF8()), logCallback,
          LogLevel.WARN);
      return false;
    }
    return true;
  }

  public static void saveLogs(String errorMsg, LogCallback logCallback, LogLevel logLevel) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(errorMsg, logLevel);
    } else {
      if (logLevel == LogLevel.INFO) {
        log.info(errorMsg);
      } else {
        log.warn(errorMsg);
      }
    }
  }

  private static String getCommandToRun(String binaryName) {
    if (binaryName.equals(EKS_AUTH_PLUGIN_BINARY)) {
      return " version";
    }
    return " --version";
  }
}
