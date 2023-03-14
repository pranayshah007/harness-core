/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
public class ExpandedJsonFunctor {
  Ambiance ambiance;
  PlanExpansionService planExpansionService;

  transient Map<String, String> groupAliases;

  public Object asJson(List<String> expressions) {
    List<String> finalExpressions = new ArrayList<>();
    if (EmptyPredicate.isEmpty(expressions)) {
      return null;
    }
    for (String expression : expressions) {
      List<String> expressionKeys = Arrays.asList(expression.split("\\."));
      if (expressionKeys.size() < 2 || EmptyPredicate.isEmpty(expressionKeys)) {
        return null;
      }
      if (expressionKeys.get(0).equals("expandedJson")) {
        List<String> response = new ArrayList<>();
        for (int i = 1; i < expressionKeys.size(); i++) {
          response.add(expressionKeys.get(i));
        }
        expression = String.join(".", response);
        finalExpressions.add(expression);
      }
    }
    Map<String, Object> response = planExpansionService.resolveExpressions(ambiance, finalExpressions);
    if (response == null) {
      return null;
    }
    List<Level> levelsWithStrategyMetadata =
        ambiance.getLevelsList().stream().filter(Level::hasStrategyMetadata).collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(levelsWithStrategyMetadata)) {
      response.put("strategy", StrategyUtils.fetchStrategyObjectMap(levelsWithStrategyMetadata));
    }
    return response;
  }
}
