/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import java.util.List;

public interface BaseScriptExecutor {
  CommandExecutionStatus executeCommandString(String command);

  CommandExecutionStatus executeCommandString(String command, boolean displayCommand);

  CommandExecutionStatus executeCommandString(String command, StringBuffer output);

  CommandExecutionStatus executeCommandString(String command, StringBuffer output, boolean displayCommand);

  ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect);

  ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis);

  LogCallback getLogCallback();
}
