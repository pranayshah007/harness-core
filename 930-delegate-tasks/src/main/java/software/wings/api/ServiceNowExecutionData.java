/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.sm.StateExecutionData;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
@BreakDependencyOn("software.wings.service.impl.servicenow.ServiceNowServiceImpl")
public class ServiceNowExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String activityId;
  private ExecutionStatus executionStatus;
  private String issueUrl;
  private String message;
  private String issueId;
  private String issueNumber;
  private String responseMsg;
  private ServiceNowTicketType ticketType;

  // import set field
  private ServiceNowImportSetResponse transformationDetails;
  private List<String> transformationValues;

  // approvalField
  private String currentState;
  private Map<String, String> currentStatus;
  private boolean waitingForChangeWindow;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    return setExecutionData(executionDetails);
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    return setExecutionData(executionDetails);
  }

  private Map<String, ExecutionDataValue> setExecutionData(Map<String, ExecutionDataValue> executionDetails) {
    if (ticketType != null && issueUrl != null) {
      putNotNull(executionDetails, "issueUrl",
          ExecutionDataValue.builder().displayName(ticketType.getDisplayName() + " Url").value(issueUrl).build());
    }

    if (EmptyPredicate.isNotEmpty(transformationValues)) {
      putNotNull(executionDetails, "transformationValues",
          ExecutionDataValue.builder()
              .displayName("Transformation Values")
              .value(transformationValues.toString().replaceAll("[\\[\\]]", ""))
              .build());
    }

    if (currentState != null) {
      putNotNull(executionDetails, "currentState",
          ExecutionDataValue.builder().displayName("current state").value(currentState).build());
    }
    return executionDetails;
  }
}
