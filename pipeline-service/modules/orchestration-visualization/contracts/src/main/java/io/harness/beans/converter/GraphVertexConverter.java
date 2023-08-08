/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.converter;
import io.harness.DelegateInfoHelper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.GraphVertex;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class GraphVertexConverter {
  @Inject DelegateInfoHelper delegateInfoHelper;

  public GraphVertex convertFrom(NodeExecution nodeExecution, List<String> logBaseKeys) {
    List<GraphDelegateSelectionLogParams> graphDelegateSelectionLogParamsList =
        delegateInfoHelper.getDelegateInformationForGivenTask(nodeExecution.getExecutableResponses(),
            nodeExecution.getMode(), AmbianceUtils.getAccountId(nodeExecution.getAmbiance()));

    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    String stepType = level.getStepType().getType();

    return GraphVertex.builder()
        .uuid(nodeExecution.getUuid())
        .ambiance(nodeExecution.getAmbiance())
        .planNodeId(level.getSetupId())
        .identifier(level.getIdentifier())
        .name(nodeExecution.getName())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .initialWaitDuration(nodeExecution.getInitialWaitDuration())
        .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
        .stepType(stepType)
        .status(nodeExecution.getStatus())
        .failureInfo(nodeExecution.getFailureInfo())
        .nodeRunInfo(nodeExecution.getNodeRunInfo())
        .mode(nodeExecution.getMode())
        .executableResponses(CollectionUtils.emptyIfNull(nodeExecution.getExecutableResponses()))
        .interruptHistories(nodeExecution.getInterruptHistories())
        .retryIds(nodeExecution.getRetryIds())
        .skipType(nodeExecution.getSkipGraphType())
        .unitProgresses(nodeExecution.getUnitProgresses())
        .progressData(nodeExecution.getPmsProgressData())
        .graphDelegateSelectionLogParams(graphDelegateSelectionLogParamsList)
        .executionInputConfigured(nodeExecution.getExecutionInputConfigured())
        .logBaseKey(EmptyPredicate.isNotEmpty(logBaseKeys) ? logBaseKeys.get(0) : "")
        .build();
  }

  public GraphVertex convertFrom(NodeExecution nodeExecution, Map<String, PmsOutcome> outcomes,
      NodeExecutionsInfo nodeExecutionsInfo, List<String> logBaseKeys) {
    List<GraphDelegateSelectionLogParams> graphDelegateSelectionLogParamsList =
        delegateInfoHelper.getDelegateInformationForGivenTask(nodeExecution.getExecutableResponses(),
            nodeExecution.getMode(), AmbianceUtils.getAccountId(nodeExecution.getAmbiance()));
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    return GraphVertex.builder()
        .uuid(nodeExecution.getUuid())
        .ambiance(nodeExecution.getAmbiance())
        .planNodeId(level.getSetupId())
        .identifier(level.getIdentifier())
        .name(nodeExecution.getName())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .initialWaitDuration(nodeExecution.getInitialWaitDuration())
        .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
        .stepType(level.getStepType().getType())
        .status(nodeExecution.getStatus())
        .failureInfo(nodeExecution.getFailureInfo())
        .stepParameters(nodeExecutionsInfo != null && nodeExecutionsInfo.getResolvedInputs() != null
                ? nodeExecutionsInfo.getResolvedInputs()
                : nodeExecution.getResolvedStepParameters())
        .nodeRunInfo(nodeExecution.getNodeRunInfo())
        .mode(nodeExecution.getMode())
        .executableResponses(CollectionUtils.emptyIfNull(nodeExecution.getExecutableResponses()))
        .interruptHistories(nodeExecution.getInterruptHistories())
        .retryIds(nodeExecution.getRetryIds())
        .skipType(nodeExecution.getSkipGraphType())
        .outcomeDocuments(outcomes)
        .executionInputConfigured(nodeExecution.getExecutionInputConfigured())
        .unitProgresses(nodeExecution.getUnitProgresses())
        .progressData(nodeExecution.getPmsProgressData())
        .graphDelegateSelectionLogParams(graphDelegateSelectionLogParamsList)
        .logBaseKey(EmptyPredicate.isNotEmpty(logBaseKeys) ? logBaseKeys.get(0) : "")
        .stepDetails(nodeExecutionsInfo == null
                ? new HashMap<>()
                : nodeExecutionsInfo.getNodeExecutionDetailsInfoList().stream().collect(
                    Collectors.toMap(NodeExecutionDetailsInfo::getName, NodeExecutionDetailsInfo::getStepDetails)))
        .build();
  }
}
