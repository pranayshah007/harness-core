/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static java.lang.Math.max;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExcludeConfig;
import io.harness.plancreator.strategy.ExpressionAxisConfig;
import io.harness.plancreator.strategy.StrategyExpressionEvaluator;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class MatrixConfigServiceHelper {
  public List<ChildrenExecutableResponse.Child> fetchChildren(List<String> keys, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxes, ParameterField<List<ExcludeConfig>> exclude, String childNodeId,
      String nodeName, Ambiance ambiance) {
    boolean useMatrixFieldName = AmbianceUtils.shouldUseMatrixFieldName(ambiance);
    List<Map<String, String>> combinations = new ArrayList<>();
    List<List<Integer>> matrixMetadata = new ArrayList<>();

    fetchCombinations(new LinkedHashMap<>(), axes, expressionAxes, combinations,
        ParameterField.isBlank(exclude) ? null : exclude.getValue(), matrixMetadata, keys, 0, new LinkedList<>());
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    int currentIteration = 0;
    int totalCount = combinations.size();
    if (totalCount == 0) {
      throw new InvalidRequestException(
          "Total number of iterations found to be 0 for this strategy. Please check pipeline yaml");
    }

    // map to store Matrix Modified Identifier String
    Map<String, Integer> modifiedIdentifierStringMap = new HashMap<>();

    for (Map<String, String> combination : combinations) {
      // Creating a runtime Map to identify similar combinations and adding a prefix counter if needed. Refer PIE-6426
      Set<Map.Entry<String, String>> entries = combination.entrySet();

      boolean isNodeNameSet = EmptyPredicate.isNotEmpty(nodeName);

      String variableName = "";
      // Resolving the nodeName in case of expressions.
      try {
        variableName = resolveNodeName(nodeName, entries, combination, currentIteration, totalCount);
      } catch (Exception e) {
        throw new InvalidRequestException("Failed to resolve the expression for the nodeName: " + nodeName);
      }

      StrategyMetadata strategyMetadata =
          StrategyMetadata.newBuilder()
              .setCurrentIteration(currentIteration)
              .setTotalIterations(totalCount)
              .setMatrixMetadata(MatrixMetadata.newBuilder()
                                     .addAllMatrixCombination(matrixMetadata.get(currentIteration))
                                     .putAllMatrixValues(combination)
                                     .setNodeName(isNodeNameSet ? variableName : "")
                                     .build())
              .build();

      String modifiedIdentifier = AmbianceUtils.getStrategyPostFixUsingMetadata(strategyMetadata, useMatrixFieldName);
      /* If this modifiedIdentifier is a duplicate (it can happen for long identifiers which are truncated),
         we need deduplicate it by appending a counter at the end: */
      if (modifiedIdentifierStringMap.containsKey(modifiedIdentifier)) {
        int cnt = modifiedIdentifierStringMap.getOrDefault(modifiedIdentifier, 0);
        modifiedIdentifierStringMap.put(modifiedIdentifier, cnt + 1);
        /* Concatenate identifier with deduplication suffix, but keep the identifier length equal or less
           than MAX_CHARACTERS_FOR_IDENTIFIER_POSTFIX */
        modifiedIdentifier =
            concatWithMaxLength(modifiedIdentifier, "_" + cnt, AmbianceUtils.MAX_CHARACTERS_FOR_IDENTIFIER_POSTFIX);
      }
      modifiedIdentifierStringMap.putIfAbsent(modifiedIdentifier, 0);
      strategyMetadata = strategyMetadata.toBuilder().setIdentifierPostFix(modifiedIdentifier).build();

      // Setting the nodeName in MatrixMetadata to empty string in case user has not given nodeName while defining
      // matrix This nodeName is used in AmbianceUtils.java to calculate the level identifier for the node based on the
      // default setting for the matrix labels.
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(strategyMetadata)
                       .build());
      currentIteration++;
    }

    return children;
  }

  private String concatWithMaxLength(String prefix, String suffix, int maxLength) {
    int maxLengthForPrefix = max(maxLength - suffix.length(), 0);
    if (prefix.length() > maxLengthForPrefix) {
      prefix = prefix.substring(0, maxLengthForPrefix);
    }
    return prefix + suffix;
  }

  public String resolveNodeName(String nodeName, Set<Map.Entry<String, String>> entries,
      Map<String, String> combination, int currentIteration, int totalCount) {
    if (EmptyPredicate.isNotEmpty(nodeName)) {
      // If nodeName field is given, using it to name the nodes.
      EngineExpressionEvaluator evaluator =
          new StrategyExpressionEvaluator(combination, currentIteration, totalCount, null, null);
      return (String) evaluator.resolve(nodeName, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
    } else {
      // If nodeName field is not given, we define the node names with the <key,value> pairs provided by the users.
      return entries.stream().map(t -> t.getValue().replace(".", "")).collect(Collectors.joining("_"));
    }
  }

  // This is used by CI during the CIInitStep. CI expands the steps YAML having strategy and the expanded YAML is then
  // executed.
  public StrategyInfo expandJsonNodeFromClass(List<String> keys, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxes, ParameterField<List<ExcludeConfig>> exclude,
      ParameterField<Integer> maxConcurrencyParameterField, JsonNode jsonNode, Optional<Integer> maxExpansionLimit,
      boolean isStepGroup, Class cls, Ambiance ambiance, String nodeName) {
    // no use of childNodeId in the case of CI
    List<ChildrenExecutableResponse.Child> children =
        fetchChildren(keys, axes, expressionAxes, exclude, "", nodeName, ambiance);
    List<JsonNode> jsonNodes = new ArrayList<>();
    List<Map<String, String>> combinations = new ArrayList<>();

    int totalCount = children.size();

    if (totalCount == 0) {
      throw new InvalidRequestException(
          "Total number of iterations found to be 0 for this strategy. Please check pipeline yaml");
    }

    if (maxExpansionLimit.isPresent()) {
      if (totalCount > maxExpansionLimit.get()) {
        throw new InvalidYamlException("Iteration count is beyond the supported limit of " + maxExpansionLimit.get());
      }
    }

    for (ChildrenExecutableResponse.Child child : children) {
      StrategyMetadata strategyMetadata = child.getStrategyMetadata();
      if (strategyMetadata.getMatrixMetadata() != null) {
        combinations.add(strategyMetadata.getMatrixMetadata().getMatrixValuesMap());
      }
    }
    for (ChildrenExecutableResponse.Child child : children) {
      StrategyMetadata strategyMetadata = child.getStrategyMetadata();
      String identifierPostFix = strategyMetadata.getIdentifierPostFix();
      int currentIteration = strategyMetadata.getCurrentIteration();
      JsonNode resolvedJsonNode =
          getResolvedJsonNode(isStepGroup, currentIteration, totalCount, jsonNode, cls, combinations);
      StrategyUtils.modifyJsonNode(resolvedJsonNode, identifierPostFix);
      jsonNodes.add(resolvedJsonNode);
    }

    int maxConcurrency = jsonNodes.size();
    if (!ParameterField.isBlank(maxConcurrencyParameterField)) {
      maxConcurrency = maxConcurrencyParameterField.getValue();
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(maxConcurrency).build();
  }

  private JsonNode getResolvedJsonNode(boolean isStepGroup, int currentIteration, int totalCount, JsonNode jsonNode,
      Class cls, List<Map<String, String>> combinations) {
    Object o;
    try {
      if (isStepGroup) {
        o = YamlUtils.read(jsonNode.toString(), StepGroupElementConfig.class);
      } else {
        o = YamlUtils.read(jsonNode.toString(), cls);
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to read yaml.", e);
    }
    StrategyUtils.replaceExpressions(o, combinations.get(currentIteration), currentIteration, totalCount, null);
    JsonNode resolvedJsonNode;
    if (isStepGroup) {
      resolvedJsonNode = JsonPipelineUtils.asTree(o);
    } else {
      resolvedJsonNode = JsonPipelineUtils.asTree(o);
    }
    return resolvedJsonNode;
  }

  // This is used by CI during the CIInitStep. CI expands the steps YAML having strategy and the expanded YAML is then
  // executed.
  public StrategyInfo expandJsonNode(List<String> keys, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxes, ParameterField<List<ExcludeConfig>> exclude,
      ParameterField<Integer> maxConcurrencyParameterField, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    List<Map<String, String>> combinations = new ArrayList<>();
    List<List<Integer>> matrixMetadata = new ArrayList<>();
    fetchCombinations(new LinkedHashMap<>(), axes, expressionAxes, combinations,
        ParameterField.isBlank(exclude) ? null : exclude.getValue(), matrixMetadata, keys, 0, new LinkedList<>());
    int totalCount = combinations.size();
    if (totalCount == 0) {
      throw new InvalidRequestException(
          "Total number of iterations found to be 0 for this strategy. Please check pipeline yaml");
    }

    if (maxExpansionLimit.isPresent()) {
      if (totalCount > maxExpansionLimit.get()) {
        throw new InvalidYamlException("Iteration count is beyond the supported limit of " + maxExpansionLimit.get());
      }
    }

    List<JsonNode> jsonNodes = new ArrayList<>();
    int currentIteration = 0;
    for (List<Integer> matrixData : matrixMetadata) {
      JsonNode clonedNode = StrategyUtils.replaceExpressions(
          JsonPipelineUtils.asTree(jsonNode), combinations.get(currentIteration), currentIteration, totalCount, null);
      StrategyUtils.modifyJsonNode(clonedNode, matrixData.stream().map(String::valueOf).collect(Collectors.toList()));
      jsonNodes.add(clonedNode);
      currentIteration++;
    }
    int maxConcurrency = jsonNodes.size();
    if (!ParameterField.isBlank(maxConcurrencyParameterField)) {
      maxConcurrency = maxConcurrencyParameterField.getValue();
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(maxConcurrency).build();
  }

  /**
   *
   * This function is used to recursively calculate the number of combinations that can be there for the
   * given matrix configuration in the yaml.
   * It takes care of excluding a given combination.
   *
   * @param currentCombinationRef - Reference variable to store the current combination
   * @param axes - The axes defined in the yaml
   * @param expressionAxes - The axes defined in the yaml
   * @param combinationsRef - The number of total combinations that are there till now
   * @param exclude - exclude as mentioned in the yaml
   * @param matrixMetadataRef - The metadata related to the combination number of values
   * @param keys - The list of keys as mentioned in the yaml under axes
   * @param index -  the current index of the key we are on
   * @param indexPath - the path till the current iteration for the indexes like [0,2,1] i.e the matrix combination
   *     index
   */
  public void fetchCombinations(Map<String, String> currentCombinationRef, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxes, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    if (shouldExclude(exclude, currentCombinationRef)) {
      return;
    }
    // This means we have traversed till the end therefore add it as part of matrix combination
    if (keys.size() == index) {
      combinationsRef.add(new HashMap<>(currentCombinationRef));
      // Add the path we chose to compute the current combination.
      matrixMetadataRef.add(new ArrayList<>(indexPath));
      return;
    }

    String key = keys.get(index);
    /*
     * There are 3 cases which can happen over here:
     * 1. If a key is present in axes then we will treat value for this key as primitive
     * 2.If a key is present in expressionAxes then it can be either the string or the object that is stored as value
     */
    if (axes.containsKey(key)) {
      handleAxes(key, currentCombinationRef, axes, expressionAxes, combinationsRef, exclude, matrixMetadataRef, keys,
          index, indexPath);
    } else if (expressionAxes.containsKey(key)) {
      handleExpression(key, currentCombinationRef, axes, expressionAxes, combinationsRef, exclude, matrixMetadataRef,
          keys, index, indexPath);
    }
  }

  // Check if currentCombinationRef should be excluded from the combinations that will be executed.
  private boolean shouldExclude(List<ExcludeConfig> exclude, Map<String, String> currentCombinationRef) {
    if (exclude == null) {
      return false;
    }
    for (ExcludeConfig excludeConfig : exclude) {
      Set<String> excludeKeySet = excludeConfig.getExclude().keySet();
      int count = 0;
      for (String key : excludeKeySet) {
        if (!currentCombinationRef.containsKey(key)) {
          return false;
        }
        if (currentCombinationRef.get(key).equals(excludeConfig.getExclude().get(key))) {
          count++;
        }
      }
      if (count == excludeKeySet.size()) {
        return true;
      }
    }
    return false;
  }

  // Adding the combination when the key is provided with primitive value.
  private void handleAxes(String key, Map<String, String> currentCombinationRef, Map<String, AxisConfig> primitiveAxes,
      Map<String, ExpressionAxisConfig> expressionAxes, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    AxisConfig axisValues = primitiveAxes.get(key);
    int i = 0;
    // If value is null in one of the axis, then there are two cases:
    // Either null was provided at the start in the pipeline or an expression was present which we were not able to
    // resolve.
    if (axisValues.getAxisValue().getValue() == null) {
      throw new InvalidYamlException("Expected List but found null value in one of the axis with key:" + key);
    }
    for (String value : axisValues.getAxisValue().getValue()) {
      currentCombinationRef.put(key, value);
      indexPath.add(i);
      fetchCombinations(currentCombinationRef, primitiveAxes, expressionAxes, combinationsRef, exclude,
          matrixMetadataRef, keys, index + 1, indexPath);
      currentCombinationRef.remove(key);
      indexPath.remove(indexPath.size() - 1);
      i++;
    }
  }

  // Adding the combination when the key is provided with expressions.
  private void handleExpression(String key, Map<String, String> currentCombinationRef, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxisConfigMap, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    ExpressionAxisConfig axisValues = expressionAxisConfigMap.get(key);
    if (axisValues.getExpression().getValue() == null) {
      throw new InvalidYamlException(
          "Unable to resolve the expression for " + key + ". Please ensure that expression is correct.");
    }
    Object value = axisValues.getExpression().getValue();
    if (!(value instanceof List)) {
      throw new InvalidYamlException(
          "Expression provided did not resolve into a list of string/objects. Please ensure that expression is correct");
    }

    int i = 0;
    for (Object val : (List<Object>) value) {
      if (val instanceof String) {
        currentCombinationRef.put(key, (String) val);
      } else {
        try {
          currentCombinationRef.put(key, JsonUtils.asJson(val));
        } catch (Exception ex) {
          throw new InvalidRequestException(
              String.format("Either Map or String expected. Found value: [%s] for this key [%s]", key, val.toString()));
        }
      }
      indexPath.add(i);
      fetchCombinations(currentCombinationRef, axes, expressionAxisConfigMap, combinationsRef, exclude,
          matrixMetadataRef, keys, index + 1, indexPath);
      currentCombinationRef.remove(key);
      indexPath.remove(indexPath.size() - 1);
      i++;
    }
  }
}
