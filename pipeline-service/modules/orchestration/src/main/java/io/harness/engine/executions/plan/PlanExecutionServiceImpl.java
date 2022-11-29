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

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
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
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.repositories.PlanExecutionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PlanExecutionServiceImpl implements PlanExecutionService {
  private static int MAX_NODES_BATCH_SIZE = 1000;

  @Inject private PlanExecutionRepository planExecutionRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Getter private final Subject<PlanStatusUpdateObserver> planStatusUpdateSubject = new Subject<>();

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
  public PlanExecution get(String planExecutionId) {
    return planExecutionRepository.findById(planExecutionId)
        .orElseThrow(() -> new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId));
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
    List<Status> statuses = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesOnlyStatus(planExecutionId);
    return OrchestrationUtils.calculateStatusForPlanExecution(statuses, planExecutionId);
  }

  @Override
  public Status calculateStatusExcluding(String planExecutionId, String excludedNodeExecutionId) {
    int currentPage = 0;
    int totalPages = 0;

    List<NodeExecution> nodeExecutions = new LinkedList<>();
    do {
      Page<NodeExecution> paginatedNodeExecutions =
          nodeExecutionService.fetchWithoutRetriesAndStatusIn(planExecutionId, EnumSet.noneOf(Status.class),
              NodeProjectionUtils.withStatus, PageRequest.of(currentPage, MAX_NODES_BATCH_SIZE));
      if (paginatedNodeExecutions == null || paginatedNodeExecutions.getTotalElements() == 0) {
        break;
      }
      totalPages = paginatedNodeExecutions.getTotalPages();
      nodeExecutions.addAll(new LinkedList<>(paginatedNodeExecutions.getContent()));
      currentPage++;
    } while (currentPage < totalPages);

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
  public long countRunningExecutionsForGivenPipeline(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.orgIdentifier)
                            .is(orgId)
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.projectIdentifier)
                            .is(projectId)
                            .and(PlanExecutionKeys.metadata + ".pipelineIdentifier")
                            .is(pipelineIdentifier)
                            .and(PlanExecutionKeys.status)
                            .in(StatusUtils.activeStatuses());
    return mongoTemplate.count(new Query(criteria), PlanExecution.class);
  }

  @Override
  public PlanExecution findNextExecutionToRun(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.orgIdentifier)
                            .is(orgId)
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.projectIdentifier)
                            .is(projectId)
                            .and(PlanExecutionKeys.metadata + ".pipelineIdentifier")
                            .is(pipelineIdentifier)
                            .and(PlanExecutionKeys.status)
                            .is(Status.QUEUED);
    return mongoTemplate.findOne(
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, PlanExecutionKeys.createdAt)), PlanExecution.class);
  }
}
