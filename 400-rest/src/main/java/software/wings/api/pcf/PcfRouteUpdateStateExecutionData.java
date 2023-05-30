/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.task.pcf.CfCommandRequest;

import software.wings.api.ExecutionDataValue;
import software.wings.sm.StateExecutionData;

import java.util.List;
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
@TargetModule(HarnessModule._957_CG_BEANS)
public class PcfRouteUpdateStateExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String activityId;
  private String accountId;
  private String appId;
  private CfCommandRequest pcfCommandRequest;
  private String commandName;
  private CfRouteUpdateRequestConfigData pcfRouteUpdateRequestConfigData;
  private List<String> tags;
  private boolean isRollback;
  private boolean isUpSizeInActiveApp;
  private CfInBuiltVariablesUpdateValues finalAppDetails;

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    return getInternalExecutionDetails();
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    return getInternalExecutionDetails();
  }

  private Map<String, ExecutionDataValue> getInternalExecutionDetails() {
    Map<String, ExecutionDataValue> execDetails = super.getExecutionDetails();
    putNotNull(execDetails, "organization",
        ExecutionDataValue.builder().value(pcfCommandRequest.getOrganization()).displayName("Organization").build());
    putNotNull(execDetails, "space",
        ExecutionDataValue.builder().value(pcfCommandRequest.getSpace()).displayName("Space").build());
    putNotNull(execDetails, "commandName",
        ExecutionDataValue.builder().value(commandName).displayName("Command Name").build());
    putNotNull(execDetails, "updateConfig",
        ExecutionDataValue.builder().value(getDisplayStringForUpdateConfig()).displayName("Update Config").build());
    // putting activityId is very important, as without it UI wont make call to fetch commandLogs that are shown
    // in activity window
    putNotNull(
        execDetails, "activityId", ExecutionDataValue.builder().value(activityId).displayName("Activity Id").build());

    return execDetails;
  }

  private String getDisplayStringForUpdateConfig() {
    StringBuilder stringBuilder = new StringBuilder(128);

    if (pcfRouteUpdateRequestConfigData.isStandardBlueGreen()) {
      stringBuilder.append('{')
          .append(pcfRouteUpdateRequestConfigData.getNewApplicationName())
          .append(" : ")
          .append(isRollback ? pcfRouteUpdateRequestConfigData.getTempRoutes()
                             : pcfRouteUpdateRequestConfigData.getFinalRoutes())
          .append('}');

      if (isNotEmpty(pcfRouteUpdateRequestConfigData.getExistingApplicationNames())) {
        pcfRouteUpdateRequestConfigData.getExistingApplicationNames().forEach(appName
            -> stringBuilder.append(", {")
                   .append(appName)
                   .append(" : ")
                   .append(isRollback ? pcfRouteUpdateRequestConfigData.getFinalRoutes()
                                      : pcfRouteUpdateRequestConfigData.getTempRoutes())
                   .append('}'));
      }
    } else {
      if (isNotEmpty(pcfRouteUpdateRequestConfigData.getExistingApplicationNames())) {
        pcfRouteUpdateRequestConfigData.getExistingApplicationNames().forEach(appName
            -> stringBuilder.append(appName)
                   .append("[")
                   .append(pcfRouteUpdateRequestConfigData.getFinalRoutes())
                   .append("]"));
      }
    }

    return stringBuilder.toString();
  }

  @Override
  public PcfRouteSwapExecutionSummary getStepExecutionSummary() {
    return PcfRouteSwapExecutionSummary.builder()
        .organization(pcfCommandRequest.getOrganization())
        .space(pcfCommandRequest.getSpace())
        .finalAppDetails(finalAppDetails)
        .pcfRouteUpdateRequestConfigData(pcfRouteUpdateRequestConfigData)
        .build();
  }
}
