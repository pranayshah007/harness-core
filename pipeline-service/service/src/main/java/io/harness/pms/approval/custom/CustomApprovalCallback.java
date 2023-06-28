/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER_SRE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessCustomApprovalException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.approval.AbstractApprovalCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.misc.TicketNG;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.custom.CustomApprovalInstanceHandler;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.custom.evaluation.CustomApprovalCriteriaEvaluator;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptOutcome;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.PushThroughNotifyCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Data
@Slf4j
public class CustomApprovalCallback extends AbstractApprovalCallback implements PushThroughNotifyCallback {
  private final String approvalInstanceId;

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private ShellScriptHelperService shellScriptHelperService;
  @Inject private CustomApprovalInstanceHandler customApprovalInstanceHandler;

  @Builder
  public CustomApprovalCallback(String approvalInstanceId) {
    this.approvalInstanceId = approvalInstanceId;
  }

  @Override
  public void push(Map<String, ResponseData> response) {
    CustomApprovalInstance instance = (CustomApprovalInstance) approvalInstanceService.get(approvalInstanceId);
    try (AutoLogContext ignore = instance.autoLogContext()) {
      pushInternal(response, instance);
    } finally {
      resetNextIteration(instance);
    }
  }

  private void pushInternal(Map<String, ResponseData> response, CustomApprovalInstance instance) {
    log.info("Received response from custom approval script execution with instanceId - {}", approvalInstanceId);
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, "Execute", false);

    if (ApprovalStatus.ABORTED.equals(instance.getStatus())) {
      log.warn("Custom Approval Instance queued was aborted. Ignoring the callback response.");
      return;
    }

    if (instance.hasExpired()) {
      log.warn("Custom Approval Instance queued has expired");
      updateApprovalInstanceAndLog(logCallback, "Approval instance has expired", LogColor.Red,
          CommandExecutionStatus.FAILURE, ApprovalStatus.EXPIRED, instance.getId());
      return;
    }

    ShellScriptTaskResponseNG scriptTaskResponse;
    try {
      ResponseData responseData = response.values().iterator().next();
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      responseData = (ResponseData) (binaryResponseData.isUsingKryoWithoutReference()
              ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
              : kryoSerializer.asInflatedObject(binaryResponseData.getData()));
      if (responseData instanceof ErrorNotifyResponseData) {
        log.warn("Failed to run Custom Approval script");
        logCallback.saveExecutionLog("Failed to run custom approval script: " + responseData, LogLevel.WARN);
        return;
      }

      scriptTaskResponse = (ShellScriptTaskResponseNG) responseData;
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Error fetching custom approval response: %s. Retrying in sometime...",
                              ExceptionUtils.getMessage(ex)),
              LogColor.Red),
          LogLevel.ERROR);
      log.error("Failed to run custom approval script", ex);
      return;
    }

    try {
      ShellExecutionData shellExecutionData =
          (ShellExecutionData) scriptTaskResponse.getExecuteCommandResponse().getCommandExecutionData();
      ShellScriptOutcome shellScriptOutcome =
          ShellScriptHelperService.prepareShellScriptOutcome(shellExecutionData.getSweepingOutputEnvVariables(),
              instance.getOutputVariables(), instance.getSecretOutputVariables());
      CustomApprovalTicketNG ticketNG =
          CustomApprovalTicketNG.builder()
              .fields(shellScriptOutcome != null ? shellScriptOutcome.getOutputVariables() : new HashMap<>())
              .build();
      checkApprovalAndRejectionCriteriaAndWithinChangeWindow(
          ticketNG, instance, logCallback, instance.getApprovalCriteria(), instance.getRejectionCriteria());
    } catch (Exception ex) {
      log.error("An error occurred with custom approval", ex);
      if (ex instanceof ApprovalStepNGException && ((ApprovalStepNGException) ex).isFatal()) {
        handleFatalException(instance, logCallback, (ApprovalStepNGException) ex);
        return;
      }

      logCallback.saveExecutionLog(LogHelper.color(String.format("Error evaluating approval/rejection criteria: %s.",
                                                       ExceptionUtils.getMessage(ex)),
                                       LogColor.Red),
          LogLevel.ERROR);
      throw new HarnessCustomApprovalException("Error while evaluating approval/rejection criteria", ex, USER_SRE);
    }
  }

  private void resetNextIteration(CustomApprovalInstance instance) {
    approvalInstanceService.resetNextIterations(instance.getId(), instance.recalculateNextIterations());
    customApprovalInstanceHandler.wakeup();
  }

  @Override
  protected boolean evaluateCriteria(TicketNG ticket, CriteriaSpecDTO criteriaSpec) {
    return CustomApprovalCriteriaEvaluator.evaluateCriteria((CustomApprovalTicketNG) ticket, criteriaSpec);
  }
}
