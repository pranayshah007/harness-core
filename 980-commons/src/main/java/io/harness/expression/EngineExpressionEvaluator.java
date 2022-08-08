/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.algorithm.IdentifierName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.FunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.exception.exceptionmanager.exceptionhandler.JexlRuntimeExceptionHandler;
import io.harness.expression.functors.DateTimeFunctor;
import io.harness.text.StringReplacer;
import io.harness.text.resolver.ExpressionResolver;
import io.harness.text.resolver.TrackingExpressionResolver;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.logging.impl.NoOpLog;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class EngineExpressionEvaluator {
  public static final String EXPR_START = "<+";
  public static final String EXPR_END = ">";
  public static final String EXPR_START_ESC = "<\\+";
  public static final String EXPR_END_ESC = ">";
  public static final String HARNESS_INTERNAL_VARIABLE_PREFIX = "__HVAR_";

  private static final Pattern VALID_VARIABLE_FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z_0-9]*$");
  private static final Pattern ALIAS_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z_0-9]*$");

  private static final int MAX_DEPTH = 15;

  private final JexlEngine engine;
  @Getter private final VariableResolverTracker variableResolverTracker;
  private final Map<String, Object> contextMap;
  @Getter private final Map<String, String> staticAliases;
  private boolean initialized;

  public EngineExpressionEvaluator(VariableResolverTracker variableResolverTracker) {
    this.engine = new JexlBuilder().logger(new NoOpLog()).create();
    this.variableResolverTracker =
        variableResolverTracker == null ? new VariableResolverTracker() : variableResolverTracker;
    this.contextMap = new LateBindingMap();
    this.staticAliases = new HashMap<>();
  }

  /**
   * Add objects/functors to context map and aliases. Context map and aliases can only be updated in this method.
   */
  protected void initialize() {
    addToContext("regex", new RegexFunctor());
    addToContext("json", new JsonFunctor());
    addToContext("xml", new XmlFunctor());
    addToContext("datetime", new DateTimeFunctor());
  }

  protected final boolean isInitialized() {
    synchronized (this) {
      return initialized;
    }
  }

  /**
   * Add objects/functors to contextMap. Should be called within the initialize method only.
   *
   * @param name   the name of the functor
   * @param object the object to put against the name
   */
  protected final void addToContext(@NotNull String name, @NotNull Object object) {
    if (isInitialized()) {
      return;
    }
    contextMap.put(name, object);
  }

  /**
   * Add a static alias. Any expression that starts with `aliasName` will be replaced by `replacement`. Should be
   * called within the initialize method only.
   *
   * @param aliasName   the name of the alias
   * @param replacement the string to replace the alias name with
   */
  protected final void addStaticAlias(@NotNull String aliasName, @NotEmpty String replacement) {
    if (isInitialized()) {
      return;
    }
    if (!validAliasName(aliasName)) {
      throw new InvalidRequestException("Invalid alias: " + aliasName);
    }
    if (aliasName.equals(replacement)) {
      throw new InvalidRequestException("Alias and replacement cannot be the same: " + aliasName);
    }
    staticAliases.put(aliasName, replacement);
  }

  /**
   * Return the prefixes to search for when evaluating expressions. The return value should not be null/empty. When
   * overriding this method, consider calling super.fetchPrefixes() and including the prefixes of the superclass in the
   * final list.
   *
   * Example:
   *   If the expression is `a.b.c` and fetchPrefixes returns ["child", "qualified", ""]
   *   We search in the following order:
   *   - child.a.b.c
   *   - qualified.a.b.c
   *   - a.b.c
   *
   * NOTE: Expression without any prefix will only be searched if the prefixes list contains "" or null.
   *
   * Example:
   * `qualified.b.c` and fetchPrefixes returns ["child", "qualified", ""] We search in the following order:
   *   - child.qualified.b.c
   *   - qualified.qualified.b.c
   *   - qualified.b.c [This searches for b.c inside contextMap entry with name 'qualified']
   *
   * @return the prefixes to search with
   */
  @NotEmpty
  protected List<String> fetchPrefixes() {
    return ImmutableList.of("datetime", "");
  }

  /**
   * This method renders expressions recursively for all String fields inside the given object. If a field is annotated
   * with @NotExpression, it is skipped.
   *
   * @param o the object to resolve
   * @return the resolved object (this can be the same object or a new one)
   */
  // TODO(archit):Replace skipUnresolvedExpressionsCheck with enum to get unresolved variable value to be replaced with
  // like empty string, null or original expression.
  public Object resolve(Object o, boolean skipUnresolvedExpressionsCheck) {
    return ExpressionEvaluatorUtils.updateExpressions(o, new ResolveFunctorImpl(this, true));
  }

  public PartialEvaluateResult partialResolve(Object o) {
    Map<String, Object> partialCtx = new HashMap<>();
    Object res = ExpressionEvaluatorUtils.updateExpressions(o, new PartialResolveFunctorImpl(this, partialCtx));
    return PartialEvaluateResult.createCompleteResult(res, partialCtx);
  }

  public String renderExpression(String expression) {
    return renderExpression(expression, true);
  }

  public String renderExpression(String expression, boolean skipUnresolvedExpressionsCheck) {
    return renderExpression(expression, null, skipUnresolvedExpressionsCheck);
  }

  public String renderExpression(String expression, Map<String, Object> ctx) {
    return renderExpression(expression, ctx, true);
  }

  public String renderExpression(String expression, Map<String, Object> ctx, boolean skipUnresolvedExpressionsCheck) {
    if (!hasExpressions(expression)) {
      return expression;
    }
    return renderExpressionInternal(expression, prepareContext(ctx), skipUnresolvedExpressionsCheck, MAX_DEPTH);
  }

  public String renderExpressionInternal(
      @NotNull String expression, @NotNull EngineJexlContext ctx, boolean skipUnresolvedExpressionsCheck, int depth) {
    checkDepth(depth, expression);
    RenderExpressionResolver resolver = new RenderExpressionResolver(this, ctx, depth);
    String finalExpression = runStringReplacer(expression, resolver);
    if (!skipUnresolvedExpressionsCheck && EmptyPredicate.isNotEmpty(resolver.getUnresolvedExpressions())) {
      throw new UnresolvedExpressionsException(new ArrayList<>(resolver.getUnresolvedExpressions()));
    }
    return finalExpression;
  }

  public Object evaluateExpression(String expression) {
    return evaluateExpression(expression, null);
  }

  public Object evaluateExpression(String expression, Map<String, Object> ctx) {
    // NOTE: Don't check for hasExpressions here. There might be normal expressions like '"true" != "false"'
    if (expression == null || EmptyPredicate.isEmpty(expression.trim())) {
      return null;
    }
    return evaluateExpressionInternal(expression, prepareContext(ctx), MAX_DEPTH);
  }

  private Object evaluateExpressionInternal(@NotNull String expression, @NotNull EngineJexlContext ctx, int depth) {
    checkDepth(depth, expression);
    EvaluateExpressionResolver resolver = new EvaluateExpressionResolver(this, ctx, depth);
    String finalExpression = runStringReplacer(expression, resolver);
    try {
      return evaluateInternal(finalExpression, ctx);
    } catch (JexlException ex) {
      if (ex.getCause() instanceof EngineFunctorException) {
        throw new EngineExpressionEvaluationException(
            (EngineFunctorException) ex.getCause(), createExpression(expression));
      } else if (ex.getCause() instanceof FunctorException) {
        // For backwards compatibility.
        throw new EngineExpressionEvaluationException((FunctorException) ex.getCause(), createExpression(expression));
      }
      log.error(format("Failed to evaluate final expression: %s", expression), ex);
      throw NestedExceptionUtils.hintWithExplanationException(JexlRuntimeExceptionHandler.getHintMessage(ex),
          JexlRuntimeExceptionHandler.getExplanationMessage(ex),
          new EngineExpressionEvaluationException("Expression evaluation failed", expression));
    }
  }

  public PartialEvaluateResult partialRenderExpression(String expression) {
    return partialRenderExpression(expression, null);
  }

  public PartialEvaluateResult partialRenderExpression(String expression, Map<String, Object> ctx) {
    if (!hasExpressions(expression)) {
      return PartialEvaluateResult.createCompleteResult(expression);
    }
    return partialRenderExpressionInternal(expression, prepareContext(ctx), new HashMap<>(), MAX_DEPTH);
  }

  private PartialEvaluateResult partialRenderExpressionInternal(
      @NotNull String expression, @NotNull EngineJexlContext ctx, @NotNull Map<String, Object> partialCtx, int depth) {
    checkDepth(depth, expression);
    PartialEvaluateExpressionResolver resolver = new PartialEvaluateExpressionResolver(this, ctx, partialCtx, depth);
    String finalExpression = runStringReplacer(expression, resolver);
    ctx.addToContext(partialCtx);
    if (!hasExpressions(finalExpression)) {
      return PartialEvaluateResult.createCompleteResult(evaluateInternal(expression, ctx));
    }

    List<String> variables = findVariables(finalExpression);
    boolean hasNonInternalVariables = variables != null
        && variables.stream().anyMatch(v -> !v.startsWith(EXPR_START + HARNESS_INTERNAL_VARIABLE_PREFIX));
    if (hasNonInternalVariables) {
      finalExpression = runStringReplacer(finalExpression, new HarnessInternalRenderExpressionResolver(partialCtx));
      return PartialEvaluateResult.createPartialResult(finalExpression, partialCtx);
    } else {
      return PartialEvaluateResult.createCompleteResult(renderExpressionInternal(expression, ctx, true, depth));
    }
  }

  public PartialEvaluateResult partialEvaluateExpression(String expression) {
    return partialEvaluateExpression(expression, null);
  }

  public PartialEvaluateResult partialEvaluateExpression(String expression, Map<String, Object> ctx) {
    // NOTE: Don't check for hasExpressions here. There might be normal expressions like '"true" != "false"'
    if (EmptyPredicate.isEmpty(expression)) {
      return null;
    }
    return partialEvaluateExpressionInternal(expression, prepareContext(ctx), new HashMap<>(), MAX_DEPTH);
  }

  private PartialEvaluateResult partialEvaluateExpressionInternal(
      @NotNull String expression, @NotNull EngineJexlContext ctx, @NotNull Map<String, Object> partialCtx, int depth) {
    checkDepth(depth, expression);
    PartialEvaluateExpressionResolver resolver = new PartialEvaluateExpressionResolver(this, ctx, partialCtx, depth);
    String finalExpression = runStringReplacer(expression, resolver);
    ctx.addToContext(partialCtx);
    if (!hasExpressions(finalExpression)) {
      return PartialEvaluateResult.createCompleteResult(evaluateInternal(expression, ctx));
    }

    List<String> variables = findVariables(finalExpression);
    boolean hasNonInternalVariables = variables != null
        && variables.stream().anyMatch(v -> !v.startsWith(EXPR_START + HARNESS_INTERNAL_VARIABLE_PREFIX));
    if (hasNonInternalVariables) {
      if (!isSingleExpression(finalExpression)) {
        finalExpression = createExpression(finalExpression);
      }
      return PartialEvaluateResult.createPartialResult(finalExpression, partialCtx);
    } else {
      return PartialEvaluateResult.createCompleteResult(evaluateExpressionInternal(expression, ctx, depth));
    }
  }

  public PartialEvaluateResult partialEvaluateExpressionBlock(@NotNull String expressionBlock,
      @NotNull EngineJexlContext ctx, @NotNull Map<String, Object> partialCtx, int depth) {
    if (EmptyPredicate.isEmpty(expressionBlock)) {
      return PartialEvaluateResult.createCompleteResult(expressionBlock);
    }

    // Check for cases like <+<+abc>.contains(<+def>)>.
    if (hasExpressions(expressionBlock)) {
      return partialEvaluateExpressionInternal(expressionBlock, ctx, partialCtx, depth - 1);
    }

    // If object is another expression, evaluate it recursively.
    Object object = evaluatePrefixCombinations(expressionBlock, ctx, depth);
    if (object instanceof String && hasExpressions((String) object)) {
      if (createExpression(expressionBlock).equals(object)) {
        // If returned expression is exactly the same, throw exception.
        throw new EngineExpressionEvaluationException(
            "Infinite loop in variable evaluation", createExpression(expressionBlock));
      } else {
        PartialEvaluateResult result = partialEvaluateExpressionInternal((String) object, ctx, partialCtx, depth - 1);
        if (result.isPartial()) {
          return result;
        } else {
          object = result.getValue();
        }
      }
    }

    if (object == null) {
      // No final expression could be resolved.
      return PartialEvaluateResult.createPartialResult(createExpression(expressionBlock), null);
    }

    String name = HARNESS_INTERNAL_VARIABLE_PREFIX + RandomStringUtils.random(12, true, false);
    partialCtx.put(name, object);
    return PartialEvaluateResult.createPartialResult(createExpression(name), partialCtx);
  }

  /**
   * Evaluate an expression block (anything inside <+...>) with the given context after applying static alias
   * substitutions and prefixes. This variant is non-recursive.
   */
  public Object evaluateExpressionBlock(@NotNull String expressionBlock, @NotNull EngineJexlContext ctx, int depth) {
    checkDepth(depth, expressionBlock);
    if (EmptyPredicate.isEmpty(expressionBlock)) {
      return expressionBlock;
    }

    // Check for cases like <+<+abc>.contains(<+def>)>.
    if (hasExpressions(expressionBlock)) {
      return evaluateExpressionInternal(expressionBlock, ctx, depth - 1);
    }

    Object object = evaluatePrefixCombinations(expressionBlock, ctx, depth);

    // If object is another expression, evaluate it recursively.
    if (object instanceof String && hasExpressions((String) object)) {
      if (createExpression(expressionBlock).equals(object)) {
        // If returned expression is exactly the same, throw exception.
        throw new EngineExpressionEvaluationException(
            "Infinite loop in variable interpretation", createExpression(expressionBlock));
      } else {
        observed(expressionBlock, object);
        return evaluateExpressionInternal((String) object, ctx, depth - 1);
      }
    }

    observed(expressionBlock, object);
    return object;
  }

  private Object evaluatePrefixCombinations(
      @NotNull String expressionBlock, @NotNull EngineJexlContext ctx, int depth) {
    // Apply all the prefixes and return first one that evaluates successfully.
    List<String> finalExpressions = preProcessExpression(expressionBlock);
    Object object = null;
    for (String finalExpression : finalExpressions) {
      try {
        if (hasExpressions(finalExpression)) {
          object = evaluateExpressionInternal(finalExpression, ctx, depth - 1);
        } else {
          object = evaluateInternal(finalExpression, ctx);
        }
      } catch (JexlException ex) {
        if (ex.getCause() instanceof EngineFunctorException) {
          throw new EngineExpressionEvaluationException(
              (EngineFunctorException) ex.getCause(), createExpression(expressionBlock));
        } else if (ex.getCause() instanceof FunctorException) {
          // For backwards compatibility.
          throw new EngineExpressionEvaluationException(
              (FunctorException) ex.getCause(), createExpression(expressionBlock));
        }
        log.debug(format("Failed to evaluate final expression: %s", finalExpression), ex);
      } catch (EngineFunctorException ex) {
        throw new EngineExpressionEvaluationException(ex, createExpression(expressionBlock));
      } catch (FunctorException ex) {
        // For backwards compatibility.
        throw new EngineExpressionEvaluationException(ex, createExpression(expressionBlock));
      }

      if (object != null) {
        return object;
      }
    }
    return null;
  }

  private void observed(String variable, Object value) {
    if (variableResolverTracker != null) {
      variableResolverTracker.observed(variable, value);
    }
  }

  /**
   * Return the expression after applying static alias substitutions and prefixes.
   *
   * @param expression the original expression
   * @return the final expression
   */
  private List<String> preProcessExpression(@NotNull String expression) {
    String normalizedExpression = applyStaticAliases(expression);
    if (hasExpressions(normalizedExpression)) {
      return Collections.singletonList(normalizedExpression);
    }
    return fetchPrefixes()
        .stream()
        .map(prefix -> EmptyPredicate.isEmpty(prefix) ? normalizedExpression : prefix + "." + normalizedExpression)
        .collect(Collectors.toList());
  }

  /**
   * Return the expression after applying static alias substitutions.
   *
   * @param expression the original expression
   * @return the final expression
   */
  private String applyStaticAliases(@NotNull String expression) {
    for (int i = 0; i < ExpressionEvaluatorUtils.DEPTH_LIMIT; i++) {
      if (staticAliases.containsKey(expression)) {
        expression = staticAliases.get(expression);
        continue;
      }

      List<String> parts = Arrays.asList(expression.split("\\.", 2));
      if (parts.size() < 2) {
        return expression;
      }

      String firstPart = parts.get(0);
      if (staticAliases.containsKey(firstPart)) {
        parts.set(0, staticAliases.get(firstPart));
        expression = String.join(".", parts);
      } else {
        return expression;
      }
    }

    throw new EngineExpressionEvaluationException(
        "Infinite loop or too deep indirection in static alias interpretation", expression);
  }

  /**
   * Evaluate an expression with the given context. This variant is non-recursive and doesn't support harness
   * expressions - variables delimited by <+...>.
   */
  protected Object evaluateInternal(@NotNull String expression, @NotNull EngineJexlContext ctx) {
    return evaluateByCreatingExpression(expression, ctx);
  }

  protected Object evaluateByCreatingExpression(@NotNull String expression, @NotNull EngineJexlContext ctx) {
    JexlExpression jexlExpression = engine.createExpression(expression);
    return jexlExpression.evaluate(ctx);
  }

  protected Object evaluateByCreatingScript(@NotNull String expression, @NotNull EngineJexlContext ctx) {
    return engine.createScript(expression).execute(ctx);
  }

  private EngineJexlContext prepareContext(Map<String, Object> ctx) {
    synchronized (this) {
      if (!initialized) {
        initialize();
        initialized = true;
      }
    }

    Map<String, Object> clonedContext = new LateBindingMap();
    clonedContext.putAll(contextMap);
    if (EmptyPredicate.isNotEmpty(ctx)) {
      clonedContext.putAll(ctx);
    }
    return new EngineJexlContext(this, clonedContext);
  }

  private static void checkDepth(int depth, String expression) {
    if (depth <= 0) {
      throw new EngineExpressionEvaluationException(
          "Infinite loop or too deep indirection in expression evaluation", expression);
    }
  }

  private static String runStringReplacer(@NotNull String expression, @NotNull ExpressionResolver resolver) {
    StringReplacer replacer = new StringReplacer(resolver, EXPR_START, EXPR_END);
    return replacer.replace(expression);
  }

  public static boolean isSingleExpression(String str) {
    return TrackingExpressionResolver.isSingleExpression(EXPR_START, EXPR_END, str);
  }

  public static boolean hasExpressions(String str) {
    return EmptyPredicate.isNotEmpty(findExpressions(str));
  }

  public static List<String> findExpressions(String str) {
    return TrackingExpressionResolver.findExpressions(EXPR_START, EXPR_END, true, false, str);
  }

  public static List<String> findVariables(String str) {
    return TrackingExpressionResolver.findExpressions(EXPR_START, EXPR_END, true, true, str);
  }

  public static boolean validVariableFieldName(String name) {
    if (EmptyPredicate.isEmpty(name)) {
      return false;
    }
    return VALID_VARIABLE_FIELD_NAME_PATTERN.matcher(name).matches();
  }

  public static boolean validAliasName(String name) {
    if (EmptyPredicate.isEmpty(name)) {
      return false;
    }
    return ALIAS_NAME_PATTERN.matcher(name).matches();
  }

  public static String createExpression(String expr) {
    return TrackingExpressionResolver.createExpression(EXPR_START, EXPR_END, expr);
  }

  private static class RenderExpressionResolver implements ExpressionResolver {
    private final EngineExpressionEvaluator engineExpressionEvaluator;
    private final EngineJexlContext ctx;
    private final int depth;
    @Getter private final Set<String> unresolvedExpressions = new HashSet<>();

    RenderExpressionResolver(EngineExpressionEvaluator engineExpressionEvaluator, EngineJexlContext ctx, int depth) {
      this.engineExpressionEvaluator = engineExpressionEvaluator;
      this.ctx = ctx;
      this.depth = depth;
    }

    @Override
    public String resolve(String expression) {
      try {
        Object value = engineExpressionEvaluator.evaluateExpressionBlock(expression, ctx, depth);
        if (value == null) {
          unresolvedExpressions.add(expression);
        }
        return String.valueOf(value);
      } catch (UnresolvedExpressionsException ex) {
        unresolvedExpressions.addAll(ex.fetchExpressions());
        return createExpression(expression);
      }
    }
  }

  private static class EvaluateExpressionResolver implements ExpressionResolver {
    private final EngineExpressionEvaluator engineExpressionEvaluator;
    private final EngineJexlContext ctx;
    private final int depth;
    private final String prefix;
    private final String suffix;
    private int varIndex;
    @Getter private final Set<String> unresolvedExpressions = new HashSet<>();

    EvaluateExpressionResolver(EngineExpressionEvaluator engineExpressionEvaluator, EngineJexlContext ctx, int depth) {
      this.engineExpressionEvaluator = engineExpressionEvaluator;
      this.ctx = ctx;
      this.depth = depth;
      this.prefix = IdentifierName.random();
      this.suffix = IdentifierName.random();
    }

    @Override
    public String resolve(String expression) {
      try {
        Object value = engineExpressionEvaluator.evaluateExpressionBlock(expression, ctx, depth - 1);
        // In case of unresolved expression, identified by null value, we are not returning early but storing the
        // context map with null value.
        if (value == null) {
          String finalExpression = createExpression(expression);
          unresolvedExpressions.add(finalExpression);
        }

        String name = prefix + ++varIndex + suffix;
        ctx.set(name, value);
        return name;
      } catch (UnresolvedExpressionsException ex) {
        unresolvedExpressions.addAll(ex.fetchExpressions());
        return createExpression(expression);
      }
    }
  }

  private static class PartialEvaluateExpressionResolver implements ExpressionResolver {
    private final EngineExpressionEvaluator engineExpressionEvaluator;
    private final EngineJexlContext ctx;
    private final Map<String, Object> partialCtx;
    private final int depth;

    PartialEvaluateExpressionResolver(EngineExpressionEvaluator engineExpressionEvaluator, EngineJexlContext ctx,
        Map<String, Object> partialCtx, int depth) {
      this.engineExpressionEvaluator = engineExpressionEvaluator;
      this.ctx = ctx;
      this.partialCtx = partialCtx;
      this.depth = depth;
    }

    @Override
    public String resolve(String expression) {
      PartialEvaluateResult result =
          engineExpressionEvaluator.partialEvaluateExpressionBlock(expression, ctx, partialCtx, depth - 1);
      if (result.isPartial()) {
        return result.getExpressionValue();
      }

      String name = HARNESS_INTERNAL_VARIABLE_PREFIX + RandomStringUtils.random(12, true, false);
      partialCtx.put(name, result.getValue());
      return createExpression(name);
    }
  }

  private static class HarnessInternalRenderExpressionResolver implements ExpressionResolver {
    private final Map<String, Object> partialCtx;

    HarnessInternalRenderExpressionResolver(Map<String, Object> partialCtx) {
      this.partialCtx = partialCtx;
    }

    @Override
    public String resolve(String expression) {
      if (partialCtx.containsKey(expression)) {
        return String.valueOf(partialCtx.get(expression));
      } else {
        return createExpression(expression);
      }
    }
  }

  @Getter
  public static class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final EngineExpressionEvaluator expressionEvaluator;
    private final boolean skipUnresolvedExpressionsCheck;

    public ResolveFunctorImpl(EngineExpressionEvaluator expressionEvaluator, boolean skipUnresolvedExpressionsCheck) {
      this.expressionEvaluator = expressionEvaluator;
      this.skipUnresolvedExpressionsCheck = skipUnresolvedExpressionsCheck;
    }

    @Override
    public String processString(String expression) {
      return expressionEvaluator.renderExpression(expression, skipUnresolvedExpressionsCheck);
    }
  }

  public static class PartialResolveFunctorImpl implements ExpressionResolveFunctor {
    private final EngineExpressionEvaluator expressionEvaluator;
    private final Map<String, Object> partialCtx;

    public PartialResolveFunctorImpl(EngineExpressionEvaluator expressionEvaluator, Map<String, Object> partialCtx) {
      this.expressionEvaluator = expressionEvaluator;
      this.partialCtx = partialCtx;
    }

    @Override
    public String processString(String expression) {
      PartialEvaluateResult result = expressionEvaluator.partialRenderExpression(expression);
      if (EmptyPredicate.isNotEmpty(result.getPartialCtx())) {
        partialCtx.putAll(result.getPartialCtx());
      }
      if (result.isPartial()) {
        return result.getExpressionValue();
      }
      return (String) result.getValue();
    }
  }

  @Value
  public static class PartialEvaluateResult {
    boolean partial;
    String expressionValue;
    Object value;
    Map<String, Object> partialCtx;

    public static PartialEvaluateResult createPartialResult(String expression, Map<String, Object> partialCtx) {
      return new PartialEvaluateResult(true, expression, null, partialCtx);
    }

    public static PartialEvaluateResult createCompleteResult(Object value) {
      return createCompleteResult(value, null);
    }

    public static PartialEvaluateResult createCompleteResult(Object value, Map<String, Object> partialCtx) {
      return new PartialEvaluateResult(false, null, value, partialCtx);
    }
  }
}
