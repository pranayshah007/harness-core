/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.expressions.usages.beans.ExpressionCategory;
import io.harness.engine.expressions.usages.beans.ExpressionMetadata;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class ExpressionUsagesEventHandler implements OrchestrationEventHandler {
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject ExpressionUsageService expressionUsageService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Override
  public void handleEvent(OrchestrationEvent event) {
    // TODO: Do sampling of the requests.
    Ambiance ambiance = event.getAmbiance();
    if (!pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.PIE_STORE_USED_EXPRESSIONS)) {
      return;
    }
    // If an entity already exists for the then don't process it until sampling is implemented.
    if (expressionUsageService.doesExpressionUsagesEntityExists(AmbianceUtils.getPipelineIdentifier(ambiance),
            AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance))) {
      return;
    }
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      Optional<PlanExecutionMetadata> optional =
          planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId());
      if (optional.isEmpty()) {
        log.error(
            "Could not find planExecutionMetadata for planExecutionId {}. Not storing the expression-usages for this execution.",
            ambiance.getPlanExecutionId());
        return;
      }
      Map<ExpressionCategory, Set<ExpressionMetadata>> expressionUsages = new HashMap<>();
      String yaml = optional.get().getYaml();

      YamlConfig yamlConfig = new YamlConfig(yaml);
      Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();

      fullMap.keySet().forEach(key -> {
        String value = fullMap.get(key).toString();
        if (EngineExpressionEvaluator.hasExpressions(value)) {
          ExpressionCategory category = calculateCategoryFromFQN(key);
          expressionUsages.computeIfAbsent(category, k -> new HashSet<>())
              .add(ExpressionMetadata.builder().fqn(key.getExpressionFqn()).expression(value).build());
        }
      });

      expressionUsageService.upsertExpressions(AmbianceUtils.getPipelineIdentifier(ambiance),
          AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), expressionUsages);
    }
  }

  private ExpressionCategory calculateCategoryFromFQN(FQN fqn) {
    if (doesFqnBelongsToWhenCondition(fqn.getFqnList())) {
      return ExpressionCategory.WHEN_CONDITION;
    } else if (doesFqnBelongsToVariableValue(fqn.getFqnList())) {
      return ExpressionCategory.VARIABLE_VALUE;
    }
    return ExpressionCategory.COMMON_FIELD_VALUE;
  }

  private boolean doesFqnBelongsToWhenCondition(List<FQNNode> fqnNodes) {
    int size = fqnNodes.size();
    // fqn must end with when.condition
    return size >= 2
        && FQNNode.builder().key("condition").nodeType(FQNNode.NodeType.KEY).build().equals(fqnNodes.get(size - 1))
        && FQNNode.builder()
               .key(YAMLFieldNameConstants.WHEN)
               .nodeType(FQNNode.NodeType.KEY)
               .build()
               .equals(fqnNodes.get(size - 2));
  }
  private boolean doesFqnBelongsToVariableValue(List<FQNNode> fqnNodes) {
    int size = fqnNodes.size();
    // fqn must end with variables.*.value
    return size >= 3
        && FQNNode.builder()
               .key(YAMLFieldNameConstants.VALUE)
               .nodeType(FQNNode.NodeType.KEY)
               .build()
               .equals(fqnNodes.get(size - 1))
        && FQNNode.NodeType.UUID.equals(fqnNodes.get(size - 2).getNodeType())
        && FQNNode.builder()
               .key(YAMLFieldNameConstants.VARIABLES)
               .nodeType(FQNNode.NodeType.KEY)
               .build()
               .equals(fqnNodes.get(size - 3));
  }
}
