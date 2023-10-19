/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.execution.NodeExecution;
import io.harness.expression.LateBindingMap;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.jexl3.JexlEngine;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NodeExecutionAncestorFunctor extends LateBindingMap {
  transient NodeExecutionsCache nodeExecutionsCache;
  transient PmsOutcomeService pmsOutcomeService;
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient NodeExecutionInfoService nodeExecutionInfoService;
  transient Ambiance ambiance;
  transient Set<NodeExecutionEntityType> entityTypes;
  transient Map<String, String> groupAliases;
  transient JexlEngine engine;

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }

    NodeExecution startNodeExecution = findStartNodeExecution((String) key);
    return startNodeExecution == null ? null
                                      : NodeExecutionValue.builder()
                                            .nodeExecutionsCache(nodeExecutionsCache)
                                            .pmsOutcomeService(pmsOutcomeService)
                                            .pmsSweepingOutputService(pmsSweepingOutputService)
                                            .nodeExecutionInfoService(nodeExecutionInfoService)
                                            .ambiance(ambiance)
                                            .startNodeExecution(startNodeExecution)
                                            .entityTypes(entityTypes)
                                            .engine(engine)
                                            .build()
                                            .bind();
  }

  private NodeExecution findStartNodeExecution(String key) {
    if (groupAliases != null && groupAliases.containsKey(key)) {
      return findStartNodeExecutionByGroup(groupAliases.get(key));
    }

    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (nodeExecutionId == null) {
      return null;
    }
    NodeExecution currNodeExecution = nodeExecutionsCache.fetch(nodeExecutionId);
    while (currNodeExecution != null) {
      if (!currNodeExecution.getSkipExpressionChain() && key.equals(currNodeExecution.getIdentifier())) {
        return currNodeExecution;
      }
      currNodeExecution = nodeExecutionsCache.fetch(currNodeExecution.getParentId());
    }
    return null;
  }

  private NodeExecution findStartNodeExecutionByGroup(String groupName) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (nodeExecutionId == null) {
      return null;
    }

    NodeExecution currNodeExecution = nodeExecutionsCache.fetch(nodeExecutionId);
    while (currNodeExecution != null) {
      if (groupName.equals(currNodeExecution.getGroup())) {
        return currNodeExecution;
      }
      currNodeExecution = nodeExecutionsCache.fetch(currNodeExecution.getParentId());
    }
    return null;
  }
}
