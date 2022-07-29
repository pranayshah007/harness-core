/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.servicenow;

import static io.harness.exception.WingsException.USER_SRE;

import static java.util.Objects.isNull;

import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ServiceNowException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.approval.AbstractApprovalCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.TicketNG;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.servicenow.evaluation.ServiceNowCriteriaEvaluator;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.PushThroughNotifyCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceNowApprovalCallback extends AbstractApprovalCallback implements PushThroughNotifyCallback {
  private final String approvalInstanceId;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private KryoSerializer kryoSerializer;

  @Builder
  public ServiceNowApprovalCallback(String approvalInstanceId) {
    this.approvalInstanceId = approvalInstanceId;
  }

  @Override
  public void push(Map<String, ResponseData> response) {
    ServiceNowApprovalInstance instance = (ServiceNowApprovalInstance) approvalInstanceService.get(approvalInstanceId);
    try (AutoLogContext ignore = instance.autoLogContext()) {
      pushInternal(response, instance);
    }
  }

  private void pushInternal(Map<String, ResponseData> response, ServiceNowApprovalInstance instance) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, false);

    if (instance.hasExpired()) {
      updateApprovalInstanceAndLog(logCallback, "Approval instance has expired", LogColor.Red,
          CommandExecutionStatus.FAILURE, ApprovalStatus.EXPIRED, instance.getId());
    }

    ServiceNowTaskNGResponse serviceNowTaskNGResponse;
    try {
      ResponseData responseData = response.values().iterator().next();
      responseData = (ResponseData) kryoSerializer.asInflatedObject(((BinaryResponseData) responseData).getData());
      if (responseData instanceof ErrorNotifyResponseData) {
        handleErrorNotifyResponse(
            logCallback, (ErrorNotifyResponseData) responseData, "Failed to fetch ServiceNow ticket:");
        return;
      }
      serviceNowTaskNGResponse = (ServiceNowTaskNGResponse) responseData;
      if (isNull(serviceNowTaskNGResponse.getTicket())) {
        log.info("Invalid ticket number");
        String errorMessage = String.format("Invalid ticket number: %s", instance.getTicketNumber());
        logCallback.saveExecutionLog(
            LogHelper.color(errorMessage, LogColor.Red), LogLevel.INFO, CommandExecutionStatus.FAILURE);
        approvalInstanceService.finalizeStatus(instance.getId(), ApprovalStatus.FAILED, errorMessage);
        return;
      }
      logCallback.saveExecutionLog(String.format("Ticket url: %s", serviceNowTaskNGResponse.getTicket().getUrl()));
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Error fetching serviceNow ticket response: %s. Retrying in sometime...",
                              ExceptionUtils.getMessage(ex)),
              LogColor.Red));
      log.error("Failed to fetch serviceNow ticket", ex);
      return;
    }

    try {
      checkApprovalAndRejectionCriteria(serviceNowTaskNGResponse.getTicket(), instance, logCallback,
          instance.getApprovalCriteria(), instance.getRejectionCriteria());
    } catch (Exception ex) {
      if (ex instanceof ApprovalStepNGException && ((ApprovalStepNGException) ex).isFatal()) {
        handleFatalException(instance, logCallback, (ApprovalStepNGException) ex);
        return;
      }

      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Error evaluating approval/rejection criteria: %s. Retrying in sometime...",
                              ngErrorHelper.getErrorSummary(ex.getMessage())),
              LogColor.Red));
      throw new ServiceNowException(
          "Error while evaluating approval/rejection criteria", ErrorCode.SERVICENOW_ERROR, USER_SRE, ex);
    }
  }

  @Override
  protected boolean evaluateCriteria(TicketNG ticket, CriteriaSpecDTO criteriaSpec) {
    return ServiceNowCriteriaEvaluator.evaluateCriteria((ServiceNowTicketNG) ticket, criteriaSpec);
  }
}
