/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.pms.expressions;

import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.ExpressionDetailResponse;
import io.harness.execution.ExpressionDetails;
import io.harness.execution.ExpressionDryRunDetail;
import io.harness.execution.ExpressionDryRunResponse;
import io.harness.execution.NodeExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.ExpressionChecker;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ejb.Singleton;

@Singleton
public class ExpressionDetailServiceImpl implements ExpressionDetailService {
  @Inject PlanExpansionService expansionService;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject PmsEngineExpressionService engineExpressionService;
  @Inject NodeExecutionService nodeExecutionService;
  private Set<String> terminalKeys = Set.of("output");
  @Override
  public ExpressionDetailResponse getExpressionResponse(String planExecutionId, String expression) {
    String expandedJson = expansionService.get(planExecutionId);
    JsonNode jsonNode = JsonPipelineUtils.readTree(expandedJson);
    return ExpressionDetailResponse.builder().expressionDetails(getAllExpressions(jsonNode, expression)).build();
  }

  @Override
  public ExpressionDryRunResponse resolveExpressions(String planExecutionId, String yaml) {
    ExpressionDryRunResponse expressionDetailResponse = ExpressionDryRunResponse.builder().isSuccess(true).build();
    String expandedJsonString = expansionService.get(planExecutionId);
    JsonNode expendedJson = JsonPipelineUtils.readTree(expandedJsonString);
    Map<String, Ambiance> fqnToAmbianceMap = getFQNToAmbianceMap(planExecutionId);
    Map<String, Object> expandedJsonMap = JsonPipelineUtils.jsonNodeToMap(expendedJson);
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    fullMap.keySet().forEach(key -> {
      String value = HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(fullMap.get(key).toString());
      List<String> innerExpressions = EngineExpressionEvaluator.findExpressions(value);
      innerExpressions.forEach(expression -> {
        String fqnTillLastGroup = getFQNTillLastGroup(key.getExpressionFqn(), expendedJson);
        Ambiance ambiance = fqnToAmbianceMap.get(fqnTillLastGroup);
        try {
          Object evaluatedValue =
              engineExpressionService.resolve(ambiance, expression, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
          expressionDetailResponse.addExpressionDryRUnDetail(ExpressionDryRunDetail.builder()
                                                                 .expression(expression)
                                                                 .resolvedValue((String) evaluatedValue)
                                                                 .isResolved(true)
                                                                 .build());
        } catch (Exception e) {
          String suggestedExpression = handleInvalidExpressionStartingFromGroup(ambiance, expression, expandedJsonMap);
          if (suggestedExpression.equals(expression)) {
            suggestedExpression = null;
          }
          expressionDetailResponse.setSuccess(false);
          expressionDetailResponse.addExpressionDryRUnDetail(ExpressionDryRunDetail.builder()
                                                                 .expression(expression)
                                                                 .suggestedExpression(suggestedExpression)
                                                                 .isResolved(false)
                                                                 .build());
        }
      });
    });

    return expressionDetailResponse;
  }

  private String handleInvalidExpressionStartingFromGroup(
      Ambiance ambiance, String expression, Map<String, Object> expandedJsonMap) {
    expression = expression.replace(EXPR_START, "").replace(EXPR_END, "");
    List<String> expressionKeys = Arrays.asList(expression.split("\\."));
    if (ExpandedJsonFunctorUtils.GROUP_ALIASES.containsKey(expressionKeys.get(0))) {
      Map<String, String> groupExpressionToGroupMap = new HashMap<>();
      expression = ExpandedJsonFunctorUtils.getSuggestedExpressionForGroup(
          new ArrayList<>(ambiance.getLevelsList()), expressionKeys.get(0), expressionKeys, groupExpressionToGroupMap);
      expression = ExpressionChecker.checkExpression(expression, expandedJsonMap);

      for (Map.Entry<String, String> entry : groupExpressionToGroupMap.entrySet()) {
        expression = expression.replace(entry.getKey(), entry.getValue());
      }
    } else if (expression.startsWith("pipeline.")) {
      expression = ExpressionChecker.checkExpression(expression, expandedJsonMap);
    }
    return EngineExpressionEvaluator.createExpression(expression);
  }

  public List<ExpressionDetails> getAllExpressions(JsonNode jsonNode, String expression) {
    List<ExpressionDetails> finalExpList = new ArrayList<>();
    JsonNode currentJsonPointer = jsonNode;
    // pipeline.stages.cd.spec.execution.steps.ShellScript_1.name
    List<String> expressionKeys = Arrays.asList(expression.split("\\."));
    for (int index = 0; index < expressionKeys.size() - 1; index++) {
      String key = expressionKeys.get(index);
      if (terminalKeys.contains(key) || currentJsonPointer.get(key) == null) {
        break;
      }
      if (currentJsonPointer.get(key).get("group") != null) {
        String group = currentJsonPointer.get(key).get("group").asText();
        String currentExp = String.join(".", expressionKeys.subList(index, expressionKeys.size()));
        finalExpList.add(ExpressionDetails.builder().scope(group).expressionBlock(currentExp).build());
        if (isCurrentKeyAnId(currentJsonPointer.get(key), key)) {
          currentExp =
              group.toLowerCase() + "." + String.join(".", expressionKeys.subList(index + 1, expressionKeys.size()));
          finalExpList.add(ExpressionDetails.builder().scope(group).expressionBlock(currentExp).build());
        }
      }
      currentJsonPointer = currentJsonPointer.get(key);
    }
    return handleStaticAliases(finalExpList);
  }

  private boolean isCurrentKeyAnId(JsonNode jsonNode, String key) {
    return jsonNode.get("identifier") != null && key.equals(jsonNode.get("identifier").asText())
        || jsonNode.get("stepInputs") != null && jsonNode.get("stepInputs").get("identifier") != null
        && key.equals(jsonNode.get("stepInputs").get("identifier").asText());
  }

  private List<ExpressionDetails> handleStaticAliases(List<ExpressionDetails> expressionDetailsList) {
    List<ExpressionDetails> finalList = new ArrayList<>(expressionDetailsList);
    Map<String, String> aliases = new HashMap<>();
    List<PmsSdkInstance> pmsSdkInstances = pmsSdkInstanceService.getActiveInstances();
    pmsSdkInstances.stream().map(PmsSdkInstance::getStaticAliases).forEach(aliases::putAll);

    for (ExpressionDetails expressionDetails : expressionDetailsList) {
      String exp = expressionDetails.getExpressionBlock();
      aliases.forEach((key, val) -> {
        if (exp.contains(val)) {
          String newExp = exp.replace(val, key);
          finalList.add(ExpressionDetails.builder()
                            .expressionBlock(newExp)
                            .scope(expressionDetails.getScope())
                            .description(expressionDetails.getDescription())
                            .build());
        }
      });
    }
    return finalList;
  }

  private Map<String, Ambiance> getFQNToAmbianceMap(String planExecutionId) {
    Map<String, Ambiance> fqnToAmbianceMap = new HashMap<>();
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.getByPlanExecutionIdWithProjections(planExecutionId, NodeProjectionUtils.withAmbiance);

    nodeExecutions.forEach(nodeExecution -> {
      Ambiance ambiance = nodeExecution.getAmbiance();

      String fqn = ambiance.getLevelsList().stream().map(Level::getIdentifier).collect(Collectors.joining("."));
      fqnToAmbianceMap.put(fqn, ambiance);
    });
    return fqnToAmbianceMap;
  }

  private String getFQNTillLastGroup(String expression, JsonNode jsonNode) {
    JsonNode currentJsonPointer = jsonNode;
    int latestGroupIndex = 0;
    List<String> expressionKeys = Arrays.asList(expression.split("\\."));
    for (int index = 0; index < expressionKeys.size() - 1; index++) {
      String key = expressionKeys.get(index);
      if (terminalKeys.contains(key) || currentJsonPointer.get(key) == null) {
        break;
      }
      if (currentJsonPointer.get(key).get("group") != null) {
        latestGroupIndex = index;
      }
      currentJsonPointer = currentJsonPointer.get(key);
    }

    return String.join(".", expressionKeys.subList(0, latestGroupIndex + 1));
  }
}
