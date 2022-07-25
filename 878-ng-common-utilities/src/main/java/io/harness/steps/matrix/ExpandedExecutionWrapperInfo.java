package io.harness.steps.matrix;

import io.harness.plancreator.execution.ExecutionWrapperConfig;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * This POJO is used to store information related to expandedExecutionWrapper
 *
 * How to use this POJO?
 * - The ExecutionWrapperConfig can have either step, parallel or stepGroup. The expandedExecutionConfigs will have the
 *   list of full expansion of the executionWrapperConfig.
 *   See strategyHelperTest's expected yaml to get an idea of what is expected.
 * - The yaml is expanded but how to know information of the strategy configured on it. To check for strategy, every
 *   expandedExecutionConfigs will have uuid for which there will be a corresponding entry in strategyExpansionData
 */
@Data
@Builder
public class ExpandedExecutionWrapperInfo {
  List<ExecutionWrapperConfig> expandedExecutionConfigs;
  Map<String, StrategyExpansionData> uuidToStrategyExpansionData;
}
