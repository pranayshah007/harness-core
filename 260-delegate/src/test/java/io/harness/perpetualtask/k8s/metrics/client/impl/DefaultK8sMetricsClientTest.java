/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.metrics.client.impl;

import static io.harness.rule.OwnerRule.UTSAV;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.statssummary.PodStats;
import io.harness.k8s.model.statssummary.PodStatsList;
import io.harness.k8s.model.statssummary.Volume;
import io.harness.perpetualtask.k8s.metrics.client.model.node.NodeMetrics;
import io.harness.perpetualtask.k8s.metrics.client.model.node.NodeMetricsList;
import io.harness.perpetualtask.k8s.metrics.client.model.pod.PodMetrics;
import io.harness.perpetualtask.k8s.metrics.client.model.pod.PodMetricsList;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DefaultK8sMetricsClientTest extends CategoryTest {
  private static final String NOT_FOUND_MSG = "Not Found";
  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  JSON json;
  String resourceToString;
  DefaultK8sMetricsClient k8sMetricsClient;

  // the actual url called has params e.g., /apis/metrics.k8s.io/v1beta1/nodes?watch=false
  final String URL_REGEX_SUFFIX = "(\\?(.*))?";
  final String METRICS_API_VERSION = "metrics.k8s.io/v1beta1";

  @Before
  public void setup() {
    k8sMetricsClient =
        new DefaultK8sMetricsClient(new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build());
    json = new JSON();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void checkIfK8sMetricsClientInstanceOfCoreV1Api() {
    assertThat(k8sMetricsClient).isNotNull().isInstanceOf(CoreV1Api.class);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void nodeMetricsClientTest() throws IOException {
    resourceToString = getResourceAsString("get_nodemetrics.json");

    stubFor(get(urlMatching("^/apis/" + METRICS_API_VERSION + "/nodes" + URL_REGEX_SUFFIX))
                .willReturn(aResponse().withStatus(200).withBody(resourceToString)));

    NodeMetricsList expected = json.deserialize(resourceToString, NodeMetricsList.class);
    NodeMetricsList response = k8sMetricsClient.nodeMetrics().list().getObject();

    assertThat(resourceToString).isNotNull().isNotEmpty();
    assertThat(response).isNotNull().isEqualTo(expected);
    assertThat(response.getKind()).isEqualTo(NodeMetricsList.class.getSimpleName());
    assertThat(response.getApiVersion()).isEqualTo(METRICS_API_VERSION);

    assertThat(response.getItems()).isNotEmpty();
    assertThat(response.getItems().get(0)).isInstanceOf(NodeMetrics.class);
    assertThat(response.getItems().get(0).getKind()).isEqualTo(NodeMetrics.class.getSimpleName());
    assertThat(response.getItems().get(0).getMetadata()).isNotNull();
    assertThat(response.getItems().get(0).getMetadata().getName()).isNotEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void nodeMetricsClientTestThrowsApiExceptionWithMessage() {
    resourceToString = "404 page not found\n";

    stubFor(get(urlMatching("^/apis/" + METRICS_API_VERSION + "/nodes" + URL_REGEX_SUFFIX))
                .willReturn(aResponse().withStatus(404).withBody(resourceToString)));

    assertThatThrownBy(() -> k8sMetricsClient.nodeMetrics().list())
        .isExactlyInstanceOf(ApiException.class)
        .hasMessageContaining(NOT_FOUND_MSG);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void podMetricsClientTest() throws IOException {
    resourceToString = getResourceAsString("get_podmetrics.json");

    stubFor(get(urlMatching("^/apis/" + METRICS_API_VERSION + "/pods" + URL_REGEX_SUFFIX))
                .willReturn(aResponse().withStatus(200).withBody(resourceToString)));

    PodMetricsList expected = json.deserialize(resourceToString, PodMetricsList.class);
    PodMetricsList response = k8sMetricsClient.podMetrics().list().getObject();

    assertThat(resourceToString).isNotNull().isNotEmpty();
    assertThat(response).isNotNull().isEqualTo(expected);
    assertThat(response.getKind()).isEqualTo(PodMetricsList.class.getSimpleName());
    assertThat(response.getApiVersion()).isEqualTo(METRICS_API_VERSION);

    assertThat(response.getItems()).isNotEmpty();
    assertThat(response.getItems().get(0)).isInstanceOf(PodMetrics.class);
    assertThat(response.getItems().get(0).getKind()).isEqualTo(PodMetrics.class.getSimpleName());
    assertThat(response.getItems().get(0).getMetadata()).isNotNull();
    assertThat(response.getItems().get(0).getMetadata().getName()).isNotEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void pvMetricsClientTest() throws IOException {
    String nodeName = "gke-pr-private-general-preemptible-763ece4e-1szr";
    resourceToString = getResourceAsString("get_nodestatssummary.json");

    stubFor(get(urlMatching("^/api/v1/nodes/" + nodeName + "/proxy/stats/summary" + URL_REGEX_SUFFIX))
                .willReturn(aResponse().withStatus(200).withBody(resourceToString)));

    PodStatsList expected = json.deserialize(resourceToString, PodStatsList.class);
    PodStatsList response = k8sMetricsClient.podStats().list(nodeName).getObject();

    verify(1, getRequestedFor(urlMatching("^/api/v1/nodes/" + nodeName + "/proxy/stats/summary" + URL_REGEX_SUFFIX)));

    assertThat(resourceToString).isNotNull().isNotEmpty();
    assertThat(response).isNotNull().isEqualTo(expected);

    assertThat(response.getItems()).isNotEmpty();
    assertThat(response.getItems().get(0)).isInstanceOf(PodStats.class);
    assertThat(response.getItems().get(0).getVolumeList().get(0)).isInstanceOf(Volume.class);

    assertThat(response.getItems().get(0).getVolumeList().get(0).getPvcRef()).isNull();
    assertThat(response.getItems().get(1).getVolumeList()).isNotNull().isEmpty();

    Volume volume = response.getItems().get(2).getVolumeList().get(5);
    assertThat(volume).isNotNull();
    assertThat(volume.getUsedBytes()).isEqualTo("1101856768");
    assertThat(volume.getCapacityBytes()).isEqualTo("78729973760");
    assertThat(volume.getAvailableBytes()).isEqualTo("77611339776");
    assertThat(volume.getTime()).isEqualTo("2020-09-01T20:07:13Z");
    assertThat(volume.getPvcRef().getName()).isEqualTo("datadir-mongo-replicaset-2");
    assertThat(volume.getPvcRef().getNamespace()).isEqualTo("delegate-scope");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void podMetricsClientTestThrowsApiExceptionWithMessage() {
    resourceToString = "404 page not found\n";

    stubFor(get(urlMatching("^/apis/" + METRICS_API_VERSION + "/pods" + URL_REGEX_SUFFIX))
                .willReturn(aResponse().withStatus(404).withBody(resourceToString)));

    assertThatThrownBy(() -> k8sMetricsClient.podMetrics().list())
        .isExactlyInstanceOf(ApiException.class)
        .hasMessageContaining(NOT_FOUND_MSG);
  }

  private String getResourceAsString(String filename) throws IOException {
    URL url = getClass().getClassLoader().getResource("k8sapidump/" + filename);
    return Resources.toString(url, StandardCharsets.UTF_8);
  }
}
