/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.ExecutionStatusResponseData;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;
import io.harness.tasks.ResponseData;

import software.wings.api.ForkElement;
import software.wings.beans.ArtifactCollectLoopParams;
import software.wings.beans.ManifestCollectLoopParams;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestInput;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateExecutionInstanceHelper;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ForkState.ForkStateExecutionData;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactCollectLoopState extends State {
  private static final String ARTIFACT_COLLECTION_NAME = "Artifact Collection";
  private static final String MANIFEST_COLLECTION_NAME = "Manifest Collection";
  @Getter @Setter private List<ArtifactInput> artifactInputList;
  @Getter @Setter private List<ManifestInput> manifestInputList;

  @Inject @Transient private StateExecutionInstanceHelper instanceHelper;
  @Inject @Transient private WorkflowExecutionServiceImpl executionService;
  @Inject @Transient private WingsPersistence wingsPersistence;

  public static final class ArtifactCollectLoopStateKeys {
    public static final String artifactInputList = "artifactInputList";
    public static final String manifestInputList = "manifestInputList";
  }

  public ArtifactCollectLoopState(String name) {
    super(name, StateType.ARTIFACT_COLLECT_LOOP_STATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    return executeInternal((ExecutionContextImpl) context);
  }

  private ExecutionResponse executeInternal(ExecutionContextImpl context) {
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    List<String> correlationIds = new ArrayList<>();

    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    ForkStateExecutionData forkStateExecutionData = new ForkStateExecutionData();
    addArtifactCollectionStates(
        stateExecutionInstance, correlationIds, executionResponseBuilder, forkStateExecutionData);
    addManifestCollectionStates(
        stateExecutionInstance, correlationIds, executionResponseBuilder, forkStateExecutionData);

    return executionResponseBuilder.stateExecutionData(forkStateExecutionData)
        .async(true)
        .correlationIds(correlationIds)
        .build();
  }

  private void addArtifactCollectionStates(StateExecutionInstance stateExecutionInstance, List<String> correlationIds,
      ExecutionResponseBuilder executionResponseBuilder, ForkStateExecutionData forkStateExecutionData) {
    if (isEmpty(artifactInputList)) {
      return;
    }

    List<String> forkStateNames = new ArrayList<>();
    forkStateExecutionData.setElements(new ArrayList<>());
    int i = 1;
    for (ArtifactInput artifactInput : artifactInputList) {
      String stateName = ARTIFACT_COLLECTION_NAME + "_" + i;
      forkStateNames.add(stateName);
      ForkElement element =
          ForkElement.builder().stateName(stateName).parentId(stateExecutionInstance.getUuid()).build();
      StateExecutionInstance childStateExecutionInstance = instanceHelper.clone(stateExecutionInstance);
      childStateExecutionInstance.setStateParams(null);

      childStateExecutionInstance.setContextElement(element);
      childStateExecutionInstance.setDisplayName(stateName);
      childStateExecutionInstance.setStateName(stateName);
      childStateExecutionInstance.setParentLoopedState(true);
      childStateExecutionInstance.setLoopedStateParams(getLoopStateParams(artifactInput, stateName));
      childStateExecutionInstance.setStateType(StateType.ARTIFACT_COLLECTION.getName());
      childStateExecutionInstance.setNotifyId(element.getUuid());

      // Check if this causes any issues.
      childStateExecutionInstance.setChildStateMachineId(null);
      executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
      correlationIds.add(element.getUuid());
      forkStateExecutionData.getElements().add(childStateExecutionInstance.getContextElement().getName());
      i++;
    }
    forkStateExecutionData.setForkStateNames(forkStateNames);
  }

  private void addManifestCollectionStates(StateExecutionInstance stateExecutionInstance, List<String> correlationIds,
      ExecutionResponseBuilder executionResponseBuilder, ForkStateExecutionData forkStateExecutionData) {
    if (isEmpty(manifestInputList)) {
      return;
    }

    List<String> forkStateNames = new ArrayList<>();
    forkStateExecutionData.setElements(new ArrayList<>());
    int i = 1;
    for (ManifestInput manifestInput : manifestInputList) {
      String stateName = MANIFEST_COLLECTION_NAME + "_" + i;
      forkStateNames.add(stateName);
      ForkElement element =
          ForkElement.builder().stateName(stateName).parentId(stateExecutionInstance.getUuid()).build();
      StateExecutionInstance childStateExecutionInstance = instanceHelper.clone(stateExecutionInstance);
      childStateExecutionInstance.setStateParams(null);

      childStateExecutionInstance.setContextElement(element);
      childStateExecutionInstance.setDisplayName(stateName);
      childStateExecutionInstance.setStateName(stateName);
      childStateExecutionInstance.setParentLoopedState(true);
      childStateExecutionInstance.setLoopedStateParams(getLoopStateParamsForManifest(manifestInput, stateName));
      childStateExecutionInstance.setStateType(StateType.ARTIFACT_COLLECTION.getName());
      childStateExecutionInstance.setNotifyId(element.getUuid());

      // Check if this causes any issues.
      childStateExecutionInstance.setChildStateMachineId(null);
      executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
      correlationIds.add(element.getUuid());
      forkStateExecutionData.getElements().add(childStateExecutionInstance.getContextElement().getName());
      i++;
    }
    forkStateExecutionData.setForkStateNames(forkStateNames);
  }

  private ArtifactCollectLoopParams getLoopStateParams(ArtifactInput artifactInput, String name) {
    return ArtifactCollectLoopParams.builder()
        .artifactStreamId(artifactInput.getArtifactStreamId())
        .buildNo(artifactInput.getBuildNo())
        .stepName(name)
        .build();
  }

  private ManifestCollectLoopParams getLoopStateParamsForManifest(ManifestInput manifestInput, String name) {
    return ManifestCollectLoopParams.builder()
        .appManifestId(manifestInput.getAppManifestId())
        .buildNo(manifestInput.getBuildNo())
        .stepName(name)
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionStatus executionStatusOfChildren = ExecutionStatus.SUCCESS;
    for (ResponseData status : response.values()) {
      ExecutionStatus executionStatus = ((ExecutionStatusResponseData) status).getExecutionStatus();
      if (executionStatus != ExecutionStatus.SUCCESS) {
        executionStatusOfChildren = executionStatus;
      }
    }

    if (ExecutionStatus.SUCCESS.equals(executionStatusOfChildren)) {
      updateArtifactsInContext((ExecutionContextImpl) context);
      updateManifestsInContext((ExecutionContextImpl) context);
    }
    return ExecutionResponse.builder().executionStatus(executionStatusOfChildren).build();
  }

  public void updateManifestsInContext(ExecutionContextImpl context) {
    String appId = context.getAppId();
    String workflowExecutionId = context.getWorkflowExecutionId();
    List<HelmChart> helmCharts = executionService.getManifestsCollected(appId, workflowExecutionId);
    if (isEmpty(helmCharts)) {
      return;
    }
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    addHelmChartsToWorkflowExecution(appId, workflowExecutionId, helmCharts);
    WorkflowStandardParams workflowStandardParams = context.fetchWorkflowStandardParamsFromContext();
    if (workflowStandardParams != null && workflowStandardParams.getWorkflowElement() != null) {
      String pipelineWorkflowExecutionId = workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
      if (!workflowExecutionId.equals(pipelineWorkflowExecutionId)) {
        addHelmChartsToWorkflowExecution(appId, pipelineWorkflowExecutionId, helmCharts);
      }
    }
    addHelmChartsToStateExecutionInstance(appId, stateExecutionInstance, helmCharts);
    // need to add artifact to parent stateExecutionInstance so that it gets transferred to all the other phases in
    // workflow.
    addHelmChartsToParentStateExecutionInstance(appId, stateExecutionInstance.getParentInstanceId(), helmCharts);
  }

  public void updateArtifactsInContext(ExecutionContextImpl context) {
    String appId = context.getAppId();
    String workflowExecutionId = context.getWorkflowExecutionId();
    List<Artifact> artifacts = executionService.getArtifactsCollected(appId, workflowExecutionId);
    if (isEmpty(artifacts)) {
      return;
    }
    StateExecutionInstance stateExecutionInstance = context.getStateExecutionInstance();
    addArtifactsToWorkflowExecution(appId, workflowExecutionId, artifacts);
    addArtifactsToStateExecutionInstance(appId, stateExecutionInstance, artifacts);
    // need to add artifact to parent stateExecutionInstance so that it gets transferred to all the other phases in
    // workflow.
    addArtifactsToParentStateExecutionInstance(appId, stateExecutionInstance.getParentInstanceId(), artifacts);
  }

  void addArtifactsToParentStateExecutionInstance(
      String appId, String stateExecutionInstanceId, List<Artifact> artifacts) {
    StateExecutionInstance stateExecutionInstance = wingsPersistence.createQuery(StateExecutionInstance.class)
                                                        .filter(StateExecutionInstanceKeys.appId, appId)
                                                        .filter(ID_KEY, stateExecutionInstanceId)
                                                        .project(StateExecutionInstanceKeys.contextElements, true)
                                                        .project(StateExecutionInstanceKeys.uuid, true)
                                                        .get();

    addArtifactsToStateExecutionInstance(appId, stateExecutionInstance, artifacts);
  }

  void addHelmChartsToParentStateExecutionInstance(
      String appId, String stateExecutionInstanceId, List<HelmChart> helmCharts) {
    StateExecutionInstance stateExecutionInstance = wingsPersistence.createQuery(StateExecutionInstance.class)
                                                        .filter(StateExecutionInstanceKeys.appId, appId)
                                                        .filter(ID_KEY, stateExecutionInstanceId)
                                                        .project(StateExecutionInstanceKeys.contextElements, true)
                                                        .project(StateExecutionInstanceKeys.uuid, true)
                                                        .get();

    addHelmChartsToStateExecutionInstance(appId, stateExecutionInstance, helmCharts);
  }

  void addArtifactsToWorkflowExecution(String appId, String workflowExecutionId, List<Artifact> artifacts) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .addToSet(WorkflowExecutionKeys.artifacts, artifacts)
            .addToSet(WorkflowExecutionKeys.executionArgs_artifacts, artifacts);

    wingsPersistence.update(query, updateOps);
  }

  void addHelmChartsToWorkflowExecution(String appId, String workflowExecutionId, List<HelmChart> helmCharts) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class)
            .addToSet(WorkflowExecutionKeys.helmCharts, helmCharts)
            .addToSet(WorkflowExecutionKeys.executionArgs_helmCharts, helmCharts);

    wingsPersistence.update(query, updateOps);
  }

  void addArtifactsToStateExecutionInstance(
      String appId, StateExecutionInstance stateExecutionInstance, List<Artifact> artifacts) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.appId, appId)
                                              .filter(ID_KEY, stateExecutionInstance.getUuid());

    List<ContextElement> contextElements = addArtifactIdsToWorkflowStandardParams(stateExecutionInstance, artifacts);

    UpdateOperations<StateExecutionInstance> updateOps =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class).set("contextElements", contextElements);

    wingsPersistence.update(query, updateOps);
  }

  void addHelmChartsToStateExecutionInstance(
      String appId, StateExecutionInstance stateExecutionInstance, List<HelmChart> helmCharts) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.appId, appId)
                                              .filter(ID_KEY, stateExecutionInstance.getUuid());

    List<ContextElement> contextElements = addHelmChartIdsToWorkflowStandardParams(stateExecutionInstance, helmCharts);

    UpdateOperations<StateExecutionInstance> updateOps =
        wingsPersistence.createUpdateOperations(StateExecutionInstance.class).set("contextElements", contextElements);

    wingsPersistence.update(query, updateOps);
  }

  @VisibleForTesting
  List<ContextElement> addArtifactIdsToWorkflowStandardParams(
      StateExecutionInstance stateExecutionInstance, List<Artifact> artifacts) {
    List<ContextElement> contextElements = stateExecutionInstance.getContextElements();
    WorkflowStandardParams workflowStandardParams =
        (WorkflowStandardParams) contextElements.stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
            .findFirst()
            .orElse(null);

    if (workflowStandardParams == null) {
      throw new InvalidRequestException("Workflow Standard Params can not be null");
    }

    List<String> artifactIds = artifacts.stream().map(Artifact::getUuid).collect(Collectors.toList());

    if (isNotEmpty(workflowStandardParams.getArtifactIds())) {
      List<String> stdParamsArtifactIds = workflowStandardParams.getArtifactIds();
      stdParamsArtifactIds.addAll(artifactIds);
      workflowStandardParams.setArtifactIds(stdParamsArtifactIds);
    } else {
      workflowStandardParams.setArtifactIds(artifactIds);
    }
    return contextElements;
  }

  List<ContextElement> addHelmChartIdsToWorkflowStandardParams(
      StateExecutionInstance stateExecutionInstance, List<HelmChart> helmCharts) {
    List<ContextElement> contextElements = stateExecutionInstance.getContextElements();
    WorkflowStandardParams workflowStandardParams =
        (WorkflowStandardParams) contextElements.stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
            .findFirst()
            .orElse(null);

    if (workflowStandardParams == null) {
      throw new InvalidRequestException("Workflow Standard Params can not be null");
    }

    List<String> helmChartIds = helmCharts.stream().map(HelmChart::getUuid).collect(Collectors.toList());

    if (isNotEmpty(workflowStandardParams.getArtifactIds())) {
      List<String> stdParamsHelmChartIds = workflowStandardParams.getHelmChartIds();
      stdParamsHelmChartIds.addAll(helmChartIds);
      workflowStandardParams.setHelmChartIds(stdParamsHelmChartIds);
    } else {
      workflowStandardParams.setHelmChartIds(helmChartIds);
    }
    return contextElements;
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to handle here.
  }
}
