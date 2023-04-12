/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.pms.expressions;

import io.harness.execution.ExpressionDetailResponse;
import io.harness.execution.ExpressionDetails;
import io.harness.execution.expansion.PlanExpansionService;
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
import javax.ejb.Singleton;

@Singleton
public class ExpressionDetailServiceImpl implements ExpressionDetailService {
  @Inject PlanExpansionService expansionService;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  private Set<String> terminalKeys = Set.of("output");
  @Override
  public ExpressionDetailResponse getExpressionResponse(String planExecutionId, String expression) {
    String expandedJson = expansionService.get(planExecutionId);
    JsonNode jsonNode = JsonPipelineUtils.readTree(expandedJson);
    return ExpressionDetailResponse.builder().expressionDetails(getAllExpressions(jsonNode, expression)).build();
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
}
