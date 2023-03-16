/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.conditionchecker;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.CONTAINS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.DOES_NOT_CONTAIN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.ENDS_WITH_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.REGEX_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.STARTS_WITH_OPERATOR;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidArgumentsException;

import com.google.common.base.Splitter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class ConditionEvaluator {
  public boolean evaluate(String input, String standard, String operator) {
    if (isBlank(input) && isBlank(standard) && isBlank(operator)) {
      return true;
    }
    OperationEvaluator operationEvaluator = evaluatorMap.get(operator);
    if (operationEvaluator == null) {
      throw new InvalidArgumentsException(
          String.format("Operator [%s] is not supported. Please user valid one", operator), USER_SRE);
    }

    return operationEvaluator.evaluate(input, standard);
  }

  static Map<String, OperationEvaluator> evaluatorMap = new HashMap<>();
  static {
    evaluatorMap.put(EQUALS_OPERATOR, new EqualsOperatorEvaluator());
    evaluatorMap.put(NOT_EQUALS_OPERATOR, new NotEqualsOperatorEvaluator());
    evaluatorMap.put(IN_OPERATOR, new INOperatorEvaluator());
    evaluatorMap.put(NOT_IN_OPERATOR, new NotINOperatorEvaluator());
    evaluatorMap.put(CONTAINS_OPERATOR, new ContainsOperatorEvaluator());
    evaluatorMap.put(DOES_NOT_CONTAIN_OPERATOR, new DoesNotContainOperatorEvaluator());
    evaluatorMap.put(STARTS_WITH_OPERATOR, new StartsWithOperatorEvaluator());
    evaluatorMap.put(ENDS_WITH_OPERATOR, new EndsWithOperatorEvaluator());
    evaluatorMap.put(REGEX_OPERATOR, new RegexOperatorEvaluator());
  }

  static class EqualsOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      if (isBlank(standard)) {
        return isBlank(input);
      }

      return standard.equals(input);
    }
  }

  static class NotEqualsOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, evaluation cant happen
      if (isBlank(standard)) {
        return isNotBlank(input);
      }

      return !standard.equals(input);
    }
  }

  static class StartsWithOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      if (isBlank(input)) {
        return false;
      }

      return input.startsWith(standard);
    }
  }

  static class EndsWithOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      if (isBlank(input)) {
        return false;
      }

      return input.endsWith(standard);
    }
  }

  static class ContainsOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      if (isBlank(input)) {
        return false;
      }

      return input.contains(standard);
    }
  }

  static class DoesNotContainOperatorEvaluator implements OperationEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      if (isBlank(input)) {
        return false;
      }

      return !input.contains(standard);
    }
  }

  static class INOperatorEvaluator implements OperationEvaluator {
    Set<String> generateAllowedValuesSet(String standard) {
      Set<String> allowedValues = new HashSet<>();
      for (String s : Splitter.on(Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).trimResults().split(standard)) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
          String str = s.substring(1, s.length() - 1);
          allowedValues.add(str.replaceAll("\"\"", "\""));
        } else {
          allowedValues.add(s);
        }
      }

      return allowedValues;
    }

    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      Set<String> allowedValuesSet = generateAllowedValuesSet(standard);
      if (allowedValuesSet.contains(input)) {
        return true;
      }

      // check for regex
      OperationEvaluator operationEvaluator = evaluatorMap.get(REGEX_OPERATOR);
      return allowedValuesSet.stream().filter(value -> operationEvaluator.evaluate(input, value)).findAny().isPresent();
    }
  }

  static class NotINOperatorEvaluator extends INOperatorEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      return !super.evaluate(input, standard);
    }
  }

  static class RegexOperatorEvaluator extends INOperatorEvaluator {
    @Override
    public boolean evaluate(String input, String standard) {
      // standard is missing, skipping evaluation
      if (isBlank(standard)) {
        return true;
      }

      return NGExpressionUtils.matchesPattern(Pattern.compile(standard, Pattern.DOTALL), input);
    }
  }
}
