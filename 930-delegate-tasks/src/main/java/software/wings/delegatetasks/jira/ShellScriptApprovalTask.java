/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.Log.Builder.aLog;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.ShellScriptApprovalTaskParameters;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.ShellExecutorConfig;

import software.wings.api.ShellScriptApprovalExecutionData;
import software.wings.beans.ApprovalDetails.Action;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellScriptApprovalTask extends AbstractDelegateRunnableTask {
  private static final String SCRIPT_APPROVAL_ENV_VARIABLE = "HARNESS_APPROVAL_STATUS";
  private static final String APPROVE_STATUS = "Approved";
  private static final String REJECTED_STATUS = "Rejected";

  @Inject private ShellExecutorFactory shellExecutorFactory;
  @Inject private DelegateLogService logService;

  public ShellScriptApprovalTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return run((ShellScriptApprovalTaskParameters) parameters[0]);
  }

  @Override
  public DelegateResponseData run(TaskParameters taskParameters) {
    ShellScriptApprovalTaskParameters parameters = (ShellScriptApprovalTaskParameters) taskParameters;

    ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                  .accountId(parameters.getAccountId())
                                                  .appId(parameters.getAppId())
                                                  .executionId(parameters.getActivityId())
                                                  .commandUnitName(parameters.getCommandName())
                                                  .workingDirectory(parameters.getWorkingDirectory())
                                                  .environment(new HashMap<>())
                                                  .scriptType(parameters.getScriptType())
                                                  .build();

    ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(shellExecutorConfig);
    List<String> items = new ArrayList<>();
    if (parameters.getOutputVars() != null && StringUtils.isNotEmpty(parameters.getOutputVars().trim())) {
      items = Arrays.asList(parameters.getOutputVars().split("\\s*,\\s*"));
      items.replaceAll(String::trim);
    }

    saveExecutionLog(parameters, "Starting Script Execution ...", RUNNING);
    ExecuteCommandResponse executeCommandResponse =
        executor.executeCommandString(parameters.getScript(), items, Collections.emptyList(), null);
    saveExecutionLog(parameters, "End of Script Execution ...", RUNNING);
    saveExecutionLog(parameters, "\n---------------------------------------------------\n", RUNNING);

    Action action = null;
    ExecutionStatus executionStatus = ExecutionStatus.RUNNING;
    if (SUCCESS == executeCommandResponse.getStatus()) {
      Map<String, String> sweepingOutputEnvVariables =
          ((ShellExecutionData) executeCommandResponse.getCommandExecutionData()).getSweepingOutputEnvVariables();

      if (MapUtils.isNotEmpty(sweepingOutputEnvVariables)
          && EmptyPredicate.isNotEmpty(sweepingOutputEnvVariables.get(SCRIPT_APPROVAL_ENV_VARIABLE))) {
        if (sweepingOutputEnvVariables.get(SCRIPT_APPROVAL_ENV_VARIABLE).equalsIgnoreCase(APPROVE_STATUS)) {
          action = Action.APPROVE;
          executionStatus = ExecutionStatus.SUCCESS;
        } else if (sweepingOutputEnvVariables.get(SCRIPT_APPROVAL_ENV_VARIABLE).equalsIgnoreCase(REJECTED_STATUS)) {
          action = Action.REJECT;
          executionStatus = ExecutionStatus.REJECTED;
        }
      }
    }

    String errorMessage;
    switch (executionStatus) {
      case SUCCESS:
        errorMessage = "Approved by Script";
        break;
      case REJECTED:
        errorMessage = "Rejected by Script";
        break;
      case RUNNING:
      default:
        errorMessage = "Waiting for Approval";
    }
    return ShellScriptApprovalExecutionData.builder()
        .approvalAction(action)
        .executionStatus(executionStatus)
        .errorMessage(errorMessage)
        .build();
  }

  private void saveExecutionLog(
      ShellScriptApprovalTaskParameters parameters, String line, CommandExecutionStatus commandExecutionStatus) {
    logService.save(parameters.getAccountId(),
        aLog()
            .appId(parameters.getAppId())
            .activityId(parameters.getActivityId())
            .logLevel(INFO)
            .commandUnitName(parameters.getCommandName())
            .logLine(line)
            .executionResult(commandExecutionStatus)
            .build());
  }
}
