/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.remote;

import static io.harness.platform.PlatformConfiguration.AUDIT_SERVICE_RESOURCES;
import static io.harness.rule.OwnerRule.NISHANT;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AuditOpenApiResourceTest extends CategoryTest {
  @Mock HttpHeaders headers;
  @Mock UriInfo uriInfo;
  static Set<String> AUDIT_RESOURCES;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    AUDIT_RESOURCES = AUDIT_SERVICE_RESOURCES.stream()
                          .filter(x -> x.isAnnotationPresent(Tag.class))
                          .map(Class::getCanonicalName)
                          .collect(toSet());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetOpenApi() throws Exception {
    AuditOpenApiResource auditOpenApiResource = new AuditOpenApiResource();
    auditOpenApiResource.setOpenApiConfiguration(new SwaggerConfiguration().resourceClasses(AUDIT_RESOURCES));
    String module = randomAlphabetic(10);
    auditOpenApiResource.setModule(module);
    Response response = auditOpenApiResource.getOpenApi(headers, uriInfo);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity().toString()).contains("This contains APIs related to Audit as defined in Harness");
    assertThat(response.getEntity().toString())
        .contains("This contains APIs related to Filter of type Audit as defined in Harness");
    Map<String, Object> apiResponseMap =
        new ObjectMapper().readValue(response.getEntity().toString(), new LinkedHashMap<String, Object>().getClass());
    assertThat(((List) apiResponseMap.get("tags")).size()).isEqualTo(2);
  }
}
