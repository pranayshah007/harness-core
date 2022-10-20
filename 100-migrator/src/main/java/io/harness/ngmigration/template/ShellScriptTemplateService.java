/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShellScriptTemplateService implements NgTemplateService {
  @Override
  public YamlDTO getNgTemplateConfig(Template template, String orgIdentifier, String projectIdentifier) {
    ShellScriptTemplate shellScriptTemplate = (ShellScriptTemplate) template.getTemplateObject();
    List<Map<String, String>> outputVariables = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getOutputVars())) {
      for (String varName : shellScriptTemplate.getOutputVars().split(",")) {
        outputVariables.add(ImmutableMap.of("name", varName, "type", "String", "value", varName));
      }
    }
    if (EmptyPredicate.isNotEmpty(shellScriptTemplate.getSecretOutputVars())) {
      for (String varName : shellScriptTemplate.getSecretOutputVars().split(",")) {
        outputVariables.add(ImmutableMap.of("name", varName, "type", "Secret", "value", varName));
      }
    }
    Map<String, Object> templateSpec =
        ImmutableMap.<String, Object>builder()
            .put("onDelegate", true)
                .put("source",
                ImmutableMap.of("type", "Inline",  "spec", ImmutableMap.of("script", shellScriptTemplate.getScriptString())))
            .put("shell", "BASH".equals(shellScriptTemplate.getScriptType()) ? "Bash" : "PowerShell")
            .put("outputVariables", outputVariables)
            .build();
    List<NGVariable> variables = null;
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      variables = template.getVariables()
                      .stream()
                      .map(variable
                          -> StringNGVariable.builder()
                                 .name(variable.getName())
                              .type(NGVariableType.STRING)
                                 .value(ParameterField.createValueField(variable.getValue()))
                                 .build())
                      .collect(Collectors.toList());
    }
    return NGTemplateConfig.builder()
        .templateInfoConfig(NGTemplateInfoConfig.builder()
                                .type(TemplateEntityType.STEP_TEMPLATE)
                                .identifier(MigratorUtility.generateIdentifier(template.getName()))
                                .variables(variables)
                                .name(template.getName())
                .projectIdentifier(projectIdentifier)
                .orgIdentifier(orgIdentifier)
                                .versionLabel("v"+template.getVersion().toString())
                                .spec(JsonUtils.asTree(ImmutableMap.of("spec", templateSpec, "type", "ShellScript",
                                    "timeout", shellScriptTemplate.getTimeoutMillis()<10000? "10s": shellScriptTemplate.getTimeoutMillis() / 1000 + "s")))
                                .build())
        .build();
  }
}
