/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.smi;

import static io.harness.rule.OwnerRule.BUHA;

import static org.junit.Assert.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpRouteGroupTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingHttpRouteGroupWithUriMatch() throws IOException {
    HttpRouteGroup httpRouteGroup =
        HttpRouteGroup.builder()
            .apiVersion("specs.smi-spec.io/v1alpha3")
            .metadata(Metadata.builder().name("smi-http-route").build())
            .spec(RouteSpec.builder()
                      .matches(List.of(URIMatch.builder().name("smi-http-uri-1").pathRegex("/metrics").build()))
                      .build())
            .build();
    String path = "/smi/HTTPRouteGroupUriTest.yaml";

    assertEqualYaml(httpRouteGroup, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingHttpRouteGroupWithMethodMatch() throws IOException {
    HttpRouteGroup httpRouteGroup =
        HttpRouteGroup.builder()
            .apiVersion("specs.smi-spec.io/v1alpha3")
            .metadata(Metadata.builder().name("smi-http-route").build())
            .spec(RouteSpec.builder()
                      .matches(List.of(
                          MethodMatch.builder().name("smi-http-method-1").methods(List.of("GET", "POST")).build()))
                      .build())
            .build();
    String path = "/smi/HTTPRouteGroupMethodTest.yaml";

    assertEqualYaml(httpRouteGroup, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingHttpRouteGroupWithHeaderMatch() throws IOException {
    HttpRouteGroup httpRouteGroup =
        HttpRouteGroup.builder()
            .apiVersion("specs.smi-spec.io/v1alpha3")
            .metadata(Metadata.builder().name("http-test-route").build())
            .spec(RouteSpec.builder()
                      .matches(List.of(HeaderMatch.builder()
                                           .name("smi-http-header-1")
                                           .headers(new TreeMap<>(Map.of("user-agent", ".*Android.*", "Content-Type",
                                               "application/json", "cookie", "^(.*?;)?(type=insider)(;.*)?$")))
                                           .build()))
                      .build())
            .build();
    String path = "/smi/HTTPRouteGroupHeaderTest.yaml";

    assertEqualYaml(httpRouteGroup, path);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingHttpRouteGroupWithPortMatch() {
    HttpRouteGroup.builder()
        .apiVersion("specs.smi-spec.io/v1alpha3")
        .metadata(Metadata.builder().name("http-test-route").build())
        .spec(RouteSpec.builder().matches(List.of(PortMatch.builder().ports(List.of(8089, 1234)).build())).build())
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreatingHttpRouteGroupWithInvalidMethod() {
    HttpRouteGroup.builder()
        .apiVersion("specs.smi-spec.io/v1alpha3")
        .metadata(Metadata.builder().name("http-test-route").build())
        .spec(RouteSpec.builder()
                  .matches(List.of(MethodMatch.builder().methods(List.of("POST", "DUMMY")).build()))
                  .build())
        .build();
  }

  private void assertEqualYaml(SMIRoute smiRoute, String path) throws IOException {
    URL url = this.getClass().getResource(path);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    String printedResource = Yaml.dump(smiRoute);

    assertEquals(printedResource, fileContents);
  }
}
