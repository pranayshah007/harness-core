/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.EXPIRED;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.springdata.SpringDataMongoUtils.returnNewOptions;

import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.expansion.PlanExpansionService;
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
import io.harness.springdata.PersistenceModule;
import io.harness.springdata.TransactionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(PIPELINE)
public class NodeExecutionServiceImpl implements NodeExecutionService {
  private static final int MAX_BATCH_SIZE = PersistenceModule.MAX_BATCH_SIZE;

  private static final Set<String> GRAPH_FIELDS = Set.of(NodeExecutionKeys.mode, NodeExecutionKeys.progressData,
      NodeExecutionKeys.unitProgresses, NodeExecutionKeys.executableResponses, NodeExecutionKeys.interruptHistories,
      NodeExecutionKeys.retryIds, NodeExecutionKeys.oldRetry, NodeExecutionKeys.failureInfo, NodeExecutionKeys.endTs);
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject private OrchestrationLogConfiguration orchestrationLogConfiguration;
  @Inject private NodeExecutionReadHelper nodeExecutionReadHelper;

  @Inject private PlanExpansionService planExpansionService;

  @Getter private final Subject<NodeStatusUpdateObserver> nodeStatusUpdateSubject = new Subject<>();
  @Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();
  @Getter private final Subject<NodeExecutionDeleteObserver> nodeDeleteObserverSubject = new Subject<>();

