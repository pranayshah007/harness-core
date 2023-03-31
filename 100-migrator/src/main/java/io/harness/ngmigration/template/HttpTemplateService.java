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
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.template.Template;
import software.wings.beans.template.command.HttpTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

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

    templateSpec.put("url", httpTemplate.getUrl());
    templateSpec.put("method", httpTemplate.getMethod());
    templateSpec.put("delegateSelectors", RUNTIME_INPUT);
    if (EmptyPredicate.isNotEmpty(httpTemplate.getBody())) {
      templateSpec.put("requestBody", httpTemplate.getBody());
    }
    if (EmptyPredicate.isNotEmpty(httpTemplate.getAssertion())) {
      templateSpec.put("assertion", httpTemplate.getAssertion());
    }
    if (EmptyPredicate.isNotEmpty(httpTemplate.getHeaders())) {
      templateSpec.put("headers", httpTemplate.getHeaders());
    }
    return JsonUtils.asTree(templateSpec);
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return StepSpecTypeConstants.HTTP;
  }

  @Override
  public String getTimeoutString(Template template) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();
    return httpTemplate.getTimeoutMillis() < 10000 ? "10s" : httpTemplate.getTimeoutMillis() / 1000 + "s";
  }
}
