/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.springdata.SpringDataMongoUtils.returnNewOptions;

import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.NodeUpdateObserver;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.interrupts.InterruptEffect;
import io.harness.observer.Subject;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEvent.Builder;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.springdata.TransactionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PIPELINE)
public class NodeExecutionServiceImpl implements NodeExecutionService {
  private static final Set<String> GRAPH_FIELDS =
      ImmutableSet.of(NodeExecutionKeys.mode, NodeExecutionKeys.progressData, NodeExecutionKeys.unitProgresses,
          NodeExecutionKeys.executableResponses, NodeExecutionKeys.interruptHistories, NodeExecutionKeys.retryIds,
          NodeExecutionKeys.oldRetry, NodeExecutionKeys.failureInfo, NodeExecutionKeys.endTs);
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject private OrchestrationLogConfiguration orchestrationLogConfiguration;

  @Getter private final Subject<NodeStatusUpdateObserver> stepStatusUpdateSubject = new Subject<>();
  @Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();
  @Getter private final Subject<NodeUpdateObserver> nodeUpdateObserverSubject = new Subject<>();

  /**
   * This is deprecated, use function
   * @param nodeExecutionId
   * @return
   */
  @Override
  @Deprecated
  public NodeExecution get(String nodeExecutionId) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    NodeExecution nodeExecution = mongoTemplate.findOne(query, NodeExecution.class);
    if (nodeExecution == null) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecution;
  }

  @Override
  public NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    NodeExecution nodeExecution = mongoTemplate.findOne(query, NodeExecution.class);
    if (nodeExecution == null) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecution;
  }

  // TODO (alexi) : Handle the case where multiple instances are returned
  @Override
  public NodeExecution getByPlanNodeUuid(String planNodeUuid, String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.nodeId).is(planNodeUuid));
    NodeExecution nodeExecution = mongoTemplate.findOne(query, NodeExecution.class);
    if (nodeExecution == null) {
      throw new InvalidRequestException("Node Execution is null for planNodeUuid: " + planNodeUuid);
    }
    return nodeExecution;
  }

  @Override
  public Optional<NodeExecution> getByNodeIdentifier(String nodeIdentifier, String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.identifier).in(nodeIdentifier));
    return Optional.ofNullable(mongoTemplate.findOne(query, NodeExecution.class));
  }

  @Override
  public List<NodeExecution> findByParentIdAndStatusIn(String parentId, EnumSet<Status> flowingStatuses) {
    Query query = query(where(NodeExecutionKeys.parentId).is(parentId))
                      .addCriteria(where(NodeExecutionKeys.status).in(flowingStatuses))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchNodeExecutions(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<Status> fetchNodeExecutionsWithoutOldRetriesOnlyStatus(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    query.fields().exclude(NodeExecutionKeys.id).include(NodeExecutionKeys.status);
    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);
    return nodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
  }

  // Important : This should be used in graph regeneration no where else
  // Please check with prashant if you need any such methods
  @Override
  public List<NodeExecution> fetchNodeExecutionsWithoutOldRetries(String planExecutionId) {
    return fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
        planExecutionId, EnumSet.noneOf(Status.class), false, new HashSet<>());
  }

  @Override
  public List<NodeExecution> fetchWithoutRetriesAndStatusIn(String planExecutionId, EnumSet<Status> statuses) {
    return fetchNodeExecutionsWithoutOldRetriesAndStatusIn(planExecutionId, statuses, false, Collections.emptySet());
  }

  @Override
  public List<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
      String planExecutionId, EnumSet<Status> statuses, boolean shouldUseProjections, Set<String> fieldsToBeIncluded) {
    return fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
        planExecutionId, statuses, shouldUseProjections, fieldsToBeIncluded, Collections.emptySet());
  }

  @Override
  public List<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusIn(String planExecutionId,
      EnumSet<Status> statuses, boolean shouldUseProjections, Set<String> fieldsToBeIncluded,
      Set<String> fieldsToBeExcluded) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    if (shouldUseProjections) {
      for (String field : fieldsToBeIncluded) {
        query.fields().include(field);
      }
      for (String field : fieldsToBeExcluded) {
        query.fields().exclude(field);
      }
    }
    if (isNotEmpty(statuses)) {
      query.addCriteria(where(NodeExecutionKeys.status).in(statuses));
    }
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchChildrenNodeExecutions(String planExecutionId, String parentId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).is(parentId))
                      .with(Sort.by(Direction.DESC, NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchChildrenNodeExecutions(
      String planExecutionId, String parentId, Set<String> fieldsToBeIncluded) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).is(parentId))
                      .with(Sort.by(Direction.DESC, NodeExecutionKeys.createdAt));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchNodeExecutionsByStatus(String planExecutionId, Status status) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).is(status));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  /**
   * This is deprecated, use below update to get only required fields
   */
  @Override
  public NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    Update updateOps = new Update().set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    ops.accept(updateOps);
    boolean shouldLog = shouldLog(updateOps);
    NodeExecution updatedNodeExecution = transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        throw new NodeExecutionUpdateFailedException(
            "Node Execution Cannot be updated with provided operations" + nodeExecutionId);
      }
      if (shouldLog) {
        orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
      }
      return updated;
    });
    nodeUpdateObserverSubject.fireInform(
        NodeUpdateObserver::onNodeUpdate, NodeUpdateInfo.builder().nodeExecution(updatedNodeExecution).build());
    return updatedNodeExecution;
  }

  @Override
  public NodeExecution update(
      @NonNull String nodeExecutionId, @NonNull Consumer<Update> ops, Set<String> fieldsToBeIncluded) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    fieldsToBeIncluded.addAll(NodeProjectionUtils.fieldsForNodeUpdateObserver);
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    Update updateOps = new Update().set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    ops.accept(updateOps);
    boolean shouldLog = shouldLog(updateOps);
    NodeExecution updatedNodeExecution = transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        throw new NodeExecutionUpdateFailedException(
            "Node Execution Cannot be updated with provided operations" + nodeExecutionId);
      }
      if (shouldLog) {
        orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
      }
      return updated;
    });
    nodeUpdateObserverSubject.fireInform(
        NodeUpdateObserver::onNodeUpdate, NodeUpdateInfo.builder().nodeExecution(updatedNodeExecution).build());
    return updatedNodeExecution;
  }

  @VisibleForTesting
  @Override
  public boolean shouldLog(Update updateOps) {
    if (!orchestrationLogConfiguration.isReduceOrchestrationLog()) {
      return false;
    }
    Set<String> fieldsUpdated = new HashSet<>();
    if (updateOps.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$set")).keySet());
    }
    if (updateOps.getUpdateObject().containsKey("$addToSet")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$addToSet")).keySet());
    }
    return fieldsUpdated.stream().anyMatch(GRAPH_FIELDS::contains);
  }

  @Override
  public void updateV2(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    update(nodeExecutionId, ops, NodeProjectionUtils.fieldsForNodeUpdateObserver);
  }

  @Override
  public NodeExecution save(NodeExecution nodeExecution) {
    if (nodeExecution.getVersion() == null) {
      // Havnt added triggerPayload in the event as no one is consuming triggerPayload on NodeExecutionStart
      Builder builder = OrchestrationEvent.newBuilder()
                            .setAmbiance(nodeExecution.getAmbiance())
                            .setStatus(nodeExecution.getStatus())
                            .setEventType(OrchestrationEventType.NODE_EXECUTION_START)
                            .setServiceName(nodeExecution.getModule());

      if (nodeExecution.getResolvedStepParameters() != null) {
        builder.setStepParameters(nodeExecution.getResolvedStepParametersBytes());
      }
      eventEmitter.emitEvent(builder.build());
      NodeExecution savedNodeExecution = transactionHelper.performTransaction(() -> {
        NodeExecution nodeExecution1 = mongoTemplate.insert(nodeExecution);
        if (orchestrationLogConfiguration.isReduceOrchestrationLog()) {
          orchestrationLogPublisher.onNodeStart(NodeStartInfo.builder().nodeExecution(nodeExecution).build());
        }
        return nodeExecution1;
      });
      if (savedNodeExecution != null) {
        emitEvent(savedNodeExecution, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
      }
      nodeExecutionStartSubject.fireInform(
          NodeExecutionStartObserver::onNodeStart, NodeStartInfo.builder().nodeExecution(savedNodeExecution).build());
      return savedNodeExecution;
    } else {
      NodeExecution savedNodeExecution = transactionHelper.performTransaction(() -> {
        orchestrationLogPublisher.onNodeStart(NodeStartInfo.builder().nodeExecution(nodeExecution).build());
        return mongoTemplate.save(nodeExecution);
      });
      if (savedNodeExecution != null) {
        emitEvent(savedNodeExecution, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
      }
      return savedNodeExecution;
    }
  }

  /**
   * Always use this method while updating statuses. This guarantees we a hopping from correct statuses.
   * As we don't have transactions it is possible that you node execution state is manipulated by some other thread and
   * your transition is no longer valid.
   *
   * Like your workflow is aborted but some other thread try to set it to running. Same logic applied to plan execution
   * status as well
   */

  @Override
  public NodeExecution updateStatusWithOps(@NonNull String nodeExecutionId, @NonNull Status status,
      Consumer<Update> ops, EnumSet<Status> overrideStatusSet) {
    Update updateOps = new Update();
    if (ops != null) {
      ops.accept(updateOps);
    }
    return updateStatusWithUpdate(nodeExecutionId, status, updateOps, overrideStatusSet);
  }

  @Override
  public NodeExecution updateStatusWithOpsV2(@NonNull String nodeExecutionId, @NonNull Status status,
      Consumer<Update> ops, EnumSet<Status> overrideStatusSet, Set<String> fieldsToBeIncluded) {
    Update updateOps = new Update();
    if (ops != null) {
      ops.accept(updateOps);
    }
    return updateStatusWithUpdate(nodeExecutionId, status, updateOps, overrideStatusSet, fieldsToBeIncluded, true);
  }

  @Override
  public NodeExecution updateStatusWithUpdate(
      @NotNull String nodeExecutionId, @NotNull Status status, Update ops, EnumSet<Status> overrideStatusSet) {
    return updateStatusWithUpdate(nodeExecutionId, status, ops, overrideStatusSet, new HashSet<>(), false);
  }

  @Override
  public NodeExecution updateStatusWithUpdate(@NotNull String nodeExecutionId, @NotNull Status status, Update ops,
      EnumSet<Status> overrideStatusSet, Set<String> includedFields, boolean shouldUseProjections) {
    EnumSet<Status> allowedStartStatuses =
        isEmpty(overrideStatusSet) ? StatusUtils.nodeAllowedStartSet(status) : overrideStatusSet;
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(allowedStartStatuses));
    if (shouldUseProjections) {
      for (String field : includedFields) {
        query.fields().include(field);
      }
    }
    Update updateOps =
        ops.set(NodeExecutionKeys.status, status).set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    NodeExecution updatedNodeExecution = transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        log.warn("Cannot update execution status for the node {} with {}", nodeExecutionId, status);
      } else {
        if (updated.getStepType().getStepCategory() == StepCategory.STAGE || StatusUtils.isFinalStatus(status)) {
          emitEvent(updated, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
        }
        if (orchestrationLogConfiguration.isReduceOrchestrationLog()) {
          orchestrationLogPublisher.onNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
        }
      }
      return updated;
    });
    if (updatedNodeExecution != null) {
      stepStatusUpdateSubject.fireInform(NodeStatusUpdateObserver::onNodeStatusUpdate,
          NodeUpdateInfo.builder().nodeExecution(updatedNodeExecution).build());
    }
    return updatedNodeExecution;
  }

  @Override
  public long markLeavesDiscontinuing(String planExecutionId, List<String> leafInstanceIds) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.uuid).in(leafInstanceIds));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", planExecutionId);
      return -1;
    }
    return updateResult.getModifiedCount();
  }

  @Override
  public long markAllLeavesDiscontinuing(String planExecutionId, EnumSet<Status> statuses) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.mode).in(ExecutionModeUtils.leafModes()))
                      .addCriteria(where(NodeExecutionKeys.status).in(statuses))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", planExecutionId);
      return -1;
    }
    return updateResult.getModifiedCount();
  }

  @Override
  public List<NodeExecution> findAllNodeExecutionsTrimmed(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.mode)
        .include(NodeExecutionKeys.parentId)
        .include(NodeExecutionKeys.oldRetry);
    return mongoTemplate.find(query, NodeExecution.class);
  }

  /**
   * Update the old execution -> set oldRetry flag set to true
   *
   * @param nodeExecutionId Id of Failed Node Execution
   */
  @Override
  public boolean markRetried(String nodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    NodeExecution nodeExecution = mongoTemplate.findAndModify(query, ops, NodeExecution.class);
    if (nodeExecution == null) {
      log.error("Failed to mark node as retry");
      return false;
    }
    orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    return true;
  }

  /**
   * Update Nodes for which the previousId was failed node execution and replace it with the
   * note execution which is being retried
   *
   * @param nodeExecutionId Old nodeExecutionId
   * @param newNodeExecutionId Id of new retry node execution
   */
  @Override
  public boolean updateRelationShipsForRetryNode(String nodeExecutionId, String newNodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.previousId, newNodeExecutionId);
    Query query = query(where(NodeExecutionKeys.previousId).is(nodeExecutionId));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (updateResult.wasAcknowledged()) {
      log.warn("No previous nodeExecutions could be updated for this nodeExecutionId: {}", nodeExecutionId);
      return false;
    }
    return true;
  }

  @Override
  public List<NodeExecution> fetchNodeExecutionsByParentId(String nodeExecutionId, boolean oldRetry) {
    Query query = query(where(NodeExecutionKeys.parentId).is(nodeExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.nodeId)
        .include(NodeExecutionKeys.identifier)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.failureInfo)
        .include(NodeExecutionKeys.adviserResponse)
        .include(NodeExecutionKeys.oldRetry);
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public boolean errorOutActiveNodes(String planExecutionId) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, ERRORED);
    ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.activeStatuses()));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as ERRORED -  planExecutionId: {}", planExecutionId);
      return false;
    }
    return true;
  }

  @Override
  public List<NodeExecution> findAllChildrenWithStatusIn(String planExecutionId, String parentId,
      EnumSet<Status> flowingStatuses, boolean includeParent, boolean shouldUseProjections,
      Set<String> fieldsToBeIncluded, Set<String> fieldsToBeExcluded) {
    if (shouldUseProjections) {
      if (!fieldsToBeIncluded.contains(NodeExecutionKeys.parentId)
          || !fieldsToBeIncluded.contains(NodeExecutionKeys.status)) {
        throw new InvalidRequestException(
            "ParentId or Status fields are not included in projectionFields, which are necessary");
      }
      if (fieldsToBeExcluded.contains(NodeExecutionKeys.id)) {
        throw new InvalidRequestException("Excluding nodeExecutionId is not allowed, as its a required field");
      }
    }
    List<NodeExecution> finalList = new ArrayList<>();
    List<NodeExecution> allExecutions = fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
        planExecutionId, flowingStatuses, true, fieldsToBeIncluded, fieldsToBeExcluded);
    return extractChildExecutions(parentId, includeParent, finalList, allExecutions);
  }

  @Override
  public List<NodeExecution> findAllChildrenWithStatusIn(
      String planExecutionId, String parentId, EnumSet<Status> flowingStatuses, boolean includeParent) {
    List<NodeExecution> finalList = new ArrayList<>();
    List<NodeExecution> allExecutions =
        fetchNodeExecutionsWithoutOldRetriesAndStatusIn(planExecutionId, flowingStatuses, false, new HashSet<>());
    return extractChildExecutions(parentId, includeParent, finalList, allExecutions);
  }

  private List<NodeExecution> extractChildExecutions(
      String parentId, boolean includeParent, List<NodeExecution> finalList, List<NodeExecution> allExecutions) {
    Map<String, List<NodeExecution>> parentChildrenMap = new HashMap<>();
    for (NodeExecution execution : allExecutions) {
      if (execution.getParentId() == null) {
        parentChildrenMap.put(execution.getUuid(), new ArrayList<>());
      } else if (parentChildrenMap.containsKey(execution.getParentId())) {
        parentChildrenMap.get(execution.getParentId()).add(execution);
      } else {
        List<NodeExecution> cList = new ArrayList<>();
        cList.add(execution);
        parentChildrenMap.put(execution.getParentId(), cList);
      }
    }
    extractChildList(parentChildrenMap, parentId, finalList);
    if (includeParent) {
      finalList.add(allExecutions.stream()
                        .filter(ne -> ne.getUuid().equals(parentId))
                        .findFirst()
                        .orElseThrow(() -> new UnexpectedException("Expected parent to be in list")));
    }
    return finalList;
  }

  private void extractChildList(
      Map<String, List<NodeExecution>> parentChildrenMap, String parentId, List<NodeExecution> finalList) {
    List<NodeExecution> children = parentChildrenMap.get(parentId);
    if (isEmpty(children)) {
      return;
    }
    finalList.addAll(children);
    children.forEach(child -> {
      // NOTE: We are ignoring the status of steps inside strategy because of max concurrency defined.
      // We need to run all the steps inside strategy once
      if (child.getStepType().getStepCategory() != StepCategory.STRATEGY) {
        extractChildList(parentChildrenMap, child.getUuid(), finalList);
      }
    });
  }

  @VisibleForTesting
  void emitEvent(NodeExecution nodeExecution, OrchestrationEventType orchestrationEventType) {
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    if (nodeExecution != null && nodeExecution.getAmbiance() != null) {
      PlanExecutionMetadata metadata =
          planExecutionMetadataService.findByPlanExecutionId(nodeExecution.getAmbiance().getPlanExecutionId())
              .orElseThrow(()
                               -> new InvalidRequestException("No Metadata present for planExecution :"
                                   + nodeExecution.getAmbiance().getPlanExecutionId()));
      triggerPayload = metadata.getTriggerPayload() != null ? metadata.getTriggerPayload() : triggerPayload;
    }

    Builder eventBuilder = OrchestrationEvent.newBuilder()
                               .setAmbiance(nodeExecution.getAmbiance())
                               .setStatus(nodeExecution.getStatus())
                               .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                               .setEventType(orchestrationEventType)
                               .setServiceName(nodeExecution.getModule())
                               .setTriggerPayload(triggerPayload);

    updateEventIfCausedByAutoAbortThroughTrigger(nodeExecution, orchestrationEventType, eventBuilder);
    eventEmitter.emitEvent(eventBuilder.build());
  }

  /**
   * This may seem very specialized logic for a particular case, but we want to keep events lighter as much as possible.
   * So putting this data only in case needed, as there will be large no of NODE_EXECUTION_STATUS_UPDATE events.
   * <p>
   * This is special handling added for CI usecase, to skip update git prs in case of pipeline auto abort from trigger.
   * NOTE: some refactoring is due, with which CI will start listenening to Stage level events only, then this wont be
   * needed here. But, that may take some time.
   */
  @VisibleForTesting
  void updateEventIfCausedByAutoAbortThroughTrigger(
      NodeExecution nodeExecution, OrchestrationEventType orchestrationEventType, Builder eventBuilder) {
    if (orchestrationEventType == OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE) {
      Level level = AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
      if (level != null && level.getStepType().getStepCategory() == StepCategory.STAGE
          && nodeExecution.getStatus() == ABORTED) {
        List<NodeExecution> allChildrenWithStatusInAborted = findAllChildrenWithStatusIn(
            nodeExecution.getAmbiance().getPlanExecutionId(), nodeExecution.getUuid(), EnumSet.of(ABORTED), false);
        if (isEmpty(allChildrenWithStatusInAborted)) {
          return;
        }

        List<NodeExecution> nodeExecutionsAbortedThroughTrigger =
            allChildrenWithStatusInAborted.stream().filter(this::isAbortedThroughTrigger).collect(Collectors.toList());
        if (isNotEmpty(nodeExecutionsAbortedThroughTrigger)) {
          eventBuilder.addTags(AUTO_ABORT_PIPELINE_THROUGH_TRIGGER);
        }
      }
    }
  }

  private boolean isAbortedThroughTrigger(NodeExecution nodeExecution) {
    return nodeExecution.getInterruptHistories().stream().anyMatch(this::isIssuedByTrigger);
  }

  private boolean isIssuedByTrigger(InterruptEffect interruptEffect) {
    InterruptConfig interruptConfig = interruptEffect.getInterruptConfig();
    return interruptConfig.hasIssuedBy() && interruptConfig.getIssuedBy().hasTriggerIssuer()
        && interruptConfig.getIssuedBy().getTriggerIssuer().getAbortPrevConcurrentExecution();
  }

  @Override
  public boolean removeTimeoutInstances(String nodeExecutionId) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.timeoutInstanceIds, new ArrayList<>());
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);

    if (!updateResult.wasAcknowledged()) {
      log.warn("TimeoutInstanceIds cannot be removed from nodeExecution {}", nodeExecutionId);
      return false;
    }
    return true;
  }

  @Override
  public List<RetryStageInfo> getStageDetailFromPlanExecutionId(String planExecutionId) {
    return fetchStageDetailFromNodeExecution(fetchStageExecutions(planExecutionId));
  }

  @Override
  public List<NodeExecution> fetchStageExecutions(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).ne(Status.SKIPPED))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STAGE));
    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public NodeExecution update(@NonNull NodeExecution nodeExecution) {
    return mongoTemplate.save(nodeExecution);
  }

  @Override
  public List<NodeExecution> fetchStageExecutionsWithEndTsAndStatusProjection(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).ne(SKIPPED))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STAGE));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.endTs)
        .include(NodeExecutionKeys.createdAt)
        .include(NodeExecutionKeys.mode)
        .include(NodeExecutionKeys.parentId)
        .include(NodeExecutionKeys.oldRetry)
        .include(NodeExecutionKeys.ambiance);

    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  // TODO optimize this to remove n+1 queries
  public List<RetryStageInfo> fetchStageDetailFromNodeExecution(List<NodeExecution> nodeExecutionList) {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    if (nodeExecutionList.size() == 0) {
      throw new InvalidRequestException("No stage to retry");
    }

    for (NodeExecution nodeExecution : nodeExecutionList) {
      String nextId = nodeExecution.getNextId();
      String parentId = nodeExecution.getParentId();
      RetryStageInfo stageDetail = RetryStageInfo.builder()
                                       .name(nodeExecution.getName())
                                       .identifier(nodeExecution.getIdentifier())
                                       .parentId(parentId)
                                       .createdAt(nodeExecution.getCreatedAt())
                                       .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
                                       .nextId(nextId != null ? nextId : get(parentId).getNextId())
                                       .build();
      stageDetails.add(stageDetail);
    }
    return stageDetails;
  }

  @Override
  public List<String> fetchStageFqnFromStageIdentifiers(String planExecutionId, List<String> stageIdentifiers) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STAGE))
                      .addCriteria(where(NodeExecutionKeys.identifier).in(stageIdentifiers));

    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);

    // fetching stageFqn of stage Nodes
    return nodeExecutions.stream()
        .map(nodeExecution -> nodeExecution.getNode().getStageFqn())
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Node> mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
      String planExecutionId, List<String> stageFQNs) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);

    Query query = new Query().addCriteria(criteria);

    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);

    // fetching stageFqn of stage Nodes
    Map<String, Node> nodeExecutionIdToPlanNode = new HashMap<>();
    nodeExecutions.forEach(
        nodeExecution -> nodeExecutionIdToPlanNode.put(nodeExecution.getUuid(), nodeExecution.getNode()));
    return nodeExecutionIdToPlanNode;
  }
}
