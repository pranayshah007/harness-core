/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.common.TemplateConstants.HTTP_HEALTH_CHECK;

import static java.util.Arrays.asList;

import io.harness.serializer.JsonUtils;

import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.HttpTemplate;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;

@Singleton
public class HttpTemplateProcessor extends StateTemplateProcessor {
  private static final String URL = "url";
  private static final String METHOD = "method";
  private static final String HEADER = "header";
  private static final String HEADERS = "headers";
  private static final String BODY = "body";
  private static final String ASSERTION = "assertion";
  private static final String TIMEOUT_MILLIS = "timeoutMillis";
  private static final String VARIABLES = "variables";

  @Override
  public TemplateType getTemplateType() {
    return TemplateType.HTTP;
  }

  @Override
  public void loadDefaultTemplates(String accountId, String accountName) {
    super.loadDefaultTemplates(asList(HTTP_HEALTH_CHECK), accountId, accountName);
  }

  @Override
  public void transform(Template template, Map<String, Object> properties) {
    HttpTemplate httpTemplate = (HttpTemplate) template.getTemplateObject();
    if (isNotEmpty(httpTemplate.getUrl())) {
      properties.put(URL, httpTemplate.getUrl());
    }
    if (isNotEmpty(httpTemplate.getMethod())) {
      properties.put(METHOD, httpTemplate.getMethod());
    }
    if (isNotEmpty(httpTemplate.getHeader())) {
      properties.put(HEADER, httpTemplate.getHeader());
    }
    if (isNotEmpty(httpTemplate.getHeaders())) {
      properties.put(HEADERS,
          httpTemplate.getHeaders()
              .stream()
              .map(header -> Document.parse(JsonUtils.asJson(header)))
              .collect(Collectors.toList()));
    }
    if (isNotEmpty(httpTemplate.getBody())) {
      properties.put(BODY, httpTemplate.getBody());
    }
    if (isNotEmpty(httpTemplate.getAssertion())) {
      properties.put(ASSERTION, httpTemplate.getAssertion());
    }
    properties.put(TIMEOUT_MILLIS, httpTemplate.getTimeoutMillis());
  }

  @Override
  public List<String> fetchTemplateProperties() {
    List<String> templateProperties = super.fetchTemplateProperties();
    templateProperties.addAll(asList(URL, METHOD, HEADER, BODY, ASSERTION, VARIABLES));
    return templateProperties;
  }
}
