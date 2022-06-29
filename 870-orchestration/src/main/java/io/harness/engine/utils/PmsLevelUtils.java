/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsLevelUtils {
  public static Level buildLevelFromNode(String runtimeId, Node node) {
    return buildLevelFromNode(runtimeId, 0, node, null);
  }

  public static Level buildLevelFromNode(String runtimeId, int retryIndex, Node node) {
    return buildLevelFromNode(runtimeId, retryIndex, node, null);
  }

  public static Level buildLevelFromNode(String runtimeId, Node node, StrategyMetadata strategyMetadata) {
    return buildLevelFromNode(runtimeId, 0, node, strategyMetadata);
  }

  public static Level buildLevelFromNode(
      String runtimeId, int retryIndex, Node node, StrategyMetadata strategyMetadata) {
    Level.Builder levelBuilder = Level.newBuilder()
                                     .setSetupId(node.getUuid())
                                     .setRuntimeId(runtimeId)
                                     .setIdentifier(node.getIdentifier())
                                     .setRetryIndex(retryIndex)
                                     .setSkipExpressionChain(node.isSkipExpressionChain())
                                     .setStartTs(System.currentTimeMillis())
                                     .setStepType(node.getStepType())
                                     .setNodeType(node.getNodeType().toString())
                                     .setOriginalIdentifier(node.getIdentifier());
    if (node.getGroup() != null) {
      levelBuilder.setGroup(node.getGroup());
    }
    if (strategyMetadata != null) {
      levelBuilder.setStrategyMetadata(strategyMetadata);
      levelBuilder.setIdentifier(node.getIdentifier() + AmbianceUtils.getStrategyPostfix(levelBuilder.build()));
    }
    return levelBuilder.build();
  }
}
