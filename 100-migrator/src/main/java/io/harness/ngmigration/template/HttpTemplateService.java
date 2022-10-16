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
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.HttpTemplate;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpTemplateService implements NgTemplateService {
  @Override
  public YamlDTO getNgTemplateConfig(Template template) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();

    Map<String, Object> templateSpec = ImmutableMap.<String, Object>builder()
                                           .put("url", httpTemplate.getUrl())
                                           .put("method", httpTemplate.getMethod())
                                           .put("requestBody", httpTemplate.getBody())
                                           .put("assertion", httpTemplate.getAssertion())
                                           .put("headers", httpTemplate.getHeaders())
                                           .build();
    List<NGVariable> variables = null;
    if (EmptyPredicate.isNotEmpty(template.getVariables())) {
      variables = template.getVariables()
                      .stream()
                      .map(variable
                          -> StringNGVariable.builder()
                                 .name(variable.getName())
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
                                .versionLabel(template.getVersion().toString())
                                .spec(JsonUtils.asTree(ImmutableMap.of("spec", templateSpec, "type", "Http", "timeout",
                                    httpTemplate.getTimeoutMillis() / 1000 + "s")))
                                .build())
        .build();
  }
}
