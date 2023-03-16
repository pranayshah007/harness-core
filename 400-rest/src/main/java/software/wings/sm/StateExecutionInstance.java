/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.RepairActionCode;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.api.PhaseElement;
import software.wings.beans.LoopParams;
import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * Represents State Machine Instance.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Data
@FieldNameConstants(innerTypeName = "StateExecutionInstanceKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "stateExecutionInstances", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class StateExecutionInstance implements PersistentEntity, AccountDataRetentionEntity, UuidAware, CreatedAtAware,
                                               UpdatedAtAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appIdExecutionIdStatus")
                 .field(StateExecutionInstanceKeys.appId)
                 .field(StateExecutionInstanceKeys.executionUuid)
                 .field(StateExecutionInstanceKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("stateTypes2")
                 .field(StateExecutionInstanceKeys.executionUuid)
                 .field(StateExecutionInstanceKeys.stateType)
                 .field(StateExecutionInstanceKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("parentInstances2")
                 .field(StateExecutionInstanceKeys.executionUuid)
                 .field(StateExecutionInstanceKeys.parentInstanceId)
                 .field(StateExecutionInstanceKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appId_endTs")
                 .field(StateExecutionInstanceKeys.appId)
                 .ascSortField(StateExecutionInstanceKeys.endTs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_stateType")
                 .field(StateExecutionInstanceKeys.accountId)
                 .field(StateExecutionInstanceKeys.status)
                 .field(StateExecutionInstanceKeys.stateType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("appId_createdAt")
                 .field(StateExecutionInstanceKeys.appId)
                 .field(StateExecutionInstanceKeys.createdAt)
                 .build())
        .build();
  }
  @Id private String uuid;
  protected String appId;
  @FdIndex private long createdAt;
  private long lastUpdatedAt;

  private String accountId;
  private String childStateMachineId;
  private String displayName;
  private String stateName;
  private String stateType;
  private ContextElement contextElement;
  private boolean contextTransition;
  private boolean rollback;
  private boolean waitingForInputs;
  // For when the State was waiting for Inputs.
  private RepairActionCode actionOnTimeout;
  private boolean continued;
  private boolean waitingForManualIntervention;
  private ExecutionInterruptType actionAfterManualInterventionTimeout;
  private boolean isRollbackProvisionerAfterPhases;
  private boolean manualInterventionCandidate;

  /**
   * @deprecated {@link software.wings.service.intfc.StateExecutionService#appendDelegateTaskDetails(String,
   *     DelegateTaskDetails)} should be used instead. Check {@link
   *     software.wings.sm.states.ShellScriptState#executeInternal(ExecutionContext, String)} for details. )
   * */
  @Deprecated private String delegateTaskId;

  List<DelegateTaskDetails> delegateTasksDetails;
  private boolean selectionLogsTrackingForTasksEnabled;

  private String rollbackPhaseName;
  private boolean parentLoopedState;
  private LoopParams loopedStateParams;

  private LinkedList<ContextElement> contextElements = new LinkedList<>();
  private Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
  private List<StateExecutionData> stateExecutionDataHistory = new ArrayList<>();

  private Integer dedicatedInterruptCount;
  private List<ExecutionInterruptEffect> interruptHistory = new ArrayList<>();

  private List<ExecutionEventAdvisor> executionEventAdvisors;

  private List<ContextElement> notifyElements;

  private StateMachineExecutionCallback callback;

  private String executionName;

  private WorkflowType executionType;

  private String executionUuid;

  @FdIndex private String parentInstanceId;

  private String subGraphFilterId;

  private String prevInstanceId;

  private String nextInstanceId;

  private String cloneInstanceId;

  private String notifyId;
  private ExecutionStatus status = ExecutionStatus.NEW;

  private Map<String, Object> stateParams;

  private Long startTs;
  private Long endTs;
  private Long expiryTs;
  private Long stateTimeout;

  private boolean retry;
  private int retryCount;

  private boolean hasInspection;

  private String workflowId;
  private String pipelineStageElementId;
  private int pipelineStageParallelIndex;
  private String stageName;
  private String phaseSubWorkflowId;
  private String stepId;

  private OrchestrationWorkflowType orchestrationWorkflowType;
  private Boolean isOnDemandRollback;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  public StateExecutionData fetchStateExecutionData() {
    return stateExecutionMap.get(displayName);
  }

  @Nullable
  public PhaseElement fetchPhaseElement() {
    PhaseElement phaseElement =
        (PhaseElement) this.getContextElements()
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

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String childStateMachineId;
    private String displayName;
    private String stateName;
    private String stateType;
    private ContextElement contextElement;
    private boolean contextTransition;
    private LinkedList<ContextElement> contextElements = new LinkedList<>();
    private Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    private List<StateExecutionData> stateExecutionDataHistory = new ArrayList<>();
    private List<ExecutionEventAdvisor> executionEventAdvisors;
    private List<ContextElement> notifyElements;
    private StateMachineExecutionCallback callback;
    private String executionName;
    private WorkflowType executionType;
    private OrchestrationWorkflowType orchestrationWorkflowType;
    private String executionUuid;
    private String parentInstanceId;
    private String subGraphFilterId;
    private String prevInstanceId;
    private String nextInstanceId;
    private String cloneInstanceId;
    private String notifyId;
    private ExecutionStatus status = ExecutionStatus.NEW;
    private Long startTs;
    private Long endTs;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private Long stateTimeout;
    private String accountId;
    private boolean rollback;

    private Builder() {}

    public static Builder aStateExecutionInstance() {
      return new Builder();
    }

    public Builder childStateMachineId(String childStateMachineId) {
      this.childStateMachineId = childStateMachineId;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder stateName(String stateName) {
      this.stateName = stateName;
      return this;
    }

    public Builder stateType(String stateType) {
      this.stateType = stateType;
      return this;
    }

    public Builder contextElement(ContextElement contextElement) {
      this.contextElement = contextElement;
      return this;
    }

    public Builder contextTransition(boolean contextTransition) {
      this.contextTransition = contextTransition;
      return this;
    }

    public Builder contextElements(LinkedList<ContextElement> contextElements) {
      this.contextElements = contextElements;
      return this;
    }

    public Builder addContextElement(ContextElement contextElement) {
      this.contextElements.add(contextElement);
      return this;
    }

    public Builder addStateExecutionData(String stateName, StateExecutionData stateExecutionData) {
      this.stateExecutionMap.put(stateName, stateExecutionData);
      return this;
    }

    public Builder addStateExecutionData(StateExecutionData stateExecutionData) {
      this.stateExecutionMap.put(displayName, stateExecutionData);
      return this;
    }

    public Builder stateExecutionMap(Map<String, StateExecutionData> stateExecutionMap) {
      this.stateExecutionMap = stateExecutionMap;
      return this;
    }

    public Builder stateExecutionDataHistory(List<StateExecutionData> stateExecutionDataHistory) {
      this.stateExecutionDataHistory = stateExecutionDataHistory;
      return this;
    }

    public Builder executionEventAdvisors(List<ExecutionEventAdvisor> executionEventAdvisors) {
      this.executionEventAdvisors = executionEventAdvisors;
      return this;
    }

    public Builder notifyElements(List<ContextElement> notifyElements) {
      this.notifyElements = notifyElements;
      return this;
    }

    public Builder callback(StateMachineExecutionCallback callback) {
      this.callback = callback;
      return this;
    }

    public Builder executionName(String executionName) {
      this.executionName = executionName;
      return this;
    }

    public Builder executionType(WorkflowType executionType) {
      this.executionType = executionType;
      return this;
    }

    public Builder orchestrationWorkflowType(OrchestrationWorkflowType orchestrationWorkflowType) {
      this.orchestrationWorkflowType = orchestrationWorkflowType;
      return this;
    }

    public Builder executionUuid(String executionUuid) {
      this.executionUuid = executionUuid;
      return this;
    }

    public Builder parentInstanceId(String parentInstanceId) {
      this.parentInstanceId = parentInstanceId;
      return this;
    }

    public Builder subGraphFilterId(String subGraphFilterId) {
      this.subGraphFilterId = subGraphFilterId;
      return this;
    }

    public Builder prevInstanceId(String prevInstanceId) {
      this.prevInstanceId = prevInstanceId;
      return this;
    }

    public Builder stateTimeout(Long stateTimeout) {
      this.stateTimeout = stateTimeout;
      return this;
    }

    public Builder nextInstanceId(String nextInstanceId) {
      this.nextInstanceId = nextInstanceId;
      return this;
    }

    public Builder cloneInstanceId(String cloneInstanceId) {
      this.cloneInstanceId = cloneInstanceId;
      return this;
    }

    public Builder notifyId(String notifyId) {
      this.notifyId = notifyId;
      return this;
    }

    public Builder status(ExecutionStatus status) {
      this.status = status;
      return this;
    }

    public Builder startTs(Long startTs) {
      this.startTs = startTs;
      return this;
    }

    public Builder endTs(Long endTs) {
      this.endTs = endTs;
      return this;
    }

    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder rollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public Builder but() {
      return aStateExecutionInstance()
          .stateName(stateName)
          .displayName(displayName)
          .rollback(rollback)
          .stateType(stateType)
          .contextElement(contextElement)
          .contextTransition(contextTransition)
          .contextElements(contextElements)
          .stateExecutionMap(stateExecutionMap)
          .stateExecutionDataHistory(stateExecutionDataHistory)
          .notifyElements(notifyElements)
          .callback(callback)
          .executionName(executionName)
          .executionType(executionType)
          .orchestrationWorkflowType(orchestrationWorkflowType)
          .executionUuid(executionUuid)
          .parentInstanceId(parentInstanceId)
          .subGraphFilterId(subGraphFilterId)
          .prevInstanceId(prevInstanceId)
          .nextInstanceId(nextInstanceId)
          .cloneInstanceId(cloneInstanceId)
          .notifyId(notifyId)
          .status(status)
          .startTs(startTs)
          .endTs(endTs)
          .uuid(uuid)
          .appId(appId)
          .createdAt(createdAt)
          .lastUpdatedAt(lastUpdatedAt)
          .accountId(accountId);
    }

    public StateExecutionInstance build() {
      StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
      stateExecutionInstance.setChildStateMachineId(childStateMachineId);
      stateExecutionInstance.setDisplayName(displayName);
      stateExecutionInstance.setStateName(stateName);
      stateExecutionInstance.setStateType(stateType);
      stateExecutionInstance.setContextElement(contextElement);
      stateExecutionInstance.setContextTransition(contextTransition);
      stateExecutionInstance.setContextElements(contextElements);
      stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
      stateExecutionInstance.setStateExecutionDataHistory(stateExecutionDataHistory);
      stateExecutionInstance.setExecutionEventAdvisors(executionEventAdvisors);
      stateExecutionInstance.setNotifyElements(notifyElements);
      stateExecutionInstance.setCallback(callback);
      stateExecutionInstance.setExecutionName(executionName);
      stateExecutionInstance.setExecutionType(executionType);
      stateExecutionInstance.setOrchestrationWorkflowType(orchestrationWorkflowType);
      stateExecutionInstance.setExecutionUuid(executionUuid);
      stateExecutionInstance.setParentInstanceId(parentInstanceId);
      stateExecutionInstance.setSubGraphFilterId(subGraphFilterId);
      stateExecutionInstance.setPrevInstanceId(prevInstanceId);
      stateExecutionInstance.setNextInstanceId(nextInstanceId);
      stateExecutionInstance.setCloneInstanceId(cloneInstanceId);
      stateExecutionInstance.setNotifyId(notifyId);
      stateExecutionInstance.setStatus(status);
      stateExecutionInstance.setStartTs(startTs);
      stateExecutionInstance.setEndTs(endTs);
      stateExecutionInstance.setUuid(uuid);
      stateExecutionInstance.setAppId(appId);
      stateExecutionInstance.setCreatedAt(createdAt);
      stateExecutionInstance.setLastUpdatedAt(lastUpdatedAt);
      stateExecutionInstance.setStateTimeout(stateTimeout);
      stateExecutionInstance.setAccountId(accountId);
      stateExecutionInstance.setRollback(rollback);
      return stateExecutionInstance;
    }
  }
}
