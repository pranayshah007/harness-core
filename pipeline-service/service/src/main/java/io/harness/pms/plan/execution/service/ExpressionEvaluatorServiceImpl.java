/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.expressions.YamlExpressionEvaluator;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetail;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetail.ExpressionEvaluationDetailBuilder;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetailDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExpressionEvaluatorServiceImpl implements ExpressionEvaluatorService {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PmsEngineExpressionService engineExpressionService;
  @Override
  public ExpressionEvaluationDetailDTO evaluateExpression(String planExecutionId, String yaml) {
    YamlExpressionEvaluator yamlExpressionEvaluator = new YamlExpressionEvaluator(yaml);

    // expression along with their fqn from the given yaml. Here the string can be an object too (eg -> echo
    // <+pipeline.name>)
    Map<FQN, String> fqnObjectMap = RuntimeInputFormHelper.fetchExpressionAndFqnFromYaml(yaml);

    Map<String, ExpressionEvaluationDetail> mapData = new HashMap<>();

    // This stores the list of all fqns for ambiance for expressions that could not be resolved via yaml
    List<String> unresolvedAmbianceFqns = new ArrayList<>();

    // This stores the Orginal fqn to unresolved expressions in yamk
    Map<String, List<String>> fqnToUnresolvedExpressionsViaYaml = new HashMap<>();

    for (Map.Entry<FQN, String> entry : fqnObjectMap.entrySet()) {
      String yamlFqn = entry.getKey().getExpressionFqn();
      String value = entry.getValue();
      List<String> unresolvedExpression = evaluateExpression(mapData, yamlFqn, value, yamlExpressionEvaluator);
      fqnToUnresolvedExpressionsViaYaml.put(yamlFqn, unresolvedExpression);
      String fqnTillLastGroup = fqnForAmbiance(yamlFqn);
      unresolvedAmbianceFqns.add(fqnTillLastGroup);
    }

    // This fetches all the leaf node executions for the given plan execution id.
    CloseableIterator<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchAllLeavesUsingPlanExecutionId(planExecutionId, NodeProjectionUtils.withAmbiance);
    Map<String, Ambiance> fqnToAmbianceMap = getFQNToAmbianceMap(nodeExecutions, unresolvedAmbianceFqns);

    for (Map.Entry<FQN, String> entry : fqnObjectMap.entrySet()) {
      String fqn = entry.getKey().getExpressionFqn();

      // We should call ambiance expression resolution only if the expression was unresolved via yaml
      if (fqnToUnresolvedExpressionsViaYaml.containsKey(fqn)) {
        String fqnTillLastGroup = fqnForAmbiance(fqn);
        Ambiance ambiance = fqnToAmbianceMap.get(fqnTillLastGroup);
        evaluateExpressionUsingAmbiance(mapData, fqn, fqnToUnresolvedExpressionsViaYaml.get(fqn), ambiance);
      }
    }
    return ExpressionEvaluationDetailDTO.builder().mapExpression(mapData).compiledYaml(yaml).build();
  }

  /**
   * This is used to fetch the ambiance fqn to the nearest pipeline,stage or step
   * For example:
   * Given this yaml:
   * pipeline:
   *   stages:
   *     stage:
   *       spec:
   *         execution:
   *           steps:
   *             - step:
   *                 identifier: step1
   *                 spec:
   *                   script: echo <+pipeline.variables.name1>
   *
   *
   * The fqn to the place where the expression is pipeline.stages.stage.spec.execution.steps.step1.spec.script
   *
   * This fqn would not be anywhere in ambiance since via ambiance this would give us the fqn till step1
   *
   * The function does the same, it removes additional names in the fully qualified name that might not be present in
   * ambiance.
   *
   * To Summarize:
   *  - We traverse all the names in the path to the given node.
   *  - We get to the nearest step/stage/pipeline node name as they might be present in the ambiance.
   *
   * @param fqn
   * @return
   */
  private String fqnForAmbiance(String fqn) {
    List<String> qualifiedName = Arrays.asList(fqn.split("\\."));
    int n = qualifiedName.size();
    int indexToNearestGroup;
    for (indexToNearestGroup = n - 1; indexToNearestGroup > 0; indexToNearestGroup--) {
      if (qualifiedName.get(indexToNearestGroup).equals("stages")
          || qualifiedName.get(indexToNearestGroup).equals("steps")
          || qualifiedName.get(indexToNearestGroup).equals("pipeline")) {
        break;
      }
    }
    // Add back the
    List<String> result = new ArrayList<>();
    for (int j = 0; j <= indexToNearestGroup + 1; j++) {
      result.add(qualifiedName.get(j));
    }
    return String.join(".", result);
  }

  /**
   *
   * This traverses all the leaf node executions of the given plan execution id.
   * If the fqn of that ambiance is a superset of the unresolved fqn set, we use that ambiance to resolve any expression
   * @param nodeExecutions
   * @param unresolvedFqnSet
   * @return
   */
  public Map<String, Ambiance> getFQNToAmbianceMap(
      CloseableIterator<NodeExecution> nodeExecutions, List<String> unresolvedFqnSet) {
    Map<String, Ambiance> fqnToAmbianceMap = new HashMap<>();

    while (nodeExecutions.hasNext()) {
      NodeExecution nodeExecution = nodeExecutions.next();
      Ambiance ambiance = nodeExecution.getAmbiance();

      String fqnTillLastGroupWithoutStrategy = getFqnTillLastGroupInAmbianceWithoutStrategy(ambiance);
      for (String usedFqn : unresolvedFqnSet) {
        if (fqnTillLastGroupWithoutStrategy.contains(usedFqn)) {
          fqnToAmbianceMap.put(usedFqn, ambiance);
        }
      }
    }
    return fqnToAmbianceMap;
  }

  private String getFqnTillLastGroupInAmbianceWithoutStrategy(Ambiance ambiance) {
    List<Level> levelsList = ambiance.getLevelsList();
    int lastGroupIndex = ambiance.getLevelsCount() - 1;
    for (int index = ambiance.getLevelsCount() - 1; index >= 0; index--) {
      if (EmptyPredicate.isNotEmpty(levelsList.get(index).getGroup())) {
        lastGroupIndex = index;
        break;
      }
    }
    return levelsList.stream()
        .limit(lastGroupIndex + 1)
        .filter(level -> !AmbianceUtils.hasStrategyMetadata(level))
        .map(Level::getIdentifier)
        .collect(Collectors.joining("."));
  }

  /**
   * returns expressions that are unresolved by yaml
   *
   * @param mapData
   * @param key
   * @param value
   * @param yamlExpressionEvaluator
   * @return
   */
  public List<String> evaluateExpression(Map<String, ExpressionEvaluationDetail> mapData, String key, String value,
      YamlExpressionEvaluator yamlExpressionEvaluator) {
    // There can be n number of expression in the value (eg -> echo <+pipeline.name> \n echo <+pipeline.variables.var1>)
    List<String> expressions = EngineExpressionEvaluator.findExpressions(value);
    List<String> unresolvedExpressions = new ArrayList<>();
    for (String expression : expressions) {
      ExpressionEvaluationDetailBuilder expressionEvaluationDetailBuilder =
          ExpressionEvaluationDetail.builder().originalExpression(expression).fqn(key);
      try {
        // First resolve using yaml
        String result = resolveFromYaml(yamlExpressionEvaluator, getExpressionForYamlEvaluator(key, expression));
        boolean resolvedByYaml = true;

        // If result is null, try evaluating with ambiance
        if (result == null) {
          unresolvedExpressions.add(expression);
          continue;
        }

        mapData.put(key + "+" + expression,
            expressionEvaluationDetailBuilder.resolvedValue(result).resolvedByYaml(resolvedByYaml).build());

      } catch (Exception e) {
        mapData.put(key + "+" + expression, expressionEvaluationDetailBuilder.error(e.getMessage()).build());
      }
    }
    return unresolvedExpressions;
  }

  public void evaluateExpressionUsingAmbiance(
      Map<String, ExpressionEvaluationDetail> mapData, String key, List<String> expressions, Ambiance ambiance) {
    for (String expression : expressions) {
      // There can be n number of expression in the value (eg -> echo <+pipeline.name> \n echo
      // <+pipeline.variables.var1>)
      ExpressionEvaluationDetailBuilder expressionEvaluationDetailBuilder =
          ExpressionEvaluationDetail.builder().originalExpression(expression).fqn(key);
      try {
        String result = resolveValueFromAmbiance(ambiance, expression);
        mapData.put(key + "+" + expression,
            expressionEvaluationDetailBuilder.resolvedValue(result).resolvedByYaml(false).build());

      } catch (Exception e) {
        mapData.put(key + "+" + expression, expressionEvaluationDetailBuilder.error(e.getMessage()).build());
      }
    }
  }

  /*
  For fqn -> pipeline.stages.cs.spec.execution.steps.ShellScript_2.spec.source.spec.script

  Expression -> <+execution.steps.ShellScript_1.name>

  This will return <+pipeline.stages.cs.spec.execution.steps.ShellScript_1.name>
   */
  public String getExpressionForYamlEvaluator(String fqn, String expression) {
    int dotIndex = expression.indexOf('.');
    String firstString = expression.substring(2, dotIndex);
    if (fqn.contains(firstString)) {
      String fqnPrefix = fqn.substring(0, fqn.indexOf(firstString));
      return "<+" + fqnPrefix + expression.substring(2);
    }
    return expression;
  }

  public String resolveValueFromAmbiance(Ambiance ambiance, String expression) {
    try {
      Object evaluatedObject =
          engineExpressionService.resolve(ambiance, expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      if (!YamlUtils.NULL_STR.equals(evaluatedObject)) {
        return HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(
            JsonPipelineUtils.getJsonString(evaluatedObject));
      }
    } catch (Exception e) {
      log.error("Not able to resolve the expression from ambiance {}", expression);
    }
    return null;
  }

  public String resolveFromYaml(YamlExpressionEvaluator yamlExpressionEvaluator, String expression) {
    try {
      Object evaluatedObject = yamlExpressionEvaluator.resolve(expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      if (!YamlUtils.NULL_STR.equals(evaluatedObject)) {
        return HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(
            JsonPipelineUtils.getJsonString(evaluatedObject));
      }
    } catch (Exception e) {
      log.error("Not able to resolve the expression from yaml{}", expression);
    }
    return null;
  }
}
