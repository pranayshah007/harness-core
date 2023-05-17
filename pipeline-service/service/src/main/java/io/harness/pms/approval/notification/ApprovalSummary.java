/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.steps.approval.step.beans.ApprovalStatus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;

@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "ApprovalSummaryKeys")
public class ApprovalSummary {
  String pipelineName;
  String orgName;
  String projectName;
  String approvalMessage;
  String startedAt;
  String expiresAt;
  String triggeredBy;
  ApprovalStatus status;
  String action;
  @NonFinal String pipelineExecutionSummary;
  Set<String> finishedStages;
  Set<String> runningStages;
  Set<String> upcomingStages;

  String pipelineExecutionLink;
  String timeRemainingForApproval;

  public Map<String, String> toParams() {
    Map<String, String> params = new HashMap<>();
    params.put(ApprovalSummaryKeys.pipelineName, pipelineName);
    params.put(ApprovalSummaryKeys.orgName, orgName);
    params.put(ApprovalSummaryKeys.projectName, projectName);
    params.put(ApprovalSummaryKeys.approvalMessage, approvalMessage);
    params.put(ApprovalSummaryKeys.startedAt, startedAt);
    params.put(ApprovalSummaryKeys.expiresAt, expiresAt);
    params.put(ApprovalSummaryKeys.triggeredBy, triggeredBy);
    params.put(ApprovalSummaryKeys.finishedStages, joinStages(finishedStages));
    params.put(ApprovalSummaryKeys.runningStages, joinStages(runningStages));
    params.put(ApprovalSummaryKeys.upcomingStages, joinStages(upcomingStages));

    params.put(ApprovalSummaryKeys.pipelineExecutionLink, pipelineExecutionLink);
    params.put(ApprovalSummaryKeys.timeRemainingForApproval, timeRemainingForApproval);
    params.put(ApprovalSummaryKeys.action, action);
    if (isNull(status)) {
      params.put(ApprovalSummaryKeys.status, "");
    } else {
      params.put(ApprovalSummaryKeys.status, String.valueOf(status).toLowerCase(Locale.ROOT));
    }
    return params;
  }

  private static String joinStages(Set<String> stages) {
    if (EmptyPredicate.isEmpty(stages)) {
      return "N/A";
    }
    return String.join(", ", stages);
  }
}
