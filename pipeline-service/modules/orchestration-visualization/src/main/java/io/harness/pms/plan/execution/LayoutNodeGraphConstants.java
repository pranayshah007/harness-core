package io.harness.pms.plan.execution;

import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LayoutNodeGraphConstants {
  String BASE_KEY = PlanExecutionSummaryKeys.layoutNodeMap + ".%s";
  String NODE_IDENTIFIER = BASE_KEY + ".nodeIdentifier";
  String NAME = BASE_KEY + ".name";
  String STRATEGY_METADATA = BASE_KEY + ".strategyMetadata";
  String STATUS = BASE_KEY + ".status";
  String START_TS = BASE_KEY + ".startTs";
  String NODE_EXECUTION_ID = BASE_KEY + ".nodeExecutionId";
  String NODE_RUN_INFO = BASE_KEY + ".nodeRunInfo";
  String END_TS = BASE_KEY + ".endTs";
  String FAILURE_INFO = BASE_KEY + ".failureInfo";
  String FAILURE_INFO_DTO = BASE_KEY + ".failureInfoDTO";
  String SKIP_INFO = BASE_KEY + ".skipInfo";
  String EXECUTION_INPUT_CONFIGURED = BASE_KEY + ".executionInputConfigured";
}
