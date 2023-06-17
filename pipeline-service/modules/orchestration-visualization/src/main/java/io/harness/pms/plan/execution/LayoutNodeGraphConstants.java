/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LayoutNodeGraphConstants {
  public String BASE_KEY = PlanExecutionSummaryKeys.layoutNodeMap + ".%s";
  public String NODE_IDENTIFIER = BASE_KEY + ".nodeIdentifier";
  public String NAME = BASE_KEY + ".name";
  public String STRATEGY_METADATA = BASE_KEY + ".strategyMetadata";
  public String STATUS = BASE_KEY + ".status";
  public String START_TS = BASE_KEY + ".startTs";
  public String NODE_EXECUTION_ID = BASE_KEY + ".nodeExecutionId";
  public String NODE_RUN_INFO = BASE_KEY + ".nodeRunInfo";
  public String END_TS = BASE_KEY + ".endTs";
  public String FAILURE_INFO = BASE_KEY + ".failureInfo";
  public String FAILURE_INFO_DTO = BASE_KEY + ".failureInfoDTO";
  public String SKIP_INFO = BASE_KEY + ".skipInfo";
  public String EXECUTION_INPUT_CONFIGURED = BASE_KEY + ".executionInputConfigured";
  public String EDGE_LAYOUT_LIST = BASE_KEY + ".edgeLayoutList";
  public String NEXT_IDS = EDGE_LAYOUT_LIST + ".nextIds";
}
