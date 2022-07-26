/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.evaluation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ApprovalStepNGException;
import io.harness.jira.JiraIssueNG;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.jira.JiraExpressionEvaluator;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@UtilityClass
public class CriteriaEvaluator {
  public boolean evaluateCriteria(JiraIssueNG issue, CriteriaSpecDTO criteriaSpec) {
    if (criteriaSpec instanceof JexlCriteriaSpecDTO) {
      return evaluateJexlCriteria(issue, (JexlCriteriaSpecDTO) criteriaSpec);
    } else if (criteriaSpec instanceof KeyValuesCriteriaSpecDTO) {
      return evaluateKeyValuesCriteria(issue, (KeyValuesCriteriaSpecDTO) criteriaSpec);
    } else {
      throw new ApprovalStepNGException("Unknown criteria type", true);
    }
  }

  private boolean evaluateJexlCriteria(JiraIssueNG issue, JexlCriteriaSpecDTO jexlCriteriaSpec) {
    String expression = jexlCriteriaSpec.getExpression();
    if (StringUtils.isBlank(expression)) {
      throw new ApprovalStepNGException("Expression cannot be blank in jexl criteria", true);
    }

    try {
      JiraExpressionEvaluator jiraExpressionEvaluator = new JiraExpressionEvaluator(issue);
      Object result = jiraExpressionEvaluator.evaluateExpression(expression);
      if (result instanceof Boolean) {
        return (boolean) result;
      } else {
        throw new ApprovalStepNGException("Non boolean result while evaluating approval condition", true);
      }
    } catch (Exception e) {
      throw new ApprovalStepNGException(
          String.format("Error while evaluating approval condition. expression: %s%n", expression), true, e);
    }
  }

  private boolean evaluateKeyValuesCriteria(JiraIssueNG issue, KeyValuesCriteriaSpecDTO keyValueCriteriaSpec) {
    List<ConditionDTO> conditions = keyValueCriteriaSpec.getConditions();
    if (isEmpty(conditions)) {
      throw new ApprovalStepNGException("Conditions in KeyValues criteria can't be empty", true);
    }

    boolean matchAnyCondition = keyValueCriteriaSpec.isMatchAnyCondition();
    for (ConditionDTO condition : conditions) {
      try {
        Operator operator = condition.getOperator();
        String standardValue = condition.getValue();
        Object issueValue = issue.getFields().get(condition.getKey());
        boolean currentResult = ConditionEvaluator.evaluate(issueValue, standardValue, operator);
        if (matchAnyCondition) {
          if (currentResult) {
            return true;
          }
        } else {
          if (!currentResult) {
            return false;
          }
        }
      } catch (Exception e) {
        throw new ApprovalStepNGException(
            String.format("Error while evaluating condition %s", condition.toString()), true, e);
      }
    }
    return !matchAnyCondition;
  }
}
