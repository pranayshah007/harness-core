/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.command.CommandExecutionResultMapper;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.beans.bash.ShellScriptParameters;
import software.wings.core.executors.bash.BashExecutorFactory;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BashScriptTaskHandler {
  private final BashExecutorFactory shellExecutorFactory;

  public CommandExecutionResult handle(final ShellScriptParameters parameters) {
    // Define output variables and secret output variables together
    final List<String> items = splitVars(parameters.getOutputVars());
    final List<String> secretItems = splitVars(parameters.getSecretOutputVars());

    final ShellExecutorConfig executorConfig = ShellExecutorConfig.builder()
                                                   .accountId(parameters.getAccountId())
                                                   .appId(parameters.getAppId())
                                                   .executionId(parameters.getActivityId())
                                                   .commandUnitName(parameters.getCommandUnit())
                                                   .workingDirectory(parameters.getWorkingDirectory())
                                                   .environment(parameters.getResolvedEnvironmentVariables())
                                                   .scriptType(parameters.getScriptType())
                                                   .build();

    final ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(executorConfig);

    return CommandExecutionResultMapper.from(
        executor.executeCommandString(parameters.getScript(), items, secretItems, parameters.getSshTimeOut()));
  }

  private List<String> splitVars(final String vars) {
    if (vars == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(vars.trim().split("\\s*,\\s*")).map(String::trim).collect(Collectors.toList());
  }
}
