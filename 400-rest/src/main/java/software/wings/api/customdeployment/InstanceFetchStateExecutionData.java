/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.customdeployment;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.api.ExecutionDataValue;
import software.wings.api.InstanceFetchStateExecutionSummary;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateExecutionData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("instanceFetchStateExecutionData")
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class InstanceFetchStateExecutionData extends StateExecutionData {
  private String activityId;
  private String hostObjectArrayPath;
  private String instanceFetchScript;
  private Map<String, String> hostAttributes;
  private String scriptOutput;
  @Builder.Default private List<InstanceStatusSummary> newInstanceStatusSummaries = new ArrayList<>();
  private List<String> tags;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionSummary = super.getExecutionSummary();
    setExecutionData(executionSummary);
    return executionSummary;
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
  }

  @Override
  public InstanceFetchStateExecutionSummary getStepExecutionSummary() {
    return InstanceFetchStateExecutionSummary.builder()
        .activityId(activityId)
        .instanceFetchScript(instanceFetchScript)
        .scriptOutput(scriptOutput)
        .tags(tags)
        .build();
  }
}
