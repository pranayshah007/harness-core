/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.expansion;

import io.harness.OrchestrationModuleConfig;
import io.harness.beans.FeatureName;
import io.harness.execution.PlanExecutionExpansion;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.planExecutionJson.PlanExecutionExpansionRepository;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
public class PlanExpansionServiceImpl implements PlanExpansionService {
  @Inject PlanExecutionExpansionRepository planExecutionExpansionRepository;

  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject OrchestrationModuleConfig moduleConfig;

  @Override
  public void addStepInputs(Ambiance ambiance, PmsStepParameters stepInputs) {
    if (shouldSkipUpdate(ambiance) || stepInputs == null) {
      return;
    }
    Update update = new Update();
    String stepInputsKey =
        String.format("%s.%s", getExpansionPathUsingLevels(ambiance), PlanExpansionConstants.STEP_INPUTS);
    String stepInputsJson = RecastOrchestrationUtils.pruneRecasterAdditions(stepInputs.clone());

    // If size of stepInputs is greater than 4KB then we will ignore.
    if (stepInputsJson.length() <= 4096) {
      update.set(stepInputsKey, Document.parse(stepInputsJson));
    }
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (currentLevel != null && currentLevel.hasStrategyMetadata()) {
      Map<String, Object> strategyMap =
          StrategyUtils.fetchStrategyObjectMap(currentLevel, AmbianceUtils.shouldUseMatrixFieldName(ambiance));
      for (Map.Entry<String, Object> entry : strategyMap.entrySet()) {
        String strategyKey = String.format("%s.%s", getExpansionPathUsingLevels(ambiance), entry.getKey());
        if (ClassUtils.isPrimitiveOrWrapper(entry.getValue().getClass())) {
          update.set(strategyKey, String.valueOf(entry.getValue()));
        } else {
          update.set(strategyKey, Document.parse(RecastOrchestrationUtils.pruneRecasterAdditions(entry.getValue())));
        }
      }
    }
    if (!update.getUpdateObject().isEmpty()) {
      planExecutionExpansionRepository.update(
          ambiance.getPlanExecutionId(), update, moduleConfig.getExpandedJsonLockConfig().getLockTimeOutInMinutes());
    }
  }

  @Override
  public void addOutcomes(Ambiance ambiance, String name, PmsOutcome outcome) {
    if (shouldSkipUpdate(ambiance) || outcome == null) {
      return;
    }
    Update update = new Update();
    update.set(getExpansionPathUsingLevels(ambiance) + String.format(".%s.", PlanExpansionConstants.OUTCOME) + name,
        Document.parse(RecastOrchestrationUtils.pruneRecasterAdditions(outcome.clone())));

    planExecutionExpansionRepository.update(
        ambiance.getPlanExecutionId(), update, moduleConfig.getExpandedJsonLockConfig().getLockTimeOutInMinutes());
  }

  @Override
  public void create(String planExecutionId) {
    planExecutionExpansionRepository.save(PlanExecutionExpansion.builder().planExecutionId(planExecutionId).build());
  }

  @Override
  public Map<String, Object> resolveExpressions(Ambiance ambiance, List<String> expressions) {
    if (shouldUseExpandedJsonFunctor(ambiance)) {
      Criteria criteria = Criteria.where("planExecutionId").is(ambiance.getPlanExecutionId());
      Query query = new Query(criteria);
      expressions.forEach(expression -> query.fields().include(expression));
      PlanExecutionExpansion planExecutionExpansion = planExecutionExpansionRepository.find(query);
      if (planExecutionExpansion == null) {
        return null;
      }
      if (planExecutionExpansion.getExpandedJson() == null) {
        return null;
      }
      return JsonUtils.asMap(planExecutionExpansion.getExpandedJson().toJson());
    }
    return null;
  }

  @Override
  public void updateStatus(Ambiance ambiance, Status status) {
    if (shouldSkipUpdate(ambiance)) {
      return;
    }
    Update update = new Update();
    update.set(getExpansionPathUsingLevels(ambiance) + String.format(".%s", PlanExpansionConstants.STATUS), status);
    planExecutionExpansionRepository.update(
        ambiance.getPlanExecutionId(), update, moduleConfig.getExpandedJsonLockConfig().getLockTimeOutInMinutes());
  }

  @VisibleForTesting
  String getExpansionPathUsingLevels(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    List<String> keyList = new ArrayList<>();
    keyList.add(PlanExpansionConstants.EXPANDED_JSON);
    for (Level level : levels) {
      if (!level.getSkipExpressionChain() || level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        keyList.add(level.getIdentifier());
      }
    }
    return String.join(".", keyList);
  }

  private boolean shouldSkipUpdate(Ambiance ambiance) {
    return !pmsFeatureFlagService.isEnabled(
               AmbianceUtils.getAccountId(ambiance), FeatureName.PIE_EXECUTION_JSON_SUPPORT)
        || (AmbianceUtils.obtainCurrentLevel(ambiance).getSkipExpressionChain()
            && AmbianceUtils.obtainCurrentLevel(ambiance).getStepType().getStepCategory() != StepCategory.STRATEGY);
  }

  private boolean shouldUseExpandedJsonFunctor(Ambiance ambiance) {
    return pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.PIE_EXPRESSION_ENGINE_V2);
  }

  @Override
  public void deleteAllExpansions(Set<String> planExecutionIds) {
    planExecutionExpansionRepository.deleteAllExpansions(planExecutionIds);
  }
}
