/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import software.wings.beans.delegation.ShellScriptParameters;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellScriptTask extends AbstractDelegateRunnableTask {
  @Inject private ShellScriptTaskHandler shellScriptTaskHandler;

  public ShellScriptTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public CommandExecutionResult run(TaskParameters parameters) {
    ShellScriptParameters scriptParameters = (ShellScriptParameters) parameters;
    log.debug("Shell script task parameters: accountId - {}, appId - {}, workingDir - {}, activityId - {}",
        scriptParameters.getAccountId(), scriptParameters.getAppId(), scriptParameters.getWorkingDirectory(),
        scriptParameters.getActivityId());
    return shellScriptTaskHandler.handle((ShellScriptParameters) parameters);
  }

  @Override
  public CommandExecutionResult run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