  @Override
  public NodeExecution get(String nodeExecutionId) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionReadHelper.getOneWithoutProjections(query);
    if (nodeExecutionOptional.isEmpty()) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecutionOptional.get();
  }

  @Override
  public NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude) {
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionReadHelper.getOne(query);
    if (nodeExecutionOptional.isEmpty()) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecutionOptional.get();
  }

  @Override
  public Optional<NodeExecution> getPipelineNodeExecutionWithProjections(
      @NonNull String planExecutionId, Set<String> fields) {
    // Uses - planExecutionId_stepCategory_identifier_idx
    // Sort is not part of index, as node selection is always one node thus it will not impact much
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.PIPELINE))
                      .with(Sort.by(Direction.ASC, NodeExecutionKeys.createdAt));
    for (String fieldName : fields) {
      query.fields().include(fieldName);
    }
    return Optional.ofNullable(mongoTemplate.findOne(query, NodeExecution.class));
  }

  // TODO (alexi) : Handle the case where multiple instances are returned
  @Override
  public NodeExecution getByPlanNodeUuid(String planNodeUuid, String planExecutionId) {
    // Uses - planExecutionId_nodeId_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.nodeId).is(planNodeUuid));
    NodeExecution nodeExecution = mongoTemplate.findOne(query, NodeExecution.class);
    if (nodeExecution == null) {
      throw new InvalidRequestException("Node Execution is null for planNodeUuid: " + planNodeUuid);
    }
    return nodeExecution;
  }

  @Override
  public List<NodeExecution> getAll(Set<String> nodeExecutionIds) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return new ArrayList<>();
    }

    if (nodeExecutionIds.size() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          String.format("requested %d records more than threshold of %d. consider pagination", nodeExecutionIds.size(),
              MAX_BATCH_SIZE));
    }
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    return nodeExecutionReadHelper.fetchNodeExecutionsWithoutProjections(query);
  }

  @Override
  public List<NodeExecution> getAllWithFieldIncluded(Set<String> nodeExecutionIds, Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return new ArrayList<>();
    }

    if (nodeExecutionIds.size() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          String.format("requested %d records more than threshold of %d. consider pagination", nodeExecutionIds.size(),
              MAX_BATCH_SIZE));
    }
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return mongoTemplate.find(query, NodeExecution.class);
  }
  @Override
  public CloseableIterator<NodeExecution> fetchAllStepNodeExecutions(
      String planExecutionId, Set<String> fieldsToInclude) {
    // Uses - planExecutionId_stepCategory_identifier_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STEP));
    for (String fieldName : fieldsToInclude) {
      query.fields().include(fieldName);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public List<Status> fetchNodeExecutionsStatusesWithoutOldRetries(String planExecutionId) {
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    // Exclude so that it can use Projection simplified from index without scanning documents.
    query.fields().exclude(NodeExecutionKeys.id).include(NodeExecutionKeys.status);
    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (CloseableIterator<NodeExecution> iterator = nodeExecutionReadHelper.fetchNodeExecutions(query)) {
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }
    return nodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
  }

  @Override
  public CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(String planExecutionId) {
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    // Can't use fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator as it uses projections
    return nodeExecutionReadHelper.fetchNodeExecutionsIteratorWithoutProjections(query);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
      String planExecutionId, EnumSet<Status> statuses, @NotNull Set<String> fieldsToInclude) {
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    if (EmptyPredicate.isNotEmpty(statuses)) {
      query.addCriteria(where(NodeExecutionKeys.status).in(statuses));
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(
      String planExecutionId, @NotNull Set<String> fieldsToInclude) {
    return fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
        planExecutionId, EnumSet.noneOf(Status.class), fieldsToInclude);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).is(parentId))
                      .with(Sort.by(Direction.DESC, NodeExecutionKeys.createdAt));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Direction sortOrderOfCreatedAt, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).is(parentId))
                      .with(Sort.by(sortOrderOfCreatedAt, NodeExecutionKeys.createdAt));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String parentId, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.parentId).is(parentId));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public CloseableIterator<NodeExecution> fetchAllNodeExecutionsByStatusIteratorFromAnalytics(
      EnumSet<Status> statuses, Set<String> fieldsToBeIncluded) {
    // Uses status_idx index
    Query query = query(where(NodeExecutionKeys.status).in(statuses));
    for (String fieldName : fieldsToBeIncluded) {
      query.fields().include(fieldName);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @Override
  public long findCountByParentIdAndStatusIn(String parentId, Set<Status> flowingStatuses) {
    // Uses - parentId_status_idx index
    Query query =
        query(where(NodeExecutionKeys.parentId).is(parentId)).addCriteria(where(NodeExecutionKeys.oldRetry).is(false));

    if (EmptyPredicate.isNotEmpty(flowingStatuses)) {
      query.addCriteria(where(NodeExecutionKeys.status).in(flowingStatuses));
    }
    return nodeExecutionReadHelper.findCount(query);
  }

  @Override
  public List<NodeExecution> extractChildExecutions(String parentId, boolean includeParent,
      List<NodeExecution> finalList, List<NodeExecution> allExecutions, boolean includeChildrenOfStrategy) {
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
    extractChildList(parentChildrenMap, parentId, finalList, includeChildrenOfStrategy);
    if (includeParent) {
      finalList.add(allExecutions.stream()
                        .filter(ne -> ne.getUuid().equals(parentId))
                        .findFirst()
                        .orElseThrow(() -> new UnexpectedException("Expected parent to be in list")));
    }
    return finalList;
  }

  // Extracts child list recursively from parentChildrenMap into finalList
  private void extractChildList(Map<String, List<NodeExecution>> parentChildrenMap, String parentId,
      List<NodeExecution> finalList, boolean includeChildrenOfStrategy) {
    List<NodeExecution> children = parentChildrenMap.get(parentId);
    if (isEmpty(children)) {
      return;
    }
    finalList.addAll(children);
    children.forEach(child -> {
      // NOTE: We are ignoring the status of steps inside strategy because of max concurrency defined.
      // We need to run all the steps inside strategy once
      if (includeChildrenOfStrategy || child.getStepType().getStepCategory() != StepCategory.STRATEGY) {
        extractChildList(parentChildrenMap, child.getUuid(), finalList, includeChildrenOfStrategy);
      }
    });
  }

  @Override
  public List<NodeExecution> findAllChildrenWithStatusInAndWithoutOldRetries(String planExecutionId, String parentId,
      EnumSet<Status> flowingStatuses, boolean includeParent, Set<String> fieldsToBeIncluded,
      boolean includeChildrenOfStrategy) {
    List<NodeExecution> finalList = new ArrayList<>();
    Set<String> finalFieldsToBeIncluded = new HashSet<>(NodeProjectionUtils.fieldsForAllChildrenExtractor);
    if (EmptyPredicate.isNotEmpty(fieldsToBeIncluded)) {
      finalFieldsToBeIncluded.addAll(fieldsToBeIncluded);
    }
    List<NodeExecution> allExecutions = new LinkedList<>();

    try (CloseableIterator<NodeExecution> iterator = fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             planExecutionId, flowingStatuses, finalFieldsToBeIncluded)) {
      while (iterator.hasNext()) {
        allExecutions.add(iterator.next());
      }
    }
    return extractChildExecutions(parentId, includeParent, finalList, allExecutions, includeChildrenOfStrategy);
  }

  @Override
  public NodeExecution save(NodeExecution nodeExecution) {
    if (nodeExecution.getVersion() == null) {
      NodeExecution savedNodeExecution = transactionHelper.performTransaction(() -> {
        NodeExecution nodeExecution1 = mongoTemplate.insert(nodeExecution);
        orchestrationLogPublisher.onNodeStart(NodeStartInfo.builder().nodeExecution(nodeExecution).build());
        return nodeExecution1;
      });
      if (savedNodeExecution != null) {
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

  // Save a collection nodeExecutions.
  // This does not send any orchestration event. So if you want to do graph update operations on NodeExecution save,
  // then use the below save() method.
  @Override
  public List<NodeExecution> saveAll(Collection<NodeExecution> nodeExecutions) {
    return new ArrayList<>(mongoTemplate.insertAll(nodeExecutions));
  }

  @Override
  public NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    return updateNodeExecutionInternal(nodeExecutionId, ops, new HashSet<>(), false);
  }

  @Override
  public NodeExecution update(
      @NonNull String nodeExecutionId, @NonNull Consumer<Update> ops, @NonNull Set<String> fieldsToBeIncluded) {
    return updateNodeExecutionInternal(nodeExecutionId, ops, fieldsToBeIncluded, true);
  }

  private NodeExecution updateNodeExecutionInternal(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops,
      @NonNull Set<String> fieldsToBeIncluded, boolean validateProjection) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    if (validateProjection) {
      validateNodeExecutionProjection(fieldsToBeIncluded);
      fieldsToBeIncluded.addAll(NodeProjectionUtils.fieldsForNodeUpdateObserver);
      for (String field : fieldsToBeIncluded) {
        query.fields().include(field);
      }
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
    return updatedNodeExecution;
  }

  @VisibleForTesting
  boolean shouldLog(Update updateOps) {
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
    updateNodeExecutionInternal(nodeExecutionId, ops, Sets.newHashSet(NodeExecutionKeys.uuid), true);
  }

  /**
   * Always use this method while updating statuses. This guarantees we are hopping from correct statuses.
   * As we don't have transactions it is possible that your node execution state is manipulated by some other thread and
   * your transition is no longer valid.
   * <p>
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

  private NodeExecution updateStatusWithUpdate(
      @NotNull String nodeExecutionId, @NotNull Status status, Update ops, EnumSet<Status> overrideStatusSet) {
    EnumSet<Status> allowedStartStatuses =
        isEmpty(overrideStatusSet) ? StatusUtils.nodeAllowedStartSet(status) : overrideStatusSet;
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(allowedStartStatuses));
    Update updateOps =
        ops.set(NodeExecutionKeys.status, status).set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    addFinalStatusOps(updateOps, status);

    NodeExecution updatedNodeExecution = transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        log.warn("Cannot update execution status for the node {} with {}", nodeExecutionId, status);
      } else {
        planExpansionService.updateStatus(updated.getAmbiance(), status);
        if (updated.getStepType().getStepCategory() == StepCategory.STAGE || StatusUtils.isFinalStatus(status)) {
          emitEvent(updated, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
        }
        orchestrationLogPublisher.onNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
      }
      return updated;
    });
    if (updatedNodeExecution != null) {
      nodeStatusUpdateSubject.fireInform(NodeStatusUpdateObserver::onNodeStatusUpdate,
          NodeUpdateInfo.builder().nodeExecution(updatedNodeExecution).build());
    }
    return updatedNodeExecution;
  }

  // Add additional updateOps based on nodeStatus to be updated
  // This is done to reduce write conflicts on same record, and send multiple updates at one go.
  private void addFinalStatusOps(Update updateOps, Status toBeUpdatedNodeStatus) {
    if (StatusUtils.isFinalStatus(toBeUpdatedNodeStatus)) {
      updateOps.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
      if (toBeUpdatedNodeStatus != EXPIRED) {
        updateOps.set(NodeExecutionKeys.timeoutInstanceIds, new ArrayList<>());
      }
    }
  }

  @Override
  public long markLeavesDiscontinuing(List<String> leafInstanceIds) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    // Use Id index
    Query query = query(where(NodeExecutionKeys.uuid).in(leafInstanceIds));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING for given nodeExecutionIds");
      return -1;
    }
    return updateResult.getModifiedCount();
  }

  @Override
  public long markAllLeavesAndQueuedNodesDiscontinuing(String planExecutionId, EnumSet<Status> statuses) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    Criteria leafNodeCriteria = where(NodeExecutionKeys.mode)
                                    .in(ExecutionModeUtils.leafModes())
                                    .and(NodeExecutionKeys.status)
                                    .in(statuses)
                                    .and(NodeExecutionKeys.oldRetry)
                                    .is(false);
    Criteria queuedNodeCriteria = where(NodeExecutionKeys.status).in(Status.QUEUED, Status.INPUT_WAITING);
    // Uses - planExecutionId_status_idx
    Query query = query(
        where(NodeExecutionKeys.planExecutionId).is(planExecutionId).orOperator(leafNodeCriteria, queuedNodeCriteria));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", planExecutionId);
      return -1;
    }
    return updateResult.getModifiedCount();
  }

  /**
   * Update the old execution -> set oldRetry flag set to true
   *
   * @param nodeExecutionId Id of Failed Node Execution
   */
  @Override
  public boolean markRetried(String nodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    NodeExecution nodeExecution = mongoTemplate.findAndModify(query, ops, NodeExecution.class);
    if (nodeExecution == null) {
      log.error("Failed to mark node as retry");
      return false;
    }
    orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    return true;
  }

  @Override
  public void deleteAllNodeExecutionAndMetadata(String planExecutionId) {
    // Fetches all nodeExecutions from analytics for given planExecutionId
    List<NodeExecution> batchNodeExecutionList = new LinkedList<>();
    Set<String> nodeExecutionsIdsToDelete = new HashSet<>();
    try (CloseableIterator<NodeExecution> iterator =
             fetchNodeExecutionsFromAnalytics(planExecutionId, NodeProjectionUtils.fieldsForNodeExecutionDelete)) {
      while (iterator.hasNext()) {
        NodeExecution next = iterator.next();
        nodeExecutionsIdsToDelete.add(next.getUuid());
        batchNodeExecutionList.add(next);
        if (batchNodeExecutionList.size() >= MAX_BATCH_SIZE) {
          deleteNodeExecutionsMetadataInternal(batchNodeExecutionList);
          batchNodeExecutionList.clear();
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(batchNodeExecutionList)) {
      deleteNodeExecutionsMetadataInternal(batchNodeExecutionList);
    }
    // At end delete all nodeExecutions
    deleteNodeExecutionsInternal(nodeExecutionsIdsToDelete);
  }

  /**
   * Deletes all nodeExecutions metadata
   *
   * @param nodeExecutionsToDelete
   */
  private void deleteNodeExecutionsMetadataInternal(List<NodeExecution> nodeExecutionsToDelete) {
    // Delete nodeExecutionMetadata example - WaitInstances, resourceRestraintInstances, timeoutInstanceIds, etc
    nodeDeleteObserverSubject.fireInform(NodeExecutionDeleteObserver::onNodesDelete, nodeExecutionsToDelete);
  }

  /**
   * Deletes all nodeExecutions for given ids
   * This method assumes the nodeExecutions will be in batch thus caller needs to handle it
   * @param batchNodeExecutionIds
   */
  private void deleteNodeExecutionsInternal(Set<String> batchNodeExecutionIds) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      Query query = query(where(NodeExecutionKeys.id).in(batchNodeExecutionIds));
      mongoTemplate.remove(query, NodeExecution.class);
      return true;
    });
  }

  /**
   * Update Nodes for which the previousId was failed node execution and replace it with the
   * note execution which is being retried
   *
   * @param nodeExecutionId    Old nodeExecutionId
   * @param newNodeExecutionId Id of new retry node execution
   */
  @Override
  public boolean updateRelationShipsForRetryNode(String nodeExecutionId, String newNodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.previousId, newNodeExecutionId);
    // Uses - previous_id_idx
    Query query = query(where(NodeExecutionKeys.previousId).is(nodeExecutionId));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (updateResult.wasAcknowledged()) {
      log.warn("No previous nodeExecutions could be updated for this nodeExecutionId: {}", nodeExecutionId);
      return false;
    }
    return true;
  }

  @Override
  public boolean errorOutActiveNodes(String planExecutionId) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, ERRORED);
    ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.activeStatuses()));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as ERRORED -  planExecutionId: {}", planExecutionId);
      return false;
    }
    return true;
  }

  @VisibleForTesting
  CloseableIterator<NodeExecution> fetchNodeExecutionsFromAnalytics(
      String planExecutionId, @NotNull Set<String> fieldsToInclude) {
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
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
        List<NodeExecution> allChildrenWithStatusInAborted = findAllChildrenWithStatusInAndWithoutOldRetries(
            nodeExecution.getAmbiance().getPlanExecutionId(), nodeExecution.getUuid(), EnumSet.of(ABORTED), false,
            Sets.newHashSet(NodeExecutionKeys.interruptHistories), false);
        if (isEmpty(allChildrenWithStatusInAborted)) {
          return;
        }

        List<NodeExecution> nodeExecutionsAbortedThroughTrigger =
            allChildrenWithStatusInAborted.stream().filter(this::isAbortedThroughTrigger).collect(Collectors.toList());
        if (EmptyPredicate.isNotEmpty(nodeExecutionsAbortedThroughTrigger)) {
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
  public List<RetryStageInfo> getStageDetailFromPlanExecutionId(String planExecutionId) {
    return fetchStageDetailFromNodeExecution(fetchStageExecutions(planExecutionId));
  }

  private List<NodeExecution> fetchStageExecutions(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).ne(Status.SKIPPED))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY));
    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchStageExecutionsWithEndTsAndStatusProjection(String planExecutionId) {
    Query query =
        query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
            .addCriteria(where(NodeExecutionKeys.status).ne(SKIPPED))
            .addCriteria(
                where(NodeExecutionKeys.stepCategory).in(Arrays.asList(StepCategory.STAGE, StepCategory.STRATEGY)));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.endTs)
        .include(NodeExecutionKeys.createdAt)
        .include(NodeExecutionKeys.planNode)
        .include(NodeExecutionKeys.mode)
        .include(NodeExecutionKeys.stepType)
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.nodeId)
        .include(NodeExecutionKeys.parentId)
        .include(NodeExecutionKeys.oldRetry)
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.resolvedParams)
        .include(NodeExecutionKeys.executableResponses);

    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  // TODO optimize this to remove n+1 queries
  private List<RetryStageInfo> fetchStageDetailFromNodeExecution(List<NodeExecution> nodeExecutionList) {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    if (nodeExecutionList.size() == 0) {
      throw new InvalidRequestException("No stage to retry");
    }

    List<NodeExecution> stageNodeExecutions = new ArrayList<>();
    Set<String> strategyNodeExecutionIds = new HashSet<>();
    for (NodeExecution nodeExecution : nodeExecutionList) {
      if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        if (AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getAmbiance())) {
          strategyNodeExecutionIds.add(nodeExecution.getUuid());

          String nextId = nodeExecution.getNextId();
          String parentId = nodeExecution.getParentId();
          RetryStageInfo stageDetail =
              RetryStageInfo.builder()
                  .name(nodeExecution.getName())
                  .identifier(nodeExecution.getIdentifier())
                  .parentId(parentId)
                  .createdAt(nodeExecution.getCreatedAt())
                  .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
                  .nextId(nextId != null
                          ? nextId
                          : getWithFieldsIncluded(parentId, Sets.newHashSet(NodeExecutionKeys.nextId)).getNextId())
                  .build();
          stageDetails.add(stageDetail);
        }
      } else {
        stageNodeExecutions.add(nodeExecution);
      }
    }

    for (NodeExecution nodeExecution : stageNodeExecutions) {
      String nextId = nodeExecution.getNextId();
      String parentId = nodeExecution.getParentId();
      if (strategyNodeExecutionIds.contains(parentId)) {
        continue;
      }
      RetryStageInfo stageDetail =
          RetryStageInfo.builder()
              .name(nodeExecution.getName())
              .identifier(nodeExecution.getIdentifier())
              .parentId(parentId)
              .createdAt(nodeExecution.getCreatedAt())
              .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
              .nextId(nextId != null
                      ? nextId
                      : getWithFieldsIncluded(parentId, Sets.newHashSet(NodeExecutionKeys.nextId)).getNextId())
              .build();
      stageDetails.add(stageDetail);
    }
    return stageDetails;
  }

  @Override
  public List<String> fetchStageFqnFromStageIdentifiers(String planExecutionId, List<String> stageIdentifiers) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY))
                      .addCriteria(where(NodeExecutionKeys.identifier).in(stageIdentifiers));

    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);

    // fetching stageFqn of stage Nodes
    return nodeExecutions.stream()
        .map(nodeExecution -> nodeExecution.getNode().getStageFqn())
        .collect(Collectors.toList());
  }

  @Override
  public List<NodeExecution> fetchStrategyNodeExecutions(String planExecutionId, List<String> stageFQNs) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stepCategory)
                            .is(StepCategory.STRATEGY)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);

    Query query = new Query().addCriteria(criteria);

    return mongoTemplate.find(query, NodeExecution.class);
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

    Map<String, NodeExecution> nodeExecutionMap = getUniqueNodeExecutionForNodes(nodeExecutions);
    // fetching stageFqn of stage Nodes
    Map<String, Node> nodeExecutionIdToPlanNode = new HashMap<>();
    nodeExecutionMap.forEach(
        (uuid, nodeExecution) -> nodeExecutionIdToPlanNode.put(nodeExecution.getUuid(), nodeExecution.getNode()));
    return nodeExecutionIdToPlanNode;
  }

  // We can have multiple nodeExecution corresponding to same node during the retry-failure-strategy. So this method
  // makes sure that only the latest nodeExecution is used for retry when there are multiple nodeExecutions due to
  // retry-failure-strategy. There will be only one such node due to retry-failure-strategy.

  // In case of strategy, returning any nodeExecution for steps is fine. Because during the execution, the children
  // nodeExecutions are decided by the original nodeExecution of strategy node. And there will be only one strategy
  // nodeExecution and that too with oldRetry false.
  private Map<String, NodeExecution> getUniqueNodeExecutionForNodes(List<NodeExecution> nodeExecutions) {
    Map<String, NodeExecution> nodeExecutionMap = new HashMap<>();
    for (NodeExecution nodeExecution : nodeExecutions) {
      if (!nodeExecutionMap.containsKey(nodeExecution.getNode().getUuid()) && !nodeExecution.getOldRetry()) {
        nodeExecutionMap.put(nodeExecution.getNode().getUuid(), nodeExecution);
      }
    }
    return nodeExecutionMap;
  }

  private void validateNodeExecutionProjection(Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(fieldsToInclude)) {
      throw new InvalidRequestException("Projection fields cannot be empty in NodeExecution query.");
    }
  }

  @Override
  public CloseableIterator<NodeExecution> fetchNodeExecutionsForGivenStageFQNs(
      String planExecutionId, List<String> stageFQNs, Collection<String> requiredFields) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);

    Query query = query(criteria);
    if (EmptyPredicate.isNotEmpty(requiredFields)) {
      for (String requiredField : requiredFields) {
        query.fields().include(requiredField);
      }
    }

    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }
}
