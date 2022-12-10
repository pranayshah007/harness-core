/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.delegation.CommandParameters;
import software.wings.beans.dto.Command;
import software.wings.service.intfc.ServiceCommandExecutorService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDC)
public class CommandTask extends AbstractDelegateRunnableTask {
  @Inject private ServiceCommandExecutorService serviceCommandExecutorService;

  public CommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public CommandExecutionResult run(TaskParameters parameters) {
    CommandParameters commandParameters = (CommandParameters) parameters;
    return run(commandParameters.getCommand(), commandParameters);
  }

  @Override
  public CommandExecutionResult run(Object[] parameters) {
    return run((Command) parameters[0], (CommandExecutionContext) parameters[1]);
  }

  private CommandExecutionResult run(Command command, CommandExecutionContext commandExecutionContext) {
    CommandExecutionStatus commandExecutionStatus;
    String errorMessage = null;

    try {
      commandExecutionStatus = serviceCommandExecutorService.execute(command, commandExecutionContext);
    } catch (Exception e) {
      log.warn("Exception while executing task {}: {}", getTaskId(), ExceptionUtils.getMessage(e), e);
      errorMessage = ExceptionUtils.getMessage(e);
      commandExecutionStatus = CommandExecutionStatus.FAILURE;
    }

    return CommandExecutionResult.builder()
        .status(commandExecutionStatus)
        .errorMessage(errorMessage)
        .commandExecutionData(commandExecutionContext.getCommandExecutionData())
        .build();
  }
}
