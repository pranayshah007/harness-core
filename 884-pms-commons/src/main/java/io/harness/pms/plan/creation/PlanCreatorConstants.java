/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class PlanCreatorConstants {
  public final String NEXT_ID = "nextId";
  public final String YAML_VERSION = "yamlVersion";
  public final String STAGE_FAILURE_STRATEGIES = "stageFailureStrategies";
  public final String STEP_GROUP_FAILURE_STRATEGIES = "stepGroupFailureStrategies";
  public final String STAGE_ID = "stageId";
  public final String STEP_GROUP_ID = "stepGroupId";
  public final String NEAREST_STRATEGY_ID = "nearestStrategyId";
  public final String ALL_STRATEGY_IDS = "allStrategyIds";
  public final String STRATEGY_NODE_TYPE = "strategyNodeType";
}