/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import java.io.File;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

@UtilityClass
public class Utils {
  private static final String newLineRegex = "\\r?\\n";

  public static ProcessResult executeScript(String directoryPath, String command, OutputStream output,
      OutputStream error, Map<String, String> environment) throws Exception {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .directory(new File(directoryPath))
                                          .environment(environment)
                                          .commandSplit(command)
                                          .readOutput(true)
                                          .redirectOutput(output)
                                          .redirectError(error);

    return processExecutor.execute();
  }

  public static StartedProcess startScript(String directoryPath, String command, OutputStream output,
      OutputStream error, Map<String, String> environment) throws Exception {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .directory(new File(directoryPath))
                                          .environment(environment)
                                          .commandSplit(command)
                                          .readOutput(true)
                                          .redirectOutput(output)
                                          .redirectError(error);

    return processExecutor.start();
  }

  public static String parseLatestRevisionNumberFromRolloutHistory(String rolloutHistory) {
    // assumes valid input from `kubectl rollout history`
    String[] lines = rolloutHistory.split(newLineRegex);
    return lines[lines.length - 1].split(" ")[0];
  }

  public static String encloseWithQuotesIfNeeded(String path) {
    String result = path.trim();
    if (StringUtils.containsWhitespace(result)) {
      result = "\"" + result + "\"";
    }
    return result;
  }

  public static boolean executeCommand(String command, int timeoutMinutes) {
    try {
      ProcessExecutor processExecutor =
          new ProcessExecutor().timeout(timeoutMinutes, TimeUnit.MINUTES).commandSplit(command).readOutput(true);
      ProcessResult processResult = processExecutor.execute();
      return processResult.getExitValue() == 0;
    } catch (Exception ex) {
      return false;
    }
  }
}
