/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_END;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_END_ESC;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_START;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_START_ESC;
import static io.harness.expression.EngineExpressionEvaluator.hasExpressions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class NGExpressionUtils {
  private static final Pattern InputSetVariablePattern =
      Pattern.compile(EXPR_START_ESC + "input" + EXPR_END_ESC + ".*");

  public static final String EXPRESSION_INPUT_CONSTANT = "executionInput";

  private static final Pattern ExecutionInputPattern = Pattern.compile(EXPR_START_ESC + "input" + EXPR_END_ESC + ".*"
      + ".executionInput\\(\\)"
      + ".*");

  private static final Pattern UpdatedExecutionInputPattern = Pattern.compile(EXPR_START_ESC + "executionInput."
      + ".*" + EXPR_END_ESC);

  public static final String DEFAULT_INPUT_SET_EXPRESSION = EXPR_START + "input" + EXPR_END;
  public static final Pattern GENERIC_EXPRESSIONS_PATTERN =
      Pattern.compile(EXPR_START_ESC + "([a-zA-Z]\\w*\\.?)*([a-zA-Z]\\w*)" + EXPR_END_ESC);

  public boolean matchesInputSetPattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return NGExpressionUtils.InputSetVariablePattern.matcher(expression).matches();
  }

  public boolean matchesExecutionInputPattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return NGExpressionUtils.ExecutionInputPattern.matcher(expression).matches();
  }
  public boolean matchesUpdatedExecutionInputPattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return NGExpressionUtils.UpdatedExecutionInputPattern.matcher(expression).matches();
  }

  public boolean matchesGenericExpressionPattern(final String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN.matcher(expression).matches();
  }

  public boolean matchesPattern(Pattern pattern, String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return pattern.matcher(expression).matches();
  }

  // Function which matches pattern on given expression.
  public boolean containsPattern(Pattern pattern, String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return pattern.matcher(expression).find();
  }

  public String getInputSetValidatorPattern(String validatorName) {
    return "\\." + validatorName + "\\(";
  }

  public boolean isRuntimeOrExpressionField(String fieldValue) {
    return matchesInputSetPattern(fieldValue) || hasExpressions(fieldValue);
  }

  public List<String> getListOfExpressions(String s) {
    Matcher matcher = GENERIC_EXPRESSIONS_PATTERN.matcher(s);
    List<String> allMatches = new ArrayList<>();
    while (matcher.find()) {
      allMatches.add(matcher.group());
    }
    return allMatches;
  }

  public String getFirstKeyOfExpression(String expression) {
    if (!containsPattern(GENERIC_EXPRESSIONS_PATTERN, expression)) {
      log.error(expression + " is not a syntactically valid pipeline expression");
      throw new InvalidRequestException(expression + " is not a syntactically valid pipeline expression");
    }
    String contentOfExpression = expression.replace(EXPR_START, "").replace(EXPR_END, "");
    String[] wordsInExpression = contentOfExpression.split("\\.");
    return wordsInExpression[0];
  }
}
