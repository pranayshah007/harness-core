/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.engine.pms.execution.strategy.plan.PlanExecutionStrategy.ENFORCEMENT_CALLBACK_ID;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.EntityNotFoundException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.ExecutionMetadataKeys;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.repositories.PlanExecutionRepository;
import io.harness.springdata.PersistenceModule;
import io.harness.waiter.StringNotifyResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PlanExecutionServiceImpl implements PlanExecutionService {
  @Inject private PlanExecutionRepository planExecutionRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Getter private final Subject<PlanStatusUpdateObserver> planStatusUpdateSubject = new Subject<>();
  @Getter private final Subject<PlanExecutionDeleteObserver> planExecutionDeleteObserverSubject = new Subject<>();

  @Override
  public PlanExecution save(PlanExecution planExecution) {
    return planExecutionRepository.save(planExecution);
  }

  /**
   * Always use this method while updating statuses. This guarantees we a hopping from correct statuses.
   * As we don't have transactions it is possible that your execution state is manipulated by some other thread and
   * your transition is no longer valid.
   * <p>
   * Like your workflow is aborted but some other thread try to set it to running. Same logic applied to plan execution
   * status as well
   */
  @Override
  public PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops) {
    return updateStatusForceful(planExecutionId, status, ops, false);
  }

  @Override
  public PlanExecution updateStatusForceful(
      @NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops, boolean forced) {
    EnumSet<Status> allowedStartStatuses = StatusUtils.planAllowedStartSet(status);
    return updateStatusForceful(planExecutionId, status, ops, forced, allowedStartStatuses);
  }

  @Override
  public PlanExecution updateStatusForceful(@NonNull String planExecutionId, @NonNull Status status,
      Consumer<Update> ops, boolean forced, EnumSet<Status> allowedStartStatuses) {
    Query query = query(where(PlanExecutionKeys.uuid).is(planExecutionId));
    if (!forced) {
      query.addCriteria(where(PlanExecutionKeys.status).in(allowedStartStatuses));
    }
    Update updateOps = new Update()
                           .set(PlanExecutionKeys.status, status)
                           .set(PlanExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    if (ops != null) {
      ops.accept(updateOps);
    }
    PlanExecution updated = planExecutionRepository.updatePlanExecution(query, updateOps, false);
    if (updated == null) {
      log.warn("Cannot update execution status for the PlanExecution {} with {}", planExecutionId, status);
    } else {
      emitEvent(updated);
    }
    if (StatusUtils.isFinalStatus(status)) {
      waitNotifyEngine.doneWith(
          String.format(ENFORCEMENT_CALLBACK_ID, planExecutionId), StringNotifyResponseData.builder().build());
    }
    return updated;
  }

  @Override
  public PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status) {
    return updateStatus(planExecutionId, status, null);
  }

  @Override
  public PlanExecution markPlanExecutionErrored(String planExecutionId) {
    return updateStatus(planExecutionId, ERRORED, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
  }

  @Override
  public PlanExecution get(String planExecutionId) {
    return planExecutionRepository.findById(planExecutionId)
        .orElseThrow(() -> new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId));
  }

  @Override
  public PlanExecution getWithFieldsIncluded(String planExecutionId, Set<String> fieldsToInclude) {
    Query query = new Query(Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    PlanExecution planExecution = mongoTemplate.findOne(query, PlanExecution.class);
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution;
  }
  @Override
  public PlanExecution getPlanExecutionMetadata(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getPlanExecutionWithProjections(planExecutionId,
        Lists.newArrayList(PlanExecutionKeys.metadata, PlanExecutionKeys.governanceMetadata,
            PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.ambiance));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution;
  }

  @Override
  public ExecutionMetadata getExecutionMetadataFromPlanExecution(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getPlanExecutionWithIncludedProjections(
        planExecutionId, Lists.newArrayList(PlanExecutionKeys.metadata));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution.getMetadata();
  }

  @Override
  public Status getStatus(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getWithProjectionsWithoutUuid(
        planExecutionId, Lists.newArrayList(PlanExecutionKeys.status));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution.getStatus();
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    NodeStatusUpdateHandler nodeStatusUpdateObserver =
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(nodeUpdateInfo);
    if (nodeStatusUpdateObserver != null) {
      nodeStatusUpdateObserver.handleNodeStatusUpdate(nodeUpdateInfo);
    }
  }

  public List<PlanExecution> findAllByPlanExecutionIdIn(List<String> planExecutionIds) {
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    return mongoTemplate.find(query, PlanExecution.class);
  }

  @Override
  public List<PlanExecution> findPrevUnTerminatedPlanExecutionsByExecutionTag(
      PlanExecution planExecution, String executionTag) {
    List<String> resumableStatuses =
        StatusUtils.resumableStatuses().stream().map(status -> status.name()).collect(Collectors.toList());

    Criteria criteria = new Criteria()
                            .and(ExecutionMetadataKeys.tagExecutionKey)
                            .is(executionTag)
                            .and(PlanExecutionKeys.status)
                            .in(resumableStatuses)
                            .and(PlanExecutionKeys.createdAt)
                            .lt(planExecution.getCreatedAt());

    return mongoTemplate.find(new Query(criteria), PlanExecution.class);
  }

  public Status calculateStatus(String planExecutionId) {
    List<Status> statuses = nodeExecutionService.fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId);
    return OrchestrationUtils.calculateStatusForPlanExecution(statuses, planExecutionId);
  }

  @Override
  public Status calculateStatusExcluding(String planExecutionId, String excludedNodeExecutionId) {
    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (CloseableIterator<NodeExecution> iterator = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.withStatus)) {
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }

    List<Status> filtered = nodeExecutions.stream()
                                .filter(ne -> !ne.getUuid().equals(excludedNodeExecutionId))
                                .map(NodeExecution::getStatus)
                                .collect(Collectors.toList());
    return StatusUtils.calculateStatus(filtered, planExecutionId);
  }

  public PlanExecution updateCalculatedStatus(String planExecutionId) {
    return updateStatus(planExecutionId, calculateStatus(planExecutionId));
  }

  private void emitEvent(PlanExecution planExecution) {
    Ambiance ambiance = buildFromPlanExecution(planExecution);
    planStatusUpdateSubject.fireInform(PlanStatusUpdateObserver::onPlanStatusUpdate, ambiance);
  }

  private Ambiance buildFromPlanExecution(PlanExecution planExecution) {
    return Ambiance.newBuilder()
        .setPlanExecutionId(planExecution.getUuid())
        .putAllSetupAbstractions(
            isEmpty(planExecution.getSetupAbstractions()) ? new HashMap<>() : planExecution.getSetupAbstractions())
        .setMetadata(
            planExecution.getMetadata() == null ? ExecutionMetadata.newBuilder().build() : planExecution.getMetadata())
        .build();
  }

  @Override
  public List<PlanExecution> findByStatusWithProjections(Set<Status> statuses, Set<String> fieldNames) {
    Query query = query(where(PlanExecutionKeys.status).in(statuses));
    Field field = query.fields();
    for (String fieldName : fieldNames) {
      field = field.include(fieldName);
    }
    return mongoTemplate.find(query, PlanExecution.class);
  }

  @Override
  public CloseableIterator<PlanExecution> fetchPlanExecutionsByStatusFromAnalytics(
      Set<Status> statuses, Set<String> fieldNames) {
    // Uses status_idx index
    Query query = query(where(PlanExecutionKeys.status).in(statuses));
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    return planExecutionRepository.fetchPlanExecutionsFromAnalytics(query);
  }

  @Override
  public List<PlanExecution> findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
      String accountId, String orgId, String projectId, long fromTS, long toTS) {
    Map<String, String> setupAbstractionSubFields = new HashMap<>();
    setupAbstractionSubFields.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractionSubFields.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractionSubFields.put(SetupAbstractionKeys.projectIdentifier, projectId);
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions)
                            .is(setupAbstractionSubFields)
                            .and(PlanExecutionKeys.lastUpdatedAt)
                            .gte(fromTS)
                            .lte(toTS);

    return mongoTemplate.find(query(criteria), PlanExecution.class);
  }

  @Override
  public long countRunningExecutionsForGivenPipelineInAccount(String accountId, String pipelineIdentifier) {
    // Uses - accountId_status_idx
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.status)
                            .in(StatusUtils.activeStatuses());
    return mongoTemplate.count(new Query(criteria), PlanExecution.class);
  }

  @Override
  public PlanExecution findNextExecutionToRunInAccount(String accountId) {
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.status)
                            .is(Status.QUEUED);
    return mongoTemplate.findOne(
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, PlanExecutionKeys.createdAt)), PlanExecution.class);
  }

  @Override
  public void deleteAllPlanExecutionAndMetadata(Set<String> planExecutionIds) {
    // Uses idx index
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    for (String fieldName : PlanExecutionProjectionConstants.fieldsForPlanExecutionDelete) {
      query.fields().include(fieldName);
    }
    List<PlanExecution> batchPlanExecutions = new LinkedList<>();
    try (CloseableIterator<PlanExecution> iterator = planExecutionRepository.fetchPlanExecutionsFromAnalytics(query)) {
      while (iterator.hasNext()) {
        PlanExecution next = iterator.next();
        batchPlanExecutions.add(next);
        if (batchPlanExecutions.size() >= PersistenceModule.MAX_BATCH_SIZE) {
          deletePlanExecutionMetadataInternal(batchPlanExecutions);
          batchPlanExecutions.clear();
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(batchPlanExecutions)) {
      // at end if any execution metadata is left, delete those as well
      deletePlanExecutionMetadataInternal(batchPlanExecutions);
    }
    deletePlanExecutionsInternal(planExecutionIds);
  }

  /*
  This functions calculates the status of the based on status of all node execution status excluding current node. If
  the status comes out to be a terminal status, we are setting it to Running as currently is running. eg -> we have
  matrix in which few stages have failed. But currently as the  pipeline is running (may be a CI stage), then it should
  be marked to Running
   */
  @Override
  public void calculateAndUpdateRunningStatus(String planNodeId, String nodeExecutionId) {
    Status updateStatusTo = RUNNING;
    Status planStatus = calculateStatusExcluding(planNodeId, nodeExecutionId);
    if (!StatusUtils.isFinalStatus(planStatus)) {
      updateStatusTo = planStatus;
    }
    log.info("Marking PlanExecution %s status to %s", planNodeId, updateStatusTo);
    updateStatus(planNodeId, updateStatusTo);
  }

  private void deletePlanExecutionMetadataInternal(List<PlanExecution> batchPlanExecutions) {
    // Delete planExecutionMetadata example - PlanExecutionMetadata, PipelineExecutionSummaryEntity
    planExecutionDeleteObserverSubject.fireInform(
        PlanExecutionDeleteObserver::onPlanExecutionsDelete, batchPlanExecutions);
  }

  private void deletePlanExecutionsInternal(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      planExecutionRepository.deleteAllByUuidIn(planExecutionIds);
      return true;
    });
  }
}
