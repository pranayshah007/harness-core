/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static java.util.Objects.isNull;

import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.servicenow.misc.TicketNG;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractApprovalCallback {
  @Inject protected ApprovalInstanceService approvalInstanceService;
  @Inject protected NGErrorHelper ngErrorHelper;

  protected void updateApprovalInstanceAndLog(NGLogCallback logCallback, String logMessage, LogColor logColor,
      CommandExecutionStatus executionStatus, ApprovalStatus approvalStatus, String approvalInstanceId,
      TicketNG ticketNG) {
    log.info(logMessage);
    logCallback.saveExecutionLog(LogHelper.color(logMessage, logColor), LogLevel.INFO, executionStatus);
    approvalInstanceService.finalizeStatus(approvalInstanceId, approvalStatus, ticketNG);
  }

  protected void updateApprovalInstanceAndLog(NGLogCallback logCallback, String logMessage, LogColor red,
      CommandExecutionStatus executionStatus, ApprovalStatus approvalStatus, String approvalInstanceId) {
    updateApprovalInstanceAndLog(
        logCallback, logMessage, red, executionStatus, approvalStatus, approvalInstanceId, null);
  }

  protected void handleErrorNotifyResponse(
      NGLogCallback logCallback, ErrorNotifyResponseData responseData, String errorMessagePrefix) {
    String errorMessage = String.format(errorMessagePrefix + " %s", responseData.getErrorMessage());
    logCallback.saveExecutionLog(LogHelper.color(errorMessage, LogColor.Red), LogLevel.ERROR);
    log.error(errorMessage, responseData.getException());
  }

  protected void checkApprovalAndRejectionCriteriaAndWithinChangeWindow(TicketNG ticket, ApprovalInstance instance,
      NGLogCallback logCallback, CriteriaSpecWrapperDTO approvalCriteria, CriteriaSpecWrapperDTO rejectionCriteria) {
    if (isNull(approvalCriteria) || isNull(approvalCriteria.getCriteriaSpecDTO())) {
      log.warn("Approval criteria can't be empty for instance id - {}", instance.getId());
      throw new InvalidRequestException("Approval criteria can't be empty");
    }

    log.info("Evaluating approval criteria for instance id - {}", instance.getId());
    logCallback.saveExecutionLog("Evaluating approval criteria...");
    CriteriaSpecDTO approvalCriteriaSpec = approvalCriteria.getCriteriaSpecDTO();
    boolean approvalEvaluationResult = evaluateCriteria(ticket, approvalCriteriaSpec);
    if (approvalEvaluationResult) {
      if (evaluateWithinChangeWindow(ticket, instance, logCallback)) {
        log.info("Approval criteria has been met for instance id - {}", instance.getId());
        updateApprovalInstanceAndLog(logCallback, "Approval criteria has been met", LogColor.Cyan,
            CommandExecutionStatus.RUNNING, ApprovalStatus.APPROVED, instance.getId(), ticket);
        return;
      }
      log.info("Approval criteria met and waiting for change window for instance id - {}", instance.getId());
      logCallback.saveExecutionLog("Approval criteria has been met, waiting for change window");
      return;
    }

    log.info("Approval criteria has not been met for instance id - {}", instance.getId());
    logCallback.saveExecutionLog("Approval criteria has not been met");

    if (isNull(rejectionCriteria) || isNull(rejectionCriteria.getCriteriaSpecDTO())
        || rejectionCriteria.getCriteriaSpecDTO().isEmpty()) {
      log.info("Approval criteria has not been met for instance id - {}", instance.getId());
      logCallback.saveExecutionLog("Rejection criteria is not present");
      return;
    }

    log.info("Evaluating rejection criteria for instance id - {}", instance.getId());
    logCallback.saveExecutionLog("Evaluating rejection criteria...");
    CriteriaSpecDTO rejectionCriteriaSpec = rejectionCriteria.getCriteriaSpecDTO();
    boolean rejectionEvaluationResult = evaluateCriteria(ticket, rejectionCriteriaSpec);
    if (rejectionEvaluationResult) {
      log.info("Rejection criteria has been met for instance id - {}", instance.getId());
      updateApprovalInstanceAndLog(logCallback, "Rejection criteria has been met", LogColor.Red,
          CommandExecutionStatus.RUNNING, ApprovalStatus.REJECTED, instance.getId(), ticket);
      return;
    }
    log.info("Rejection criteria has also not been met for instance id - {}", instance.getId());
    logCallback.saveExecutionLog("Rejection criteria has also not been met");
  }

  protected void handleFatalException(
      ApprovalInstance instance, NGLogCallback logCallback, ApprovalStepNGException ex) {
    log.error("Error while evaluating approval/rejection criteria", ex);
    String errorMessage = String.format(
        "Fatal error evaluating approval/rejection criteria: %s", ngErrorHelper.getErrorSummary(ex.getMessage()));
    logCallback.saveExecutionLog(LogHelper.color(errorMessage, LogColor.Red), LogLevel.ERROR);
    approvalInstanceService.finalizeStatus(instance.getId(), ApprovalStatus.FAILED, errorMessage);
  }

  protected abstract boolean evaluateCriteria(TicketNG ticket, CriteriaSpecDTO criteriaSpec);

  protected boolean evaluateWithinChangeWindow(TicketNG ticket, ApprovalInstance instance, NGLogCallback logCallback) {
    // to add implementation, override this method in approval callback
    return true;
  }

  protected void updateTicketFieldsInApprovalInstance(TicketNG ticket, ApprovalInstance instance) {
    // to add implementation, override this method in approval callback
  }
}
