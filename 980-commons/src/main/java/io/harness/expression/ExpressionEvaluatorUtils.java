/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.algorithm.IdentifierName;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.expression.common.ExpressionConstants;

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.logging.impl.NoOpLog;
import org.apache.commons.text.StrLookup;
import org.apache.commons.text.StrSubstitutor;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ExpressionEvaluatorUtils {
  public static final int EXPANSION_LIMIT = 6 * 1024 * 1024; // 6 MB
  public static final int EXPANSION_MULTIPLIER_LIMIT = 10;
  public static final int DEPTH_LIMIT = 10;
  private static final int DEBUG_LENGTH_LIMIT = 1 * 1024 * 1024; // 1 MB

  private static final JexlEngine engine = new JexlBuilder().logger(new NoOpLog()).create();
  private static final String REGEX = "[0-9]+";

  public static String substitute(
      ExpressionEvaluator expressionEvaluator, String expression, JexlContext ctx, VariableResolverTracker tracker) {
    if (expression == null) {
      return null;
    }

    String prefix = IdentifierName.random();
    String suffix = IdentifierName.random();
    Pattern pattern = Pattern.compile(prefix + REGEX + suffix);
    EvaluateVariableResolver variableResolver = EvaluateVariableResolver.builder()
                                                    .expressionEvaluator(expressionEvaluator)
                                                    .context(ctx)
                                                    .variableResolverTracker(tracker)
                                                    .prefix(prefix)
                                                    .suffix(suffix)
                                                    .build();
    return substitute(expression, ctx, variableResolver, pattern, false);
  }

  public static String substituteSecured(
      ExpressionEvaluator expressionEvaluator, String expression, JexlContext ctx, VariableResolverTracker tracker) {
    if (expression == null) {
      return null;
    }

    String prefix = IdentifierName.random();
    String suffix = IdentifierName.random();
    Pattern pattern = Pattern.compile(prefix + REGEX + suffix);
    EvaluateVariableResolver variableResolver = EvaluateVariableResolver.builder()
                                                    .expressionEvaluator(expressionEvaluator)
                                                    .context(ctx)
                                                    .variableResolverTracker(tracker)
                                                    .prefix(prefix)
                                                    .suffix(suffix)
                                                    .build();
    return substituteSecretsSecured(expression, ctx, variableResolver, pattern);
  }

  public static String substitute(@NotNull String expression, JexlContext ctx, StrLookup<Object> variableResolver,
      Pattern pattern, boolean newDelimiters) {
    StrSubstitutor substitutor = getSubstitutor(variableResolver, newDelimiters);

    String result = expression;
    int limit = Math.max(EXPANSION_LIMIT, EXPANSION_MULTIPLIER_LIMIT * expression.length());
    // We go maximum upto DEPTH_LIMIT depth while resolving all the variables
    for (int i = 0; i < DEPTH_LIMIT; i++) {
      // Lets use an example, we want to replace variables (i.e, ${..}) in expression - echo "${testVar}" && echo "1234"
      // ctx has map having (testVar, example) as one of its enteries.
      // pattern - ABCD[0-9]+WXYZ
      String original = result;
      // This replaces all the variables (i.e, ${...}) in original string.
      // Each variable in original is replaced with the corresponding output of EvaluateVariableResolver#lookup
      // After this step: result - echo "ABCD1WXYZ" && echo "1234", ctx map has one more entry now (ABCD1WXYZ, example)
      result = substitutor.replace(new StringBuffer(original));

      // Now we replace each pattern of type ABCD[0-9]+WXYZ in result string with its correct value.
      for (;;) {
        final Matcher matcher = pattern.matcher(result);
        // If we don't find ABCD[0-9]+WXYZ pattern in string, it means there is nothing left to replace and we exit the
        // outer loop
        if (!matcher.find()) {
          break;
        }

        StringBuffer sb = new StringBuffer();
        // We iterate till all the matched patterns are replaced.
        do {
          // Extract the current matched entry with pattern. After this step: name - ABCD1WXYZ
          String name = matcher.group(0);

          // Get the value from ctx map. After this step: value - example
          // '\' and '$' are escaped in the value. The matched entry with pattern is replaced with value.
          Object ctxValue = ctx.get(name);
          String value = "";
          if (ctxValue instanceof Future) {
            // If we have future from context, means secret value is being evaulated asynchronously.
            // Let's extract the value from end result of this future object.
            try {
              value = String.valueOf(((Future<?>) ctxValue).get());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
              log.error("Encountered error while extracting secret value from future ", e.getCause());
              throw new RuntimeException(
                  String.format("Encountered error while resolving secrets expression %s", expression), e.getCause());
            }
          } else {
            // Cast the value from context to string.
            value = String.valueOf(ctxValue);
          }

          // This appends the string to sb till the replaced matched entry.
          // After this step: sb - echo "example
          matcher.appendReplacement(sb, value.replace("\\", "\\\\").replace("$", "\\$"));
        } while (matcher.find());
        // This appends the left over string to sb. After this step: sb - echo "example" && echo "1234"
        matcher.appendTail(sb);
        result = sb.toString();
      }
      if (result.length() > DEBUG_LENGTH_LIMIT) {
        log.info("The expression length: {} has exceeded {} limit.", result.length(), DEBUG_LENGTH_LIMIT);
      }
      if (result.equals(original)) {
        return result;
      }
      if (result.length() > limit) {
        throw new CriticalExpressionEvaluationException("Exponentially growing interpretation", expression);
      }
    }

    throw new CriticalExpressionEvaluationException(
        "Infinite loop or too deep indirection in property interpretation", expression);
  }

  /**
   * Fetch field from inside the object using jexl conventions. POJSs, Maps, Classes having get method are supported.
   *
   * @param obj the object whose field we have to find
   * @param field the field name
   * @return value wrapped in optional if field is found, else Optional.none()
   */
  public static Optional<Object> fetchField(Object obj, String field) {
    if (obj == null || field == null) {
      return Optional.empty();
    }

    try {
      Object retObj = engine.getProperty(obj, field);
      return Optional.ofNullable(retObj);
    } catch (JexlException ex) {
      log.debug(format("Could not fetch field '%s'", field), ex);
      return Optional.empty();
    }
  }

  /**
   * Update expression values inside object recursively. The new value is obtained using the functor. If the field has a
   * NotExpression annotation, it is skipped. String fields are rendered and ParameterFields are evaluated if they have
   * an expression.
   *
   * @param obj           the object to update
   * @param functor       the functor which provides the evaluated and rendered values
   * @return the new object with updated objects (this can be done in-place or a new object can be returned)
   */
  public static Object updateExpressions(Object obj, ExpressionResolveFunctor functor) {
    return updateExpressionsInternal(obj, functor, 250, new HashSet<>());
  }

  private static Object updateExpressionsInternal(
      Object obj, ExpressionResolveFunctor functor, int depth, Set<Integer> cache) {
    if (depth <= 0) {
      throw new CriticalExpressionEvaluationException("Recursion or too deep hierarchy in property interpretation");
    }
    if (obj == null) {
      return null;
    }

    Class<?> c = obj.getClass();
    if (ClassUtils.isPrimitiveOrWrapper(c)) {
      return obj;
    }

    if (obj instanceof String) {
      return functor.processString((String) obj);
    }

    // In case of array, update in-place and return null.
    if (c.isArray()) {
      if (c.getComponentType().isPrimitive()) {
        return obj;
      }

      int length = Array.getLength(obj);
      for (int i = 0; i < length; i++) {
        Object arrObj = Array.get(obj, i);
        Object newArrObj = updateExpressionsInternal(arrObj, functor, depth - 1, cache);
        if (newArrObj != null) {
          Array.set(obj, i, newArrObj);
        }
      }
      return obj;
    }

    // In case of object, iterate over fields and update them in a similar manner.
    return updateExpressionFields(obj, functor, depth, cache);
  }

  private static Object updateExpressionFields(
      Object o, ExpressionResolveFunctor functor, int depth, Set<Integer> cache) {
    if (o == null) {
      return null;
    }

    int hashCode = System.identityHashCode(o);
    if (cache.contains(hashCode)) {
      return o;
    } else {
      cache.add(hashCode);
    }

    // Check if resolveFunctor has any custom handling for this field type.
    ResolveObjectResponse resp = functor.processObject(o);
    if (resp.isProcessed()) {
      return resp.getFinalValue();
    }

    if (o instanceof List) {
      List l = (List) o;
      for (int i = 0; i < l.size(); i++) {
        l.set(i, updateExpressionsInternal(l.get(i), functor, depth - 1, cache));
      }
      return o;
    }

    if (o instanceof Set && !(o instanceof ImmutableSet)) {
      Set l = (Set) o;
      Object newSet =
          l.stream().map(ob -> updateExpressionsInternal(ob, functor, depth - 1, cache)).collect(Collectors.toSet());
      l.clear();
      l.addAll((Set) newSet);
      return o;
    }

    if (o instanceof Map) {
      Map m = (Map) o;
      m.replaceAll((k, v) -> {
        try {
          return updateExpressionsInternal(v, functor, depth - 1, cache);
        } catch (UnresolvedExpressionsException ex) {
          // Throwing the error again with field name added in the message
          // Now the error message would like this: "Some expressions couldn't be evaluated: 'key': expr2, expr2"
          throw new UnresolvedExpressionsException((String) k, new ArrayList<>(ex.fetchExpressions()));
        }
      });
      return o;
    }

    Class<?> c = o.getClass();
    while (c.getSuperclass() != null) {
      for (Field f : c.getDeclaredFields()) {
        // Ignore field if skipPredicate returns true or if the field is static.
        if ((functor.supportsNotExpression() && f.isAnnotationPresent(NotExpression.class))
            || Modifier.isStatic(f.getModifiers())) {
          continue;
        }

        boolean isAccessible = f.isAccessible();
        f.setAccessible(true);
        try {
          updateExpressionFieldsSingleField(o, f, functor, depth - 1, cache);
          f.setAccessible(isAccessible);
        } catch (IllegalAccessException ignored) {
          log.error("Field [{}] is not accessible", f.getName());
        }
      }
      c = c.getSuperclass();
    }
    return o;
  }

  private static void updateExpressionFieldsSingleField(Object o, Field f, ExpressionResolveFunctor functor, int depth,
      Set<Integer> cache) throws IllegalAccessException {
    if (f == null) {
      return;
    }

    Object obj = f.get(o);
    Object updatedObj = updateExpressionsInternal(obj, functor, depth, cache);
    f.set(o, updatedObj);
  }

  private static StrSubstitutor getSubstitutor(StrLookup<Object> variableResolver, boolean newDelimiters) {
    StrSubstitutor substitutor = new StrSubstitutor();
    substitutor.setEnableSubstitutionInVariables(true);
    substitutor.setVariableResolver(variableResolver);
    substitutor.setValueDelimiter("");
    if (newDelimiters) {
      substitutor.setVariablePrefix(ExpressionConstants.EXPR_START);
      substitutor.setVariableSuffix(ExpressionConstants.EXPR_END);
    }
    return substitutor;
  }

  private static String resolveSecured(Matcher matcher, JexlContext ctx) {
    StringBuffer sb = new StringBuffer();
    do {
      String name = matcher.group(0);
      String value = ctx.get(name) instanceof SecretString ? SecretString.SECRET_MASK : String.valueOf(ctx.get(name));
      matcher.appendReplacement(sb, value.replace("\\", "\\\\").replace("$", "\\$"));
    } while (matcher.find());
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String substituteSecretsSecured(
      @NotNull String expression, JexlContext ctx, StrLookup<Object> variableResolver, Pattern pattern) {
    StrSubstitutor substitutor = getSubstitutor(variableResolver, false);

    String result = expression;
    int limit = Math.max(EXPANSION_LIMIT, EXPANSION_MULTIPLIER_LIMIT * expression.length());
    for (int i = 0; i < DEPTH_LIMIT; i++) {
      String original = result;
      result = substitutor.replace(new StringBuffer(original));

      for (;;) {
        final Matcher matcher = pattern.matcher(result);
        if (!matcher.find()) {
          break;
        }

        result = resolveSecured(matcher, ctx);
      }
      if (result.equals(original)) {
        return result;
      }
      if (result.length() > limit) {
        throw new CriticalExpressionEvaluationException("Exponentially growing interpretation", expression);
      }
    }

    throw new CriticalExpressionEvaluationException(
        "Infinite loop or too deep indirection in property interpretation", expression);
  }
}
