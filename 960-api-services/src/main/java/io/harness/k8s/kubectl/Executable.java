/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import java.io.OutputStream;
import java.util.Map;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

interface Executable {
  ProcessResult execute(String directory, OutputStream output, OutputStream error, boolean printCommand,
      Map<String, String> env) throws Exception;
  StartedProcess executeInBackground(String directory, OutputStream output, OutputStream error) throws Exception;
  String command();
}
