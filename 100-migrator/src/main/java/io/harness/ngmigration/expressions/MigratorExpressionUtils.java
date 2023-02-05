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
  public static Object render(Object object, Map<String, Object> customExpressions) {
    Map<String, Object> context = prepareContextMap(customExpressions);
    return ExpressionEvaluatorUtils.updateExpressions(object, new MigratorResolveFunctor(context));
  }

  @NotNull
  static Map<String, Object> prepareContextMap(Map<String, Object> customExpressions) {
    Map<String, Object> context = new HashMap<>();
    // Infra Expressions
    context.put("infra.kubernetes.namespace", "<+infra.namespace>");
    context.put("infra.kubernetes.infraId", "<+INFRA_KEY>");
    context.put("infra.helm.releaseName", "<+infra.releaseName>");
    context.put("infra.name", "<+infra.name>");

    // Env Expressions
    context.put("env.name", "<+env.name>");
    context.put("env.description", "<+env.description>");
    context.put("env.environmentType", "<+env.type>");
    context.put("env.uuid", "<+env.identifier>");

    // Service Expressions
    context.put("service.name", "<+service.name>");
    context.put("service.Name", "<+service.name>");
    context.put("Service.name", "<+service.name>");
    context.put("service.tag", "<+service.tags>");
    context.put("service.uuid", "<+service.identifier>");
    context.put("service.description", "<+service.description>");

    Map<String, String> artifactExpressions = new HashMap<>();
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.image", "<+ARTIFACT_PLACEHOLDER.image>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.tag", "<+ARTIFACT_PLACEHOLDER.tag>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.source.dockerconfig", "<+ARTIFACT_PLACEHOLDER.imagePullSecret>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.fileName", "<+ARTIFACT_PLACEHOLDER.fileName>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.format", "<+ARTIFACT_PLACEHOLDER.repositoryFormat>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.getSHA()", "<+ARTIFACT_PLACEHOLDER.metadata.SHA>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.groupId", "<+ARTIFACT_PLACEHOLDER.groupId>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.package", "<+ARTIFACT_PLACEHOLDER.metadata.package>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.region", "<+ARTIFACT_PLACEHOLDER.metadata.region>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.repository", "<+ARTIFACT_PLACEHOLDER.repository>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.repositoryName", "<+ARTIFACT_PLACEHOLDER.repositoryName>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.url", "<+ARTIFACT_PLACEHOLDER.url>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.URL", "<+ARTIFACT_PLACEHOLDER.URL>");
    artifactExpressions.put(
        "ARTIFACT_PLACEHOLDER.metadata.artifactFileName", "<+ARTIFACT_PLACEHOLDER.metadata.fileName>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.buildFullDisplayName", "<+ARTIFACT_PLACEHOLDER.uiDisplayName>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.displayName", "<+ARTIFACT_PLACEHOLDER.displayName>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.artifactId", "<+ARTIFACT_PLACEHOLDER.metadata.artifactId>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.metadata.version", "<+ARTIFACT_PLACEHOLDER.metadata.version>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.revision", "<+ARTIFACT_PLACEHOLDER.buildNo>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.buildNo", "<+ARTIFACT_PLACEHOLDER.tag>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.source.registryUrl", "<+ARTIFACT_PLACEHOLDER.registryUrl>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.URL", "<+ARTIFACT_PLACEHOLDER.URL>");
    artifactExpressions.put("ARTIFACT_PLACEHOLDER.url", "<+ARTIFACT_PLACEHOLDER.URL>");

    artifactExpressions.forEach((k, v) -> {
      // Artifact Expressions
      context.put(k.replace("ARTIFACT_PLACEHOLDER", "artifact"), v.replace("ARTIFACT_PLACEHOLDER", "artifact"));
      // Rollback Artifact Expressions
      context.put(
          k.replace("ARTIFACT_PLACEHOLDER", "rollbackArtifact"), v.replace("ARTIFACT_PLACEHOLDER", "rollbackArtifact"));
    });

    // Application Expressions
    context.put("app.name", "<+project.name>");
    context.put("app.description", "<+project.description>");

    // Variables
    context.put("workflow.variables", new WorkflowVariablesMigratorFunctor());
    context.put("pipeline.variables", new PipelineVariablesMigratorFunctor());
    context.put("serviceVariable", new ServiceVariablesMigratorFunctor());
    context.put("serviceVariables", new ServiceVariablesMigratorFunctor());
    context.put("service.variables", new ServiceVariablesMigratorFunctor());
    context.put("servicevariable", new ServiceVariablesMigratorFunctor());
    context.put("environmentVariable", new EnvVariablesMigratorFunctor());
    context.put("environmentVariables", new EnvVariablesMigratorFunctor());

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
