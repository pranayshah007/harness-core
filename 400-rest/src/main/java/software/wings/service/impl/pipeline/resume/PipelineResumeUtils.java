/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.pipeline.resume;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.isActiveStatus;
import static io.harness.beans.ExecutionStatus.isNegativeStatus;
import static io.harness.beans.ExecutionStatus.resumableStatuses;
import static io.harness.beans.FeatureName.SPG_CG_FIXING_PIPELINE_RESUME;
import static io.harness.beans.FeatureName.SPG_CG_LIST_RESUMED_PIPELINES;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.beans.SearchFilter.Operator.OR;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notEmptyCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.APPROVAL_RESUME;
import static software.wings.sm.StateType.ENV_LOOP_RESUME_STATE;
import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_RESUME_STATE;
import static software.wings.sm.StateType.ENV_STATE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.api.EnvStateExecutionData;
import software.wings.api.SkipStateExecutionData;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.PipelineStageGroupedInfo;
import software.wings.beans.PipelineStageGroupedInfo.PipelineStageGroupedInfoBuilder;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.PipelineService;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.states.EnvLoopResumeState.EnvLoopResumeStateKeys;
import software.wings.sm.states.EnvResumeState.EnvResumeStateKeys;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.ForkState.ForkStateExecutionData;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineResumeUtils {
  private static final String PIPELINE_RESUME_PIPELINE_CHANGED = "You cannot resume a pipeline which has been modified";
  public static final String PIPELINE_INVALID = "You cannot resume pipeline, seems to be invalid";
  public static final String PIPELINE_RESUME_ERROR_INVALID_STATUS = "Pipeline resume is not available for [%s]. "
      + "Resumable states are : [%s]";
  public static final String ERROR_MSG_PIPELINE_STAGE_DOES_NOT_EXISTS = "Pipeline Stage does not exist or has not "
      + "yet started: [%s]";
  public static final String ERR_MSG_PIPELINE_STAGE_MUST_CONTAIN_PIPELINE_STAGE_ELEMENT_ELEMENT =
      "PipelineStage must contain PipelineStageElement element";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private PipelineService pipelineService;
  @Inject private DeploymentAuthHandler deploymentAuthHandler;
  @Inject private FeatureFlagService featureFlagService;

  public Pipeline getPipelineForResume(String appId, int parallelIndexToResume, WorkflowExecution prevWorkflowExecution,
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap) {
    checkPipelineResumeAvailable(prevWorkflowExecution);
    deploymentAuthHandler.authorize(appId, prevWorkflowExecution);
    String pipelineId = prevWorkflowExecution.getWorkflowId();
    ExecutionArgs executionArgs = prevWorkflowExecution.getExecutionArgs();
    Pipeline pipeline = pipelineService.readPipelineResolvedVariablesLoopedInfo(
        appId, pipelineId, executionArgs.getWorkflowVariables(), true);
    if (pipeline == null) {
      throw new InvalidRequestException("Pipeline does not exist");
    }
    if (isEmpty(pipeline.getPipelineStages())) {
      throw new InvalidRequestException("You cannot resume an empty pipeline");
    }

    updatePipelineForResume(pipeline, parallelIndexToResume, prevWorkflowExecution, stateExecutionInstanceMap);
    return pipeline;
  }

  private void updatePipelineForResume(Pipeline pipeline, int parallelIndexToResume,
      WorkflowExecution prevWorkflowExecution, ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap) {
    List<PipelineStageExecution> pipelineStageExecutions =
        prevWorkflowExecution.getPipelineExecution().getPipelineStageExecutions();
    if (isEmpty(pipelineStageExecutions)) {
      // Previous pipeline execution had 0 stages.
      throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
    }

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    for (int i = 0; i < pipelineStages.size(); i++) {
      PipelineStage pipelineStage = pipelineStages.get(i);
      if (pipelineStageExecutions.size() <= i) {
        // Previous pipeline execution did not have enough stages.
        throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
      }
      List<PipelineStageElement> pipelineStageElements = pipelineStage.getPipelineStageElements();
      if (isEmpty(pipelineStageElements)) {
        throw new InvalidRequestException(
            PIPELINE_INVALID + ". Stage { " + pipelineStage.getName() + " } is incomplete/invalid");
      }

      List<PipelineStageExecution> stageExecutions;
      stageExecutions = getPipelineStageExecutions(pipelineStageExecutions, pipelineStage);
      // Check for compatibility.
      checkStageAndStageExecutions(pipelineStage, stageExecutions);

      PipelineStageElement pse = pipelineStageElements.get(0);
      if (pse.getParallelIndex() >= parallelIndexToResume) {
        // Resume stages done
        break;
      }

      if (isNotValidState(pse.getType())) {
        // Return if the state is not EnvState or ApprovalState or loop state
        continue;
      }
      replaceWithResumeStage(pipeline, prevWorkflowExecution, stateExecutionInstanceMap, stageExecutions, pse);
    }
  }

  private void replaceWithResumeStage(Pipeline pipeline, WorkflowExecution prevWorkflowExecution,
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap,
      List<PipelineStageExecution> stageExecutions, PipelineStageElement pse) {
    StateExecutionInstance stateExecutionInstance = stateExecutionInstanceMap.get(pse.getName());
    StateExecutionData stateExecutionData = null;
    if (stateExecutionInstance != null) {
      stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
    }

    // we don't need to populate data for skipped states
    if (featureFlagService.isEnabled(SPG_CG_FIXING_PIPELINE_RESUME, pipeline.getAccountId())) {
      if (stateExecutionData instanceof SkipStateExecutionData) {
        return;
      }
    }

    Map<String, Object> properties = new HashMap<>();
    properties.put(EnvResumeStateKeys.prevStateExecutionId, stateExecutionInstance.getUuid());
    properties.put(EnvResumeStateKeys.prevPipelineExecutionId, prevWorkflowExecution.getUuid());

    StateType type = StateType.valueOf(pse.getType());
    switch (type) {
      case ENV_STATE:
        setResumePropertiesEnvState(stageExecutions, pse, properties);
        break;
      case APPROVAL:
        pse.setType(APPROVAL_RESUME.name());
        break;
      case ENV_LOOP_STATE:
        setResumePropertiesEnvLoopState(
            pipeline, stateExecutionInstanceMap, pse, (ForkStateExecutionData) stateExecutionData, properties);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unknown state type: " + type.name() + " found in pipeline: " + pipeline.getUuid());
    }
    pse.setProperties(properties);
  }

  private void setResumePropertiesEnvLoopState(Pipeline pipeline,
      ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap, PipelineStageElement pse,
      ForkStateExecutionData forkStateExecutionData, Map<String, Object> properties) {
    pse.setType(ENV_LOOP_RESUME_STATE.name());
    Map<String, String> prevWorkflowExecutionIdStateInstanceId = new HashMap<>();
    if (isNotEmpty(forkStateExecutionData.getForkStateNames())) {
      for (String element : forkStateExecutionData.getForkStateNames()) {
        StateExecutionInstance executionInstanceLooped = stateExecutionInstanceMap.get(element);
        if (executionInstanceLooped == null) {
          continue;
        }
        String executionInstanceLoppedId = executionInstanceLooped.getUuid();
        StateExecutionData stateExecutionDataLooped = executionInstanceLooped.fetchStateExecutionData();
        if (stateExecutionDataLooped instanceof EnvStateExecutionData) {
          EnvStateExecutionData envStateExecutionDataLooped = (EnvStateExecutionData) stateExecutionDataLooped;
          String workflowExecutionId = envStateExecutionDataLooped.getWorkflowExecutionId();
          prevWorkflowExecutionIdStateInstanceId.put(workflowExecutionId, executionInstanceLoppedId);
        }
      }
      properties.put(
          EnvLoopResumeStateKeys.workflowExecutionIdWithStateExecutionIds, prevWorkflowExecutionIdStateInstanceId);
    }
  }

  private void setResumePropertiesEnvState(
      List<PipelineStageExecution> stageExecutions, PipelineStageElement pse, Map<String, Object> properties) {
    pse.setType(ENV_RESUME_STATE.name());
    List<WorkflowExecution> workflowExecutions = stageExecutions.stream()
                                                     .filter(t -> isNotEmpty(t.getWorkflowExecutions()))
                                                     .map(t -> t.getWorkflowExecutions().get(0))
                                                     .collect(Collectors.toList());
    List<String> workflowExecutionIds;
    if (isEmpty(workflowExecutions)) {
      // This might happen in case a pipeline stage is skipped.
      workflowExecutionIds = new ArrayList<>();
    } else {
      workflowExecutionIds = workflowExecutions.stream().map(WorkflowExecution::getUuid).collect(Collectors.toList());
    }
    properties.put(EnvResumeStateKeys.prevWorkflowExecutionIds, workflowExecutionIds);
  }

  private boolean isNotValidState(String type) {
    return !ENV_STATE.name().equals(type) && !APPROVAL.name().equals(type) && !ENV_LOOP_STATE.name().equals(type);
  }

  @VisibleForTesting
  List<PipelineStageExecution> getPipelineStageExecutions(
      List<PipelineStageExecution> pipelineStageExecutions, PipelineStage pipelineStage) {
    List<PipelineStageExecution> stageExecutions;
    stageExecutions = pipelineStageExecutions.stream()
                          .filter(t
                              -> StringUtils.equals(t.getPipelineStageElementId(),
                                  pipelineStage.getPipelineStageElements().get(0).getUuid()))
                          .collect(Collectors.toList());
    return stageExecutions;
  }

  /**
   * Maps stageName to parallelIndex starting from 1.
   * @param stageName
   * @param pipeline
   * @return
   */
  public int getParallelIndexFromPipelineStageName(String stageName, Pipeline pipeline) {
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      if (pipelineStage.getName().matches(stageName)) {
        notEmptyCheck(ERR_MSG_PIPELINE_STAGE_MUST_CONTAIN_PIPELINE_STAGE_ELEMENT_ELEMENT,
            pipelineStage.getPipelineStageElements());
        int groupIndex = pipelineStage.getPipelineStageElements().get(0).getParallelIndex();
        log.info("Translated stage name: {}, to parallel Index: {} for Pipeline Execution: {}", stageName, groupIndex);
        return groupIndex;
      }
    }
    throw new InvalidRequestException(String.format(ERROR_MSG_PIPELINE_STAGE_DOES_NOT_EXISTS, stageName));
  }

  public Pipeline getPipelineFromWorkflowExecution(WorkflowExecution workflowExecution, String appId) {
    if (workflowExecution.getPipelineExecution() != null
        && workflowExecution.getPipelineExecution().getPipeline() != null) {
      return workflowExecution.getPipelineExecution().getPipeline();
    } else {
      if (workflowExecution.getPipelineSummary() != null
          && !isEmpty(workflowExecution.getPipelineSummary().getPipelineId())) {
        String pipelineId = workflowExecution.getPipelineSummary().getPipelineId();
        return pipelineService.getPipeline(appId, pipelineId);
      } else {
        throw new InvalidRequestException(String.format(PIPELINE_INVALID));
      }
    }
  }

  public void updatePipelineExecutionsAfterResume(
      WorkflowExecution currWorkflowExecution, WorkflowExecution prevWorkflowExecution) {
    String pipelineResumeId = prevWorkflowExecution.getPipelineResumeId();
    if (isEmpty(pipelineResumeId)) {
      pipelineResumeId = prevWorkflowExecution.getUuid();
    }

    // NOTE: The below operations should ideally be atomic but we don't have multi-document transactions yet.
    UpdateOperations<WorkflowExecution> currOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    currOps.set(WorkflowExecutionKeys.pipelineResumeId, pipelineResumeId);
    currOps.set(WorkflowExecutionKeys.latestPipelineResume, Boolean.TRUE);
    currOps.set(WorkflowExecutionKeys.cdPageCandidate, Boolean.TRUE);
    wingsPersistence.update(wingsPersistence.createQuery(WorkflowExecution.class)
                                .filter(WorkflowExecutionKeys.appId, currWorkflowExecution.getAppId())
                                .filter(WorkflowExecutionKeys.uuid, currWorkflowExecution.getUuid()),
        currOps);
    currWorkflowExecution.setPipelineResumeId(pipelineResumeId);
    currWorkflowExecution.setLatestPipelineResume(true);
    currWorkflowExecution.setCdPageCandidate(true);

    boolean resumedPipelines =
        featureFlagService.isEnabled(SPG_CG_LIST_RESUMED_PIPELINES, prevWorkflowExecution.getAccountId());

    UpdateOperations<WorkflowExecution> prevOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    prevOps.set(WorkflowExecutionKeys.pipelineResumeId, pipelineResumeId);
    prevOps.set(WorkflowExecutionKeys.latestPipelineResume, Boolean.FALSE);
    prevOps.set(WorkflowExecutionKeys.cdPageCandidate, resumedPipelines);
    wingsPersistence.update(wingsPersistence.createQuery(WorkflowExecution.class)
                                .filter(WorkflowExecutionKeys.appId, prevWorkflowExecution.getAppId())
                                .filter(WorkflowExecutionKeys.uuid, prevWorkflowExecution.getUuid()),
        prevOps);
    prevWorkflowExecution.setPipelineResumeId(pipelineResumeId);
    prevWorkflowExecution.setLatestPipelineResume(false);
    prevWorkflowExecution.setCdPageCandidate(resumedPipelines);
  }

  public List<PipelineStageGroupedInfo> getResumeStages(String appId, WorkflowExecution prevWorkflowExecution) {
    checkPipelineResumeAvailable(prevWorkflowExecution);
    String pipelineId = prevWorkflowExecution.getWorkflowId();
    ExecutionArgs executionArgs = prevWorkflowExecution.getExecutionArgs();
    Pipeline pipeline = pipelineService.readPipelineWithResolvedVariables(
        appId, pipelineId, executionArgs.getWorkflowVariables(), true);
    if (pipeline == null) {
      throw new InvalidRequestException("Pipeline does not exist");
    }

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (isEmpty(pipelineStages)) {
      throw new InvalidRequestException("You cannot resume an empty pipeline");
    }

    List<PipelineStageExecution> pipelineStageExecutions =
        prevWorkflowExecution.getPipelineExecution().getPipelineStageExecutions();
    if (isEmpty(pipelineStageExecutions)) {
      // Previous pipeline execution had 0 stages.
      throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
    }

    boolean foundFailedStage = false;
    List<PipelineStageGroupedInfoBuilder> groupedInfoBuilders = new ArrayList<>();
    for (int i = 0; i < pipelineStages.size(); i++) {
      if (pipelineStageExecutions.size() <= i) {
        // Previous pipeline execution did not have enough stages.
        throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
      }

      PipelineStage pipelineStage = pipelineStages.get(i);
      if (foundFailedStage && !pipelineStage.isParallel()) {
        // We already found a failed stage and the new stage is not parallel with previous ones. So we stop our
        // iteration.
        break;
      }

      List<PipelineStageExecution> stageExecutions = getPipelineStageExecutions(pipelineStageExecutions, pipelineStage);
      checkStageAndStageExecutions(pipelineStage, stageExecutions);

      List<String> newPipelineStageElementNames = pipelineStage.getPipelineStageElements() == null
          ? Collections.emptyList()
          : pipelineStage.getPipelineStageElements()
                .stream()
                .map(pse -> {
                  if (APPROVAL.name().equals(pse.getType())) {
                    return "Approval";
                  }
                  return pse.getName();
                })
                .collect(Collectors.toList());
      if (pipelineStage.isParallel() && isNotEmpty(groupedInfoBuilders)) {
        // The stage is parallel to the previous one. Just append the new element names to the last group info.
        groupedInfoBuilders.get(groupedInfoBuilders.size() - 1).pipelineStageElementNames(newPipelineStageElementNames);
      } else {
        // The stage is not parallel the to previous one. We have to construct a new group info with the correct name
        // and parallel index.
        PipelineStageGroupedInfoBuilder builder = PipelineStageGroupedInfo.builder()
                                                      .name(pipelineStage.getName())
                                                      .pipelineStageElementNames(newPipelineStageElementNames);
        if (isNotEmpty(pipelineStage.getPipelineStageElements())) {
          builder.parallelIndex(pipelineStage.getPipelineStageElements().get(0).getParallelIndex());
        }
        groupedInfoBuilders.add(builder);
      }

      boolean isNegativeStatus = stageExecutions.stream().anyMatch(t -> isNegativeStatus(t.getStatus()));
      if (isNegativeStatus) {
        // Found a non-successful stage in a FAILED pipeline. We will now continue only till we find a stages which are
        // parallel with the current one.
        foundFailedStage = true;
      }
    }

    return groupedInfoBuilders.stream().map(PipelineStageGroupedInfoBuilder::build).collect(Collectors.toList());
  }

  public List<WorkflowExecution> getResumeHistory(String appId, WorkflowExecution prevWorkflowExecution) {
    checkPipelineResumeHistoryAvailable(prevWorkflowExecution);
    String pipelineResumeId = prevWorkflowExecution.getPipelineResumeId();
    if (isEmpty(pipelineResumeId)) {
      return new ArrayList<>();
    }

    List<WorkflowExecution> workflowExecutions = wingsPersistence.createQuery(WorkflowExecution.class)
                                                     .filter(WorkflowExecutionKeys.appId, appId)
                                                     .filter(WorkflowExecutionKeys.pipelineResumeId, pipelineResumeId)
                                                     .project(WorkflowExecutionKeys.stateMachine, false)
                                                     .asList();
    if (isEmpty(workflowExecutions)) {
      return workflowExecutions;
    }

    workflowExecutions.sort(Comparator.comparingLong(WorkflowExecution::getCreatedAt));
    return workflowExecutions;
  }

  @VisibleForTesting
  void checkPipelineResumeAvailable(WorkflowExecution prevWorkflowExecution) {
    if (prevWorkflowExecution.getWorkflowType() != PIPELINE) {
      throw new InvalidRequestException(
          format("Pipeline resume not available for workflow executions: %s", prevWorkflowExecution.getUuid()));
    }
    if (!ExecutionStatus.resumableStatuses.contains(prevWorkflowExecution.getStatus())) {
      throw new InvalidRequestException(format(PIPELINE_RESUME_ERROR_INVALID_STATUS, prevWorkflowExecution.getUuid(),
          ExecutionStatus.resumableStatuses.toString()));
    }
    if (isNotEmpty(prevWorkflowExecution.getPipelineResumeId()) && !prevWorkflowExecution.isLatestPipelineResume()) {
      throw new InvalidRequestException(
          format("Pipeline resume is only available on the latest iteration of a resumed execution: %s",
              prevWorkflowExecution.getUuid()));
    }

    List<PipelineStageExecution> pipelineStageExecutions =
        prevWorkflowExecution.getPipelineExecution().getPipelineStageExecutions();
    if (isEmpty(pipelineStageExecutions)) {
      throw new InvalidRequestException("You cannot resume an empty pipeline");
    } else {
      boolean notInActiveStatus = false;
      for (PipelineStageExecution pipelineStageExecution : pipelineStageExecutions) {
        if (!isActiveStatus(pipelineStageExecution.getStatus())) {
          notInActiveStatus = true;
          break;
        }
      }
      if (!notInActiveStatus) {
        throw new InvalidRequestException(
            "Pipeline resume is not available for a pipeline that failed during artifact collection");
      }
    }
  }

  @VisibleForTesting
  void checkPipelineResumeHistoryAvailable(WorkflowExecution prevWorkflowExecution) {
    if (prevWorkflowExecution.getWorkflowType() != PIPELINE) {
      throw new InvalidRequestException(
          format("Pipeline resume not available for workflow executions: %s", prevWorkflowExecution.getUuid()));
    }
  }

  /***
   * checkStageAndStageExecution checks if the stage and stageExecution contain the same workflows/executions with the
   * same workflow ids. If a new workflow is introduced or an old one removed from a pipeline, the stage executions will
   * differ.
   */
  @VisibleForTesting
  void checkStageAndStageExecutions(PipelineStage stage, List<PipelineStageExecution> stageExecutions) {
    if (isEmpty(stageExecutions)) {
      throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
    }

    if (stageExecutions.size() > 1 && stageExecutions.stream().anyMatch(t -> !t.isLooped())) {
      throw new InvalidRequestException(PIPELINE_RESUME_PIPELINE_CHANGED);
    }

    if (stageExecutions.stream().anyMatch(stageExecution -> resumableStatuses.contains(stageExecution.getStatus()))) {
      // PipelineExecutionStage being in one of the resumableStatuses (FAILED/ABORTED/REJECTED/EXPIRED/ERROR) means
      // that this PipelineStage belongs to the last parallel Group (Because Pipelines are executed sequentially, only
      // when a group of parallel PipelineStages are successful execution moves to next).

      // Since this PipelineStage is both in final parallel group and failed. Allowing it to resume doesn't have any
      // danger.
      return;
    }

    notNullCheck("Pipeline stage " + stage.getName() + "seems to be invalid", stage.getPipelineStageElements());

    boolean anySkipped = stageExecutions.stream().anyMatch(t -> t.getStatus() == SKIPPED);
    if (anySkipped) {
      // Don't check for skipped stage executions as they have no workflow executions attached to them. In case of
      // looped, either all should be skipped or none.
      return;
    }
    PipelineStageElement stageElement = stage.getPipelineStageElements().get(0);
    List<WorkflowExecution> workflowExecutions = stageExecutions.stream()
                                                     .filter(t -> isNotEmpty(t.getWorkflowExecutions()))
                                                     .map(t -> t.getWorkflowExecutions().get(0))
                                                     .collect(Collectors.toList());

    String newWorkflowId = isEmpty(stageElement.getProperties())
        ? null
        : (String) stageElement.getProperties().get(EnvStateKeys.workflowId);

    Set<String> oldWorkflowIdSet = workflowExecutions.stream()
                                       .filter(execution -> execution != null && execution.getWorkflowId() != null)
                                       .map(WorkflowExecution::getWorkflowId)
                                       .collect(Collectors.toSet());

    if (isEmpty(oldWorkflowIdSet)) {
      if (isNotEmpty(newWorkflowId)) {
        throw new InvalidRequestException(format(
            "You cannot resume a pipeline which has been modified. Pipeline stage [%s] modified and a workflow has been added.",
            stage.getName()));
      } else {
        return;
      }
    }

    if (isEmpty(newWorkflowId)) {
      if (isNotEmpty(oldWorkflowIdSet)) {
        String workflowName = workflowExecutions.get(0).getName();
        throw new InvalidRequestException(format(
            "You cannot resume a pipeline which has been modified. Pipeline stage [%s] modified and a workflow [%s] has been removed.",
            stage.getName(), isEmpty(workflowName) ? oldWorkflowIdSet.iterator().next() : workflowName));
      } else {
        return;
      }
    }

    if (!newWorkflowId.equals(oldWorkflowIdSet.iterator().next())) {
      String workflowName = workflowExecutions.get(0).getName();
      throw new InvalidRequestException(format(
          "You cannot resume a pipeline which has been modified. Pipeline stage [%s] modified and a workflow [%s] has been changed.",
          stage.getName(), workflowName));
    }
  }

  public static void addLatestPipelineResumeFilter(PageRequest<WorkflowExecution> pageRequest) {
    pageRequest.addFilter("", OR,
        SearchFilter.builder().fieldName(WorkflowExecutionKeys.pipelineResumeId).op(NOT_EXISTS).build(),
        SearchFilter.builder()
            .fieldName(WorkflowExecutionKeys.latestPipelineResume)
            .op(EQ)
            .fieldValues(new Object[] {Boolean.TRUE})
            .build());
  }
}
