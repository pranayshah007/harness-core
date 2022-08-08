/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.scheduler.approval.ApprovalPollingHandler.PUMP_INTERVAL;
import static software.wings.scheduler.approval.ApprovalPollingHandler.TARGET_INTERVAL;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.shell.ShellScriptApprovalTaskParameters;
import io.harness.shell.ScriptType;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.ShellScriptApprovalExecutionData;
import software.wings.beans.ApprovalDetails.Action;
import software.wings.beans.TaskType;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.service.intfc.ApprovalPolingService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles shell script approvals.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ShellScriptApprovalService {
  private static final long TIME_OUT_IN_MINUTES = 5;
  private static final String SCRIPT_APPROVAL_DIRECTORY = "/tmp";
  private static final String SCRIPT_APPROVAL_COMMAND = "Execute Approval Script";
  private static final String SCRIPT_APPROVAL_ENV_VARIABLE = "HARNESS_APPROVAL_STATUS";

  private final DelegateService delegateService;
  private final WaitNotifyEngine waitNotifyEngine;
  private final ApprovalPolingService approvalPolingService;
  @Inject private StateExecutionService stateExecutionService;

  @Inject
  public ShellScriptApprovalService(
      DelegateService delegateService, WaitNotifyEngine waitNotifyEngine, ApprovalPolingService approvalPolingService) {
    this.delegateService = delegateService;
    this.waitNotifyEngine = waitNotifyEngine;
    this.approvalPolingService = approvalPolingService;
  }

  public void handleShellScriptPolling(ApprovalPollingJobEntity scriptApprovalPollingEntity) {
    String accountId = scriptApprovalPollingEntity.getAccountId();
    String appId = scriptApprovalPollingEntity.getAppId();
    String approvalId = scriptApprovalPollingEntity.getApprovalId();
    String activityId = scriptApprovalPollingEntity.getActivityId();
    String scriptString = scriptApprovalPollingEntity.getScriptString();
    List<String> delegateSelectors = scriptApprovalPollingEntity.getDelegateSelectors();
    String stateExecutionInstanceId = scriptApprovalPollingEntity.getStateExecutionInstanceId();

    long retryInterval = scriptApprovalPollingEntity.getRetryInterval();
    long delayUntilNext = retryInterval - PUMP_INTERVAL.toMillis();
    int expressionFunctorToken = scriptApprovalPollingEntity.getExpressionFunctorToken();

    boolean shouldRetry = !tryShellScriptApproval(accountId, appId, approvalId, activityId, scriptString,
        delegateSelectors, stateExecutionInstanceId, expressionFunctorToken);
    if (shouldRetry && retryInterval != TARGET_INTERVAL.toMillis()) {
      long nextIteration = System.currentTimeMillis() + delayUntilNext;
      approvalPolingService.updateNextIteration(scriptApprovalPollingEntity.getUuid(), nextIteration);
    }
  }

  private void appendDelegateTaskDetails(String stateExecutionInstanceId, DelegateTask delegateTask) {
    if (isBlank(delegateTask.getUuid())) {
      delegateTask.setUuid(generateUuid());
    }

    stateExecutionService.appendDelegateTaskDetails(stateExecutionInstanceId,
        DelegateTaskDetails.builder()
            .delegateTaskId(delegateTask.getUuid())
            .taskDescription(delegateTask.calcDescription())
            .setupAbstractions(delegateTask.getSetupAbstractions())
            .build());
  }

  boolean tryShellScriptApproval(String accountId, String appId, String approvalId, String activityId,
      String scriptString, List<String> delegateSelectors, String stateExecutionInstanceId,
      int expressionFunctorToken) {
    ShellScriptApprovalTaskParameters shellScriptApprovalTaskParameters =
        ShellScriptApprovalTaskParameters.builder()
            .accountId(accountId)
            .appId(appId)
            .activityId(activityId)
            .commandName(SCRIPT_APPROVAL_COMMAND)
            .outputVars(SCRIPT_APPROVAL_ENV_VARIABLE)
            .workingDirectory(SCRIPT_APPROVAL_DIRECTORY)
            .scriptType(ScriptType.BASH)
            .script(scriptString)
            .delegateSelectors(delegateSelectors)
            .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                                    .waitId(activityId)
                                    .description("Shell Script Approval")
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.SHELL_SCRIPT_APPROVAL.name())
                                              .parameters(new Object[] {shellScriptApprovalTaskParameters})
                                              .expressionFunctorToken(expressionFunctorToken)
                                              .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                                              .build())
                                    .selectionLogsTrackingEnabled(true)
                                    .build();

    appendDelegateTaskDetails(stateExecutionInstanceId, delegateTask);

    DelegateResponseData responseData = null;
    try {
      responseData = delegateService.executeTask(delegateTask);
    } catch (Exception e) {
      log.error("Failed to fetch Approval Status from Script", e);
      return true;
    }

    if (responseData instanceof ShellScriptApprovalExecutionData) {
      ShellScriptApprovalExecutionData executionData = (ShellScriptApprovalExecutionData) responseData;
      if (executionData.getApprovalAction() == Action.APPROVE || executionData.getApprovalAction() == Action.REJECT) {
        try {
          approveWorkflow(approvalId, appId, executionData.getExecutionStatus());
        } catch (Exception e) {
          log.error("Failed to Approve/Reject Status", e);
        }
        return true;
      }
    } else if (responseData instanceof ErrorNotifyResponseData) {
      log.error("Shell Script Approval task failed unexpectedly {}", responseData);
      return true;
    }
    return false;
  }

  private void approveWorkflow(String approvalId, String appId, ExecutionStatus approvalStatus) {
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .appId(appId)
                                                   .approvalId(approvalId)
                                                   .approvedOn(System.currentTimeMillis())
                                                   .build();

    if (approvalStatus == ExecutionStatus.SUCCESS || approvalStatus == ExecutionStatus.REJECTED) {
      executionData.setApprovedOn(System.currentTimeMillis());
    }

    executionData.setStatus(approvalStatus);
    waitNotifyEngine.doneWith(approvalId, executionData);
  }
}
