/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static java.util.Objects.isNull;

import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.tasks.ResponseData;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SdkCoreStepUtils {
  public static StepResponse createStepResponseFromChildResponse(Map<String, ResponseData> responseDataMap) {
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
    List<Status> childStatuses = new LinkedList<>();
    String nodeExecutionId = "";
    boolean hasFailureInfo = false;

    // Sorting in the descending order of endTs.
    List<StepResponseNotifyData> stepResponseNotifyDataList = extractSortedStepResponseNotifyData(responseDataMap);
    for (StepResponseNotifyData responseNotifyData : stepResponseNotifyDataList) {
      Status executionStatus = responseNotifyData.getStatus();
      childStatuses.add(executionStatus);
      nodeExecutionId = responseNotifyData.getNodeUuid();
      if (StatusUtils.brokeStatuses().contains(executionStatus)) {
        if (responseNotifyData.getFailureInfo() != null) {
          failureInfoBuilder.addAllFailureData(responseNotifyData.getFailureInfo().getFailureDataList());
          failureInfoBuilder.addAllFailureTypes(responseNotifyData.getFailureInfo().getFailureTypesList());
          // set the errorMessage only for the first element with failure that would be the latest failureInfo.
          if (!hasFailureInfo) {
            failureInfoBuilder.setErrorMessage(responseNotifyData.getFailureInfo().getErrorMessage());
          }
          hasFailureInfo = true;
        }
      }
    }
    if (hasFailureInfo) {
      responseBuilder.failureInfo(failureInfoBuilder.build());
    }
    responseBuilder.status(StatusUtils.calculateStatusForNode(childStatuses, nodeExecutionId));
    return responseBuilder.build();
  }

  private static List<StepResponseNotifyData> extractSortedStepResponseNotifyData(
      Map<String, ResponseData> responseDataMap) {
    return responseDataMap.values()
        .stream()
        .map(o -> (StepResponseNotifyData) o)
        .sorted((a, b) -> {
          if (a.getNodeExecutionEndTs() == null || b.getNodeExecutionEndTs() == null) {
            return -1;
          }
          // Sorting in the descending order of endTs.
          return Math.toIntExact(b.getNodeExecutionEndTs() - a.getNodeExecutionEndTs());
        })
        .collect(Collectors.toList());
  }
  public static ParameterField<String> getParameterFieldHandleValueNull(ParameterField<String> fieldValue) {
    if (isNull(fieldValue)) {
      return null;
    }

    if (isNull(fieldValue.getValue())) {
      fieldValue.setValue("");
    }

    return fieldValue;
  }
}
