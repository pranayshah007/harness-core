/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.execution.Status;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
public interface NodeExecutionService {
  /**
   * Fetches nodeExecution and uses id Index
   * Use this method only when all or most fields are required, like clone, graph etc
   * @param nodeExecutionId
   * @return
   */
  NodeExecution get(String nodeExecutionId);

  CloseableIterator<NodeExecution> get(List<String> nodeExecutionIds);

  /**
   * Fetches nodeExecution and uses id Index
   * @param nodeExecutionId
   * @param fieldsToInclude
   * @return NodeExecution with fields included as projection
   */
  NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude);

  /**
   * Get pipeline node from a given planExecutionId with projection
   * Uses - planExecutionId_stepCategory_identifier_idx
   * @param planExecutionId
   * @param fields
   * @return
   */
  Optional<NodeExecution> getPipelineNodeExecutionWithProjections(@NonNull String planExecutionId, Set<String> fields);

  /**
   * Fetches nodeExecution for given planExecutionId and planNodeId
   * Uses - planExecutionId_nodeId_idx index
   * @param planNodeUuid
   * @param planExecutionId
   * @return
   */
  NodeExecution getByPlanNodeUuid(String planNodeUuid, String planExecutionId);

  /**
   * Only allows getting nodeExecutions within max batch size, only if no projection is required
   * Get approval before using this method, Example for all retriedNodeIds
   * Uses id index
   * @param nodeExecutionIds
   * @return NodeExecutions with all properties
   */
  List<NodeExecution> getAll(Set<String> nodeExecutionIds);
  List<NodeExecution> getAllWithFieldIncluded(Set<String> nodeExecutionIds, Set<String> fieldsToInclude);

  /**
   * Fetches all step nodeExecutions with given projected fields, checks stepCategory should be step
   * Check before using this method if you need all nodes or subset of nodes
   * Uses - planExecutionId_stepCategory_identifier_idx
   * @param planExecutionId
   * @param fieldsToInclude
   * @return
   */
  CloseableIterator<NodeExecution> fetchAllStepNodeExecutions(String planExecutionId, Set<String> fieldsToInclude);

  /**
   * Fetches all statuses for nodeExecutions for give planExecutionId and oldRetry false
   * Uses - planExecutionId_status_idx index
   * @param planExecutionId
   * @return
   */
  List<Status> fetchNodeExecutionsStatusesWithoutOldRetries(String planExecutionId);

  /**
   * Returns iterator for nodeExecution without old retries without projection
   * Uses - planExecutionId_status_idx
   * Check before using this, as it gets all nodes without projections for a planExecutionId (Get approval)
   * Example -> Complete Graph generation
   * @param planExecutionId
   * @return
   */
  CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(String planExecutionId);

  /**
   * Returns iterator for nodeExecution without old retries and statusIn defined in param
   * Uses - planExecutionId_status_idx
   * @param planExecutionId
   * @return
   */
  CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
      String planExecutionId, EnumSet<Status> statuses, @NotNull Set<String> fieldsToInclude);

  /**
   * Returns iterator for nodeExecution without old retries for given planExecutionId
   * Uses - planExecutionId_status_idx
   * @param planExecutionId
   * @return
   */
  CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(
      String planExecutionId, @NotNull Set<String> fieldsToInclude);

  /**
   * Returns iterator for children nodeExecution for given parentId(direct children only) with projection sort by
   * CreatedAt (Desc) Uses - planExecutionId_parentId_createdAt_idx
   * TODO(archit): Check if planExecutionId and sort is required or not
   * @param planExecutionId
   * @param parentId
   * @param fieldsToBeIncluded
   * @return
   */
  CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Set<String> fieldsToBeIncluded);

  /**
   * Returns iterator for children nodeExecution for given parentId(direct children only) with projection sort by
   * CreatedAt (Desc) Uses - planExecutionId_parentId_createdAt_idx
   * TODO(archit): Check if planExecutionId and sort is required or not
   * @param planExecutionId
   * @param parentId
   * @param fieldsToBeIncluded
   * @return
   */
  CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Direction sortOrderOfCreatedAt, Set<String> fieldsToBeIncluded);

  /**
   * Returns iterator for children nodeExecution for given parentId(direct children only) with projection (No Sort, thus
   * default db sort) Uses - parentId_status_idx
   * @param parentId
   * @param fieldsToBeIncluded
   * @return
   */
  CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(String parentId, Set<String> fieldsToBeIncluded);

  /**
   * Fetches all nodes with given status with fieldsToBeIncluded as projections from analytics node
   * Uses - status_idx index
   * @param statuses
   * @param fieldsToBeIncluded
   * @return
   */
  CloseableIterator<NodeExecution> fetchAllNodeExecutionsByStatusIteratorFromAnalytics(
      EnumSet<Status> statuses, Set<String> fieldsToBeIncluded);

  /**
   * Count number of nodeExecutions for given parentId(direct children only) and statuses for those nodeExecutions
   * Uses - parentId_status_idx
   * @param parentId
   * @param flowingStatuses
   * @return
   */
  long findCountByParentIdAndStatusIn(String parentId, Set<Status> flowingStatuses);

  /**
   * Returns children executions (including grandchildren) for given parentId
   * Note: nodeExecution should atleast have parentId projected fields
   * Doesn't make any DB calls
   *
   * @param parentId
   * @param includeParent
   * @param finalList                 -> it contains the result from allExecutions
   * @param allExecutions
   * @param includeChildrenOfStrategy
   * @return
   */
  List<NodeExecution> extractChildExecutions(String parentId, boolean includeParent, List<NodeExecution> finalList,
      List<NodeExecution> allExecutions, boolean includeChildrenOfStrategy);

  // stepType, parentId and Status are already included into projections

  /**
   * Internally uses pagination to get all children of given planExecutionId
   * Apart from fieldsTobeIncluded, NodeProjectionUtils.fieldsForAllChildrenExtractor fields are already in projection
   * Uses - planExecutionId_status_idx index
   *
   * @param planExecutionId
   * @param parentId
   * @param statuses
   * @param includeParent
   * @param fieldsToBeIncluded
   * @param includeChildrenOfStrategy
   * @return all children(including grandchildren) in given planExecutionId and of parentId having one of the statuses
   */
  List<NodeExecution> findAllChildrenWithStatusInAndWithoutOldRetries(String planExecutionId, String parentId,
      EnumSet<Status> statuses, boolean includeParent, Set<String> fieldsToBeIncluded,
      boolean includeChildrenOfStrategy);

  /**
   * Note: It depends upon findAllChildrenWithStatusInAndWithoutOldRetries
   * Thus only NodeProjectionUtils.fieldsForAllChildrenExtractor fields are in projection
   * @param planExecutionId
   * @param parentId
   * @param includeParent
   * @return all children(including grandchildren) in given planExecutionId and of parentId for all statuses
   */
  default List<NodeExecution> findAllChildrenOnlyIds(String planExecutionId, String parentId, boolean includeParent) {
    return findAllChildrenWithStatusInAndWithoutOldRetries(
        planExecutionId, parentId, EnumSet.noneOf(Status.class), includeParent, new HashSet<>(), false);
  }

  /**
   * Creates NodeExecution for given value, and sends orchestrationLog event of type NodeExecutionStart and
   * OrchestrationEvent of type NodeExecutionStart
   * @param nodeExecution
   * @return
   */
  NodeExecution save(NodeExecution nodeExecution);

  /**
   * Save a collection nodeExecutions.
   * This does not send any orchestration event nor orchestration log event . So if you want to do graph update
   * operations on NodeExecution save, then use the above save() method.
   * @param nodeExecutions
   * @return
   */
  List<NodeExecution> saveAll(Collection<NodeExecution> nodeExecutions);

  /**
   * Use this method to update nodeExecution, it will return new NodeExecution after update
   * Get approval before this, as it will return all fields in return value
   * It will also send OrchestrationLogEvent if any set field contains GRAPH_FIELDS in NodeExecutionServiceImpl
   * Example -> retry node or clone nodes or processAdviserResponse where different impl may require different fields
   * @param nodeExecutionId
   * @param ops
   * @return
   */
  NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops);

  /**
   * Use this method to update nodeExecution, it will return new NodeExecution after update
   * Projection fields cannot be empty, else it throws exception
   * It will also send OrchestrationLogEvent if any set field contains GRAPH_FIELDS in NodeExecutionServiceImpl
   * @param nodeExecutionId
   * @param ops
   * @param fieldsToBeIncluded
   * @return
   */
  NodeExecution update(
      @NonNull String nodeExecutionId, @NonNull Consumer<Update> ops, @NonNull Set<String> fieldsToBeIncluded);

  /**
   * NodeExecution update with ops, use this if returned value is not required
   * It will also send OrchestrationLogEvent if any set field contains GRAPH_FIELDS in NodeExecutionServiceImpl
   * Get approval before using this method
   * @param nodeExecutionId
   * @param ops
   */
  void updateV2(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops);

  /**
   * Use this method while updating statuses. This guarantees we are hopping from correct statuses.
   * As we don't have transactions it is possible that your node execution state is manipulated by some other thread and
   * your transition is no longer valid.
   * Like your workflow is aborted but some other thread try to set it to running. Same logic applied to plan execution
   * status as well
   */
  NodeExecution updateStatusWithOps(@NonNull String nodeExecutionId, @NonNull Status targetStatus, Consumer<Update> ops,
      EnumSet<Status> overrideStatusSet);

  /**
   * Mark the given nodeExecutionIds to status as discontinuing
   * @param leafInstanceIds
   * @return
   */
  long markLeavesDiscontinuing(List<String> leafInstanceIds);

  /**
   * Mark all leaf nodes in given status and oldRetry false or all nodes in status [INPUT_WAITING , QUEUED]
   * as Discontinuing
   * Uses - planExecutionId_status_idx
   * @param planExecutionId
   * @param statuses
   * @return
   */
  long markAllLeavesAndQueuedNodesDiscontinuing(String planExecutionId, EnumSet<Status> statuses);

  /**
   * Update the old execution -> set oldRetry flag set to true
   * It also sends OrchestrationLogEvent for NodeUpdate
   * @param nodeExecutionId Id of Failed Node Execution
   */
  boolean markRetried(String nodeExecutionId);

  /**
   * Deletes the nodeExecutions and its related metadata
   * @param planExecutionId Id of to be deleted planExecution
   */
  void deleteAllNodeExecutionAndMetadata(String planExecutionId);

  /**
   * Update Nodes for which the previousId was failed node execution and replace it with the
   * note execution which is being retried
   * Uses - previous_id_idx
   * @param nodeExecutionId Old nodeExecutionId
   * @param newNodeExecutionId Id of new retry node execution
   */
  boolean updateRelationShipsForRetryNode(String nodeExecutionId, String newNodeExecutionId);

  /**
   * Mark all the activeStatuses nodes in given planExecutionId as ERRORED
   * Uses - planExecutionId_status_idx
   * @param planExecutionId
   * @return
   */
  boolean errorOutActiveNodes(String planExecutionId);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<RetryStageInfo> getStageDetailFromPlanExecutionId(String planExecutionId);

  List<NodeExecution> fetchStageExecutions(String planExecutionId);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<NodeExecution> fetchStrategyNodeExecutions(String planExecutionId, List<String> stageFQNs);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<String> fetchStageFqnFromStageIdentifiers(String planExecutionId, List<String> stageIdentifiers);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  Map<String, Node> mapNodeExecutionIdWithPlanNodeForGivenStageFQN(String planExecutionId, List<String> stageFQNs);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> fetchStageExecutionsWithEndTsAndStatusProjection(String planExecutionId);

  CloseableIterator<NodeExecution> fetchNodeExecutionsForGivenStageFQNs(
      String planExecutionId, List<String> stageFQNs, Collection<String> requiredFields);
}
