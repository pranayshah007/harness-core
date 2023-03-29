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
  CommandExecutionStatus executeCommandString(String command, boolean useSshAgent);

  CommandExecutionStatus executeCommandString(String command, boolean displayCommand, boolean useSshAgent);

  CommandExecutionStatus executeCommandString(
      String command, boolean displayCommand, boolean winrmScriptCommandSplit, boolean useSshAgent);

  CommandExecutionStatus executeCommandString(String command, StringBuffer output, boolean useSshAgent);

  CommandExecutionStatus executeCommandString(
      String command, StringBuffer output, boolean displayCommand, boolean useSshAgent);

  CommandExecutionStatus executeCommandString(String command, boolean winrmScriptCommandSplit, StringBuffer output,
      boolean displayCommand, boolean useSshAgent);

  ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect, boolean useSshAgent);

  ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis, boolean useSshAgent);

  LogCallback getLogCallback();
}
