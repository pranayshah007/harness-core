package io.harness.engine.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Level;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsLevelUtils {
  public static Level buildLevelFromNode(String runtimeId, Node node) {
    return buildLevelFromNode(runtimeId, 0, node);
  }

  public static Level buildLevelFromNode(String runtimeId, int retryIndex, Node node) {
    Level.Builder levelBuilder = Level.newBuilder()
                                     .setSetupId(node.getUuid())
                                     .setRuntimeId(runtimeId)
                                     .setIdentifier(node.getIdentifier())
                                     .setRetryIndex(retryIndex)
                                     .setSkipExpressionChain(node.isSkipExpressionChain())
                                     .setStartTs(System.currentTimeMillis())
                                     .setStepType(node.getStepType())
                                     .setNodeType(node.getNodeType().toString())
                                     .setServiceName(node.getServiceName());
    if (node.getGroup() != null) {
      levelBuilder.setGroup(node.getGroup());
    }
    return levelBuilder.build();
  }
}
