package io.harness.engine.executions.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.ExecutionMetadataKeys;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.repositories.PlanExecutionRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PlanExecutionServiceImpl implements PlanExecutionService {
  @Inject private PlanExecutionRepository planExecutionRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Inject private NodeExecutionService nodeExecutionService;

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
    EnumSet<Status> allowedStartStatuses = StatusUtils.planAllowedStartSet(status);
    Query query = query(where(PlanExecutionKeys.uuid).is(planExecutionId))
                      .addCriteria(where(PlanExecutionKeys.status).in(allowedStartStatuses));
    Update updateOps = new Update()
                           .set(PlanExecutionKeys.status, status)
                           .set(PlanExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    if (ops != null) {
      ops.accept(updateOps);
    }
    PlanExecution updated = mongoTemplate.findAndModify(
        query, updateOps, new FindAndModifyOptions().upsert(false).returnNew(true), PlanExecution.class);
    if (updated == null) {
      log.warn("Cannot update execution status for the PlanExecution {} with {}", planExecutionId, status);
    } else {
      emitEvent(updated);
    }
    return updated;
  }

  @Override
  public PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status) {
    return updateStatus(planExecutionId, status, null);
  }

  @Override
  public PlanExecution update(@NonNull String planExecutionId, @NonNull Consumer<Update> ops) {
    Query query = query(where(PlanExecutionKeys.uuid).is(planExecutionId));
    Update updateOps = new Update().set(PlanExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    ops.accept(updateOps);
    PlanExecution updated = mongoTemplate.findAndModify(query, updateOps, PlanExecution.class);
    if (updated == null) {
      throw new InvalidRequestException("Node Execution Cannot be updated with provided operations" + planExecutionId);
    }
    return updated;
  }

  @Override
  public PlanExecution get(String planExecutionId) {
    return planExecutionRepository.findById(planExecutionId)
        .orElseThrow(() -> new InvalidRequestException("Plan Execution is null for id: " + planExecutionId));
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
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetries(planExecutionId);
    return OrchestrationUtils.calculateStatus(nodeExecutions, planExecutionId);
  }

  @Override
  public Status calculateStatusExcluding(String planExecutionId, String excludedNodeExecutionId) {
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
        planExecutionId, EnumSet.noneOf(Status.class));
    List<NodeExecution> filtered = nodeExecutions.stream()
                                       .filter(ne -> !ne.getUuid().equals(excludedNodeExecutionId))
                                       .collect(Collectors.toList());
    return OrchestrationUtils.calculateStatus(filtered, planExecutionId);
  }

  public PlanExecution updateCalculatedStatus(String planExecutionId) {
    return updateStatus(planExecutionId, calculateStatus(planExecutionId));
  }

  private void emitEvent(PlanExecution planExecution) {
    Ambiance ambiance = buildFromPlanExecution(planExecution);
    eventEmitter.emitEvent(OrchestrationEvent.newBuilder()
                               .setAmbiance(ambiance)
                               .setEventType(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE)
                               .setStatus(planExecution.getStatus())
                               .build());
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
}
