/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateInfo;
import io.harness.beans.EdgeList;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.ExecutionNode;
import io.harness.beans.ExecutionNodeAdjacencyList;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.dto.GraphVertexDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.PlanExecutionUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionGraphMapper {
  public ExecutionNode toExecutionNode(GraphVertexDTO graphVertex) {
    String basefqn = PlanExecutionUtils.getFQNUsingLevelDTOs(graphVertex.getAmbiance().getLevels());
    return ExecutionNode.builder()
        .endTs(graphVertex.getEndTs())
        .failureInfo(graphVertex.getFailureInfo())
        .skipInfo(graphVertex.getSkipInfo())
        .nodeRunInfo(graphVertex.getNodeRunInfo())
        .stepParameters(graphVertex.getStepParameters())
        .name(graphVertex.getName())
        .baseFqn(basefqn)
        .outcomes(graphVertex.getOrchestrationMapOutcomes())
        .startTs(graphVertex.getStartTs())
        .endTs(graphVertex.getEndTs())
        .identifier(graphVertex.getIdentifier())
        .status(ExecutionStatus.getExecutionStatus(graphVertex.getStatus()))
        .stepType(graphVertex.getStepType())
        .uuid(graphVertex.getUuid())
        .setupId(graphVertex.getPlanNodeId())
        .executableResponses(graphVertex.getExecutableResponses())
        .unitProgresses(graphVertex.getUnitProgresses())
        .progressData(graphVertex.getProgressData())
        .delegateInfoList(mapDelegateSelectionLogParamsToDelegateInfo(graphVertex.getGraphDelegateSelectionLogParams()))
        .interruptHistories(InterruptConfigDTOMapper.toInterruptEffectDTOList(graphVertex.getInterruptHistories()))
        .stepDetails(graphVertex.getOrchestrationMapStepDetails())
        .strategyMetadata(graphVertex.getStrategyMetadata())
        .build();
  }

  private List<DelegateInfo> mapDelegateSelectionLogParamsToDelegateInfo(
      List<GraphDelegateSelectionLogParams> delegateSelectionLogParams) {
    return delegateSelectionLogParams.stream()
        .filter(param -> param.getSelectionLogParams() != null)
        .map(ExecutionGraphMapper::getDelegateInfoForUI)
        .collect(Collectors.toList());
  }

  public DelegateInfo getDelegateInfoForUI(GraphDelegateSelectionLogParams graphDelegateSelectionLogParams) {
    return DelegateInfo.builder()
        .id(graphDelegateSelectionLogParams.getSelectionLogParams().getDelegateId())
        .name(graphDelegateSelectionLogParams.getSelectionLogParams().getDelegateName())
        .taskId(graphDelegateSelectionLogParams.getTaskId())
        .taskName(graphDelegateSelectionLogParams.getTaskName())
        .build();
  }

  public final Function<EdgeList, ExecutionNodeAdjacencyList> toExecutionNodeAdjacencyList = edgeList
      -> ExecutionNodeAdjacencyList.builder().children(edgeList.getEdges()).nextIds(edgeList.getNextIds()).build();

  public final Function<OrchestrationGraphDTO, ExecutionGraph> toExecutionGraph = orchestrationGraph
      -> ExecutionGraph.builder()
             .rootNodeId(orchestrationGraph.getRootNodeIds().get(0))
             .nodeMap(orchestrationGraph.getAdjacencyList().getGraphVertexMap().entrySet().stream().collect(
                 Collectors.toMap(Map.Entry::getKey, entry -> toExecutionNode(entry.getValue()))))
             .nodeAdjacencyListMap(orchestrationGraph.getAdjacencyList().getAdjacencyMap().entrySet().stream().collect(
                 Collectors.toMap(Map.Entry::getKey, entry -> toExecutionNodeAdjacencyList.apply(entry.getValue()))))
             .build();

  public ExecutionGraph toExecutionGraph(OrchestrationGraphDTO orchestrationGraph) {
    return ExecutionGraph.builder()
        .rootNodeId(orchestrationGraph.getRootNodeIds().isEmpty() ? null : orchestrationGraph.getRootNodeIds().get(0))
        .nodeMap(orchestrationGraph.getAdjacencyList().getGraphVertexMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> toExecutionNode(entry.getValue()))))
        .nodeAdjacencyListMap(orchestrationGraph.getAdjacencyList().getAdjacencyMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> toExecutionNodeAdjacencyList.apply(entry.getValue()))))
        .build();
  }
}
