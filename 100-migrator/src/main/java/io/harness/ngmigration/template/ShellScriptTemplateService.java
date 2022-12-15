/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import io.harness.data.structure.EmptyPredicate;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class ShellScriptTemplateService implements NgTemplateService {
  @Override
  public boolean isMigrationSupported() {
    return true;
  }
  @Override
  public JsonNode getNgTemplateConfigSpec(Template template, String orgIdentifier, String projectIdentifier) {
    ShellScriptTemplate shellScriptTemplate = (ShellScriptTemplate) template.getTemplateObject();
    List<Map<String, String>> outputVariables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getOutputVars())) {
      for (String varName : shellScriptTemplate.getOutputVars().split(",")) {
        outputVariables.add(ImmutableMap.of(
            "name", valueOrDefaultEmpty(varName), "type", "String", "value", valueOrDefaultEmpty(varName)));
      }
    }
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getSecretOutputVars())) {
      for (String varName : shellScriptTemplate.getSecretOutputVars().split(",")) {
        outputVariables.add(ImmutableMap.of(
            "name", valueOrDefaultEmpty(varName), "type", "Secret", "value", valueOrDefaultEmpty(varName)));
      }
    }
    List<Map<String, String>> variables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      template.getVariables().forEach(variable -> {
        variables.add(ImmutableMap.of("name", valueOrDefaultEmpty(variable.getName()), "type", "String", "value",
            valueOrDefaultEmpty(variable.getValue())));
      });
    }
    Map<String, Object> templateSpec =
        ImmutableMap.<String, Object>builder()
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
    return StringUtils.isNotBlank(val) ? val : "";
  }
}
