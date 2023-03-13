/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.execution.expansion.PlanExpansionConstants.OUTCOME;
import static io.harness.execution.expansion.PlanExpansionConstants.STEP_INPUTS;
import static io.harness.expression.EngineExpressionEvaluator.hasExpressions;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpandedJsonFunctorUtils {
  // Todo: Take this via evaluator
  List<String> PREFIX_COMBINATIONS = Lists.newArrayList(OUTCOME, STEP_INPUTS);

  public List<String> getExpressions(Ambiance ambiance, Map<String, String> groupAliases, String expression) {
    // If there is embedded expression inside it, then we should not split this into multiple expressions.
    if (hasExpressions(expression)) {
      return Collections.singletonList(expression);
    }
    // We split by . so that we can convert relative qualified names to fully qualified names.
    List<String> expressionKeys = Arrays.asList(expression.split("\\."));

    if (EmptyPredicate.isEmpty(expressionKeys)) {
      return null;
    }
    List<String> expressions = new ArrayList<>();
    String group = expressionKeys.get(0);
    String finalExpression = expression;
    List<Level> levels = new ArrayList<>(ambiance.getLevelsList());

    if (groupAliases.containsKey(group)) {
      finalExpression = getFullyQualifiedExpressionBasedOnGroup(levels, group, expressionKeys);
    } else {
      List<String> fullyQualifiedName = new ArrayList<>();
      fullyQualifiedName.add("expandedJson");
      for (Level level : levels) {
        if (level.getIdentifier().equals(group)) {
          break;
        }
        if (!level.getSkipExpressionChain() || level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
          fullyQualifiedName.add(level.getIdentifier());
        }
      }
      fullyQualifiedName.add(finalExpression);
      finalExpression = String.join(".", fullyQualifiedName);
    }
    expressions.add(finalExpression);
    expressions.addAll(fetchPrefixExpressions(finalExpression));
    return expressions;
  }

  /**
   *  This function expands the group to it's corresponding qualified name.
   *  For example: If we have stage as group, it will do the following:
   *   - Fetch levels from ambiance.
   *   - Reverse these levels
   *   - Traverse these levels to find the group. We need to find the last group therefore we have to reverse.
   *   - Create a list of string we get from traversal.
   *   - The list will be in reverse order, so we need to reverse it and then convert to string.
   */
  private String expandGroupExpression(List<Level> levels, String groupName) {
    // stage.identifier -> pipeline.stages.stageIdentifier
    Collections.reverse(levels);
    List<String> fullyQualifiedName = new ArrayList<>();
    boolean shouldAdd = false;
    for (Level level : levels) {
      if (groupName.equalsIgnoreCase(level.getGroup())) {
        shouldAdd = true;
      }
      if (shouldAdd) {
        if (!level.getSkipExpressionChain() || level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
          fullyQualifiedName.add(level.getIdentifier());
        }
      }
    }
    Collections.reverse(fullyQualifiedName);
    return String.join(".", fullyQualifiedName);
  }

  /**
   * This fetches all the combinations that can happen for the given expression.
   * In V1, we fetch in step parameters first and then in outputs
   * This adds all the combinations to the list. For example:
   * If user gives pipeline.stages.stage1.variables.varA
   *
   * This would expand that function such that we have stepInputs is in between every subsequent keys
   * and same goes for output too.
   * @param finalExpression
   * @return
   */
  private List<String> fetchPrefixExpressions(String finalExpression) {
    List<String> result = new ArrayList<>();
    List<String> fullyQualifiedNames = Arrays.asList(finalExpression.split("\\."));
    for (int i = 0; i < fullyQualifiedNames.size(); i++) {
      for (String prefix : PREFIX_COMBINATIONS) {
        List<String> tempList = new ArrayList<>(fullyQualifiedNames);
        tempList.add(i, prefix);
        result.add(String.join(".", tempList));
      }
    }
    return result;
  }

  private String getFullyQualifiedExpressionBasedOnGroup(
      List<Level> levels, String group, List<String> expressionKeys) {
    String groupExpressions = expandGroupExpression(levels, group);
    List<String> postFix = Lists.newArrayList("expandedJson");
    postFix.add(groupExpressions);
    postFix.addAll(expressionKeys.subList(1, expressionKeys.size()));
    return String.join(".", postFix);
  }
}
