/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import io.harness.data.structure.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@UtilityClass
public class ServerlessUtils {
  public static ProcessResult executeScript(String directoryPath, String command, OutputStream output,
      OutputStream error, long timeoutInMillis, Map<String, String> envVariables)
      throws InterruptedException, TimeoutException, IOException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .directory(new File(directoryPath))
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .commandSplit(command)
                                          .readOutput(true)
                                          .environment(CollectionUtils.emptyIfNull(envVariables))
                                          .redirectOutput(output)
                                          .redirectError(error);
    return processExecutor.execute();
  }

  public static String encloseWithQuotesIfNeeded(String path) {
    String result = path.trim();
    if (StringUtils.containsWhitespace(result)) {
      result = "\"" + result + "\"";
    }
    return result;
  }
}
