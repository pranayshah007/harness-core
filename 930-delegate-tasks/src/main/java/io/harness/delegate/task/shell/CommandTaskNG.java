/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.shell.SshSessionManager;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(CDP)
public class CommandTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<String, CommandHandler> commandUnitHandlers;

  public CommandTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (parameters instanceof SshCommandTaskParameters) {
      return runSsh((SshCommandTaskParameters) parameters);
    } else if (parameters instanceof WinrmTaskParameters) {
      // TODO winrm logic
      return CommandTaskResponse.builder().status(CommandExecutionStatus.SUCCESS).build();
    } else {
      throw new IllegalArgumentException(String.format("Invalid parameters type provide %s", parameters.getClass()));
    }
  }

  private DelegateResponseData runSsh(SshCommandTaskParameters parameters) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      CommandExecutionStatus status = CommandExecutionStatus.FAILURE;
      for (NgCommandUnit commandUnit : parameters.getCommandUnits()) {
        CommandHandler handler = commandUnitHandlers.get(commandUnit.getCommandUnitType());
        status = handler.handle(parameters, commandUnit, this.getLogStreamingTaskClient(), commandUnitsProgress);
        if (CommandExecutionStatus.FAILURE.equals(status)) {
          break;
        }
      }

      return CommandTaskResponse.builder()
          .status(status)
          .errorMessage(getErrorMessage(status))
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing command task", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      if (!parameters.executeOnDelegate && isNotEmpty(parameters.getHost())) {
        SshSessionManager.evictAndDisconnectCachedSession(parameters.getExecutionId(), parameters.getHost());
      }
    }
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

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
