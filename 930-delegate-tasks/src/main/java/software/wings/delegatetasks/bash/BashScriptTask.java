/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.bash.ShellScriptTaskParametersNG;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BashScriptTask extends AbstractDelegateRunnableTask {
  @Inject private BashScriptTaskHandler bashScriptTaskHandler;

  public BashScriptTask(final DelegateTaskPackage delegateTaskPackage, final BooleanSupplier preExecute,
      final Consumer<DelegateTaskResponse> postExecute) {
    super(delegateTaskPackage, null, postExecute, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Use the other API");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException {
    final ShellScriptTaskParametersNG scriptParameters = (ShellScriptTaskParametersNG) parameters;

    log.info("Shell script task parameters: accountId - {}, workingDir - {}", scriptParameters.getAccountId(),
        scriptParameters.getWorkingDirectory());

    final var executeCommandResponse = bashScriptTaskHandler.handle(scriptParameters);

    return ShellScriptTaskResponseNG.builder()
        .executeCommandResponse(executeCommandResponse)
        .status(executeCommandResponse.getStatus())
        .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
        .build();
  }

  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Shell Script execution queued.";
      case FAILURE:
        return "Shell Script execution failed. Please check execution logs.";
      case RUNNING:
        return "Shell Script execution running.";
      case SKIPPED:
        return "Shell Script execution skipped.";
      case SUCCESS:
      default:
        return "";
    }
  }
}
