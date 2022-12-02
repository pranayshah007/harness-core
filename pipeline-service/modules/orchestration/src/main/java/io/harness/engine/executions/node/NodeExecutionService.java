/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.execution.Status;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface NodeExecutionService {
  NodeExecution get(String nodeExecutionId);

  List<NodeExecution> getAll(Set<String> nodeExecutionIds);

  NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude);

  NodeExecution getByPlanNodeUuid(String planNodeUuid, String planExecutionId);

  Page<NodeExecution> fetchAllStepNodeExecutions(
      String planExecutionId, Set<String> fieldsToInclude, Pageable pageable);

  List<Status> fetchNodeExecutionsWithoutOldRetriesOnlyStatus(String planExecutionId);

  // TODO(Projection): Only left in graph generation in error
  List<NodeExecution> fetchNodeExecutionsWithoutOldRetries(String planExecutionId);

  // TODO(Projection): only left in test case
  List<NodeExecution> fetchWithoutRetriesAndStatusIn(String planExecutionId, EnumSet<Status> statuses);

  Page<NodeExecution> fetchWithoutRetriesAndStatusIn(
      String planExecutionId, EnumSet<Status> statuses, Set<String> fieldsToInclude, Pageable pageable);

  // TODO(Projection): Only left in FindAllChildrenWithStatusIn
  List<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
      String planExecutionId, EnumSet<Status> statuses, boolean shouldUseProjections, Set<String> fieldsToBeIncluded);

  Page<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
      String planExecutionId, EnumSet<Status> statuses, Set<String> fieldsToBeIncluded, Pageable pageable);

  Page<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(String planExecutionId, EnumSet<Status> statuses,
      Set<String> fieldsToBeIncluded, Set<String> fieldsToBeExcluded, Pageable pageable);

  // TODO(Projection): Only left in FindAllChildrenWithStatusIn
  List<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(String planExecutionId, EnumSet<Status> statuses,
      boolean shouldUseProjections, Set<String> fieldsToBeIncluded, Set<String> fieldsToBeExcluded);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> fetchChildrenNodeExecutions(
      String planExecutionId, String parentId, Set<String> fieldsToBeIncluded);

  // TODO(Projection): only left in test case
  List<NodeExecution> fetchNodeExecutionsByStatus(String planExecutionId, Status status);

  Page<NodeExecution> fetchNodeExecutionsByStatus(
      String planExecutionId, Status status, Set<String> fieldsToBeIncluded, Pageable pageable);

  Page<NodeExecution> fetchAllNodeExecutionsByStatus(
      EnumSet<Status> statuses, Set<String> fieldNames, Pageable pageable);

  NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops);

  NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops, Set<String> fieldsToBeIncluded);

  @VisibleForTesting boolean shouldLog(Update updateOps);

  void updateV2(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops);

  NodeExecution updateStatusWithOps(@NonNull String nodeExecutionId, @NonNull Status targetStatus, Consumer<Update> ops,
      EnumSet<Status> overrideStatusSet);

  NodeExecution updateStatusWithOpsV2(@NonNull String nodeExecutionId, @NonNull Status targetStatus,
      Consumer<Update> ops, EnumSet<Status> overrideStatusSet, Set<String> fieldsToBeIncluded);

  NodeExecution updateStatusWithUpdate(
      @NonNull String nodeExecutionId, @NonNull Status targetStatus, Update ops, EnumSet<Status> overrideStatusSet);

  List<NodeExecution> saveAll(Collection<NodeExecution> nodeExecutions);

  NodeExecution save(NodeExecution nodeExecution);

  NodeExecution updateStatusWithUpdate(@NotNull String nodeExecutionId, @NotNull Status status, Update ops,
      EnumSet<Status> overrideStatusSet, Set<String> includedFields, boolean shouldUseProjections);

  long markLeavesDiscontinuing(String planExecutionId, List<String> leafInstanceIds);

  long markAllLeavesAndQueuedNodesDiscontinuing(String planExecutionId, EnumSet<Status> statuses);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> findAllNodeExecutionsTrimmed(String planExecutionId);

  boolean markRetried(String nodeExecutionId);

  boolean updateRelationShipsForRetryNode(String nodeExecutionId, String newNodeExecutionId);

  // Todo: Change this to use category instead of identifier. Only used for fetching pipeline node.
  Optional<NodeExecution> getByNodeIdentifier(@NonNull String nodeIdentifier, @NonNull String planExecutionId);

  Optional<NodeExecution> getPipelineNodeExecution(@NonNull String planExecutionId);
  Optional<NodeExecution> getPipelineNodeExecutionWithProjections(@NonNull String planExecutionId, Set<String> fields);

  long findCountByParentIdAndStatusIn(String parentId, Set<Status> flowingStatuses);

  default List<NodeExecution> findAllChildrenOnlyIds(String planExecutionId, String parentId, boolean includeParent) {
    return findAllChildrenWithStatusIn(planExecutionId, parentId, EnumSet.noneOf(Status.class), includeParent, true,
        Sets.newHashSet(
            NodeExecutionKeys.id, NodeExecutionKeys.parentId, NodeExecutionKeys.status, NodeExecutionKeys.stepType),
        new HashSet<>());
  }

  List<NodeExecution> findAllChildrenWithStatusIn(String planExecutionId, String parentId, EnumSet<Status> statuses,
      boolean includeParent, boolean shouldUseProjections, Set<String> fieldsToBeIncluded,
      Set<String> fieldsToBeExcluded);

  List<NodeExecution> findAllChildrenWithStatusIn(
      String planExecutionId, String parentId, EnumSet<Status> flowingStatuses, boolean includeParent);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> fetchNodeExecutionsByParentId(String nodeExecutionId, boolean oldRetry);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> fetchNodeExecutionsByParentIdWithAmbianceAndNode(
      String nodeExecutionId, boolean oldRetry, boolean includeParent);

  boolean errorOutActiveNodes(String planExecutionId);

  List<NodeExecution> extractChildExecutions(
      String parentId, boolean includeParent, List<NodeExecution> finalList, List<NodeExecution> allExecutions);

  boolean removeTimeoutInstances(String nodeExecutionId);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<RetryStageInfo> getStageDetailFromPlanExecutionId(String planExecutionId);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<NodeExecution> fetchStageExecutions(String planExecutionId);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<NodeExecution> fetchStrategyNodeExecutions(String planExecutionId, List<String> stageFQNs);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  List<String> fetchStageFqnFromStageIdentifiers(String planExecutionId, List<String> stageIdentifiers);

  // TODO(Projection): Make it paginated, and projection, in retry flow
  Map<String, Node> mapNodeExecutionIdWithPlanNodeForGivenStageFQN(String planExecutionId, List<String> stageFQNs);

  // TODO(Projection): Make it paginated, has projection
  List<NodeExecution> fetchStageExecutionsWithEndTsAndStatusProjection(String planExecutionId);

  NodeExecution update(@NonNull NodeExecution nodeExecution);
}
