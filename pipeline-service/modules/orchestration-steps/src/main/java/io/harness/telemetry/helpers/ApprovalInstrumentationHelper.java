/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.APPROVAL_CRITERIA_SPEC_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.APPROVAL_STEP;
import static io.harness.telemetry.helpers.InstrumentationConstants.APPROVAL_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.AUTO_APPROVAL;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PIPELINE_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.REJECTION_CRITERIA_SPEC_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.RETRY_INTERNAL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ApprovalInstrumentationHelper extends InstrumentationHelper {
  public CompletableFuture<Void> sendApprovalEvent(ApprovalInstance approvalInstance) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, approvalInstance.getAccountId());
    eventPropertiesMap.put(ORG, approvalInstance.getProjectIdentifier());
    eventPropertiesMap.put(PROJECT, approvalInstance.getOrgIdentifier());
    eventPropertiesMap.put(APPROVAL_TYPE, approvalInstance.getType());
    eventPropertiesMap.put(PIPELINE_ID, approvalInstance.getPipelineIdentifier());
    switch (approvalInstance.getType()) {
      case JIRA_APPROVAL:
        return publishJiraApprovalInfo((JiraApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
      case CUSTOM_APPROVAL:
        return publishCustomApprovalInfo((CustomApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
      case HARNESS_APPROVAL:
        return publishHarnessApprovalInfo(
            (HarnessApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
      case SERVICENOW_APPROVAL:
        return publishServiceNowApprovalInfo(
            (ServiceNowApprovalInstance) approvalInstance, APPROVAL_STEP, eventPropertiesMap);
    }
    return null;
  }

  private CompletableFuture<Void> publishCustomApprovalInfo(
      CustomApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  private CompletableFuture<Void> publishServiceNowApprovalInfo(
      ServiceNowApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  private CompletableFuture<Void> publishHarnessApprovalInfo(
      HarnessApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    eventPropertiesMap.put(AUTO_APPROVAL, approvalInstance.getAutoApproval() != null);
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  private CompletableFuture<Void> publishJiraApprovalInfo(
      JiraApprovalInstance approvalInstance, String eventName, HashMap<String, Object> eventPropertiesMap) {
    String accountId = approvalInstance.getAccountId();
    eventPropertiesMap.put(RETRY_INTERNAL, approvalInstance.getRetryInterval().fetchFinalValue());
    eventPropertiesMap.put(REJECTION_CRITERIA_SPEC_TYPE, approvalInstance.getRejectionCriteria().getType());
    eventPropertiesMap.put(APPROVAL_CRITERIA_SPEC_TYPE, approvalInstance.getApprovalCriteria().getType());
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }
}
