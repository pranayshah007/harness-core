/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.Variable;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.HttpTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class HttpTemplateService implements NgTemplateService {
  @Override
  public boolean isMigrationSupported() {
    return true;
  }

  @Override
  public JsonNode getNgTemplateConfigSpec(
      MigrationContext context, Template template, String orgIdentifier, String projectIdentifier) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();

    Map<String, Object> templateSpec = new HashMap<>();
    List<Variable> cgVariables = template.getVariables();

    templateSpec.put("url", convertToNGVariable(cgVariables, httpTemplate.getUrl()));
    templateSpec.put("method", httpTemplate.getMethod());
    templateSpec.put("delegateSelectors", RUNTIME_INPUT);
    if (isNotEmpty(httpTemplate.getBody())) {
      templateSpec.put("requestBody", httpTemplate.getBody());
    }
    if (isNotEmpty(httpTemplate.getAssertion())) {
      templateSpec.put("assertion", httpTemplate.getAssertion());
    }
    if (isNotEmpty(httpTemplate.getHeaders())) {
      templateSpec.put("headers", httpTemplate.getHeaders());
    }

    if (isNotEmpty(cgVariables)) {
      List<Map<String, String>> variables = new ArrayList<>();
      if (isNotEmpty(template.getVariables())) {
        template.getVariables().forEach(variable -> {
          variables.add(ImmutableMap.of("name", valueOrDefaultEmpty(variable.getName()), "value",
              StringUtils.isNotBlank(variable.getValue()) ? variable.getValue() : "", "type", "String"));
        });
      }

      templateSpec.put("inputVariables", variables);
    }
    return JsonUtils.asTree(templateSpec);
  }

  private String convertToNGVariable(List<Variable> variables, String cgValue) {
    if (isEmpty(variables)) {
      return cgValue;
    }

    if (!MigratorUtility.isExpression(cgValue)) {
      return cgValue;
    }

    String value = cgValue;
    String varValueString = cgValue.substring(2, cgValue.length() - 1);

    for (Variable var : variables) {
      if (var.getName().equals(varValueString)) {
        value = String.format("<+spec.inputVariables.%s>", var.getName());
      }
    }
    return value;
  }

  private String valueOrDefaultEmpty(String val) {
    return StringUtils.isNotBlank(val) ? val : "";
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return StepSpecTypeConstants.HTTP;
  }

  @Override
  public String getTimeoutString(Template template) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();
    return httpTemplate.getTimeoutMillis() < 10000 ? "10s"
                                                   : MigratorUtility.toTimeoutString(httpTemplate.getTimeoutMillis());
  }
}
