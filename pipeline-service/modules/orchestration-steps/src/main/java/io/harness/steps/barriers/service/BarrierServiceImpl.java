/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.distribution.barrier.Barrier.State;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.distribution.barrier.Barrier.builder;
import static io.harness.distribution.barrier.Forcer.State.ABANDONED;
import static io.harness.distribution.barrier.Forcer.State.APPROACHING;
import static io.harness.distribution.barrier.Forcer.State.ARRIVED;
import static io.harness.distribution.barrier.Forcer.State.TIMED_OUT;
import static io.harness.govern.Switch.unhandled;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.ASYNC_WAITING;
import static io.harness.pms.contracts.execution.Status.EXPIRED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.barrier.Barrier;
import io.harness.distribution.barrier.BarrierId;
import io.harness.distribution.barrier.ForceProctor;
import io.harness.distribution.barrier.Forcer;
import io.harness.distribution.barrier.ForcerId;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.BarrierNodeRepository;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.PersistenceUtils;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierExecutionInstance.BarrierExecutionInstanceKeys;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionKeys;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.beans.BarrierResponseData.BarrierError;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.steps.barriers.beans.StageDetail.StageDetailKeys;
import io.harness.steps.barriers.service.visitor.BarrierVisitor;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class BarrierServiceImpl implements BarrierService, ForceProctor {
  private static final String LEVEL = "level";
  private static final String PLAN = "plan";
  private static final String STAGE = "stage";
  private static final String STEP_GROUP = "stepGroup";
  private static final String STEP = "step";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String BARRIER_UPSERT_LOCK = "BARRIER_UPSERT_LOCK_";

  @Inject private PersistentLocker persistentLocker;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private BarrierNodeRepository barrierNodeRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private MongoTemplate hMongoTemplate;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private Injector injector;

  public void registerIterators(IteratorConfig config) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("PmsBarrierExecutionInstanceMonitor")
            .poolSize(config.getThreadPoolCount())
            .interval(ofSeconds(config.getTargetIntervalInSeconds()))
            .build(),
        BarrierService.class,
        MongoPersistenceIterator.<BarrierExecutionInstance, SpringFilterExpander>builder()
            .clazz(BarrierExecutionInstance.class)
            .fieldName(BarrierExecutionInstanceKeys.nextIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .handler(this::update)
            .filterExpander(
                query -> query.addCriteria(Criteria.where(BarrierExecutionInstanceKeys.barrierState).in(STANDING)))
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public BarrierExecutionInstance save(BarrierExecutionInstance barrierExecutionInstance) {
    return barrierNodeRepository.save(barrierExecutionInstance);
  }

  @Override
  public List<BarrierExecutionInstance> saveAll(List<BarrierExecutionInstance> barrierExecutionInstances) {
    return (List<BarrierExecutionInstance>) barrierNodeRepository.saveAll(barrierExecutionInstances);
  }

  @Override
  public BarrierExecutionInstance get(String barrierUuid) {
    return barrierNodeRepository.findById(barrierUuid)
        .orElseThrow(() -> new InvalidRequestException("Barrier not found for id: " + barrierUuid));
  }

  @Override
  public BarrierExecutionInstance findByIdentifierAndPlanExecutionId(String identifier, String planExecutionId) {
    return barrierNodeRepository.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);
  }

  @Override
  public List<BarrierExecutionInstance> findManyByPlanExecutionIdAndStrategySetupId(
      String planExecutionId, String strategySetupId) {
    /* This method is used by `BarrierWithinStrategyExpander` for fetching all BarrierExecutionInstances which
       have positions that are children of a given strategy node. */
    return barrierNodeRepository.findManyByPlanExecutionIdAndSetupInfo_StrategySetupIds(
        planExecutionId, strategySetupId);
  }

  @Override
  public boolean existsByPlanExecutionIdAndStrategySetupId(String planExecutionId, String strategySetupId) {
    /* This method is used by `BarrierWithinStrategyExpander` for fetching all BarrierExecutionInstances which
       have positions that are children of a given strategy node. */
    return barrierNodeRepository.existsByPlanExecutionIdAndSetupInfo_StrategySetupIds(planExecutionId, strategySetupId);
  }

  @Override
  public BarrierExecutionInstance findByPlanNodeIdAndPlanExecutionId(String planNodeId, String planExecutionId) {
    Criteria positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                                    .elemMatch(Criteria.where(BarrierPositionKeys.stepSetupId).is(planNodeId));
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);

    Query query = query(planExecutionIdCriteria.andOperator(positionCriteria));
    return mongoTemplate.findOne(query, BarrierExecutionInstance.class);
  }

  @Override
  public BarrierExecutionInstance update(BarrierExecutionInstance barrierExecutionInstance) {
    if (barrierExecutionInstance.getBarrierState() != STANDING) {
      return barrierExecutionInstance;
    }

    Forcer forcer = buildForcer(barrierExecutionInstance);

    Barrier barrier = builder().id(new BarrierId(barrierExecutionInstance.getUuid())).forcer(forcer).build();
    State state = barrier.pushDown(this);

    switch (state) {
      case STANDING:
        return barrierExecutionInstance;
      case DOWN:
        log.info("The barrier [{}] is down", barrierExecutionInstance.getUuid());
        waitNotifyEngine.doneWith(
            barrierExecutionInstance.getUuid(), BarrierResponseData.builder().failed(false).build());
        break;
      case ENDURE:
        waitNotifyEngine.doneWith(barrierExecutionInstance.getUuid(),
            BarrierResponseData.builder()
                .failed(true)
                .barrierError(BarrierError.builder().timedOut(false).errorMessage("The barrier was abandoned").build())
                .build());
        break;
      case TIMED_OUT:
        waitNotifyEngine.doneWith(barrierExecutionInstance.getUuid(),
            BarrierResponseData.builder()
                .failed(true)
                .barrierError(BarrierError.builder().timedOut(true).errorMessage("The barrier timed out").build())
                .build());
        break;
      default:
        unhandled(state);
    }

    return HMongoTemplate.retry(() -> updateState(barrierExecutionInstance.getUuid(), state));
  }

  @Override
  public BarrierExecutionInstance updateState(String uuid, State state) {
    Query query = new Query(Criteria.where(BarrierExecutionInstanceKeys.uuid).is(uuid));
    Update update = new Update().set(BarrierExecutionInstanceKeys.barrierState, state);

    return mongoTemplate.findAndModify(query, update, BarrierExecutionInstance.class);
  }

  @Override
  public List<BarrierExecutionInstance> updatePosition(String planExecutionId, BarrierPositionType positionType,
      String positionSetupId, String positionExecutionId, String stageExecutionId, String stepGroupExecutionId,
      boolean isNewBarrierUpdateFlow) {
    List<BarrierExecutionInstance> barrierExecutionInstances =
        findByPosition(planExecutionId, positionType, positionSetupId);

    Update update = obtainRuntimeIdUpdate(positionType, positionSetupId, positionExecutionId, stageExecutionId,
        stepGroupExecutionId, isNewBarrierUpdateFlow);

    // mongo does not support multiple documents atomic update, let's update one by one
    barrierExecutionInstances.forEach(instance
        -> HMongoTemplate.retry(
            ()
                -> mongoTemplate.findAndModify(
                    query(Criteria.where(BarrierExecutionInstanceKeys.uuid)
                              .is(instance.getUuid())
                              .andOperator(obtainBarrierPositionCriteria(positionType, positionSetupId))),
                    update, BarrierExecutionInstance.class)));
    return barrierExecutionInstances;
  }

  @Override
  public void upsert(BarrierExecutionInstance barrierExecutionInstance) {
    Update update = obtainInstanceUpdate(barrierExecutionInstance);
    hMongoTemplate.upsert(query(Criteria.where(BarrierExecutionInstanceKeys.identifier)
                                    .is(barrierExecutionInstance.getIdentifier())
                                    .and(BarrierExecutionInstanceKeys.planExecutionId)
                                    .is(barrierExecutionInstance.getPlanExecutionId())),
        update, BarrierExecutionInstance.class);
  }

  @Override
  public void updateBarrierPositionInfoListAndStrategyConcurrency(String barrierIdentifier, String planExecutionId,
      List<BarrierPositionInfo.BarrierPosition> barrierPositions, String strategyId, int concurrency) {
    Update update = obtainBarrierPositionInfoAndStrategyConcurrencyUpdate(barrierPositions, strategyId, concurrency);
    hMongoTemplate.findAndModify(query(Criteria.where(BarrierExecutionInstanceKeys.identifier)
                                           .is(barrierIdentifier)
                                           .and(BarrierExecutionInstanceKeys.planExecutionId)
                                           .is(planExecutionId)),
        update, BarrierExecutionInstance.class);
  }

  private Update obtainRuntimeIdUpdate(BarrierPositionType positionType, String positionSetupId,
      String positionExecutionId, String stageExecutionId, String stepGroupExecutionId,
      boolean isNewBarrierUpdateFlow) {
    String position = "position";
    final String positions = BarrierExecutionInstanceKeys.positions + ".$[" + position + "].";
    Update update;
    switch (positionType) {
      case STAGE:
        Criteria stageCriteria =
            Criteria.where(position.concat(".").concat(BarrierPositionKeys.stageSetupId)).is(positionSetupId);
        if (isNewBarrierUpdateFlow) {
          stageCriteria = stageCriteria.and(position.concat(".").concat(BarrierPositionKeys.strategyNodeType)).isNull();
        }
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stageRuntimeId), positionExecutionId)
                     .filterArray(stageCriteria);
        break;
      case STEP_GROUP:
        Criteria stepGroupCriteria =
            Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepGroupSetupId)).is(positionSetupId);
        if (isNewBarrierUpdateFlow) {
          stepGroupCriteria = stepGroupCriteria.and(position.concat(".").concat(BarrierPositionKeys.strategyNodeType))
                                  .in(BarrierPositionType.STAGE, null);
        }
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stepGroupRuntimeId), positionExecutionId)
                     .filterArray(stepGroupCriteria);
        break;
      case STEP:
        Criteria stepCriteria =
            Criteria.where(position.concat(".").concat(BarrierPositionKeys.stepSetupId)).is(positionSetupId);
        if (isNewBarrierUpdateFlow) {
          stepCriteria = stepCriteria.and(position.concat(".").concat(BarrierPositionKeys.stageRuntimeId))
                             .is(stageExecutionId)
                             .and(position.concat(".").concat(BarrierPositionKeys.stepGroupRuntimeId))
                             .is(stepGroupExecutionId);
        }
        update = new Update()
                     .set(positions.concat(BarrierPositionKeys.stepRuntimeId), positionExecutionId)
                     .filterArray(stepCriteria);
        break;
      default:
        throw new InvalidRequestException(String.format("%s position type is not implemented", positionType));
    }

    return update;
  }

  private Update obtainInstanceUpdate(BarrierExecutionInstance barrierExecutionInstance) {
    Update update =
        new Update()
            .set(BarrierExecutionInstanceKeys.name, barrierExecutionInstance.getName())
            .set(BarrierExecutionInstanceKeys.identifier, barrierExecutionInstance.getIdentifier())
            .set(BarrierExecutionInstanceKeys.planExecutionId, barrierExecutionInstance.getPlanExecutionId())
            .set(BarrierExecutionInstanceKeys.barrierState, STANDING)
            .set(BarrierExecutionInstanceKeys.setupInfoName, barrierExecutionInstance.getSetupInfo().getName())
            .set(BarrierExecutionInstanceKeys.setupInfoIdentifier,
                barrierExecutionInstance.getSetupInfo().getIdentifier())
            .addToSet(BarrierExecutionInstanceKeys.stages)
            .each(barrierExecutionInstance.getSetupInfo().getStages())
            .set(BarrierExecutionInstanceKeys.positionInfoPlanExecutionId,
                barrierExecutionInstance.getPositionInfo().getPlanExecutionId())
            .addToSet(BarrierExecutionInstanceKeys.positions)
            .each(barrierExecutionInstance.getPositionInfo().getBarrierPositionList());
    if (barrierExecutionInstance.getSetupInfo().getStrategySetupIds() != null) {
      update.addToSet(BarrierExecutionInstanceKeys.strategySetupIds)
          .each(barrierExecutionInstance.getSetupInfo().getStrategySetupIds());
    }
    return update;
  }

  private Update obtainBarrierPositionInfoAndStrategyConcurrencyUpdate(
      List<BarrierPositionInfo.BarrierPosition> barrierPositions, String strategyId, int concurrency) {
    return new Update()
        .set(BarrierExecutionInstanceKeys.positions, barrierPositions)
        .set(BarrierExecutionInstanceKeys.strategyConcurrencyMap.concat(".").concat(strategyId), concurrency);
  }

  /**
   * Barrier works with 4 forcers : Plan -> Stage -> Step Group -> Barrier Node
   */
  private Forcer buildForcer(BarrierExecutionInstance barrierExecutionInstance) {
    final String planExecutionId = barrierExecutionInstance.getPlanExecutionId();

    return Forcer.builder()
        .id(new ForcerId(barrierExecutionInstance.getPlanExecutionId()))
        .metadata(ImmutableMap.of(LEVEL, PLAN))
        .children(barrierExecutionInstance.getPositionInfo()
                      .getBarrierPositionList()
                      .stream()
                      .map(position -> {
                        final Forcer step =
                            Forcer.builder()
                                .id(new ForcerId(position.getStepRuntimeId()))
                                .metadata(ImmutableMap.of(LEVEL, STEP, PLAN_EXECUTION_ID, planExecutionId))
                                .build();
                        final Forcer stepGroup =
                            Forcer.builder()
                                .id(new ForcerId(position.getStepGroupRuntimeId()))
                                .metadata(ImmutableMap.of(LEVEL, STEP_GROUP, PLAN_EXECUTION_ID, planExecutionId))
                                .children(Collections.singletonList(step))
                                .build();
                        boolean isStepGroupPresent =
                            EmptyPredicate.isNotEmpty(stepGroup.getId().getValue()) && !position.isStepGroupRollback();
                        return Forcer.builder()
                            .id(new ForcerId(position.getStageRuntimeId()))
                            .metadata(ImmutableMap.of(LEVEL, STAGE, PLAN_EXECUTION_ID, planExecutionId))
                            .children(isStepGroupPresent ? Collections.singletonList(stepGroup)
                                                         : Collections.singletonList(step))
                            .build();
                      })
                      .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Forcer.State getForcerState(ForcerId forcerId, Map<String, Object> metadata) {
    Status status;
    if (PLAN.equals(metadata.get(LEVEL))) {
      PlanExecution planExecution;
      try {
        status = planExecutionService.getStatus(forcerId.getValue());
      } catch (InvalidRequestException e) {
        log.warn("Plan Execution was not found. State set to APPROACHING", e);
        return APPROACHING;
      }

      if (StatusUtils.positiveStatuses().contains(status)) {
        return ARRIVED;
      } else if (StatusUtils.brokeStatuses().contains(status) || status == ABORTED) {
        return ABANDONED;
      }
    } else {
      NodeExecution forcerNode =
          nodeExecutionService.getWithFieldsIncluded(forcerId.getValue(), NodeProjectionUtils.withStatus);
      status = forcerNode.getStatus();
    }

    if (StatusUtils.positiveStatuses().contains(status)) {
      return ARRIVED;
    } else if (status == EXPIRED) {
      return TIMED_OUT;
    } else if (StatusUtils.finalStatuses().contains(status)) {
      return ABANDONED;
    }

    if (STEP.equals(metadata.get(LEVEL)) && (status == ASYNC_WAITING)) {
      return ARRIVED;
    }

    return APPROACHING;
  }

  @Override
  public List<BarrierExecutionInstance> findByStageIdentifierAndPlanExecutionIdAnsStateIn(
      String stageIdentifier, String planExecutionId, Set<State> stateSet) {
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);
    Criteria stageIdentifierCriteria = Criteria.where(BarrierExecutionInstanceKeys.stages)
                                           .elemMatch(Criteria.where(StageDetailKeys.identifier).is(stageIdentifier));

    Query query = query(new Criteria().andOperator(planExecutionIdCriteria, stageIdentifierCriteria));

    if (!stateSet.isEmpty()) {
      query.addCriteria(where(BarrierExecutionInstanceKeys.barrierState).in(stateSet));
    }

    return mongoTemplate.find(query, BarrierExecutionInstance.class);
  }

  @VisibleForTesting
  protected List<BarrierExecutionInstance> findByPosition(
      String planExecutionId, BarrierPositionType positionType, String positionSetupId) {
    Criteria planExecutionIdCriteria = Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).is(planExecutionId);

    Query query = query(new Criteria().andOperator(
        planExecutionIdCriteria, obtainBarrierPositionCriteria(positionType, positionSetupId)));

    return mongoTemplate.find(query, BarrierExecutionInstance.class);
  }

  private Criteria obtainBarrierPositionCriteria(BarrierPositionType positionType, String positionSetupId) {
    Criteria positionCriteria;
    switch (positionType) {
      case STAGE:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stageSetupId).is(positionSetupId));
        break;
      case STEP_GROUP:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stepGroupSetupId).is(positionSetupId));
        break;
      case STEP:
        positionCriteria = Criteria.where(BarrierExecutionInstanceKeys.positions)
                               .elemMatch(Criteria.where(BarrierPositionKeys.stepSetupId).is(positionSetupId));
        break;
      default:
        throw new InvalidRequestException(String.format("%s position type is not implemented", positionType));
    }

    return positionCriteria;
  }

  @Override
  public List<BarrierSetupInfo> getBarrierSetupInfoList(String yaml) {
    try {
      YamlNode yamlNode = YamlUtils.extractPipelineField(yaml).getNode();
      BarrierVisitor barrierVisitor = new BarrierVisitor(injector);
      barrierVisitor.walkElementTree(yamlNode);
      return new ArrayList<>(barrierVisitor.getBarrierIdentifierMap().values());
    } catch (IOException e) {
      log.error("Error while extracting yaml");
      throw new InvalidRequestException("Error while extracting yaml");
    } catch (InvalidRequestException e) {
      log.error("Error while processing yaml");
      throw e;
    }
  }

  @Override
  public Map<String, List<BarrierPositionInfo.BarrierPosition>> getBarrierPositionInfoList(String yaml) {
    try {
      YamlNode yamlNode = YamlUtils.extractPipelineField(yaml).getNode();
      BarrierVisitor barrierVisitor = new BarrierVisitor(injector);
      barrierVisitor.walkElementTree(yamlNode);
      return barrierVisitor.getBarrierPositionInfoMap();
    } catch (IOException e) {
      log.error("Error while extracting yaml");
      throw new InvalidRequestException("Error while extracting yaml");
    } catch (InvalidRequestException e) {
      log.error("Error while processing yaml");
      throw e;
    }
  }

  @Override
  public void deleteAllForGivenPlanExecutionId(Set<String> planExecutionIds) {
    // Uses - planExecutionId_barrierState_stagesIdentifier_idx
    Criteria planExecutionIdCriteria =
        Criteria.where(BarrierExecutionInstanceKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(planExecutionIdCriteria);

    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed deleting BarrierExecutionInstance; attempt: {}",
            "[Failed]: Failed deleting BarrierExecutionInstance; attempt: {}");

    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, BarrierExecutionInstance.class));
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  public void upsertBarrierExecutionInstance(BarrierStepNode field, String planExecutionId,
      String parentInfoStrategyNodeType, String stageId, String stepGroupId, String strategyId,
      List<String> allStrategyIds) {
    String setupId = field.getUuid();
    String barrierId = field.getBarrierStepInfo().getIdentifier();
    BarrierPositionType strategyNodeType =
        getStrategyNodeType(parentInfoStrategyNodeType, setupId, barrierId, planExecutionId);
    BarrierExecutionInstance barrierExecutionInstance = getBarrierExecutionInstance(
        field, barrierId, planExecutionId, stageId, stepGroupId, strategyId, strategyNodeType, allStrategyIds);
    try (AcquiredLock<?> ignore = persistentLocker.waitToAcquireLock(
             BARRIER_UPSERT_LOCK + barrierId, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      upsert(barrierExecutionInstance);
    }
  }

  private BarrierPositionType getStrategyNodeType(
      String parentInfoStrategyNodeType, String stepSetupId, String barrierId, String planExecutionId) {
    BarrierPositionType strategyNodeType = null;
    if (isNotEmpty(parentInfoStrategyNodeType)) {
      if (YAMLFieldNameConstants.STAGE.equals(parentInfoStrategyNodeType)) {
        strategyNodeType = BarrierPositionType.STAGE;
      } else if (YAMLFieldNameConstants.STEP_GROUP.equals(parentInfoStrategyNodeType)) {
        strategyNodeType = BarrierPositionType.STEP_GROUP;
      } else {
        log.warn(
            "parentInfoStrategyNodeType [{}] for Barrier Step with setupId: [{}], barrierId: [{}], planExecutionId: [{}], is neither stage or stepGroup."
                + " Setting strategyNodeType to null.",
            parentInfoStrategyNodeType, stepSetupId, barrierId, planExecutionId);
      }
    }
    return strategyNodeType;
  }

  private BarrierExecutionInstance getBarrierExecutionInstance(BarrierStepNode field, String barrierId,
      String planExecutionId, String stageId, String stepGroupId, String strategyId,
      BarrierPositionType strategyNodeType, List<String> allStrategyIds) {
    List<BarrierPositionInfo.BarrierPosition> barrierPositionList =
        List.of(BarrierPositionInfo.BarrierPosition.builder()
                    .stageSetupId(stageId)
                    .stepGroupSetupId(isNotEmpty(stepGroupId) ? stepGroupId : null)
                    .strategySetupId(isNotEmpty(strategyId) ? strategyId : null)
                    .allStrategySetupIds(allStrategyIds)
                    .strategyNodeType(strategyNodeType)
                    .stepSetupId(field.getUuid())
                    .stepGroupRollback(false)
                    .isDummyPosition(isNotEmpty(strategyId))
                    .build());
    return BarrierExecutionInstance.builder()
        .setupInfo(BarrierSetupInfo.builder()
                       .name(field.getBarrierStepInfo().getName())
                       .identifier(barrierId)
                       .stages(Set.of(StageDetail.builder().identifier(stageId).build()))
                       .strategySetupIds(new HashSet<>(allStrategyIds))
                       .build())
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(planExecutionId)
                          .barrierPositionList(barrierPositionList)
                          .build())
        .name(field.getBarrierStepInfo().getName())
        .barrierState(Barrier.State.STANDING)
        .identifier(field.getBarrierStepInfo().getIdentifier())
        .planExecutionId(planExecutionId)
        .build();
  }
}
