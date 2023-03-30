/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class ShellScriptTemplateService implements NgTemplateService {
  @Override
  public boolean isMigrationSupported() {
    return true;
  }

  @Override
  public JsonNode getNgTemplateConfigSpec(
      MigrationContext context, Template template, String orgIdentifier, String projectIdentifier) {
    ShellScriptTemplate shellScriptTemplate = (ShellScriptTemplate) template.getTemplateObject();
    List<Map<String, String>> outputVariables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getOutputVars())) {
      for (String varName : shellScriptTemplate.getOutputVars().split(",")) {
        outputVariables.add(getOutputVariable(varName, "String"));
      }
    }
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getSecretOutputVars())) {
      for (String varName : shellScriptTemplate.getSecretOutputVars().split(",")) {
        outputVariables.add(getOutputVariable(varName, "Secret"));
      }
    }

    Set<String> expressions = MigratorExpressionUtils.getExpressions(shellScriptTemplate);
    List<Map<String, String>> variables = new ArrayList<>();
    Map<String, Object> customExpressions = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(expressions)) {
      final Pattern pattern = Pattern.compile("[a-zA-Z_]+[\\w.]*");
      expressions.stream()
          .filter(exp -> exp.contains("."))
          .filter(exp -> pattern.matcher(exp).matches())
          .forEach(exp -> {
            String value = exp.startsWith("context.") ? exp.replaceFirst("context\\.", "") : exp;
            value = value.replace('.', '_');
            customExpressions.put(exp, "${" + value + "}");
          });
      customExpressions.values()
          .stream()
          .distinct()
          .map(String.class ::cast)
          .map(value -> value.substring(2, value.length() - 1))
          .forEach(value -> variables.add(getEnvironmentVariable(value, null)));
    }

    MigratorExpressionUtils.render(context, shellScriptTemplate, customExpressions);

    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      template.getVariables()
          .stream()
          .filter(variable -> StringUtils.isNotBlank(variable.getName()))
          .forEach(variable -> variables.add(getEnvironmentVariable(variable.getName(), variable.getValue())));
    }
    Map<String, Object> templateSpec =
        ImmutableMap.<String, Object>builder()
            .put("delegateSelectors", RUNTIME_INPUT)
            .put("onDelegate", true)
            .put("source",
                ImmutableMap.of(
                    "type", "Inline", "spec", ImmutableMap.of("script", shellScriptTemplate.getScriptString())))
            .put("shell", "BASH".equals(shellScriptTemplate.getScriptType()) ? "Bash" : "PowerShell")
            .put("outputVariables", outputVariables)
            .put("environmentVariables", variables)
            .build();
    return JsonUtils.asTree(templateSpec);
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return StepSpecTypeConstants.SHELL_SCRIPT;
  }

  @Override
  public String getTimeoutString(Template template) {
    ShellScriptTemplate shellScriptTemplate = (ShellScriptTemplate) template.getTemplateObject();
    return shellScriptTemplate.getTimeoutMillis() < 10000 ? "10s" : shellScriptTemplate.getTimeoutMillis() / 1000 + "s";
  }

  static String valueOrDefaultEmpty(String val) {
    return StringUtils.isNotBlank(val) ? MigratorUtility.generateName(val).replace('-', '_') : "";
  }

  static String valueOrDefaultRuntime(String val) {
    return StringUtils.isNotBlank(val) ? val.trim() : "<+input>";
  }

  static Map<String, String> getEnvironmentVariable(String varName, String value) {
    return ImmutableMap.<String, String>builder()
        .put("name", valueOrDefaultEmpty(varName))
        .put("type", "String")
        .put("value", valueOrDefaultRuntime(value))
        .build();
  }

  static Map<String, String> getOutputVariable(String varName, String type) {
    return ImmutableMap.<String, String>builder()
        .put("name", valueOrDefaultEmpty(varName))
        .put("type", type)
        .put("value", valueOrDefaultEmpty(varName))
        .build();
  }
}
