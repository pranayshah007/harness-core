/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.perpetualtask.k8s.watch.CrdWorkloadFetcher.WorkloadReference.builder;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionListBuilder;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1StatusBuilder;
import io.kubernetes.client.util.ClientBuilder;
import lombok.Builder;
import lombok.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CE)
public class CrdWorkloadFetcherTest extends CategoryTest {
  private CrdWorkloadFetcher crdWorkloadFetcher;

  private static final Integer REPLICAS = 1;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().notifier(new ConsoleNotifier(true)));

  @Before
  public void setUp() throws Exception {
    ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build();
    crdWorkloadFetcher = new CrdWorkloadFetcher(apiClient);
  }

  @Value
  @Builder
  private static class FooBar {
    V1ObjectMeta metadata;
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFetchWorkload() throws Exception {
    CustomResourceDefinitionList crdList = new CustomResourceDefinitionListBuilder()
                                               .addNewItem()
                                               .withApiVersion("apps/v1")
                                               .withKind("CustomResourceDefinition")
                                               .withNewSpec()
                                               .withNewNames()
                                               .withKind("FooBary")
                                               .withPlural("foobaries")
                                               .endNames()
                                               .endSpec()
                                               .endItem()
                                               .build();
    stubFor(get(urlPathEqualTo("/apis/apiextensions.k8s.io/v1/customresourcedefinitions"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(crdList))));

    FooBar foobar = FooBar.builder()
                        .metadata(new V1ObjectMetaBuilder()
                                      .withNamespace("harness")
                                      .withName("event-service-foobar")
                                      .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                      .addToLabels("key1", "val1")
                                      .addToLabels("key2", "val2")
                                      .build())
                        .build();
    stubFor(get(urlPathEqualTo("/apis/apps/v1/namespaces/harness/foobaries/event-service-foobar"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(foobar))));
    assertThat(crdWorkloadFetcher.getWorkload(builder()
                                                  .apiVersion("apps/v1")
                                                  .namespace("harness")
                                                  .name("event-service-foobar")
                                                  .uid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                                  .kind("FooBary")
                                                  .build()))
        .isEqualTo(Workload.of("FooBary",
            new V1ObjectMetaBuilder()
                .withNamespace("harness")
                .withName("event-service-foobar")
                .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                .addToLabels("key1", "val1")
                .addToLabels("key2", "val2")
                .build(),
            REPLICAS));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFetchWorkloadWithRegularPluralIfNoCrdAccess() throws Exception {
    stubFor(get(urlPathEqualTo("/apis/apiextensions.k8s.io/v1/customresourcedefinitions"))
                .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(new V1StatusBuilder()
                                                                   .withKind("Status")
                                                                   .withApiVersion("v1")
                                                                   .withStatus("Failure")
                                                                   .withReason("Forbidden")
                                                                   .withCode(403)
                                                                   .build()))));

    FooBar foobar = FooBar.builder()
                        .metadata(new V1ObjectMetaBuilder()
                                      .withNamespace("harness")
                                      .withName("event-service-foobar")
                                      .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                      .addToLabels("key1", "val1")
                                      .addToLabels("key2", "val2")
                                      .build())
                        .build();
    stubFor(get(urlPathEqualTo("/apis/apps/v1/namespaces/harness/foobars/event-service-foobar"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(foobar))));
    assertThat(crdWorkloadFetcher.getWorkload(builder()
                                                  .apiVersion("apps/v1")
                                                  .namespace("harness")
                                                  .name("event-service-foobar")
                                                  .uid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                                  .kind("FooBar")
                                                  .build()))
        .isEqualTo(Workload.of("FooBar",
            new V1ObjectMetaBuilder()
                .withNamespace("harness")
                .withName("event-service-foobar")
                .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                .addToLabels("key1", "val1")
                .addToLabels("key2", "val2")
                .build(),
            REPLICAS));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldReturnWorkloadWithKnownMetadataIfNoAccess() throws Exception {
    stubFor(get(urlPathEqualTo("/apis/apiextensions.k8s.io/v1/customresourcedefinitions"))
                .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(new V1StatusBuilder()
                                                                   .withKind("Status")
                                                                   .withApiVersion("v1")
                                                                   .withStatus("Failure")
                                                                   .withReason("Forbidden")
                                                                   .withCode(403)
                                                                   .build()))));

    stubFor(get(urlPathEqualTo("/apis/apps/v1/namespaces/harness/foobars/event-service-foobar"))
                .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(new V1StatusBuilder()
                                                                   .withKind("Status")
                                                                   .withApiVersion("v1")
                                                                   .withStatus("Failure")
                                                                   .withReason("Forbidden")
                                                                   .withCode(403)
                                                                   .build()))));
    assertThat(crdWorkloadFetcher.getWorkload(builder()
                                                  .apiVersion("apps/v1")
                                                  .namespace("harness")
                                                  .name("event-service-foobar")
                                                  .uid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                                  .kind("FooBar")
                                                  .build()))
        .isEqualTo(Workload.of("FooBar",
            new V1ObjectMetaBuilder()
                .withNamespace("harness")
                .withName("event-service-foobar")
                .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                .build(),
            REPLICAS));
  }
}
