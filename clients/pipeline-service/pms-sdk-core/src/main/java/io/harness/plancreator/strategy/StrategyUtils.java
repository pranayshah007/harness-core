/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.plancreator.strategy.StrategyConstants.CURRENT_GLOBAL_ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.ITEM;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATIONS;
import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;
import static io.harness.plancreator.strategy.StrategyConstants.PARTITION;
import static io.harness.plancreator.strategy.StrategyConstants.REPEAT;
import static io.harness.plancreator.strategy.StrategyConstants.TOTAL_GLOBAL_ITERATIONS;
import static io.harness.plancreator.strategy.StrategyConstants.TOTAL_ITERATIONS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.IDENTIFIER;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NAME;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.strategy.StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX_ESCAPED;

import io.harness.advisers.nextstep.NextStageAdviserParameters;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.LevelUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;
import io.harness.strategy.StrategyValidationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StrategyUtils {
  @Inject IterationVariables iterationVariables;
  public boolean isWrappedUnderStrategy(YamlField yamlField) {
    YamlField strategyField = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY);
    return strategyField != null;
  }

  public boolean isWrappedUnderStrategy(YamlNode yamlField) {
    YamlField strategyField = yamlField.getField(YAMLFieldNameConstants.STRATEGY);
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
      String pipelineRollbackStageId = getPipelineRollbackStageId(stageField);
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        String siblingFieldUuid = siblingField.getNode().getUuid();
        adviserObtainments.add(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    NextStageAdviserParameters.builder()
                        .nextNodeId(siblingFieldUuid.equals(pipelineRollbackStageId) ? null : siblingFieldUuid)
                        .pipelineRollbackStageId(pipelineRollbackStageId)
                        .build())))
                .build());
      }
    }
    return adviserObtainments;
  }

  public String getPipelineRollbackStageId(YamlField currentField) {
    List<YamlNode> stages =
        YamlUtils.getGivenYamlNodeFromParentPath(currentField.getNode(), YAMLFieldNameConstants.STAGES).asArray();
    Optional<String> pipelineRollbackUuid =
        stages.stream()
            .filter(stage
                -> stage.getField(YAMLFieldNameConstants.STAGE) != null
                    && stage.getField(YAMLFieldNameConstants.STAGE).getType().equals("PipelineRollback"))
            .map(stage -> stage.getField(YAMLFieldNameConstants.STAGE).getUuid())
            .findAny();
    return pipelineRollbackUuid.orElse(null);
  }

  public Map<String, GraphLayoutNode> modifyStageLayoutNodeGraph(YamlField yamlField) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField siblingField = yamlField.getNode().nextSiblingFromParentArray(
        yamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
    EdgeLayoutList edgeLayoutList;
    String planNodeId = yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode().getUuid();
    if (siblingField == null) {
      edgeLayoutList = EdgeLayoutList.newBuilder().addCurrentNodeChildren(planNodeId).build();
    } else {
      edgeLayoutList = EdgeLayoutList.newBuilder()
                           .addNextIds(siblingField.getNode().getUuid())
                           .addCurrentNodeChildren(planNodeId)
                           .build();
    }

    StrategyType strategyType = StrategyType.LOOP;
    if (yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode().getField("matrix") != null) {
      strategyType = StrategyType.MATRIX;
    } else if (yamlField.getNode().getField(YAMLFieldNameConstants.STRATEGY).getNode().getField("parallelism")
        != null) {
      strategyType = StrategyType.PARALLELISM;
    }
    stageYamlFieldMap.put(yamlField.getNode().getUuid(),
        GraphLayoutNode.newBuilder()
            .setNodeUUID(yamlField.getNode().getUuid())
            .setNodeType(strategyType.name())
            .setName(yamlField.getNode().getName())
            .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
            .setNodeIdentifier(yamlField.getNode().getIdentifier())
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

  public List<AdviserObtainment> getAdviserObtainmentFromMetaDataForStep(
      KryoSerializer kryoSerializer, YamlField currentField) {
    if (currentField.checkIfParentIsParallel(STEPS)) {
      return new ArrayList<>();
    }
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (currentField != null && currentField.getNode() != null) {
      YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(currentField.getName(),
          Arrays.asList(
              YAMLFieldNameConstants.STEP, YAMLFieldNameConstants.STEP_GROUP, YAMLFieldNameConstants.PARALLEL));
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        adviserObtainments.add(
            getAdviserObtainmentsForParallelStepParent(currentField, kryoSerializer, siblingField.getNode().getUuid()));
      }
    }
    return adviserObtainments;
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      Map<String, ByteString> metadataMap, List<AdviserObtainment> adviserObtainments) {
    addStrategyFieldDependencyIfPresent(
        kryoSerializer, ctx, uuid, name, identifier, planCreationResponseMap, metadataMap, adviserObtainments, true);
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      Map<String, ByteString> metadataMap, List<AdviserObtainment> adviserObtainments, Boolean shouldProceedIfFailed) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    if (strategyField != null) {
      // This is mandatory because it is the parent's responsibility to pass the nodeId and the childNodeId to the
      // strategy node
      metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyField.getNode().getUuid(),
          ByteString.copyFrom(kryoSerializer.asDeflatedBytes(StrategyMetadata.builder()
                                                                 .strategyNodeId(uuid)
                                                                 .adviserObtainments(adviserObtainments)
                                                                 .childNodeId(strategyField.getNode().getUuid())
                                                                 .strategyNodeName(refineIdentifier(name))
                                                                 .strategyNodeIdentifier(refineIdentifier(identifier))
                                                                 .shouldProceedIfFailed(shouldProceedIfFailed)
                                                                 .build())));
      planCreationResponseMap.put(uuid,
          PlanCreationResponse.builder()
              .dependencies(
                  DependenciesUtils.toDependenciesProto(ImmutableMap.of(uuid, strategyField))
                      .toBuilder()
                      .putDependencyMetadata(uuid, Dependency.newBuilder().putAllMetadata(metadataMap).build())
                      .build())
              .build());
    }
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx,
      String fieldUuid, String fieldIdentifier, String fieldName, Map<String, YamlField> dependenciesNodeMap,
      Map<String, ByteString> metadataMap, List<AdviserObtainment> adviserObtainments) {
    addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, fieldUuid, fieldIdentifier, fieldName, dependenciesNodeMap,
        metadataMap, adviserObtainments, true);
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx,
      String fieldUuid, String fieldIdentifier, String fieldName, Map<String, YamlField> dependenciesNodeMap,
      Map<String, ByteString> metadataMap, List<AdviserObtainment> adviserObtainments, Boolean shouldProceedIfFailed) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    if (strategyField != null) {
      dependenciesNodeMap.put(fieldUuid, strategyField);
      // This is mandatory because it is the parent's responsibility to pass the nodeId and the childNodeId to the
      // strategy node
      metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyField.getNode().getUuid(),
          ByteString.copyFrom(
              kryoSerializer.asDeflatedBytes(StrategyMetadata.builder()
                                                 .strategyNodeId(fieldUuid)
                                                 .adviserObtainments(adviserObtainments)
                                                 .childNodeId(strategyField.getNode().getUuid())
                                                 .strategyNodeIdentifier(refineIdentifier(fieldIdentifier))
                                                 .strategyNodeName(refineIdentifier(fieldName))
                                                 .shouldProceedIfFailed(shouldProceedIfFailed)
                                                 .build())));
    }
  }

  public void modifyJsonNode(JsonNode jsonNode, List<String> combinations) {
    String newIdentifier = jsonNode.get(IDENTIFIER).asText() + "_" + String.join("_", combinations);
    String newName = jsonNode.get(NAME).asText() + "_" + String.join("_", combinations);
    JsonNodeUtils.updatePropertyInObjectNode(jsonNode, IDENTIFIER, newIdentifier);
    JsonNodeUtils.updatePropertyInObjectNode(jsonNode, NAME, newName);
  }

  public String replaceExpressions(
      String jsonString, Map<String, String> combinations, int currentIteration, int totalIteration, String itemValue) {
    EngineExpressionEvaluator evaluator =
        new StrategyExpressionEvaluator(combinations, currentIteration, totalIteration, itemValue);
    return (String) evaluator.resolve(jsonString, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  /**
   * This function remove <+strategy.identifierPostFix> if present on the passed string
   */
  private String refineIdentifier(String identifier) {
    return identifier.replaceAll(STRATEGY_IDENTIFIER_POSTFIX_ESCAPED, "");
  }

  /**
   * This is used to fetch strategy object map at a given level
   * @param level
   * @return
   */
  public Map<String, Object> fetchStrategyObjectMap(Level level) {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    if (level.hasStrategyMetadata()) {
      return fetchStrategyObjectMap(Lists.newArrayList(level));
    }
    strategyObjectMap.put(ITERATION, 0);
    strategyObjectMap.put(ITERATIONS, 1);
    strategyObjectMap.put(TOTAL_ITERATIONS, 1);
    return strategyObjectMap;
  }

  /**
   * This is used to fetch the strategy object map from different values. It combines the axis of all it's parent
   *
   * @param levelsWithStrategyMetadata
   * @return
   */
  public Map<String, Object> fetchStrategyObjectMap(List<Level> levelsWithStrategyMetadata) {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    Map<String, Object> matrixValuesMap = new HashMap<>();
    Map<String, Object> repeatValuesMap = new HashMap<>();

    List<IterationVariables> levels = new ArrayList<>();
    for (Level level : levelsWithStrategyMetadata) {
      levels.add(IterationVariables.builder()
                     .currentIteration(level.getStrategyMetadata().getCurrentIteration())
                     .totalIterations(level.getStrategyMetadata().getTotalIterations())
                     .build());

      if (level.getStrategyMetadata().hasMatrixMetadata()) {
        // MatrixMapLocal can contain either a string as value or a json as value.
        Map<String, String> matrixMapLocal = level.getStrategyMetadata().getMatrixMetadata().getMatrixValuesMap();
        matrixValuesMap.putAll(getMatrixMapFromCombinations(matrixMapLocal));
      }
      if (level.getStrategyMetadata().hasForMetadata()) {
        repeatValuesMap.put(ITEM, level.getStrategyMetadata().getForMetadata().getValue());
        repeatValuesMap.put(PARTITION, level.getStrategyMetadata().getForMetadata().getPartitionList());
      }

      if (LevelUtils.isStepLevel(level)) {
        fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);
      }

      strategyObjectMap.put(ITERATION, level.getStrategyMetadata().getCurrentIteration());
      strategyObjectMap.put(ITERATIONS, level.getStrategyMetadata().getTotalIterations());
      strategyObjectMap.put(TOTAL_ITERATIONS, level.getStrategyMetadata().getTotalIterations());
      strategyObjectMap.put("identifierPostFix", AmbianceUtils.getStrategyPostfix(level));
    }
    strategyObjectMap.put(MATRIX, matrixValuesMap);
    strategyObjectMap.put(REPEAT, repeatValuesMap);

    return strategyObjectMap;
  }

  /***
   * This method calculates total no of iteration that the final step will undergo.
   * Ex: If stage has parallelism 3, stepGroup3 and step as 3, then totalIterations will be 27 and
   * currentIteration say middle iteration of each stage+stepGroup+step will have 13(indexes range from 0-26) as idx and
   * that is also calculated.
   * @param strategyObjectMap
   * @param levels
   */
  public void fetchGlobalIterationsVariablesForStrategyObjectMap(
      Map<String, Object> strategyObjectMap, List<IterationVariables> levels) {
    List<Integer> preprocessedProductArray = new ArrayList<>();
    ListIterator<IterationVariables> itr = levels.listIterator(levels.size());

    preprocessedProductArray.add(itr.previous().getTotalIterations());
    while (itr.hasPrevious()) {
      // Iterate in reverse
      preprocessedProductArray.add(
          preprocessedProductArray.get(preprocessedProductArray.size() - 1) * itr.previous().getTotalIterations());
    }
    Collections.reverse(preprocessedProductArray);

    strategyObjectMap.put(CURRENT_GLOBAL_ITERATION, getCurrentGlobalIteration(levels, preprocessedProductArray));
    strategyObjectMap.put(TOTAL_GLOBAL_ITERATIONS, preprocessedProductArray.get(0));
  }

  private Object getCurrentGlobalIteration(List<IterationVariables> levels, List<Integer> preprocessedProductArray) {
    int currentGlobalIteration = 0;
    for (int i = 0; i <= levels.size() - 2; i++) {
      currentGlobalIteration += levels.get(i).getCurrentIteration() * preprocessedProductArray.get(i + 1);
    }
    currentGlobalIteration += levels.get(levels.size() - 1).getCurrentIteration();
    return currentGlobalIteration;
  }

  public Map<String, Object> getMatrixMapFromCombinations(Map<String, String> combinationsMap) {
    Map<String, Object> objectMap = new HashMap<>();
    for (Map.Entry<String, String> entry : combinationsMap.entrySet()) {
      // We are trying to check if it is a valid json or not. If yes, then we add it as map inside our object
      // else store it as string
      try {
        objectMap.put(entry.getKey(), JsonUtils.asMap(entry.getValue()));
      } catch (Exception ex) {
        objectMap.put(entry.getKey(), entry.getValue());
      }
    }
    return objectMap;
  }

  public AdviserObtainment getAdviserObtainmentsForParallelStepParent(
      YamlField currentField, KryoSerializer kryoSerializer, String siblingId) {
    boolean isStepInsideRollback = YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null;
    if (!isStepInsideRollback) {
      return AdviserObtainment.newBuilder()
          .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
          .setParameters(ByteString.copyFrom(
              kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(siblingId).build())))
          .build();
    } else {
      return AdviserObtainment.newBuilder()
          .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
          .setParameters(ByteString.copyFrom(
              kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(siblingId).build())))
          .build();
    }
  }
}
