/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.windows.CmdUtils.WIN_RM_MARKER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.shell.CommandExecutionData;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.SshSessionManager;
import io.harness.shell.ssh.SshClientManager;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class CommandTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<Pair, CommandHandler> commandUnitHandlers;

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
      return runWinRm((WinrmTaskParameters) parameters);
    } else {
      throw new IllegalArgumentException(String.format("Invalid parameters type provide %s", parameters.getClass()));
    }
  }

  private DelegateResponseData runSsh(SshCommandTaskParameters parameters) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      return getTaskResponse(parameters, commandUnitsProgress, ScriptType.BASH);
    } catch (Exception e) {
      throw SshWinRmExceptionHandler.handle(e, log, commandUnitsProgress, true);
    } finally {
      if (!parameters.executeOnDelegate && isNotEmpty(parameters.getHost())) {
        SshSessionManager.evictAndDisconnectCachedSession(parameters.getExecutionId(), parameters.getHost());
        SshClientManager.evictCacheAndDisconnect(parameters.getExecutionId(), parameters.getHost());
      }
    }
  }

  private DelegateResponseData runWinRm(WinrmTaskParameters parameters) {
    getLogStreamingTaskClient().getMarkers().add(WIN_RM_MARKER);
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      return getTaskResponse(parameters, commandUnitsProgress, ScriptType.POWERSHELL);
    } catch (Exception e) {
      throw SshWinRmExceptionHandler.handle(e, log, commandUnitsProgress, false);
    }
  }

  private CommandTaskResponse getTaskResponse(
      CommandTaskParameters parameters, CommandUnitsProgress commandUnitsProgress, ScriptType scriptType) {
    CommandExecutionStatus status = CommandExecutionStatus.FAILURE;
    CommandExecutionData commandExecutionData = null;
    Map<String, String> outputVariables = new HashMap<>();
    Map<String, Object> taskContext = new LinkedHashMap<>();
    logCommandUnits(parameters.getCommandUnits());
    for (NgCommandUnit commandUnit : parameters.getCommandUnits()) {
      CommandHandler handler = commandUnitHandlers.get(Pair.of(commandUnit.getCommandUnitType(), scriptType.name()));
      ExecuteCommandResponse executeCommandResponse =
          handler.handle(parameters, commandUnit, this.getLogStreamingTaskClient(), commandUnitsProgress, taskContext);

      if (executeCommandResponse.getCommandExecutionData() instanceof ShellExecutionData) {
        ShellExecutionData shellExecutionData = (ShellExecutionData) executeCommandResponse.getCommandExecutionData();
        mergeOutputVariablesBetweenCommandUnits(outputVariables, shellExecutionData.getSweepingOutputEnvVariables());
        shellExecutionData.setSweepingOutputEnvVariables(outputVariables);
        commandExecutionData = shellExecutionData;
      }

      status = executeCommandResponse.getStatus();
      if (CommandExecutionStatus.FAILURE.equals(status)) {
        break;
      }
    }

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder().status(status).commandExecutionData(commandExecutionData).build();

    return CommandTaskResponse.builder()
        .status(status)
        .executeCommandResponse(executeCommandResponse)
        .errorMessage(getErrorMessage(status))
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .build();
  }

  private void logCommandUnits(List<NgCommandUnit> units) {
    String commandUnits = "Received command units to execute on delegate: ["
        + Joiner.on(", ").skipNulls().join(units.stream().map(NgCommandUnit::getName).collect(Collectors.toList()))
        + "]";
    log.info(commandUnits);
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

  private void mergeOutputVariablesBetweenCommandUnits(Map<String, String> mergedMap, Map<String, String> nextMap) {
    if (EmptyPredicate.isEmpty(nextMap)) {
      return;
    }

    nextMap.entrySet().stream().filter(entry -> EmptyPredicate.isNotEmpty(entry.getValue())).forEach(entry -> {
      mergedMap.put(entry.getKey(), entry.getValue());
    });
  }
}
