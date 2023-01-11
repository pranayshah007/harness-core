/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions;

import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.ExpressionEvaluatorUtils;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

@Singleton
public class MigratorExpressionUtils {
  public static Object render(Object object, Map<String, String> customExpressions) {
    Map<String, Object> context = prepareContextMap(customExpressions);
    return ExpressionEvaluatorUtils.updateExpressions(object, new MigratorResolveFunctor(context));
  }

  @NotNull
  static Map<String, Object> prepareContextMap(Map<String, String> customExpressions) {
    Map<String, Object> context = new HashMap<>();
    // Infra Expressions
    context.put("infra.kubernetes.namespace", "<+infra.namespace>");
    context.put("infra.kubernetes.infraId", "<+INFRA_KEY>");

    // Env Expressions
    context.put("env.name", "<+env.name>");
    context.put("env.description", "<+env.description>");
    context.put("env.environmentType", "<+env.type>");

    // Service Expressions
    context.put("service.name", "<+service.name>");
    context.put("service.description", "<+service.description>");

    // Artifact Expressions
    context.put("artifact.metadata.image", "<+artifact.image>");
    context.put("artifact.metadata.tag", "<+artifact.tag>");
    context.put("artifact.source.dockerconfig", "<+artifact.imagePullSecret>");

    // Application Expressions
    context.put("app.name", "<+project.name>");
    context.put("app.description", "<+project.description>");

    // Variables
    context.put("workflow.variables", new WorkflowVariablesMigratorFunctor());
    context.put("pipeline.variables", new PipelineVariablesMigratorFunctor());
    context.put("serviceVariable", new ServiceVariablesMigratorFunctor());

    // Secrets
    context.put("secrets", new SecretMigratorFunctor());

    // App
    context.put("app.defaults", new AppVariablesMigratorFunctor());

    // Http Step
    context.put("httpResponseCode", "<+httpResponseCode>");
    context.put("httpResponseBody", "<+httpResponseBody>");
    context.put("httpMethod", "<+httpMethod>");
    context.put("httpUrl", "<+httpUrl>");

    if (EmptyPredicate.isNotEmpty(customExpressions)) {
      context.putAll(customExpressions);
    }

    return context;
  }

  public static Set<String> extractAll(String source) {
    /*
      Should start with `${`
      Should end with `}`
      Between the curly braces it can match any word character(a-z, A-Z, 0-9, _)
    */
    String pattern = "\\$\\{[\\w-.\"()]+}";
    if (source == null) {
      return Collections.emptySet();
    }
    Set<String> matches = new HashSet<>();
    final Pattern compiled = Pattern.compile(pattern);
    final Matcher matcher = compiled.matcher(source);
    while (matcher.find()) {
      String match = matcher.group();
      if (!match.isEmpty()) {
        matches.add(match);
      }
    }
    // We only want to return what is within the curly braces.
    // ${artifact.image} -> artifact.image
    return matches.stream().map(str -> str.substring(2, str.length() - 1)).collect(Collectors.toSet());
  }
}
