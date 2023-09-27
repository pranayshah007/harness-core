/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class StageLevelAmbianceModifier implements AmbianceModifier {
  private final PlanExecutionMetadataService planExecutionMetadataService;
  @Inject
  public StageLevelAmbianceModifier(PlanExecutionMetadataService planExecutionMetadataService) {
    this.planExecutionMetadataService = planExecutionMetadataService;
  }

  @Override
  public Ambiance modify(Ambiance givenAmbiance) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(givenAmbiance);
    Ambiance.Builder clonedBuilder = givenAmbiance.toBuilder().setStageExecutionId(currentLevel.getRuntimeId());
    if (AmbianceUtils.isRollbackModeExecution(givenAmbiance)) {
      clonedBuilder.setOriginalStageExecutionIdForRollbackMode(
          obtainOriginalStageExecutionIdForRollbackMode(givenAmbiance, currentLevel));
    }
    return clonedBuilder.build();
  }

  String obtainOriginalStageExecutionIdForRollbackMode(Ambiance ambiance, Level stageLevel) {
    List<PostExecutionRollbackInfo> postExecutionRollbackInfoList = getPostExecutionRollbackInfo(ambiance);
    if (AmbianceUtils.obtainCurrentLevel(ambiance).getStepType().getStepCategory().equals(StepCategory.STRATEGY)) {
      // postExecutionRollbackStageId will be the strategy setup id, that is what we need as the current setup id
      String strategySetupId = AmbianceUtils.obtainCurrentSetupId(ambiance);
      int currentIteration = stageLevel.getStrategyMetadata().getCurrentIteration();
      return postExecutionRollbackInfoList.stream()
          .filter(info -> Objects.equals(info.getPostExecutionRollbackStageId(), strategySetupId))
          .filter(info -> info.getRollbackStageStrategyMetadata().getCurrentIteration() == currentIteration)
          .map(PostExecutionRollbackInfo::getOriginalStageExecutionId)
          .findFirst()
          .orElse("");
    }
    String currentSetupId = stageLevel.getSetupId();
    return postExecutionRollbackInfoList.stream()
        .filter(info -> Objects.equals(info.getPostExecutionRollbackStageId(), currentSetupId))
        .map(PostExecutionRollbackInfo::getOriginalStageExecutionId)
        .findFirst()
        .orElse("");
  }

  private List<PostExecutionRollbackInfo> getPostExecutionRollbackInfo(Ambiance ambiance) {
    // TODO(archit): Remove get from execution_metadata from next release
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
        ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    if (EmptyPredicate.isEmpty(planExecutionMetadata.getPostExecutionRollbackInfos())) {
      return ambiance.getMetadata().getPostExecutionRollbackInfoList();
    }
    return planExecutionMetadata.getPostExecutionRollbackInfos();
  }
}
