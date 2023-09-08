/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "BarrierPositionInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierPositionInfo {
  String planExecutionId;
  List<BarrierPosition> barrierPositionList;

  @Data
  @Builder(toBuilder = true)
  @FieldNameConstants(innerTypeName = "BarrierPositionKeys")
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class BarrierPosition {
    String pipelineSetupId;
    String pipelineRuntimeId;

    String stageSetupId;
    String stageRuntimeId;

    String stepGroupSetupId;
    String stepGroupRuntimeId;

    String stepSetupId;
    String stepRuntimeId;

    /* `strategySetupId` contains the setupId of the closest parent node containing a looping strategy
     (if there is any). */
    String strategySetupId;

    /* `strategyNodeType` is used to store whether the closest parent node containing a looping strategy
     is of type STEP_GROUP or STAGE. This field is used in `BarrierServiceImpl.obtainRuntimeIdUpdate` and
    `BarrierWithinStrategyExpander` for updating runtime info related to a given BarrierPosition. */
    StrategyNodeType strategyNodeType;

    boolean stepGroupRollback;

    public enum BarrierPositionType { STAGE, STEP_GROUP, STEP }
    public enum StrategyNodeType { STEP_GROUP, STAGE }
  }
}
