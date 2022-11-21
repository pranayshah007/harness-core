/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.template.Template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public interface NgTemplateService {
  default boolean isMigrationSupported() {
    return false;
  }
  JsonNode getNgTemplateConfigSpec(Template template, String orgIdentifier, String projectIdentifier);

  String getNgTemplateStepName(Template template);

  String getTimeoutString(Template template);

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
