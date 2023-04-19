/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.jira.JiraIssueKeyNG;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.servicenow.ServiceNowTicketKeyNG;
import io.harness.steps.approval.step.beans.ApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.custom.beans.CustomApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfoDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.jira.beans.JiraApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(CDC)
@Singleton
public class ApprovalInstanceResponseMapper {
  private final JiraApprovalHelperService jiraApprovalHelperService;
  private final ServiceNowApprovalHelperService serviceNowApprovalHelperService;

  @Inject
  public ApprovalInstanceResponseMapper(JiraApprovalHelperService jiraApprovalHelperService,
      ServiceNowApprovalHelperService serviceNowApprovalHelperService) {
    this.jiraApprovalHelperService = jiraApprovalHelperService;
    this.serviceNowApprovalHelperService = serviceNowApprovalHelperService;
  }

  public ApprovalInstanceResponseDTO toApprovalInstanceResponseDTO(ApprovalInstance instance) {
    if (instance == null) {
      return null;
    }

    return ApprovalInstanceResponseDTO.builder()
        .id(instance.getId())
        .type(instance.getType())
        .status(instance.getStatus())
        .deadline(instance.getDeadline())
        .details(toApprovalInstanceDetailsDTO(instance))
        .createdAt(instance.getCreatedAt())
        .lastModifiedAt(instance.getLastModifiedAt())
        .errorMessage(instance.getErrorMessage())
        .build();
  }

  private ApprovalInstanceDetailsDTO toApprovalInstanceDetailsDTO(ApprovalInstance instance) {
    switch (instance.getType()) {
      case HARNESS_APPROVAL:
        return toHarnessApprovalInstanceDetailsDTO((HarnessApprovalInstance) instance);
      case JIRA_APPROVAL:
        return toJiraApprovalInstanceDetailsDTO((JiraApprovalInstance) instance);
      case SERVICENOW_APPROVAL:
        return toServiceNowApprovalInstanceDetailsDTO((ServiceNowApprovalInstance) instance);
      case CUSTOM_APPROVAL:
        return toCustomApprovalInstanceDetailsDTO((CustomApprovalInstance) instance);
      default:
        return null;
    }
  }

  private ApprovalInstanceDetailsDTO toHarnessApprovalInstanceDetailsDTO(HarnessApprovalInstance instance) {
    return HarnessApprovalInstanceDetailsDTO.builder()
        .approvalMessage(instance.getApprovalMessage())
        .includePipelineExecutionHistory(instance.isIncludePipelineExecutionHistory())
        .approvers(instance.getApprovers())
        .approvalActivities(instance.getApprovalActivities())
        .approverInputs(
            instance.fetchLastApprovalActivity()
                .map(approvalActivity
                    -> approvalActivity.getApproverInputs() == null ? new ArrayList<ApproverInputInfoDTO>()
                                                                    : approvalActivity.getApproverInputs()
                                                                          .stream()
                                                                          .map(ApproverInput::toApproverInputInfoDTO)
                                                                          .collect(Collectors.toList()))
                .orElse(instance.getApproverInputs()))
        .validatedApprovalUserGroups(instance.getValidatedApprovalUserGroups())
        .build();
  }

  private ApprovalInstanceDetailsDTO toJiraApprovalInstanceDetailsDTO(JiraApprovalInstance instance) {
    JiraConnectorDTO connectorDTO = jiraApprovalHelperService.getJiraConnector(
        AmbianceUtils.getAccountId(instance.getAmbiance()), AmbianceUtils.getOrgIdentifier(instance.getAmbiance()),
        AmbianceUtils.getProjectIdentifier(instance.getAmbiance()), instance.getConnectorRef());

    return JiraApprovalInstanceDetailsDTO.builder()
        .connectorRef(instance.getConnectorRef())
        .issue(new JiraIssueKeyNG(connectorDTO.getJiraUrl(), instance.getIssueKey(), instance.getTicketFields()))
        .approvalCriteria(instance.getApprovalCriteria())
        .rejectionCriteria(instance.getRejectionCriteria())
        .build();
  }

  private ApprovalInstanceDetailsDTO toServiceNowApprovalInstanceDetailsDTO(ServiceNowApprovalInstance instance) {
    ServiceNowConnectorDTO connectorDTO = serviceNowApprovalHelperService.getServiceNowConnector(
        AmbianceUtils.getAccountId(instance.getAmbiance()), AmbianceUtils.getOrgIdentifier(instance.getAmbiance()),
        AmbianceUtils.getProjectIdentifier(instance.getAmbiance()), instance.getConnectorRef());

    Map<String, String> fields;
    if (!isEmpty(instance.getTicketFields())) {
      fields = new HashMap<>();
      instance.getTicketFields().forEach((k, v) -> fields.put(k, v.getDisplayValue()));
    } else {
      fields = null;
    }

    return ServiceNowApprovalInstanceDetailsDTO.builder()
        .connectorRef(instance.getConnectorRef())
        .ticket(new ServiceNowTicketKeyNG(
            connectorDTO.getServiceNowUrl(), instance.getTicketNumber(), instance.getTicketType(), fields))
        .approvalCriteria(instance.getApprovalCriteria())
        .rejectionCriteria(instance.getRejectionCriteria())
        .changeWindowSpec(instance.getChangeWindow())
        .build();
  }

  private ApprovalInstanceDetailsDTO toCustomApprovalInstanceDetailsDTO(CustomApprovalInstance instance) {
    return CustomApprovalInstanceDetailsDTO.builder()
        .approvalCriteria(instance.getApprovalCriteria())
        .rejectionCriteria(instance.getRejectionCriteria())
        .build();
  }
}
