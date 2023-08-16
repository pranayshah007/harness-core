/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jira.JiraIssueKeyNG;
import io.harness.steps.approval.step.beans.ApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("JiraApprovalInstanceDetails")
@Schema(name = "JiraApprovalInstanceDetails", description = "This contains details of Jira Approval Instance")
public class JiraApprovalInstanceDetailsDTO implements ApprovalInstanceDetailsDTO {
  @NotEmpty String connectorRef;
  @NotNull JiraIssueKeyNG issue;
  @NotNull io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO approvalCriteria;
  @NotNull CriteriaSpecWrapperDTO rejectionCriteria;

  // id of the delegate task created in the latest polling event
  String latestDelegateTaskId;
  String delegateTaskName;
}
