/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.CollectionUtils.nullIfEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.execution.export.ExportExecutionsUtils;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.NameValuePair;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(CDC)
@Value
@Builder
public class ApprovalMetadata implements ExecutionDetailsMetadata {
  String name;
  ExecutionStatus status;
  Duration timeout;
  EmbeddedUserMetadata approvedBy;
  ZonedDateTime approvedOn;
  String comments;
  ApprovalStateType approvalType;
  List<NameValuePair> variables;

  // User group approval.
  @Getter(onMethod = @__(@JsonIgnore)) @JsonIgnore List<String> userGroupIds;
  @NonFinal @Setter List<String> userGroups;

  // ServiceNow and Jira Approvals.
  String ticketUrl;
  ServiceNowTicketType ticketType;
  String issueUrl;
  String issueKey;
  String currentStatus;
  String approvalField;
  String approvalValue;
  String rejectionField;
  String rejectionValue;

  // Shell Script Approval.
  // activityId is used to fill up subCommands later when we want to query execution logs.
  @Getter(onMethod = @__(@JsonIgnore)) @JsonIgnore String activityId;
  @NonFinal @Setter List<ActivityCommandUnitMetadata> subCommands;

  public void addUserGroup(String userGroupName) {
    if (userGroupName == null) {
      return;
    }

    if (userGroups == null) {
      setUserGroups(new ArrayList<>());
    }
    userGroups.add(userGroupName);
  }

  static ApprovalMetadata fromStateExecutionData(StateExecutionData stateExecutionData) {
    if (!(stateExecutionData instanceof ApprovalStateExecutionData)) {
      return null;
    }

    ApprovalStateExecutionData approvalStateExecutionData = (ApprovalStateExecutionData) stateExecutionData;
    return ApprovalMetadata.builder()
        .name(approvalStateExecutionData.getStateName())
        .status(approvalStateExecutionData.getStatus())
        .timeout(approvalStateExecutionData.getTimeoutMillis() == null
                ? null
                : Duration.ofMillis(approvalStateExecutionData.getTimeoutMillis()))
        .approvedBy(EmbeddedUserMetadata.fromEmbeddedUser(approvalStateExecutionData.getApprovedBy()))
        .approvedOn(
            approvalStateExecutionData.getApprovedOn() == null || approvalStateExecutionData.getApprovedOn() <= 0
                ? null
                : ExportExecutionsUtils.prepareZonedDateTime(approvalStateExecutionData.getApprovedOn()))
        .comments(approvalStateExecutionData.getComments())
        .approvalType(approvalStateExecutionData.getApprovalStateType())
        .variables(nullIfEmpty(approvalStateExecutionData.getVariables()))
        .userGroupIds(nullIfEmpty(emptyIfNull(approvalStateExecutionData.getUserGroups())
                                      .stream()
                                      .filter(Objects::nonNull)
                                      .collect(Collectors.toList())))
        .ticketUrl(approvalStateExecutionData.getTicketUrl())
        .ticketType(approvalStateExecutionData.getTicketType())
        .issueUrl(approvalStateExecutionData.getIssueUrl())
        .issueKey(approvalStateExecutionData.getIssueKey())
        .currentStatus(approvalStateExecutionData.getCurrentStatus())
        .approvalField(approvalStateExecutionData.getApprovalField())
        .approvalValue(approvalStateExecutionData.getApprovalValue())
        .rejectionField(approvalStateExecutionData.getRejectionField())
        .rejectionValue(approvalStateExecutionData.getRejectionValue())
        .activityId(approvalStateExecutionData.getActivityId())
        .build();
  }
}
