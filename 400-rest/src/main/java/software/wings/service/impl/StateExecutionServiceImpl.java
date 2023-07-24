/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HIterator;

import software.wings.api.PhaseElement;
import software.wings.api.PhaseExecutionData;
import software.wings.api.SelectNodeStepExecutionSummary;
import software.wings.beans.ServiceInstance;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiryController;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.PhaseExecutionSummary;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;
import software.wings.sm.StepExecutionSummary;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import org.jetbrains.annotations.Nullable;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@ValidateOnExecution
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class StateExecutionServiceImpl implements StateExecutionService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private SweepingOutputService sweepingOutputService;

  @Override
  public Map<String, StateExecutionInstance> executionStatesMap(String appId, String executionUuid) {
    Map<String, StateExecutionInstance> allInstancesIdMap = new HashMap<>();

    try (HIterator<StateExecutionInstance> stateExecutionInstances =
             new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                 .filter(StateExecutionInstanceKeys.appId, appId)
                                 .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                 .project(StateExecutionInstanceKeys.accountId, true)
                                 .project(StateExecutionInstanceKeys.contextElement, true)
                                 .project(StateExecutionInstanceKeys.contextTransition, true)
                                 .project(StateExecutionInstanceKeys.dedicatedInterruptCount, true)
                                 .project(StateExecutionInstanceKeys.displayName, true)
                                 .project(StateExecutionInstanceKeys.executionType, true)
                                 .project(StateExecutionInstanceKeys.uuid, true)
                                 .project(StateExecutionInstanceKeys.interruptHistory, true)
                                 .project(StateExecutionInstanceKeys.lastUpdatedAt, true)
                                 .project(StateExecutionInstanceKeys.parentInstanceId, true)
                                 .project(StateExecutionInstanceKeys.prevInstanceId, true)
                                 .project(StateExecutionInstanceKeys.stateExecutionDataHistory, true)
                                 .project(StateExecutionInstanceKeys.stateExecutionMap, true)
                                 .project(StateExecutionInstanceKeys.stateName, true)
                                 .project(StateExecutionInstanceKeys.stateType, true)
                                 .project(StateExecutionInstanceKeys.status, true)
                                 .project(StateExecutionInstanceKeys.hasInspection, true)
                                 .project(StateExecutionInstanceKeys.appId, true)
                                 .project(StateExecutionInstanceKeys.selectionLogsTrackingForTasksEnabled, true)
                                 .project(StateExecutionInstanceKeys.delegateTasksDetails, true)
                                 .project(StateExecutionInstanceKeys.delegateTaskId, true)
                                 .fetch())) {
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        stateExecutionInstance.getStateExecutionMap().entrySet().removeIf(
            entry -> !entry.getKey().equals(stateExecutionInstance.getDisplayName()));
        allInstancesIdMap.put(stateExecutionInstance.getUuid(), stateExecutionInstance);
      }
    }
    return allInstancesIdMap;
  }

  @Override
  public int getRollingPhaseCount(String appId, String executionUuid) {
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, executionUuid, WorkflowExecutionKeys.originalExecution);
    WorkflowExecutionInfo originalExecutionInfo = workflowExecution.getOriginalExecution();
    if (originalExecutionInfo == null) {
      throw new InvalidRequestException("No Original Execution found. Can't Rollback Execution : " + executionUuid);
    }
    return Math.toIntExact(wingsPersistence.createQuery(StateExecutionInstance.class)
                               .filter(StateExecutionInstanceKeys.appId, appId)
                               .filter(StateExecutionInstanceKeys.executionUuid, originalExecutionInfo.getExecutionId())
                               .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
                               .count());
  }

  @Override
  public List<String> phaseNames(String appId, String executionUuid) {
    List<String> names = new ArrayList<>();
    try (HIterator<StateExecutionInstance> stateExecutionInstances =
             new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                 .filter(StateExecutionInstanceKeys.appId, appId)
                                 .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                 .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
                                 .project(StateExecutionInstanceKeys.displayName, true)
                                 .fetch())) {
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        names.add(stateExecutionInstance.getDisplayName());
      }
    }
    return names;
  }

  @Override
  public List<StateExecutionData> fetchPhaseExecutionData(
      String appId, String executionUuid, String phaseName, CurrentPhase curentPhase) {
    List<StateExecutionData> executionDataList = new ArrayList<>();
    try (HIterator<StateExecutionInstance> stateExecutionInstances =
             new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                 .filter(StateExecutionInstanceKeys.appId, appId)
                                 .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                 .filter(StateExecutionInstanceKeys.stateType, StateType.PHASE.name())
                                 .order(Sort.ascending(StateExecutionInstanceKeys.createdAt))
                                 .project(StateExecutionInstanceKeys.displayName, true)
                                 .project(StateExecutionInstanceKeys.stateExecutionMap, true)
                                 .project(StateExecutionInstanceKeys.uuid, true)
                                 .fetch())) {
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
        if (CurrentPhase.EXCLUDE == curentPhase && stateExecutionInstance.getDisplayName().equals(phaseName)) {
          return executionDataList;
        }
        executionDataList.add(stateExecutionData);
        if (CurrentPhase.INCLUDE == curentPhase && stateExecutionInstance.getDisplayName().equals(phaseName)) {
          return executionDataList;
        }
      }
    }
    if (phaseName != null) {
      throw new InvalidRequestException("Phase Name [" + phaseName + " is missing from workflow execution]");
    }
    return executionDataList;
  }

  @VisibleForTesting
  List<StateExecutionInstance> fetchPreviousPhasesStateExecutionInstances(
      String appId, String executionUuid, String phaseName, CurrentPhase currentPhase) {
    List<StateExecutionInstance> instanceList = new ArrayList<>();
    try (HIterator<StateExecutionInstance> stateExecutionInstances =
             new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                 .filter(StateExecutionInstanceKeys.appId, appId)
                                 .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                 .filter(StateExecutionInstanceKeys.stateType, StateType.PHASE.name())
                                 .order(Sort.ascending(StateExecutionInstanceKeys.createdAt))
                                 .fetch())) {
      for (StateExecutionInstance stateExecutionInstance : stateExecutionInstances) {
        if (CurrentPhase.EXCLUDE == currentPhase && stateExecutionInstance.getDisplayName().equals(phaseName)) {
          return instanceList;
        }
        instanceList.add(stateExecutionInstance);
        if (CurrentPhase.INCLUDE == currentPhase && stateExecutionInstance.getDisplayName().equals(phaseName)) {
          return instanceList;
        }
      }
    }

    return instanceList;
  }

  @Override
  public void updateStateExecutionData(String appId, String stateExecutionId, StateExecutionData stateExecutionData) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionId);
    if (stateExecutionInstance == null) {
      return;
    }
    Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
    stateExecutionMap.put(stateExecutionInstance.getDisplayName(), stateExecutionData);
    wingsPersistence.save(stateExecutionInstance);
  }

  @Override
  public void updateStateExecutionInstance(@NotNull StateExecutionInstance stateExecutionInstance) {
    wingsPersistence.save(stateExecutionInstance);
  }

  @Override
  public StateExecutionInstance getStateExecutionData(String appId, String stateExecutionId) {
    return wingsPersistence.getWithAppId(StateExecutionInstance.class, appId, stateExecutionId);
  }

  @Override
  public PageResponse<StateExecutionInstance> list(PageRequest<StateExecutionInstance> pageRequest) {
    return wingsPersistence.query(StateExecutionInstance.class, pageRequest);
  }

  @Override
  public List<StateExecutionInstance> listByIdsUsingSecondary(Collection<String> stateExecutionInstanceIds) {
    if (isEmpty(stateExecutionInstanceIds)) {
      return Collections.emptyList();
    }

    return wingsPersistence.createAnalyticsQuery(StateExecutionInstance.class, excludeAuthority)
        .field(StateExecutionInstanceKeys.uuid)
        .in(stateExecutionInstanceIds)
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  @Override
  public List<ServiceInstance> getHostExclusionList(
      StateExecutionInstance stateExecutionInstance, PhaseElement phaseElement, String infraMappingId) {
    List<ServiceInstance> hostExclusionList = new ArrayList<>();

    List<StateExecutionInstance> instanceList = fetchPreviousPhasesStateExecutionInstances(
        stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid(),
        phaseElement == null ? null : phaseElement.getPhaseName(), CurrentPhase.EXCLUDE);

    if (isEmpty(instanceList)) {
      return hostExclusionList;
    }

    processPreviousPhaseExecutionsData(
        stateExecutionInstance, phaseElement, infraMappingId, hostExclusionList, instanceList);
    return hostExclusionList;
  }

  private void processPreviousPhaseExecutionsData(StateExecutionInstance stateExecutionInstance,
      PhaseElement phaseElement, String infraMappingId, List<ServiceInstance> hostExclusionList,
      List<StateExecutionInstance> previousStateExecutionInstances) {
    for (StateExecutionInstance previousStateExecutionInstance : previousStateExecutionInstances) {
      PhaseExecutionData phaseExecutionData = getPhaseExecutionDataSweepingOutput(previousStateExecutionInstance);

      if (doesNotNeedProcessing(stateExecutionInstance, phaseElement, phaseExecutionData)) {
        continue;
      }

      PhaseExecutionSummary phaseExecutionSummary =
          getPhaseExecutionSummarySweepingOutput(previousStateExecutionInstance);
      if (phaseExecutionSummary == null || phaseExecutionSummary.getPhaseStepExecutionSummaryMap() == null) {
        continue;
      }
      for (PhaseStepExecutionSummary phaseStepExecutionSummary :
          phaseExecutionSummary.getPhaseStepExecutionSummaryMap().values()) {
        if (phaseStepExecutionSummary == null || isEmpty(phaseStepExecutionSummary.getStepExecutionSummaryList())) {
          continue;
        }
        for (StepExecutionSummary stepExecutionSummary : phaseStepExecutionSummary.getStepExecutionSummaryList()) {
          if (stepExecutionSummary instanceof SelectNodeStepExecutionSummary) {
            SelectNodeStepExecutionSummary selectNodeStepExecutionSummary =
                (SelectNodeStepExecutionSummary) stepExecutionSummary;
            if (selectNodeStepExecutionSummary.isExcludeSelectedHostsFromFuturePhases()) {
              List<ServiceInstance> serviceInstanceList =
                  ((SelectNodeStepExecutionSummary) stepExecutionSummary).getServiceInstanceList();
              if (serviceInstanceList != null) {
                hostExclusionList.addAll(serviceInstanceList);
              }
            }
          }
        }
      }
    }
  }

  @Override
  public PhaseExecutionSummary fetchPhaseExecutionSummarySweepingOutput(
      @NotNull StateExecutionInstance stateExecutionInstance) {
    return getPhaseExecutionSummarySweepingOutput(stateExecutionInstance);
  }

  @Override
  public PhaseExecutionData fetchPhaseExecutionDataSweepingOutput(StateExecutionInstance stateExecutionInstance) {
    return getPhaseExecutionDataSweepingOutput(stateExecutionInstance);
  }

  @VisibleForTesting
  PhaseExecutionSummary getPhaseExecutionSummarySweepingOutput(@NotNull StateExecutionInstance stateExecutionInstance) {
    return sweepingOutputService.findSweepingOutput(SweepingOutputInquiryController.obtainFromStateExecutionInstance(
        stateExecutionInstance, PhaseExecutionSummary.SWEEPING_OUTPUT_NAME));
  }

  @VisibleForTesting
  PhaseExecutionData getPhaseExecutionDataSweepingOutput(@NotNull StateExecutionInstance stateExecutionInstance) {
    return sweepingOutputService.findSweepingOutput(SweepingOutputInquiryController.obtainFromStateExecutionInstance(
        stateExecutionInstance, PhaseExecutionData.SWEEPING_OUTPUT_NAME));
  }

  @Nullable
  private String getPhaseExecutionId(@NotNull StateExecutionInstance stateExecutionInstance) {
    PhaseElement phaseElement = fetchPhaseElement(stateExecutionInstance);
    return phaseElement == null
        ? null
        : stateExecutionInstance.getExecutionUuid() + phaseElement.getUuid() + phaseElement.getPhaseName();
  }

  @Nullable
  private PhaseElement fetchPhaseElement(@NotNull StateExecutionInstance stateExecutionInstance) {
    PhaseElement phaseElement =
        (PhaseElement) stateExecutionInstance.getContextElements()
            .stream()
            .filter(
                ce -> ContextElementType.PARAM == ce.getElementType() && PhaseElement.PHASE_PARAM.equals(ce.getName()))
            .findFirst()
            .orElse(null);
    if (phaseElement == null) {
      return null;
    }
    return phaseElement;
  }

  private boolean doesNotNeedProcessing(
      StateExecutionInstance stateExecutionInstance, PhaseElement phaseElement, PhaseExecutionData phaseExecutionData) {
    if (stateExecutionInstance.getDisplayName().equals(phaseElement == null ? null : phaseElement.getPhaseName())) {
      return true;
    }
    return phaseElement != null && phaseElement.getInfraDefinitionId() != null
        && !phaseElement.getInfraDefinitionId().equals(phaseExecutionData.getInfraDefinitionId());
  }

  @Override
  public StateExecutionData phaseStateExecutionData(String appId, String executionUuid, String phaseName) {
    StateExecutionInstance stateExecutionInstance = wingsPersistence.createQuery(StateExecutionInstance.class)
                                                        .filter(StateExecutionInstanceKeys.appId, appId)
                                                        .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
                                                        .filter(StateExecutionInstanceKeys.displayName, phaseName)
                                                        .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
                                                        .project(StateExecutionInstanceKeys.displayName, true)
                                                        .project(StateExecutionInstanceKeys.stateExecutionMap, true)
                                                        .get();
    return stateExecutionInstance.fetchStateExecutionData();
  }

  @Override
  public StateMachine obtainStateMachine(StateExecutionInstance stateExecutionInstance) {
    final WorkflowExecution workflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, stateExecutionInstance.getAppId(), stateExecutionInstance.getExecutionUuid());

    return workflowExecutionService.obtainStateMachine(workflowExecution);
  }

  @Override
  public StateExecutionInstance fetchPreviousPhaseStateExecutionInstance(
      String appId, String executionUuid, String currentStateExecutionId) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, currentStateExecutionId);

    if (stateExecutionInstance == null) {
      return null;
    }

    if (stateExecutionInstance.getStateType().equals(PHASE.name())) {
      StateExecutionInstance previousPhaseStateExecutionInstance =
          getStateExecutionInstance(appId, executionUuid, stateExecutionInstance.getPrevInstanceId());
      if (previousPhaseStateExecutionInstance != null
          && previousPhaseStateExecutionInstance.getStateType().equals(PHASE.name())) {
        return previousPhaseStateExecutionInstance;
      }
    } else {
      return fetchPreviousPhaseStateExecutionInstance(
          appId, executionUuid, stateExecutionInstance.getParentInstanceId());
    }
    return null;
  }

  @Override
  public StateExecutionInstance fetchCurrentPhaseStateExecutionInstance(
      String appId, String executionUuid, String currentStateExecutionId) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, currentStateExecutionId);

    if (stateExecutionInstance == null) {
      return null;
    }
    if (stateExecutionInstance.getStateType().equals(PHASE.name())) {
      return stateExecutionInstance;
    } else {
      return fetchCurrentPhaseStateExecutionInstance(
          appId, executionUuid, stateExecutionInstance.getParentInstanceId());
    }
  }

  @Override
  public StateExecutionInstance fetchCurrentPhaseStepStateExecutionInstance(
      String appId, String executionUuid, String currentStateExecutionId) {
    StateExecutionInstance stateExecutionInstance =
        getStateExecutionInstance(appId, executionUuid, currentStateExecutionId);

    if (stateExecutionInstance == null) {
      return null;
    }
    if (stateExecutionInstance.getStateType().equals(PHASE_STEP.name())) {
      return stateExecutionInstance;
    } else {
      return fetchCurrentPhaseStepStateExecutionInstance(
          appId, executionUuid, stateExecutionInstance.getParentInstanceId());
    }
  }

  @Override
  public StateExecutionInstance getStateExecutionInstance(String appId, String executionUuid, String instanceId) {
    return wingsPersistence.createQuery(StateExecutionInstance.class)
        .filter(StateExecutionInstanceKeys.appId, appId)
        .filter(StateExecutionInstanceKeys.executionUuid, executionUuid)
        .filter(StateExecutionInstanceKeys.uuid, instanceId)
        .get();
  }

  @Override
  public void appendDelegateTaskDetails(String instanceId, DelegateTaskDetails delegateTaskDetails) {
    wingsPersistence.update(
        wingsPersistence.createQuery(StateExecutionInstance.class).filter(StateExecutionInstanceKeys.uuid, instanceId),
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class)
            .push(StateExecutionInstanceKeys.delegateTasksDetails, delegateTaskDetails));
  }
}
