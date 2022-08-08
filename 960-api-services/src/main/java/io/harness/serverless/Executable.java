/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public interface Executable {
  String command();

  ServerlessCliResponse execute(String directory, OutputStream output, OutputStream error, boolean printCommand,
      long timeoutInMillis, Map<String, String> envVariables)
      throws IOException, TimeoutException, InterruptedException;
}
