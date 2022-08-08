/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.ApprovalDetails.Action;
import software.wings.sm.StateExecutionData;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class ShellScriptApprovalExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private ExecutionStatus executionStatus;
  private Action approvalAction;
  private String activityId;
  private Map<String, String> sweepingOutputEnvVariables;
  private String name;
  private String approvalId;
  private String errorMessage;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    setExecutionData(executionDetails);

    return executionDetails;
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    setExecutionData(executionDetails);

    return executionDetails;
  }

  private void setExecutionData(Map<String, ExecutionDataValue> executionDetails) {
    putNotNull(executionDetails, "activityId",
        ExecutionDataValue.builder().displayName("Activity Id").value(activityId).build());
    putNotNull(executionDetails, "name", ExecutionDataValue.builder().displayName("Name").value(name).build());

    if (approvalAction != null) {
      putNotNull(executionDetails, "approvalAction",
          ExecutionDataValue.builder().displayName("Approval Action").value(approvalAction.name()).build());
    }

    if (executionStatus != null) {
      putNotNull(executionDetails, "executionStatus",
          ExecutionDataValue.builder().displayName("Execution Status").value(executionStatus.name()).build());
    }

    if (isNotEmpty(sweepingOutputEnvVariables)) {
      putNotNull(executionDetails, "sweepingOutputEnvVariables",
          ExecutionDataValue.builder()
              .displayName("Script Output")
              .value(removeNullValues(sweepingOutputEnvVariables))
              .build());
    }
  }

  @Override
  public ScriptStateExecutionSummary getStepExecutionSummary() {
    return ScriptStateExecutionSummary.builder()
        .activityId(activityId)
        .sweepingOutputEnvVariables(sweepingOutputEnvVariables)
        .build();
  }
}
