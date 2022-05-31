/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.rollback;

import static io.harness.pms.yaml.YAMLFieldNameConstants.FAILED_CHILDREN_OUTPUT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.steps.SectionStepSweepingOutput;
import io.harness.steps.SectionStepSweepingOutput.SectionStepSweepingOutputBuilder;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class RollbackUtility {
  /**
   * It publishes the information of failedNodeIds in the output at the step level. Note that it publishes setupIds of
   * failed Children
   *
   * Follows a recursive approach to fill in the failedNodeIds so failedNodeIds contain only failed leaf nodes.
   * Example:
   * Let us say a shell script failed. It will reach its parent, for that parent the shell script is failed. Now for its
   * grandParent, the failedNodeId will be of shellScript only as we kind of move the id of leaf node up
   *
   */
  public void publishRollbackInformation(Ambiance ambiance, Map<String, ResponseData> responseDataMap,
      ExecutionSweepingOutputService executionSweepingOutputService) {
    SectionStepSweepingOutputBuilder builder = SectionStepSweepingOutput.builder();
    List<String> failedExecutionIds = new ArrayList<>();
    for (ResponseData responseData : responseDataMap.values()) {
      StepResponseNotifyData responseNotifyData = (StepResponseNotifyData) responseData;
      Status executionStatus = responseNotifyData.getStatus();
      if (StatusUtils.brokeStatuses().contains(executionStatus)) {
        failedExecutionIds.add(responseNotifyData.getNodeExecutionId());
      }
    }
    List<OptionalSweepingOutput> onFailRollbackOptionalOutput =
        executionSweepingOutputService.listOutputsWithGivenNameAndRuntimeIds(
            ambiance, FAILED_CHILDREN_OUTPUT, failedExecutionIds);
    if (onFailRollbackOptionalOutput.isEmpty() && !failedExecutionIds.isEmpty()) {
      builder.failedNodeIds(failedExecutionIds);
    } else {
      for (int i = 0; i < onFailRollbackOptionalOutput.size(); i++) {
        OptionalSweepingOutput optionalSweepingOutput = onFailRollbackOptionalOutput.get(i);
        String nodeUuid = failedExecutionIds.get(i);
        if (optionalSweepingOutput.isFound()) {
          SectionStepSweepingOutput sectionStepSweepingOutput =
              (SectionStepSweepingOutput) optionalSweepingOutput.getOutput();
          for (String failedNode : sectionStepSweepingOutput.getFailedNodeIds()) {
            builder.failedNodeId(failedNode);
          }
        } else {
          builder.failedNodeId(nodeUuid);
        }
      }
    }
    SectionStepSweepingOutput output = builder.build();
    if (!output.getFailedNodeIds().isEmpty()) {
      executionSweepingOutputService.consume(ambiance, FAILED_CHILDREN_OUTPUT, builder.build(), null);
    }
  }
}
