/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StageStrategyUtils {
  public boolean isWrappedUnderStrategy(YamlField yamlField) {
    YamlField strategyField = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY);
    return strategyField != null;
  }

  public String getSwappedPlanNodeId(PlanCreationContext ctx, AbstractStageNode stageNode) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    // Since strategy is a child of stage but in execution we want to wrap stage around strategy,
    // we are swapping the uuid of stage and strategy node.
    String planNodeId = stageNode.getUuid();
    if (strategyField != null) {
      planNodeId = strategyField.getNode().getUuid();
    }
    return planNodeId;
  }

  public String getSwappedPlanNodeId(PlanCreationContext ctx, StageElementConfig stageNode) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    // Since strategy is a child of stage but in execution we want to wrap stage around strategy,
    // we are swapping the uuid of stage and strategy node.
    String planNodeId = stageNode.getUuid();
    if (strategyField != null) {
      planNodeId = strategyField.getNode().getUuid();
    }
    return planNodeId;
  }
  public List<AdviserObtainment> getAdviserObtainments(
      YamlField stageField, KryoSerializer kryoSerializer, boolean checkForStrategy) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (stageField != null && stageField.getNode() != null) {
      // if parent is parallel, then we need not add nextStepAdvise as all the executions will happen in parallel
      if (stageField.checkIfParentIsParallel(STAGES)) {
        return adviserObtainments;
      }
      if (checkForStrategy && isWrappedUnderStrategy(stageField)) {
        return adviserObtainments;
      }
      YamlField siblingField = stageField.getNode().nextSiblingFromParentArray(
          stageField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        adviserObtainments.add(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
                .build());
      }
    }
    return adviserObtainments;
  }

  public Map<String, GraphLayoutNode> modifyStageLayoutNodeGraph(YamlField yamlField) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField siblingField = yamlField.getNode().nextSiblingFromParentArray(
        yamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
    EdgeLayoutList edgeLayoutList;
    String planNodeId = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode().getUuid();
    if (siblingField == null) {
      edgeLayoutList = EdgeLayoutList.newBuilder().build();
    } else {
      edgeLayoutList = EdgeLayoutList.newBuilder()
                           .addNextIds(siblingField.getNode().getUuid())
                           .addCurrentNodeChildren(planNodeId)
                           .build();
    }

    StrategyType strategyType = StrategyType.FOR;
    if (yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode().getField("matrix") != null) {
      strategyType = StrategyType.MATRIX;
    }
    stageYamlFieldMap.put(yamlField.getNode().getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(yamlField.getNode().getUuid())
            .setNodeType(strategyType.name())
            .setName(YAMLFieldNameConstants.STRATEGY)
            .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
            .setNodeIdentifier(YAMLFieldNameConstants.STRATEGY)
            .setEdgeLayoutList(edgeLayoutList)
            .build());
    stageYamlFieldMap.put(planNodeId,
        GraphLayoutNode.newBuilder()
            .setNodeUUID(planNodeId)
            .setNodeType(yamlField.getNode().getType())
            .setName(yamlField.getNode().getName())
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(yamlField.getNode().getIdentifier())
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
            .build());
    return stageYamlFieldMap;
  }
}
