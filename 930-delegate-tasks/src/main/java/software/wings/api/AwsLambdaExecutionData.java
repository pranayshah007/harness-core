/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.sm.StateExecutionData;

import com.google.common.collect.Maps;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AwsLambdaExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String functionName;
  private String functionArn;
  private String functionVersion;
  private Integer statusCode;
  private String functionError;
  private String logResult;
  private String payload;
  private boolean executionDisabled;
  private String assertionStatement;
  private String assertionStatus;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    return getInternalExecutionDetails();
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    return getInternalExecutionDetails();
  }

  private Map<String, ExecutionDataValue> getInternalExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = Maps.newLinkedHashMap();
    putNotNull(executionDetails, "functionName",
        ExecutionDataValue.builder().displayName("Function Name").value(functionName).build());
    putNotNull(executionDetails, "functionArn",
        ExecutionDataValue.builder().displayName("Function ARN").value(functionArn).build());
    putNotNull(executionDetails, "functionVersion",
        ExecutionDataValue.builder().displayName("Function Version").value(functionVersion).build());
    putNotNull(executionDetails, "executionDisabled",
        ExecutionDataValue.builder().displayName("Execution Disabled").value(executionDisabled).build());
    putNotNull(executionDetails, "statusCode",
        ExecutionDataValue.builder().displayName("Status Code").value(statusCode).build());
    putNotNull(executionDetails, "functionError",
        ExecutionDataValue.builder().displayName("Function Error").value(functionError).build());
    putNotNull(executionDetails, "payload", ExecutionDataValue.builder().displayName("Payload").value(payload).build());
    putNotNull(executionDetails, "assertionStatement",
        ExecutionDataValue.builder().displayName("Assertion").value(assertionStatement).build());
    putNotNull(executionDetails, "assertionStatus",
        ExecutionDataValue.builder().displayName("Assertion Result").value(assertionStatus).build());
    putNotNull(
        executionDetails, "logResult", ExecutionDataValue.builder().displayName("Log Result").value(logResult).build());
    return executionDetails;
  }
}
