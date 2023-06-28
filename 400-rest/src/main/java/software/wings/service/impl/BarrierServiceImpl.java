/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.eraro.ErrorCode.BARRIERS_NOT_RUNNING_CONCURRENTLY;
import static io.harness.govern.Switch.unhandled;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.sm.StateType.BARRIER;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;

import static java.time.Duration.ofMinutes;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.distribution.barrier.Barrier;
import io.harness.distribution.barrier.BarrierId;
import io.harness.distribution.barrier.ForceProctor;
import io.harness.distribution.barrier.Forcer;
import io.harness.distribution.barrier.Forcer.State;
import io.harness.distribution.barrier.ForcerId;
import io.harness.exception.WingsException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HIterator;
import io.harness.persistence.HKeyIterator;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.BarrierInstance;
import software.wings.beans.BarrierInstance.BarrierInstanceKeys;
import software.wings.beans.BarrierInstancePipeline;
import software.wings.beans.BarrierInstanceWorkflow;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.BarrierService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.BarrierStatusData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class BarrierServiceImpl extends IteratorPumpAndRedisModeHandler implements BarrierService, ForceProctor {
  private static final String APP_ID = "appId";
  private static final String LEVEL = "level";
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(1);

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowService workflowService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private MorphiaPersistenceProvider<BarrierInstance> persistenceProvider;
  @Inject private AppService appService;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<BarrierInstance, MorphiaFilterExpander<BarrierInstance>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, BarrierService.class,
                MongoPersistenceIterator.<BarrierInstance, MorphiaFilterExpander<BarrierInstance>>builder()
                    .clazz(BarrierInstance.class)
                    .fieldName(BarrierInstanceKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .handler(this::update)
                    .filterExpander(query -> query.filter(BarrierInstanceKeys.state, STANDING.name()))
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<BarrierInstance, MorphiaFilterExpander<BarrierInstance>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       BarrierService.class,
                       MongoPersistenceIterator.<BarrierInstance, MorphiaFilterExpander<BarrierInstance>>builder()
                           .clazz(BarrierInstance.class)
                           .fieldName(BarrierInstanceKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this::update)
                           .filterExpander(query -> query.filter(BarrierInstanceKeys.state, STANDING.name()))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "BarrierInstanceMonitor";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public String save(@Valid BarrierInstance barrier) {
    if (barrier.getAccountId() == null) {
      barrier.setAccountId(appService.getAccountIdByAppId(barrier.getAppId()));
    }
    return wingsPersistence.save(barrier);
  }

  @Override
  public BarrierInstance get(String barrierId) {
    return wingsPersistence.get(BarrierInstance.class, barrierId);
  }

  @Value
  @Builder
  private static class BarrierDetail {
    private String name;
    private BarrierInstanceWorkflow workflow;
  }

  @Override
  public BarrierInstance update(String appId, String barrierId) {
    final BarrierInstance barrierInstance = wingsPersistence.getWithAppId(BarrierInstance.class, appId, barrierId);
    return update(barrierInstance);
  }

  @Override
  public BarrierInstance update(BarrierInstance barrierInstance) {
    if (!STANDING.name().equals(barrierInstance.getState())) {
      return barrierInstance;
    }

    boolean updated = false;

    // First lets try to fill up all the missing data that is available already
    final BarrierInstancePipeline pipeline = barrierInstance.getPipeline();
    // Grouping by on workflowId + pipelineStageId Because If a workflow is added twice manually in pipeline in same
    // parallel section, then they have different pipeline stages IDs, whereas we only want to group together the looped
    // workflows.
    Instant startTime = Instant.now();
    Map<String, List<BarrierInstanceWorkflow>> barrierWorkflows = pipeline.getWorkflows().stream().collect(
        Collectors.groupingBy(BarrierInstanceWorkflow::getUniqueWorkflowKeyInPipeline));

    for (Map.Entry<String, List<BarrierInstanceWorkflow>> entry : barrierWorkflows.entrySet()) {
      List<BarrierInstanceWorkflow> workflows = entry.getValue();
      String pipelineStageId = workflows.get(0).getPipelineStageId();
      if (workflows.stream().anyMatch(t -> t.getPipelineStageExecutionId() == null)) {
        try (HKeyIterator<StateExecutionInstance> keys =
                 new HKeyIterator(wingsPersistence.createQuery(StateExecutionInstance.class)
                                      .filter(StateExecutionInstanceKeys.appId, barrierInstance.getAppId())
                                      .filter(StateExecutionInstanceKeys.executionUuid, pipeline.getExecutionId())
                                      .filter(StateExecutionInstanceKeys.stateType, ENV_STATE.name())
                                      .filter(StateExecutionInstanceKeys.pipelineStageElementId, pipelineStageId)
                                      .fetchKeys())) {
          if (!keys.hasNext()) {
            continue;
          }

          for (BarrierInstanceWorkflow workflow : workflows) {
            if (keys.hasNext()) {
              workflow.setPipelineStageExecutionId(keys.next().getId().toString());
              updated = true;
            } else {
              break;
            }
          }
        }
      }

      String workflowId = workflows.get(0).getUuid();
      if (workflows.stream().anyMatch(t -> t.getWorkflowExecutionId() == null)) {
        try (HKeyIterator<WorkflowExecution> keys = new HKeyIterator(
                 wingsPersistence.createQuery(WorkflowExecution.class)
                     .filter(WorkflowExecutionKeys.accountId, barrierInstance.getAccountId())
                     .filter(WorkflowExecutionKeys.appId, barrierInstance.getAppId())
                     .filter(WorkflowExecutionKeys.pipelineExecutionId, pipeline.getExecutionId())
                     .filter(WorkflowExecutionKeys.executionArgs_pipelinePhaseElementId, pipelineStageId)
                     .filter(WorkflowExecutionKeys.workflowId, workflowId)
                     .limit(NO_LIMIT)
                     .fetchKeys())) {
          if (!keys.hasNext()) {
            continue;
          }
          for (BarrierInstanceWorkflow workflow : workflows) {
            if (keys.hasNext()) {
              workflow.setWorkflowExecutionId(keys.next().getId().toString());
              updated = true;
            } else {
              break;
            }
          }
        }
      }
    }
    for (int index = 0; index < pipeline.getWorkflows().size(); ++index) {
      BarrierInstanceWorkflow workflow = pipeline.getWorkflows().get(index);
      if (workflow.getPhaseExecutionId() == null) {
        try (HKeyIterator<StateExecutionInstance> keys = new HKeyIterator(
                 wingsPersistence.createQuery(StateExecutionInstance.class)
                     .filter(StateExecutionInstanceKeys.appId, barrierInstance.getAppId())
                     .filter(StateExecutionInstanceKeys.executionUuid, workflow.getWorkflowExecutionId())
                     .filter(StateExecutionInstanceKeys.phaseSubWorkflowId, workflow.getPhaseUuid())
                     .field(StateExecutionInstanceKeys.stateType)
                     .in(asList(PHASE.name(), PHASE_STEP.name()))
                     .fetchKeys())) {
          if (!keys.hasNext()) {
            continue;
          }
          workflow.setPhaseExecutionId(keys.next().getId().toString());
          updated = true;
          if (keys.hasNext()) {
            log.error("More than one execution instance for the same phase state");
          }
        }
      }

      if (workflow.getStepExecutionId() == null) {
        try (HKeyIterator<StateExecutionInstance> keys = new HKeyIterator(
                 wingsPersistence.createQuery(StateExecutionInstance.class)
                     .filter(StateExecutionInstanceKeys.appId, barrierInstance.getAppId())
                     .filter(StateExecutionInstanceKeys.executionUuid, workflow.getWorkflowExecutionId())
                     .filter(StateExecutionInstanceKeys.stateType, BARRIER.name())
                     .filter(StateExecutionInstanceKeys.stepId, workflow.getStepUuid())
                     .fetchKeys())) {
          if (!keys.hasNext()) {
            continue;
          }
          workflow.setStepExecutionId(keys.next().getId().toString());
          updated = true;
          if (keys.hasNext()) {
            log.error("More than one execution instance for the same phase state");
          }
        }
      }
    }

    // Update the DB to not need to make the same queries again
    if (updated) {
      log.info("Updating barrier instance {} with name {}", barrierInstance.getUuid(), barrierInstance.getName());
      barrierInstance.setNextIteration(System.currentTimeMillis() + ofMinutes(1).toMillis());
      wingsPersistence.merge(barrierInstance);
    }

    // Lets construct a barrier
    Forcer forcer = buildForcer(barrierInstance);

    Barrier barrier = Barrier.builder().id(new BarrierId(barrierInstance.getUuid())).forcer(forcer).build();
    Barrier.State state = barrier.pushDown(this);
    switch (state) {
      case STANDING:
        return barrierInstance;
      case DOWN:
        waitNotifyEngine.doneWith(barrierInstance.getUuid(), BarrierStatusData.builder().failed(false).build());
        break;
      case ENDURE:
        waitNotifyEngine.doneWith(barrierInstance.getUuid(), BarrierStatusData.builder().failed(true).build());
        break;
      default:
        unhandled(state);
    }

    log.info("Barrier instance {} with name {} reached final state {}", barrierInstance.getUuid(),
        barrierInstance.getName(), state);
    UpdateOperations<BarrierInstance> updateOperations =
        wingsPersistence.createUpdateOperations(BarrierInstance.class).set(BarrierInstanceKeys.state, state.name());
    wingsPersistence.update(barrierInstance, updateOperations);
    return barrierInstance;
  }

  private Forcer buildForcer(BarrierInstance barrierInstance) {
    final BarrierInstancePipeline pipeline = barrierInstance.getPipeline();
    final String appId = barrierInstance.getAppId();
    return Forcer.builder()
        .id(new ForcerId(pipeline.getExecutionId()))
        .metadata(ImmutableMap.of(LEVEL, "pipeline", APP_ID, appId))
        .children(pipeline.getWorkflows()
                      .stream()
                      .map(workflow -> {
                        final Forcer step = Forcer.builder()
                                                .id(new ForcerId(workflow.getStepExecutionId()))
                                                .metadata(ImmutableMap.of(LEVEL, "step", APP_ID, appId))
                                                .build();
                        final Forcer phase = Forcer.builder()
                                                 .id(new ForcerId(workflow.getPhaseExecutionId()))
                                                 .metadata(ImmutableMap.of(LEVEL, "phase", APP_ID, appId))
                                                 .children(asList(step))
                                                 .build();
                        return Forcer.builder()
                            .id(new ForcerId(workflow.getPipelineStageExecutionId()))
                            .metadata(ImmutableMap.of(LEVEL, "workflow", APP_ID, appId))
                            .children(asList(phase))
                            .build();
                      })
                      .collect(toList()))
        .build();
  }

  @Override
  public Forcer.State getForcerState(ForcerId forcerId, Map<String, Object> metadata) {
    ExecutionStatus status = null;

    if ("pipeline".equals(metadata.get(LEVEL))) {
      final WorkflowExecution workflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                                      .filter(WorkflowExecutionKeys.appId, metadata.get(APP_ID))
                                                      .filter(WorkflowExecutionKeys.uuid, forcerId.getValue())
                                                      .project(WorkflowExecutionKeys.status, true)
                                                      .get();
      // The barriers are created before the pipeline is triggered. This creates a window in which barrier background
      // job might trigger update while the workflow is still missing. This will happen also if we failed to trigger
      // after we created the barriers.
      if (workflowExecution == null) {
        return State.APPROACHING;
      }
      status = workflowExecution.getStatus();
    } else {
      status = wingsPersistence.createQuery(StateExecutionInstance.class)
                   .filter(StateExecutionInstanceKeys.appId, metadata.get(APP_ID))
                   .filter(StateExecutionInstanceKeys.uuid, forcerId.getValue())
                   .project(StateExecutionInstanceKeys.status, true)
                   .get()
                   .getStatus();
    }

    if (ExecutionStatus.isPositiveStatus(status)) {
      return State.ARRIVED;
    } else if (ExecutionStatus.isFinalStatus(status)) {
      return State.ABANDONED;
    }

    if ("step".equals(metadata.get(LEVEL))
        && (status == ExecutionStatus.STARTING || status == ExecutionStatus.RUNNING)) {
      return State.ARRIVED;
    }

    return State.APPROACHING;
  }

  @Override
  public String findByStep(String appId, String pipelineStageId, int pipelineStageParallelIndex,
      String workflowExecutionId, String identifier) {
    final String pipelineExecutionId = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.appId, appId)
                                           .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                           .project(WorkflowExecutionKeys.pipelineExecutionId, true)
                                           .get()
                                           .getPipelineExecutionId();

    try (HKeyIterator<BarrierInstance> keys =
             new HKeyIterator(wingsPersistence.createQuery(BarrierInstance.class)
                                  .filter(BarrierInstanceKeys.appId, appId)
                                  .filter(BarrierInstanceKeys.name, identifier)
                                  .filter(BarrierInstanceKeys.pipeline_executionId, pipelineExecutionId)
                                  .filter(BarrierInstanceKeys.pipeline_parallelIndex, pipelineStageParallelIndex)
                                  .filter(BarrierInstanceKeys.pipeline_workflows_pipelineStageId, pipelineStageId)
                                  .fetchKeys())) {
      if (!keys.hasNext()) {
        // We would not be able to find a barrier for if it is noop
        return null;
      }
      String result = keys.next().getId().toString();
      if (keys.hasNext()) {
        log.error("More than one barrier for the same pipeline execution with the same identifier");
      }

      return result;
    }
  }

  @Override
  public List<BarrierInstance> obtainInstances(
      String appId, List<OrchestrationWorkflowInfo> orchestrations, String pipelineExecutionId, int parallelIndex) {
    if (isEmpty(orchestrations)) {
      return null;
    }

    final List<BarrierDetail> barrierDetails = new ArrayList<>();
    boolean isAnyLooped = false;
    for (int i = 0; i < orchestrations.size(); ++i) {
      int concurrentTrack = i;
      final OrchestrationWorkflowInfo workflow = orchestrations.get(concurrentTrack);
      if (!(workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow)) {
        continue;
      }

      isAnyLooped = isAnyLooped || workflow.isLooped();

      final CanaryOrchestrationWorkflow orchestrationWorkflow =
          (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

      for (GraphNode node : orchestrationWorkflow.getPreDeploymentSteps().getSteps()) {
        BarrierDetail barrierDetail =
            barrierDetail(workflow, orchestrationWorkflow.getPreDeploymentSteps().getUuid(), node);
        if (barrierDetail != null) {
          barrierDetails.add(barrierDetail);
        }
      }

      for (GraphNode node : orchestrationWorkflow.getPostDeploymentSteps().getSteps()) {
        BarrierDetail barrierDetail =
            barrierDetail(workflow, orchestrationWorkflow.getPostDeploymentSteps().getUuid(), node);
        if (barrierDetail != null) {
          barrierDetails.add(barrierDetail);
        }
      }

      for (WorkflowPhase phase :
          ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases()) {
        for (PhaseStep section : phase.getPhaseSteps()) {
          // Note that if all the steps from the same section running in
          // parallel is not critical someone still could want to do that
          // session.stepsInParallel()
          for (GraphNode node : section.getSteps()) {
            BarrierDetail barrierDetail = barrierDetail(workflow, phase.getUuid(), node);
            if (barrierDetail != null) {
              barrierDetails.add(barrierDetail);
            }
          }
        }
      }
    }

    final Map<String, List<BarrierDetail>> barriers =
        barrierDetails.stream().collect(Collectors.<BarrierDetail, String>groupingBy(BarrierDetail::getName));

    boolean finalIsAnyLooped = isAnyLooped;
    return barriers.entrySet()
        .stream()
        .map(entry -> {
          final List<BarrierDetail> value = entry.getValue();
          final long count =
              value.stream().map(details -> details.getWorkflow().getPipelineStageId()).distinct().count();

          // All items should be in different concurrentTrack
          if (count < value.size()) {
            if (!finalIsAnyLooped) {
              throw new WingsException(BARRIERS_NOT_RUNNING_CONCURRENTLY);
            }
          }
          return entry;
        })
        .filter(entry -> entry.getValue().size() > 1)
        .map(entry -> {
          BarrierInstance barrier =
              BarrierInstance.builder()
                  .name(entry.getKey())
                  .state(STANDING.name())
                  .pipeline(BarrierInstancePipeline.builder()
                                .executionId(pipelineExecutionId)
                                .parallelIndex(parallelIndex)
                                .workflows(entry.getValue().stream().map(BarrierDetail::getWorkflow).collect(toList()))
                                .build())
                  .build();
          barrier.setAppId(appId);
          return barrier;
        })
        .collect(toList());
  }

  private BarrierDetail barrierDetail(OrchestrationWorkflowInfo workflow, String phaseUuid, GraphNode node) {
    if (!BARRIER.name().equals(node.getType())) {
      return null;
    }

    return BarrierDetail.builder()
        .name((String) node.getProperties().get("identifier"))
        .workflow(BarrierInstanceWorkflow.builder()
                      .uuid(workflow.getWorkflowId())
                      .pipelineStageId(workflow.getPipelineStageId())
                      .phaseUuid(phaseUuid)
                      .stepUuid(node.getId())
                      .build())
        .build();
  }

  @Override
  public void updateAllActiveBarriers(String appId) {
    try (HIterator<BarrierInstance> barrierInstances =
             new HIterator<>(wingsPersistence.createQuery(BarrierInstance.class)
                                 .filter(BarrierInstanceKeys.appId, appId)
                                 .filter(BarrierInstanceKeys.state, STANDING.name())
                                 .fetch())) {
      for (BarrierInstance barrierInstance : barrierInstances) {
        update(barrierInstance);
      }
    }
  }
}
