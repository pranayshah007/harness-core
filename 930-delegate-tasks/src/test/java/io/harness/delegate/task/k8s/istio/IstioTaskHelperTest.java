/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.istio;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.k8s.model.Kind.Deployment;
import static io.harness.k8s.utils.ObjectYamlUtils.readYaml;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.SAHIL;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.istio.Destination;
import io.harness.k8s.model.istio.HttpRouteDestination;
import io.harness.k8s.model.istio.PortSelector;
import io.harness.k8s.model.istio.Subset;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.esotericsoftware.yamlbeans.YamlException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.fabric8.istio.api.networking.v1alpha3.DestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.PortSelectorBuilder;
import io.fabric8.istio.api.networking.v1alpha3.TCPRoute;
import io.fabric8.istio.api.networking.v1alpha3.TLSRoute;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpec;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class IstioTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Inject @InjectMocks private IstioTaskHelper istioTaskHelper;
  @Mock private KubernetesHelperService kubernetesHelperService;
  @Mock private LogCallback executionLogCallback;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsWithFabric8() throws IOException {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304));
    List<IstioDestinationWeight> destinationWeights =
        asList(IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("10").build(),
            IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("40").build(),
            IstioDestinationWeight.builder().destination("host: test\nsubset: default").weight("50").build());

    istioTaskHelper.updateVirtualServiceWithDestinationWeights(destinationWeights, service, executionLogCallback);
    List<HTTPRouteDestination> routes = service.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(10, 40, 50);
    assertThat(routes.stream()
                   .map(HTTPRouteDestination::getDestination)
                   .map(io.fabric8.istio.api.networking.v1alpha3.Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable, "default");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeights() throws IOException {
    KubernetesResource kubernetesResource = createVirtualServiceKubernetesResource(ImmutableMap.of("localhost", 2304));
    List<IstioDestinationWeight> destinationWeights =
        asList(IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("10").build(),
            IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("40").build(),
            IstioDestinationWeight.builder().destination("host: test\nsubset: default").weight("50").build());

    istioTaskHelper.updateVirtualServiceWithDestinationWeights(
        destinationWeights, kubernetesResource, executionLogCallback);
    kubernetesResource.setSpec(KubernetesHelperService.toYaml(kubernetesResource.getValue()));
    assertThat(kubernetesResource.getSpec())
        .isEqualTo("apiVersion: \"networking.istio.io/v1alpha3\"\n"
            + "kind: \"VirtualService\"\n"
            + "metadata:\n"
            + "  annotations:\n"
            + "    harness.io/managed: \"true\"\n"
            + "  name: \"harness-example-istio-virtualservice\"\n"
            + "spec:\n"
            + "  gateways:\n"
            + "  - \"harness-example-istio-gateway\"\n"
            + "  hosts:\n"
            + "  - \"test.com\"\n"
            + "  http:\n"
            + "  - route:\n"
            + "    - destination:\n"
            + "        host: \"localhost\"\n"
            + "        port:\n"
            + "          number: 2304\n"
            + "        subset: \"canary\"\n"
            + "      weight: 10\n"
            + "    - destination:\n"
            + "        host: \"localhost\"\n"
            + "        port:\n"
            + "          number: 2304\n"
            + "        subset: \"stable\"\n"
            + "      weight: 40\n"
            + "    - destination:\n"
            + "        host: \"test\"\n"
            + "        port:\n"
            + "          number: 2304\n"
            + "        subset: \"default\"\n"
            + "      weight: 50\n");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsMultipleRoutesWithFabric8() {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304, "0.0.0.0", 8030));
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only one route is allowed in VirtualService");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsMultipleRoutes()
      throws YamlException, JsonProcessingException {
    KubernetesResource service =
        createVirtualServiceKubernetesResource(ImmutableMap.of("localhost", 2304, "0.0.0.0", 8030));
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only one route is allowed in VirtualService");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNoRoutesWithFabric8() {
    VirtualService service = virtualServiceWith(ImmutableMap.of());
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Http route is not present in VirtualService. Only Http routes are allowed");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNoRoutes() throws YamlException, JsonProcessingException {
    KubernetesResource service = createVirtualServiceKubernetesResource(ImmutableMap.of());
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Http route is not present in VirtualService. Only Http routes are allowed");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsInvalidPort() throws YamlException {
    String spec = "apiVersion: networking.istio.io/v1alpha3\n"
        + "kind: VirtualService\n"
        + "metadata:\n"
        + "  annotations:\n"
        + "    harness.io/managed: \"true\"\n"
        + "  name: harness-example-istio-virtualservice\n"
        + "spec:\n"
        + "  gateways:\n"
        + "    - harness-example-istio-gateway\n"
        + "  hosts:\n"
        + "    - test.com\n"
        + "  http:\n"
        + "    - route:\n"
        + "        - destination:\n"
        + "            host: harness-example-istio-svc\n"
        + "            port:\n"
        + "              number: integer";
    KubernetesResource kubernetesResource =
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName("default/VirtualService/vs"))
            .spec(spec)
            .value(readYaml(spec).get(0))
            .build();
    List<IstioDestinationWeight> destinationWeights =
        asList(IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("10").build(),
            IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("40").build(),
            IstioDestinationWeight.builder().destination("host: test\nsubset: default").weight("50").build());
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, kubernetesResource, executionLogCallback))
        .hasMessageContaining("Invalid format of port number. String cannot be converted to Integer format");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNonHttpRoutesWithFabric8() {
    VirtualServiceSpec spec = new VirtualServiceSpecBuilder().withHttp(new HTTPRoute()).withTcp(new TCPRoute()).build();
    VirtualService service = new VirtualServiceBuilder().withSpec(spec).build();
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");

    spec.setTcp(emptyList());
    spec.setTls(asList(new TLSRoute()));
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNonHttpRoutes() throws YamlException {
    String spec = "apiVersion: networking.istio.io/v1alpha3\n"
        + "kind: VirtualService\n"
        + "metadata:\n"
        + "  annotations:\n"
        + "    harness.io/managed: \"true\"\n"
        + "  name: harness-example-istio-virtualservice\n"
        + "spec:\n"
        + "  gateways:\n"
        + "    - harness-example-istio-gateway\n"
        + "  hosts:\n"
        + "    - test.com\n"
        + "  http:\n"
        + "    - route: []\n"
        + "  tls:\n"
        + "    - route: []";
    KubernetesResource kubernetesResource =
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName("default/VirtualService/vs"))
            .spec(spec)
            .value(readYaml(spec).get(0))
            .build();
    List<IstioDestinationWeight> destinationWeights = emptyList();
    KubernetesResource finalKubernetesResource = kubernetesResource;
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, finalKubernetesResource, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");

    spec = "apiVersion: networking.istio.io/v1alpha3\n"
        + "kind: VirtualService\n"
        + "metadata:\n"
        + "  annotations:\n"
        + "    harness.io/managed: \"true\"\n"
        + "  name: harness-example-istio-virtualservice\n"
        + "spec:\n"
        + "  gateways:\n"
        + "    - harness-example-istio-gateway\n"
        + "  hosts:\n"
        + "    - test.com\n"
        + "  http:\n"
        + "    - route: []\n"
        + "  tcp:\n"
        + "    - route: []";
    kubernetesResource = KubernetesResource.builder()
                             .resourceId(KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName(
                                 "default/VirtualService/vs"))
                             .spec(spec)
                             .value(readYaml(spec).get(0))
                             .build();
    KubernetesResource finalKubernetesResource1 = kubernetesResource;
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, finalKubernetesResource1, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceManifestFilesWithRoutesForCanaryWithFabric8() throws IOException {
    VirtualService service1 = virtualServiceWith(ImmutableMap.of("localhost", 1234));
    List<KubernetesResource> resources =
        asList(KubernetesResource.builder()
                   .resourceId(KubernetesResourceId.builder().name("service1").kind(Kind.VirtualService.name()).build())
                   .value(ImmutableMap.of(
                       "metadata", ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.managed, "true"))))
                   .spec("mock")
                   .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("service2").kind(Kind.VirtualService.name()).build())
                .value(ImmutableMap.of())
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("deployment").kind(Deployment.name()).build())
                .build());

    KubernetesClient mockClient = mock(KubernetesClient.class);
    doReturn(mockClient).when(kubernetesHelperService).getKubernetesClient(any());
    ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable resource =
        mock(ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable.class);
    doReturn(resource).when(mockClient).load(any());
    doReturn(asList(service1)).when(resource).items();
    istioTaskHelper.updateVirtualServiceManifestFilesWithRoutesForCanary(
        resources, KubernetesConfig.builder().build(), executionLogCallback);
    List<HTTPRouteDestination> routes = service1.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(100, 0);
    assertThat(routes.stream()
                   .map(HTTPRouteDestination::getDestination)
                   .map(io.fabric8.istio.api.networking.v1alpha3.Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackStable, HarnessLabelValues.trackCanary);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceManifestFilesWithRoutesForCanary() throws IOException {
    KubernetesResource virtualService = createVirtualServiceKubernetesResource(ImmutableMap.of("localhost", 1234));
    List<KubernetesResource> resources =
        asList(KubernetesResource.builder()
                   .resourceId(KubernetesResourceId.builder().name("service2").kind(Kind.VirtualService.name()).build())
                   .value(ImmutableMap.of())
                   .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("deployment").kind(Deployment.name()).build())
                .build(),
            virtualService);

    istioTaskHelper.updateVirtualServiceManifestFilesWithRoutesForCanary(resources, null, executionLogCallback);
    List<HttpRouteDestination> routes = (List<HttpRouteDestination>) virtualService.getField("spec.http[0].route");
    assertThat(routes.stream().map(HttpRouteDestination::getWeight)).containsExactly(100, 0);
    assertThat(routes.stream().map(HttpRouteDestination::getDestination).map(Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackStable, HarnessLabelValues.trackCanary);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGenerateSubsetsForDestinationRuleWithFabric8() {
    List<String> subsetNames = new ArrayList<>();
    subsetNames.add(HarnessLabelValues.trackCanary);
    subsetNames.add(HarnessLabelValues.trackStable);

    final List<io.fabric8.istio.api.networking.v1alpha3.Subset> result =
        istioTaskHelper.generateSubsetsForDestinationRule(subsetNames);

    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGenerateSubsetsForDestinationRule() {
    List<String> subsetNames = new ArrayList<>();
    subsetNames.add(HarnessLabelValues.trackCanary);
    subsetNames.add(HarnessLabelValues.trackStable);

    final List<Subset> result = istioTaskHelper.getSubsets(subsetNames);

    assertThat(result.size()).isEqualTo(2);
  }

  private VirtualService virtualServiceWith(Map<String, Integer> destinations) {
    List<HTTPRoute> routes =
        destinations.entrySet()
            .stream()
            .map(entry
                -> new HTTPRouteBuilder()
                       .withRoute(new HTTPRouteDestinationBuilder()
                                      .withDestination(
                                          new DestinationBuilder()
                                              .withHost(entry.getKey())
                                              .withPort(new PortSelectorBuilder().withNumber(entry.getValue()).build())
                                              .build())
                                      .build())
                       .build())
            .collect(Collectors.toList());

    return new VirtualServiceBuilder().withSpec(new VirtualServiceSpecBuilder().withHttp(routes).build()).build();
  }

  private KubernetesResource createVirtualServiceKubernetesResource(Map<String, Integer> destinations)
      throws YamlException, JsonProcessingException {
    String spec = "apiVersion: networking.istio.io/v1alpha3\n"
        + "kind: VirtualService\n"
        + "metadata:\n"
        + "  annotations:\n"
        + "    harness.io/managed: \"true\"\n"
        + "  name: harness-example-istio-virtualservice\n"
        + "spec:\n"
        + "  gateways:\n"
        + "    - harness-example-istio-gateway\n"
        + "  hosts:\n"
        + "    - test.com\n"
        + "  http:\n"
        + "    - route:\n"
        + "        - destination:\n"
        + "            host: harness-example-istio-svc";
    KubernetesResource kubernetesResource =
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName("default/VirtualService/vs"))
            .spec(spec)
            .value(readYaml(spec).get(0))
            .build();
    if (isEmpty(destinations)) {
      kubernetesResource.setField("spec.http", Collections.emptyList());
    } else {
      List<Map<String, Object>> httpRoutes = new ArrayList<>();
      for (Map.Entry<String, Integer> entry : destinations.entrySet()) {
        Map<String, Object> routesMap = new HashMap<>();
        List<HttpRouteDestination> route = new ArrayList<>();
        HttpRouteDestination httpRouteDestination =
            HttpRouteDestination.builder()
                .destination(Destination.builder()
                                 .host(entry.getKey())
                                 .port(PortSelector.builder().number(entry.getValue()).build())
                                 .build())
                .build();
        route.add(httpRouteDestination);
        routesMap.put("route", route);
        httpRoutes.add(routesMap);
      }
      kubernetesResource.setField("spec.http", httpRoutes);
    }
    kubernetesResource.setSpec(KubernetesHelperService.toYaml(kubernetesResource.getValue()));
    kubernetesResource.setValue(readYaml(kubernetesResource.getSpec()).get(0));
    return kubernetesResource;
  }
}
