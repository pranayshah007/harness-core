/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.resume;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.SweepingOutputServiceImpl;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ResumeStateUtils {
  public static final Integer RESUME_STATE_TIMEOUT_MILLIS = 60 * 1000;

  @Transient @Inject private StateExecutionService stateExecutionService;
  @Transient @Inject private SweepingOutputService sweepingOutputService;
  @Transient @Inject private FeatureFlagService featureFlagService;
  @Transient @Inject private WingsPersistence wingsPersistence;

  public ExecutionResponse prepareExecutionResponse(ExecutionContext context, String prevStateExecutionId) {
    ExecutionResponseBuilder executionResponseBuilder = prepareExecutionResponseBuilder(context, prevStateExecutionId);
    return executionResponseBuilder.build();
  }

  public ExecutionResponseBuilder prepareExecutionResponseBuilder(
      ExecutionContext context, String prevStateExecutionId) {
    StateExecutionInstance stateExecutionInstance =
        stateExecutionService.getStateExecutionData(context.getAppId(), prevStateExecutionId);
    notNullCheck("stateExecutionInstance is null", stateExecutionInstance);
    StateExecutionData stateExecutionData = stateExecutionInstance.fetchStateExecutionData();
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder()
                                                            .executionStatus(stateExecutionInstance.getStatus())
                                                            .errorMessage(stateExecutionData.getErrorMsg())
                                                            .stateExecutionData(stateExecutionData);
    if (isNotEmpty(stateExecutionInstance.getContextElements())) {
      // Copy context elements for build workflow.
      List<ContextElement> contextElements = stateExecutionInstance.getContextElements()
                                                 .stream()
                                                 .filter(el
                                                     -> el.getElementType() == ContextElementType.ARTIFACT
                                                         || el.getElementType() == ContextElementType.ARTIFACT_VARIABLE)
                                                 .collect(Collectors.toList());
      if (isNotEmpty(contextElements)) {
        executionResponseBuilder.contextElements(contextElements);
      }

      if (featureFlagService.isEnabled(
              FeatureName.MERGE_RUNTIME_VARIABLES_IN_RESUME, stateExecutionInstance.getAccountId())) {
        // This is working because when we continue the pipeline it will always have a nextState so
        // stateExecutionInstance will always be cloned from context. Hence we are updating the context element by
        // getting it from context. We have updated the stateExecutionInstance seperately as updating the context
        // element will just update the stateExecutionInstance of the context and not in the db itself.Adding the
        // updated context element to executionResponse will not work because it will just update the
        // stateExecutionInstance of the db and not the context and the stateExecutionInstance for the next state is
        // formed using the stateExecutionInstance of the context.
        // Test comment
        WorkflowStandardParams workflowStandardParams =
            (WorkflowStandardParams) stateExecutionInstance.getContextElements()
                .stream()
                .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
                .findFirst()
                .orElse(null);

        if (workflowStandardParams != null && workflowStandardParams.getWorkflowElement().getVariables() != null) {
          mergeWorkflowElementVariables(context, workflowStandardParams);
        }
      }
    }
    return executionResponseBuilder;
  }

  @VisibleForTesting
  void mergeWorkflowElementVariables(ExecutionContext context, WorkflowStandardParams oldParams) {
    // Test comment
    WorkflowStandardParams currParams = context.getContextElement(ContextElementType.STANDARD);
    if (currParams != null) {
      oldParams.getWorkflowElement().getVariables().forEach((key, value) -> {
        if (currParams.getWorkflowElement().getVariables() != null) {
          currParams.getWorkflowElement().getVariables().put(key, value);
        } else {
          Map<String, Object> variableMap = new HashMap<>();
          variableMap.put(key, value);
          currParams.getWorkflowElement().setVariables(variableMap);
        }
      });
      UpdateOperations<StateExecutionInstance> ops =
          wingsPersistence.createUpdateOperations(StateExecutionInstance.class);
      ops.set(StateExecutionInstanceKeys.contextElements, currParams);
      Query<StateExecutionInstance> query =
          wingsPersistence.createQuery(StateExecutionInstance.class)
              .filter(StateExecutionInstanceKeys.appId, context.getAppId())
              .filter(StateExecutionInstanceKeys.uuid, context.getStateExecutionInstanceId());
      wingsPersistence.update(query, ops);
    }
  }

  public String fetchPipelineExecutionId(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    return workflowStandardParams == null || workflowStandardParams.getWorkflowElement() == null
        ? null
        : workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
  }

  public void copyPipelineStageOutputs(String appId, String fromPipelineExecutionId, String fromStateExecutionId,
      List<String> fromWorkflowExecutionIds, String toPipelineExecutionId, String toStateExecutionId) {
    if (fromPipelineExecutionId.equals(toPipelineExecutionId)) {
      return;
    }

    List<SweepingOutputInstance> instances = new ArrayList<>();
    // Copy outputs from ApprovalState, ApprovalResumeState and EnvResumeState.
    try (
        HIterator<SweepingOutputInstance> instancesHIterator = new HIterator<>(
            sweepingOutputService.prepareApprovalStateOutputsQuery(appId, fromPipelineExecutionId, fromStateExecutionId)
                .fetch(new FindOptions().modifier("$hint", "pipelineStateExecution")))) {
      for (SweepingOutputInstance instance : instancesHIterator) {
        instances.add(instance);
      }
    }

    // Copy outputs from EnvState.
    if (isNotEmpty(fromWorkflowExecutionIds)) {
      for (String fromWorkflowExecutionId : fromWorkflowExecutionIds) {
        try (HIterator<SweepingOutputInstance> instancesHIterator = new HIterator<>(
                 sweepingOutputService
                     .prepareEnvStateOutputsQuery(appId, fromPipelineExecutionId, fromWorkflowExecutionId)
                     .fetch())) {
          for (SweepingOutputInstance instance : instancesHIterator) {
            instances.add(instance);
          }
        }
      }
    }

    if (isEmpty(instances)) {
      return;
    }

    // Remove duplicates.
    Set<String> instanceIds = new HashSet<>();
    List<SweepingOutputInstance> newInstances = new ArrayList<>();
    for (SweepingOutputInstance instance : instances) {
      if (instanceIds.contains(instance.getUuid())) {
        continue;
      }

      instanceIds.add(instance.getUuid());
      newInstances.add(instance);
    }

    for (SweepingOutputInstance instance : newInstances) {
      sweepingOutputService.save(SweepingOutputServiceImpl
                                     .prepareSweepingOutputBuilder(appId, toPipelineExecutionId, toPipelineExecutionId,
                                         null, toStateExecutionId, SweepingOutputInstance.Scope.PIPELINE)
                                     .name(instance.getName())
                                     .output(instance.getOutput())
                                     .value(instance.getValue())
                                     .build());
    }
  }
}
