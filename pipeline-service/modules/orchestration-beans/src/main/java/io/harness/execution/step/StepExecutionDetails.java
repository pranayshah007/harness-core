/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.step;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.step.approval.custom.CustomApprovalStepExecutionDetails;
import io.harness.execution.step.approval.harness.HarnessApprovalStepExecutionDetails;
import io.harness.execution.step.approval.jira.JiraApprovalStepExecutionDetails;
import io.harness.execution.step.approval.servicenow.ServiceNowApprovalStepExecutionDetails;
import io.harness.execution.step.jira.create.JiraCreateStepExecutionDetails;
import io.harness.execution.step.jira.update.JiraUpdateStepExecutionDetails;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(CDP)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "stepType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = HarnessApprovalStepExecutionDetails.class, name = "HarnessApproval")
  , @JsonSubTypes.Type(value = CustomApprovalStepExecutionDetails.class, name = "CustomApproval"),
      @JsonSubTypes.Type(value = ServiceNowApprovalStepExecutionDetails.class, name = "ServiceNowApproval"),
      @JsonSubTypes.Type(value = JiraApprovalStepExecutionDetails.class, name = "JiraApproval"),
      @JsonSubTypes.Type(value = JiraCreateStepExecutionDetails.class, name = "JiraCreate"),
      @JsonSubTypes.Type(value = JiraUpdateStepExecutionDetails.class, name = "JiraUpdate")
})
public interface StepExecutionDetails {}
