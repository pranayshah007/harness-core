/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;
import io.harness.DelegateInfoHelper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateInfo;
import io.harness.beans.ExecutionNode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NodeExecutionToExecutioNodeMapper {
  @Inject PmsOutcomeService pmsOutcomeService;
  @Inject private DelegateInfoHelper delegateInfoHelper;

  public ExecutionNode mapNodeExecutionToExecutionNode(NodeExecution nodeExecution) {
    Map<String, PmsOutcome> outcomes =
        PmsOutcomeMapper.convertJsonToOrchestrationMap(pmsOutcomeService.findAllOutcomesMapByRuntimeId(
            nodeExecution.getAmbiance().getPlanExecutionId(), nodeExecution.getUuid()));

    List<GraphDelegateSelectionLogParams> graphDelegateSelectionLogParamsList =
        delegateInfoHelper.getDelegateInformationForGivenTask(nodeExecution.getExecutableResponses(),
            nodeExecution.getMode(), AmbianceUtils.getAccountId(nodeExecution.getAmbiance()));

    List<DelegateInfo> delegateInfoList = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(graphDelegateSelectionLogParamsList)) {
      for (GraphDelegateSelectionLogParams graphDelegateSelectionLogParams : graphDelegateSelectionLogParamsList) {
        delegateInfoList.add(ExecutionGraphMapper.getDelegateInfoForUI(graphDelegateSelectionLogParams));
      }
    }
    Level currentLevel = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    return ExecutionNode.builder()
        .uuid(nodeExecution.getUuid())
        .setupId(currentLevel.getSetupId())
        .name(nodeExecution.getName())
        .identifier(currentLevel.getIdentifier())
        .stepParameters(nodeExecution.getPmsStepParameters())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .stepType(AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance()).getType())
        .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
        .failureInfo(FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()))
        .interruptHistories(InterruptConfigDTOMapper.toInterruptEffectDTOList(nodeExecution.getInterruptHistories()))
        .nodeRunInfo(nodeExecution.getNodeRunInfo())
        .executableResponses(nodeExecution.getExecutableResponses())
        .unitProgresses(nodeExecution.getUnitProgresses())
        .progressData(nodeExecution.getPmsProgressData())
        .outcomes(OrchestrationMapBackwardCompatibilityUtils.convertToOrchestrationMap(outcomes))
        .baseFqn(null)
        .delegateInfoList(delegateInfoList)
        .build();
  }
}
