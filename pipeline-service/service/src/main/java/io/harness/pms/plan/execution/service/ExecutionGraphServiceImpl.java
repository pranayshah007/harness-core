/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.OrchestrationGraph;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionSubGraphResponse;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionGraphServiceImpl implements ExecutionGraphService {
  NodeExecutionService nodeExecutionService;
  GraphGenerationService graphGenerationService;

  @Override
  public NodeExecutionSubGraphResponse getNodeExecutionSubGraph(String nodeExecutionId, String planExecutionId) {
    List<NodeExecution> nodeExecutions;
    List<String> parentIds = new ArrayList<>();
    parentIds.add(nodeExecutionId);
    /*
      Extracting recursive child NodeExecutions for given nodeExecutionId
      And Incase of Multiple Retries for any child entity, we will only consider the lastRetried NodeExecution
      And will exclude the children of OldRetries NodeExecutions
    */
    nodeExecutions = nodeExecutionService.fetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries(
        planExecutionId, parentIds);
    nodeExecutions.add(nodeExecutionService.get(nodeExecutionId));
    OrchestrationGraph graph = graphGenerationService.buildOrchestrationGraphForNodeExecution(
        planExecutionId, nodeExecutionId, nodeExecutions);
    OrchestrationGraphDTO orchestrationGraphDTO = OrchestrationGraphDTOConverter.convertFrom(graph);
    ExecutionGraph executionGraph = ExecutionGraphMapper.toExecutionGraph(orchestrationGraphDTO);
    return NodeExecutionSubGraphResponse.builder().executionGraph(executionGraph).build();
  }
}
