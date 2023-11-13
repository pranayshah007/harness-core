/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.istio;

import static io.harness.rule.OwnerRule.BUHA;

import static org.junit.Assert.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VirtualServiceTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHttpVirtualServiceWithURI() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(HttpVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .http(List.of(VirtualServiceDetails.builder()
                                        .match(List.of(URIMatch.builder()
                                                           .ignoreUriCase(true)
                                                           .uri(MatchDetails.builder().prefix("/metrics").build())
                                                           .name("istio-http-uri-rule-1")
                                                           .build()))
                                        .route(List.of(HttpRouteDestination.builder()
                                                           .destination(Destination.builder().host("test-svc").build())
                                                           .weight(80)
                                                           .build(),
                                            HttpRouteDestination.builder()
                                                .destination(Destination.builder().host("test-svc-stage").build())
                                                .weight(20)
                                                .build()))
                                        .build()))
                      .build())
            .build();

    String path = "/istio/HttpVirtualServiceWithURITest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHttpVirtualServiceWithScheme() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(HttpVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .http(List.of(VirtualServiceDetails.builder()
                                        .match(List.of(SchemeMatch.builder()
                                                           .scheme(MatchDetails.builder().exact("http").build())
                                                           .name("istio-http-uri-rule-1")
                                                           .build()))
                                        .route(List.of(HttpRouteDestination.builder()
                                                           .destination(Destination.builder().host("test-svc").build())
                                                           .weight(80)
                                                           .build(),
                                            HttpRouteDestination.builder()
                                                .destination(Destination.builder().host("test-svc-stage").build())
                                                .weight(20)
                                                .build()))
                                        .build()))
                      .build())
            .build();

    String path = "/istio/HttpVirtualServiceWithSchemeTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHttpVirtualServiceWithMethod() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(HttpVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .gateways(List.of("istio-test-gateway"))
                      .http(List.of(VirtualServiceDetails.builder()
                                        .match(List.of(MethodMatch.builder()
                                                           .method(MatchDetails.builder().exact("GET").build())
                                                           .name("istio-http-uri-rule-1")
                                                           .build()))
                                        .route(List.of(HttpRouteDestination.builder()
                                                           .destination(Destination.builder().host("test-svc").build())
                                                           .weight(80)
                                                           .build(),
                                            HttpRouteDestination.builder()
                                                .destination(Destination.builder().host("test-svc-stage").build())
                                                .weight(20)
                                                .build()))
                                        .build()))
                      .build())
            .build();

    String path = "/istio/HttpVirtualServiceWithMethodTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHttpVirtualServiceWithAuthority() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(HttpVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .http(List.of(
                          VirtualServiceDetails.builder()
                              .match(List.of(AuthorityMatch.builder()
                                                 .authority(MatchDetails.builder().exact("api.harness.io").build())
                                                 .name("istio-http-uri-rule-1")
                                                 .build()))
                              .route(List.of(HttpRouteDestination.builder()
                                                 .destination(Destination.builder().host("test-svc").build())
                                                 .weight(80)
                                                 .build(),
                                  HttpRouteDestination.builder()
                                      .destination(Destination.builder().host("test-svc-stage").build())
                                      .weight(20)
                                      .build()))
                              .build()))
                      .build())
            .build();

    String path = "/istio/HttpVirtualServiceWithAuthorityTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHttpVirtualServiceWithHeader() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(HttpVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .http(List.of(
                          VirtualServiceDetails.builder()
                              .match(List.of(PortMatch.builder().port(8080).name("istio-http-uri-rule-1").build()))
                              .route(List.of(HttpRouteDestination.builder()
                                                 .destination(Destination.builder().host("test-svc").build())
                                                 .weight(80)
                                                 .build(),
                                  HttpRouteDestination.builder()
                                      .destination(Destination.builder().host("test-svc-stage").build())
                                      .weight(20)
                                      .build()))
                              .build()))
                      .build())
            .build();

    String path = "/istio/HttpVirtualServiceWithPortTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTCPVirtualServiceWithPort() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(TCPVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .tcp(List.of(
                          VirtualServiceDetails.builder()
                              .match(List.of(PortMatch.builder().port(8080).name("istio-http-uri-rule-1").build()))
                              .route(List.of(HttpRouteDestination.builder()
                                                 .destination(Destination.builder().host("test-svc").build())
                                                 .weight(80)
                                                 .build(),
                                  HttpRouteDestination.builder()
                                      .destination(Destination.builder().host("test-svc-stage").build())
                                      .weight(20)
                                      .build()))
                              .build()))
                      .build())
            .build();

    String path = "/istio/TcpVirtualServiceWithPortTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTCPVirtualServiceWithURI() throws IOException {
    VirtualService.builder()
        .apiVersion("networking.istio.io/v1alpha3")
        .metadata(Metadata.builder().name("test-vs").build())
        .spec(TCPVirtualServiceSpec.builder()
                  .hosts(List.of("host1", "host2"))
                  .tcp(List.of(VirtualServiceDetails.builder()
                                   .match(List.of(URIMatch.builder().build()))
                                   .route(List.of(HttpRouteDestination.builder()
                                                      .destination(Destination.builder().host("test-svc").build())
                                                      .weight(80)
                                                      .build(),
                                       HttpRouteDestination.builder()
                                           .destination(Destination.builder().host("test-svc-stage").build())
                                           .weight(20)
                                           .build()))
                                   .build()))
                  .build())
        .build();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTlsVirtualServiceWithPort() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(TlsVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .tls(List.of(
                          VirtualServiceDetails.builder()
                              .match(List.of(PortMatch.builder().port(8080).name("istio-http-uri-rule-1").build()))
                              .route(List.of(HttpRouteDestination.builder()
                                                 .destination(Destination.builder().host("test-svc").build())
                                                 .weight(80)
                                                 .build(),
                                  HttpRouteDestination.builder()
                                      .destination(Destination.builder().host("test-svc-stage").build())
                                      .weight(20)
                                      .build()))
                              .build()))
                      .build())
            .build();

    String path = "/istio/TlsVirtualServiceWithPortTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTlsVirtualServiceWithHost() throws IOException {
    VirtualService virtualService =
        VirtualService.builder()
            .apiVersion("networking.istio.io/v1alpha3")
            .metadata(Metadata.builder().name("test-vs").build())
            .spec(TlsVirtualServiceSpec.builder()
                      .hosts(List.of("host1", "host2"))
                      .tls(List.of(VirtualServiceDetails.builder()
                                       .match(List.of(HostMatch.builder()
                                                          .sniHosts(List.of("*.com", "*.io"))
                                                          .name("istio-http-uri-rule-1")
                                                          .build()))
                                       .route(List.of(HttpRouteDestination.builder()
                                                          .destination(Destination.builder().host("test-svc").build())
                                                          .weight(80)
                                                          .build(),
                                           HttpRouteDestination.builder()
                                               .destination(Destination.builder().host("test-svc-stage").build())
                                               .weight(20)
                                               .build()))
                                       .build()))
                      .build())
            .build();

    String path = "/istio/TlsVirtualServiceWithHostTest.yaml";

    assertEqualYaml(virtualService, path);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTlsVirtualServiceWithUri() {
    VirtualService.builder()
        .apiVersion("networking.istio.io/v1alpha3")
        .metadata(Metadata.builder().name("test-vs").build())
        .spec(TlsVirtualServiceSpec.builder()
                  .hosts(List.of("host1", "host2"))
                  .tls(List.of(VirtualServiceDetails.builder()
                                   .match(List.of(URIMatch.builder().build()))
                                   .route(List.of(HttpRouteDestination.builder()
                                                      .destination(Destination.builder().host("test-svc").build())
                                                      .weight(80)
                                                      .build(),
                                       HttpRouteDestination.builder()
                                           .destination(Destination.builder().host("test-svc-stage").build())
                                           .weight(20)
                                           .build()))
                                   .build()))
                  .build())
        .build();
  }

  private void assertEqualYaml(VirtualService vs, String path) throws IOException {
    URL url = this.getClass().getResource(path);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    String printedResource = Yaml.dump(vs);

    assertEquals(printedResource, fileContents);
  }
}
