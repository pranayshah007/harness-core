/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.InvalidRequestException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;

import software.wings.expression.SecretManagerMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import org.apache.commons.collections.map.SingletonMap;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.commons.text.StrSubstitutor;

/**
 * The Class ExpressionEvaluator.
 */
@OwnedBy(CDC)
@Data
public class ExpressionEvaluator {
  public static final String DEFAULT_ARTIFACT_VARIABLE_NAME = "artifact";
  public static final String DEFAULT_HELMCHART_VARIABLE_NAME = "helmChart";
  public static final String ARTIFACT_FILE_NAME_VARIABLE = "ARTIFACT_FILE_NAME";
  public static final String ROLLBACK_ARTIFACT_FILE_NAME_VARIABLE = "ROLLBACK_ARTIFACT_FILE_NAME";

  public static final Pattern wingsVariablePattern = Pattern.compile("\\$\\{[^{}]+}");
  public static final Pattern variableNamePattern = Pattern.compile("^[-_a-zA-Z][-_\\w]*$");
  private static final Pattern serviceDefaultArtifactVariablePattern = Pattern.compile("\\$\\{artifact[.}]");
  private static final Pattern serviceArtifactVariablePattern = Pattern.compile("\\$\\{artifacts\\.([^.{}]+)[.}]");
  private static final Pattern emptyCustomExpression = Pattern.compile("\\$\\{[{ }]*}");
  protected SecretManagerMode evaluationMode;

  private Map<String, Object> expressionFunctorMap = new HashMap<>();

  private JexlEngine engine = new JexlBuilder().logger(new NoOpLog()).create();

  public void addFunctor(String name, ExpressionFunctor functor) {
    if (functor == null) {
      throw new InvalidArgumentsException(Pair.of("functor", "null"));
    }
    expressionFunctorMap.put(name, functor);
  }

  public Object evaluate(String expression, String name, Object value) {
    Map<String, Object> context = new SingletonMap(name, value);
    return evaluate(expression, context, null);
  }

  public Object evaluate(String expression, Map<String, Object> context) {
    return evaluate(expression, context, null);
  }

  public Object evaluate(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    expression = normalizeExpression(expression, context, defaultObjectPrefix);

    JexlContext jc = prepareContext(context, defaultObjectPrefix);
    return evaluate(expression, jc);
  }

  public Object evaluate(String expression, JexlContext context) {
    if (expression == null) {
      return null;
    }

    // Ref: https://stackoverflow.com/q/66498157
    // Line break in JEXL String
    if (expression.contains("\n")) {
      expression = expression.replaceAll("\n", "\\\\u000a");
    }

    JexlExpression jexlExpression = engine.createExpression(expression);
    Object ret = jexlExpression.evaluate(context);

    LateBindingContext lateBindingContext = (LateBindingContext) context;
    ExpressionEvaluatorContext.set(lateBindingContext.getMap());

    return ret;
  }

  public String normalizeExpression(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    if (expression == null) {
      return null;
    }

    JexlContext jc = prepareContext(context, null);

    final NormalizeVariableResolver variableResolver =
        NormalizeVariableResolver.builder().objectPrefixes(generatePrefixList(defaultObjectPrefix)).context(jc).build();

    StrSubstitutor substitutor = new StrSubstitutor();
    substitutor.setEnableSubstitutionInVariables(true);
    substitutor.setVariableResolver(variableResolver);

    StringBuffer sb = new StringBuffer(expression);
    return substitutor.replace(sb);
  }

  public String substitute(String expression, Map<String, Object> context) {
    return substitute(expression, context, null, null);
  }

  public String substitute(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    return substitute(expression, context, null, defaultObjectPrefix);
  }

  public String substitute(String expression, Map<String, Object> context, VariableResolverTracker tracker) {
    return substitute(expression, context, tracker, null);
  }

  public String substitute(
      String expression, Map<String, Object> context, VariableResolverTracker tracker, String defaultObjectPrefix) {
    if (expression == null) {
      return null;
    }

    JexlContext jc = prepareContext(context, defaultObjectPrefix);
    return ExpressionEvaluatorUtils.substitute(this, expression, jc, tracker);
  }

  public String substituteSecured(
      String expression, Map<String, Object> context, VariableResolverTracker tracker, String defaultObjectPrefix) {
    if (expression == null) {
      return null;
    }

    JexlContext jc = prepareContext(context, defaultObjectPrefix);
    return ExpressionEvaluatorUtils.substituteSecured(this, expression, jc, tracker);
  }

  public static void isValidVariableName(String name) {
    // Verify variable name should not contain any special character
    if (isEmpty(name)) {
      return;
    }
    Matcher matcher = ExpressionEvaluator.variableNamePattern.matcher(name);
    if (!matcher.matches()) {
      throw new InvalidRequestException("Special characters are not allowed in variable name", USER);
    }
  }

  /**
   * Validates and gets name from expression
   * @param expression
   * @return
   */
  public static String getName(String expression) {
    if (isEmpty(expression)) {
      return expression;
    }
    Matcher matcher = ExpressionEvaluator.wingsVariablePattern.matcher(expression);
    if (matcher.matches()) {
      expression = matcher.group(0).substring(2, matcher.group(0).length() - 1);
    }
    return expression;
  }

  public static boolean matchesVariablePattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return ExpressionEvaluator.wingsVariablePattern.matcher(expression).matches();
  }

  public static boolean containsVariablePattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return ExpressionEvaluator.wingsVariablePattern.matcher(expression).find();
  }

  public static boolean isEmptyCustomExpression(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return ExpressionEvaluator.emptyCustomExpression.matcher(expression).matches();
  }

  public static void updateServiceArtifactVariableNames(String str, Set<String> serviceArtifactVariableNames) {
    if (str == null) {
      return;
    }

    // TODO: ASR: IMP: ARTIFACT_FILE_NAME behaves differently for multi artifact
    // Matches ${ARTIFACT_FILE_NAME}
    if (str.contains("${" + ARTIFACT_FILE_NAME_VARIABLE + "}")) {
      serviceArtifactVariableNames.add(DEFAULT_ARTIFACT_VARIABLE_NAME);
    }

    // Matches ${artifact} or ${artifact.buildNo}
    Matcher matcher = serviceDefaultArtifactVariablePattern.matcher(str);
    if (matcher.find()) {
      serviceArtifactVariableNames.add(DEFAULT_ARTIFACT_VARIABLE_NAME);
    }

    // Matches ${artifacts.artifact} or ${artifacts.artifact.buildNo}
    matcher = serviceArtifactVariablePattern.matcher(str);
    while (matcher.find()) {
      serviceArtifactVariableNames.add(matcher.group(1));
    }
  }

  private JexlContext prepareContext(Map<String, Object> context, String defaultObjectPrefix) {
    final Map<String, Object> map = new HashMap(context);
    map.putAll(expressionFunctorMap);
    return LateBindingContext.builder()
        .prefixes(generatePrefixList(defaultObjectPrefix))
        .expressionEvaluator(this)
        .map(map)
        .build();
  }

  private List<String> generatePrefixList(String defaultObjectPrefix) {
    List<String> prefixes = new ArrayList<>();
    if (defaultObjectPrefix != null) {
      prefixes.add(defaultObjectPrefix);
    }
    prefixes.add("context");
    return prefixes;
  }
}
