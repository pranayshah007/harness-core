/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExpressionEvaluatorServiceImpl implements ExpressionEvaluatorService {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PmsEngineExpressionService engineExpressionService;
  @Override
  public ExpressionEvaluationDetailDTO evaluateExpression(String planExecutionId, String yaml) {
    YamlExpressionEvaluator yamlExpressionEvaluator = new YamlExpressionEvaluator(yaml);
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchAllWithPlanExecutionId(planExecutionId, NodeProjectionUtils.withAmbiance);

    // This map matches the fqn with each Ambiance. Here the fqn is calculated using level's identifier (till where last
    // group in level is not null) of each ambiance.
    Map<String, Ambiance> fqnToAmbianceMap = getFQNToAmbianceMap(nodeExecutions);

    // expression along with their fqn from the given yaml. Here the string can be an object too (eg -> echo
    // <+pipeline.name>)
    Map<FQN, String> fqnObjectMap = RuntimeInputFormHelper.fetchExpressionAndFqnFromYaml(yaml);

    Map<String, ExpressionEvaluationDetail> mapData = new HashMap<>();
    for (Map.Entry<FQN, String> entry : fqnObjectMap.entrySet()) {
      String fqn = entry.getKey().getExpressionFqn();
      String value = entry.getValue();

      // calculating the fqn to find the ambiance which will resolve the expression
      String fqnTillLastGroup = getFQNTillLastGroup(fqn, fqnToAmbianceMap);
      Ambiance ambiance = fqnToAmbianceMap.get(fqnTillLastGroup);
      evaluateExpression(mapData, ambiance, fqn, value, yamlExpressionEvaluator);
    }

    return ExpressionEvaluationDetailDTO.builder().mapExpression(mapData).compiledYaml(yaml).build();
  }

  private String getFQNTillLastGroup(String fqn, Map<String, Ambiance> lastGroupFqnToAmbianceMap) {
    List<String> expressionKeys = Arrays.asList(fqn.split("\\."));
    String subStringFqn = expressionKeys.get(0);
    String resultedFqn = subStringFqn;

    // Using the fqn, we are finding the fqn which can resolve the expression. For that we have
    // lastGroupFqnToAmbianceMap which stores the fqn with Ambiance
    /*
    For example:
    pipeline.stages.cs.spec.execution.steps.ShellScript_1.timeout: <+pipeline.variables.var1>

    For fqn: pipeline.stages.cs.spec.execution.steps.ShellScript_1.timeout, this function will return
    pipeline.stages.cs.spec.execution.steps.ShellScript_1
     */
    for (int index = 1; index < expressionKeys.size(); index++) {
      subStringFqn += "." + expressionKeys.get(index);
      if (lastGroupFqnToAmbianceMap.containsKey(subStringFqn)) {
        resultedFqn = subStringFqn;
      }
    }

    return resultedFqn;
  }

  private Map<String, Ambiance> getFQNToAmbianceMap(List<NodeExecution> nodeExecutions) {
    Map<String, Ambiance> fqnToAmbianceMap = new HashMap<>();

    nodeExecutions.forEach(nodeExecution -> {
      Ambiance ambiance = nodeExecution.getAmbiance();

      String fqn = getFqnTillLastGroupInAmbiance(ambiance);
      fqnToAmbianceMap.put(fqn, ambiance);
    });
    return fqnToAmbianceMap;
  }

  private String getFqnTillLastGroupInAmbiance(Ambiance ambiance) {
    List<Level> levelsList = ambiance.getLevelsList();
    int lastGroupIndex = ambiance.getLevelsCount() - 1;
    for (int index = ambiance.getLevelsCount() - 1; index >= 0; index--) {
      if (levelsList.get(index).getGroup() != null) {
        lastGroupIndex = index;
        break;
      }
    }
    return levelsList.stream().limit(lastGroupIndex + 1).map(Level::getIdentifier).collect(Collectors.joining("."));
  }

  public void evaluateExpression(Map<String, ExpressionEvaluationDetail> mapData, Ambiance ambiance, String key,
      String value, YamlExpressionEvaluator yamlExpressionEvaluator) {
    // There can be n number of expression in the value (eg -> echo <+pipeline.name> \n echo <+pipeline.variables.var1>)
    List<String> expressions = EngineExpressionEvaluator.findExpressions(value);

    for (String expression : expressions) {
      ExpressionEvaluationDetailBuilder expressionEvaluationDetailBuilder =
          ExpressionEvaluationDetail.builder().originalExpression(expression).fqn(key);
      try {
        // First resolve using yaml
        String result = resolveFromYaml(yamlExpressionEvaluator, getExpressionForYamlEvaluator(key, expression));
        boolean resolvedByYaml = true;

        // If result is null, try evaluating with ambiance
        if (result == null) {
          result = resolveValueFromAmbiance(ambiance, expression);
          resolvedByYaml = false;
        }

        mapData.put(key + "+" + expression,
            expressionEvaluationDetailBuilder.resolvedValue(result).resolvedByYaml(resolvedByYaml).build());

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
  private static String getExpressionForYamlEvaluator(String fqn, String expression) {
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
