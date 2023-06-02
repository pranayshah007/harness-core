/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.strategy.StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX_ESCAPED;

import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;
import io.harness.strategy.StrategyValidationUtils;

import com.google.protobuf.ByteString;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StrategyUtilsV1 {
  public boolean isWrappedUnderStrategy(YamlField yamlField) {
    YamlField strategyField = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY);
    return strategyField != null;
  }

  public String getSwappedPlanNodeId(PlanCreationContext ctx, String originalPlanNodeId) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    // Since strategy is a child of stage but in execution we want to wrap stage around strategy,
    // we are swapping the uuid of stage and strategy node.
    String planNodeId = originalPlanNodeId;
    if (strategyField != null) {
      planNodeId = strategyField.getNode().getUuid();
    }
    return planNodeId;
  }

  public String getIdentifierWithExpression(PlanCreationContext ctx, String originalIdentifier) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    // Since strategy is a child of stage but in execution we want to wrap stage around strategy,
    // we are appending an expression that will be resolved during execution
    String identifier = originalIdentifier;
    if (strategyField != null) {
      identifier = originalIdentifier + StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
    }
    return identifier;
  }

  public Map<String, GraphLayoutNode> modifyStageLayoutNodeGraph(YamlField yamlField, String nextNodeUuid) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    EdgeLayoutList edgeLayoutList;
    YamlNode strategyNode = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode();
    String planNodeId = strategyNode.getProperty(YamlNode.UUID_FIELD_NAME);
    if (nextNodeUuid == null) {
      edgeLayoutList = EdgeLayoutList.newBuilder().addCurrentNodeChildren(planNodeId).build();
    } else {
      edgeLayoutList = EdgeLayoutList.newBuilder().addNextIds(nextNodeUuid).addCurrentNodeChildren(planNodeId).build();
    }

    StrategyType strategyType = StrategyType.LOOP;
    if (strategyNode.getProperty(StrategyType.MATRIX.displayName) != null) {
      strategyType = StrategyType.MATRIX;
    } else if (strategyNode.getProperty(StrategyType.PARALLELISM.displayName) != null) {
      strategyType = StrategyType.PARALLELISM;
    }
    stageYamlFieldMap.put(yamlField.getNode().getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(yamlField.getNode().getUuid())
            .setNodeType(strategyType.name())
            .setName(yamlField.getId())
            .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
            .setNodeIdentifier(yamlField.getId())
            .setEdgeLayoutList(edgeLayoutList)
            .build());
    stageYamlFieldMap.put(planNodeId,
        GraphLayoutNode.newBuilder()
            .setNodeUUID(planNodeId)
            .setNodeType(yamlField.getNode().getType())
            .setName(yamlField.getName())
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(yamlField.getName())
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
            .build());
    return stageYamlFieldMap;
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx,
      String fieldUuid, Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap,
      List<AdviserObtainment> adviserObtainments) {
    addStrategyFieldDependencyIfPresent(
        kryoSerializer, ctx, fieldUuid, dependenciesNodeMap, metadataMap, adviserObtainments, true);
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx,
      String fieldUuid, Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap,
      List<AdviserObtainment> adviserObtainments, Boolean shouldProceedIfFailed) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);

    if (strategyField != null) {
      dependenciesNodeMap.put(fieldUuid, strategyField);
      // This is mandatory because it is the parent's responsibility to pass the nodeId and the childNodeId to the
      // strategy node
      metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyField.getNode().getUuid(),
          ByteString.copyFrom(kryoSerializer.asDeflatedBytes(
              StrategyMetadata.builder()
                  .strategyNodeId(fieldUuid)
                  .adviserObtainments(adviserObtainments)
                  .childNodeId(strategyField.getNode().getUuid())
                  .strategyNodeIdentifier(refineIdentifier(ctx.getCurrentField().getId()))
                  .strategyNodeName(refineIdentifier(ctx.getCurrentField().getNodeName()))
                  .shouldProceedIfFailed(shouldProceedIfFailed)
                  .build())));
    }
  }

  /**
   * This function remove <+strategy.identifierPostFix> if present on the passed string
   */
  private String refineIdentifier(String identifier) {
    return identifier.replaceAll(STRATEGY_IDENTIFIER_POSTFIX_ESCAPED, "");
  }
}
