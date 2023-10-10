/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.template.Template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public interface NgTemplateService {
  default Set<String> getExpressions(Template template) {
    return MigratorExpressionUtils.getExpressions(template.getTemplateObject());
  }

  default TemplateEntityType getTemplateEntityType() {
    return TemplateEntityType.STEP_TEMPLATE;
  }

  default boolean isMigrationSupported() {
    return false;
  }

  JsonNode getNgTemplateConfigSpec(
      MigrationContext context, Template template, String orgIdentifier, String projectIdentifier);

  String getNgTemplateStepName(Template template);

  default String getTimeoutString(Template template) {
    return "10m";
  }

  default List<NGVariable> getTemplateVariables(Template template) {
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      return template.getVariables()
          .stream()
          .map(variable
              -> StringNGVariable.builder()
                     .name(variable.getName())
                     .type(NGVariableType.STRING)
                     .description(variable.getDescription())
                     .value(StringUtils.isNotBlank(variable.getValue())
                             ? ParameterField.createValueField(variable.getValue())
                             : ParameterField.createValueField(""))
                     .build())
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}
