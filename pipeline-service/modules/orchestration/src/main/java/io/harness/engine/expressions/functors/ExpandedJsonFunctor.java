/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Builder
public class ExpandedJsonFunctor {
  Ambiance ambiance;
  PlanExpansionService planExpansionService;
  NodeExecutionInfoService nodeExecutionInfoService;

  transient Map<String, String> groupAliases;

  public Object asJson(List<String> expressions) {
    if (EmptyPredicate.isEmpty(expressions)) {
      return null;
    }
    Map<String, Object> response = planExpansionService.resolveExpressions(ambiance, expressions);
    if (response == null) {
      return null;
    }
    List<Level> levelsWithStrategyMetadata =
        ambiance.getLevelsList().stream().filter(AmbianceUtils::hasStrategyMetadata).collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(levelsWithStrategyMetadata)) {
      response.put("strategy", nodeExecutionInfoService.fetchStrategyObjectMap(levelsWithStrategyMetadata));
    } else {
      response.put("strategy",
          nodeExecutionInfoService.fetchStrategyObjectMap(AmbianceUtils.obtainCurrentLevel(ambiance).getRuntimeId()));
    }
    return response;
  }
}
