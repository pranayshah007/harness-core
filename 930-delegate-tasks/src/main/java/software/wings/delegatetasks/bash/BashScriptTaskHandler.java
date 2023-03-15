/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import static io.harness.shell.ScriptType.BASH;

import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.beans.bash.ShellScriptTaskParametersNG;
import software.wings.core.executors.bash.BashExecutorFactory;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BashScriptTaskHandler {
  public static final String COMMAND_UNIT = "Execute";
  private final BashExecutorFactory shellExecutorFactory;

  public ExecuteCommandResponse handle(final ShellScriptTaskParametersNG parameters) {
    final ShellExecutorConfig executorConfig = ShellExecutorConfig.builder()
                                                   .accountId(parameters.getAccountId())
                                                   .executionId(parameters.getExecutionId())
                                                   .commandUnitName(COMMAND_UNIT)
                                                   .workingDirectory(parameters.getWorkingDirectory())
                                                   .environment(parameters.getEnvironmentVariables())
                                                   .scriptType(BASH)
                                                   .build();

    final ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(executorConfig);

    return executor.executeCommandString(
        parameters.getScript(), parameters.getOutputVars(), parameters.getSecretOutputVars(), null);
  }
}
