/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class StagesExpressionExtractor {
  public String replaceExpressionsWithJsonNode(String pipelineYaml, Map<String, String> expressionValues) {
    if (EmptyPredicate.isEmpty(expressionValues)) {
      return pipelineYaml;
    }
    for (Map.Entry<String, String> entry : expressionValues.entrySet()) {
      String expression = entry.getKey();
      if (!NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, expression)) {
        log.error(expression + " is not a syntactically valid pipeline expression.");
        throw new InvalidRequestException(
            expression + " is not a syntactically valid pipeline expression. Is the expression surrounded by <+ >?");
      }
    }
    JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
    StagesExpressionEvaluator expressionEvaluator = new StagesExpressionEvaluator(expressionValues);
    pipelineJsonNode = (JsonNode) expressionEvaluator.resolve(
        pipelineJsonNode, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return YamlUtils.writeYamlString(pipelineJsonNode);
  }

  public Set<String> getNonLocalExpressions(String pipelineYaml, List<String> stageIdentifiers) {
    Map<String, List<String>> allExpressions = getAllExpressionsInListOfStages(pipelineYaml, stageIdentifiers);
    return removeLocalExpressions(allExpressions);
  }

  Map<String, List<String>> getAllExpressionsInListOfStages(String pipelineYaml, List<String> stageIdentifiers) {
    Map<String, List<String>> stageIdToListOfExpressions = new LinkedHashMap<>();
    List<BasicStageInfo> stageYamlList = getStageYamlList(pipelineYaml, stageIdentifiers);
    stageYamlList.forEach(stageYaml -> {
      List<String> listOfExpressions = getListOfExpressions(stageYaml.getYaml());
      stageIdToListOfExpressions.put(stageYaml.getIdentifier(), listOfExpressions);
    });
    return stageIdToListOfExpressions;
  }

  List<BasicStageInfo> getStageYamlList(String pipelineYaml, List<String> stageIdentifiers) {
    try {
      List<BasicStageInfo> stageYamlList = new ArrayList<>();
      YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
      List<YamlNode> stagesYamlNodes = pipelineYamlField.getNode()
                                           .getField(YAMLFieldNameConstants.PIPELINE)
                                           .getNode()
                                           .getField(YAMLFieldNameConstants.STAGES)
                                           .getNode()
                                           .asArray();
      for (YamlNode stageYamlNode : stagesYamlNodes) {
        if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
          BasicStageInfo basicStageInfoWithYaml = getBasicStageInfoWithYaml(stageYamlNode);
          if (stageIdentifiers.contains(basicStageInfoWithYaml.getIdentifier())) {
            stageYamlList.add(basicStageInfoWithYaml);
          }
          continue;
        }
        List<YamlNode> parallelStagesYamlNode =
            stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
        for (YamlNode parallelStageYamlNode : parallelStagesYamlNode) {
          BasicStageInfo basicStageInfoWithYaml = getBasicStageInfoWithYaml(parallelStageYamlNode);
          if (stageIdentifiers.contains(basicStageInfoWithYaml.getIdentifier())) {
            stageYamlList.add(basicStageInfoWithYaml);
          }
        }
      }
      return stageYamlList;
    } catch (IOException e) {
      log.error("Could not read pipeline yaml while extracting stage yaml list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting stage yaml list");
    }
  }

  @VisibleForTesting
  BasicStageInfo getBasicStageInfoWithYaml(YamlNode stageYamlNode) throws IOException {
    String identifier = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getIdentifier();
    String name = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getName();
    String type = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getType();
    String yaml = YamlPipelineUtils.getYamlString(stageYamlNode.getCurrJsonNode());
    return BasicStageInfo.builder().identifier(identifier).name(name).type(type).yaml(yaml).build();
  }

  List<String> getListOfExpressions(String stageYaml) {
    return NGExpressionUtils.getListOfExpressions(stageYaml);
  }

  Set<String> removeLocalExpressions(Map<String, List<String>> expressionsMap) {
    Set<String> expressionsToOtherStages = new HashSet<>();
    Set<String> stageIdentifiers = expressionsMap.keySet();
    for (String stageIdentifier : stageIdentifiers) {
      List<String> allExpressions = expressionsMap.get(stageIdentifier);
      List<String> otherStageExpressions =
          allExpressions.stream()
              .filter(expression -> {
                if (isLocalToStage(expression) || isReferringToNonStageValue(expression)) {
                  return false;
                }
                String stageInExpression = getStageIdentifierInExpression(expression);
                return stageInExpression != null && !stageIdentifiers.contains(stageInExpression);
              })
              .collect(Collectors.toList());
      expressionsToOtherStages.addAll(otherStageExpressions);
    }
    return expressionsToOtherStages;
  }

  boolean isLocalToStage(String expression) {
    String firstKeyOfExpression = NGExpressionUtils.getFirstKeyOfExpression(expression);
    return !firstKeyOfExpression.equals("pipeline") && !firstKeyOfExpression.equals("stages");
  }

  boolean isReferringToNonStageValue(String expression) {
    String[] wordsInExpression = expression.replace(EXPR_START, "").replace(EXPR_END, "").split("\\.");
    if (wordsInExpression.length < 2) {
      return true;
    }
    return wordsInExpression[0].equals("pipeline") && !wordsInExpression[1].equals("stages");
  }

  String getStageIdentifierInExpression(String expression) {
    String firstKeyOfExpression = NGExpressionUtils.getFirstKeyOfExpression(expression);
    String[] wordsInExpression = expression.replace(EXPR_START, "").replace(EXPR_END, "").split("\\.");
    if (firstKeyOfExpression.equals("pipeline")) {
      if (wordsInExpression.length > 2) {
        return wordsInExpression[2];
      } else {
        return null;
      }
    } else if (firstKeyOfExpression.equals("stages")) {
      if (wordsInExpression.length > 1) {
        return wordsInExpression[1];
      } else {
        return null;
      }
    }
    throw new InvalidRequestException(expression + " is not a pipeline level or stages level expression");
  }
}
