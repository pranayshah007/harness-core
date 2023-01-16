/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.beans.bash.ShellScriptParameters;
import software.wings.core.executors.bash.BashExecutorFactory;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BashScriptTaskHandler {
  private final BashExecutorFactory shellExecutorFactory;

  public ExecuteCommandResponse handle(final ShellScriptParameters parameters) {
    final ShellExecutorConfig executorConfig = ShellExecutorConfig.builder()
                                                   .accountId(parameters.getAccountId())
                                                   .appId(parameters.getAppId())
                                                   .executionId(parameters.getActivityId())
                                                   .commandUnitName(parameters.getCommandUnit())
                                                   .workingDirectory(parameters.getWorkingDirectory())
                                                   .environment(parameters.getEnvironmentVariables())
                                                   .scriptType(parameters.getScriptType())
                                                   .build();

    final ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(executorConfig);

    return executor.executeCommandString(
        parameters.getScript(), parameters.getOutputVars(), parameters.getSecretOutputVars(), null);
  }
}
