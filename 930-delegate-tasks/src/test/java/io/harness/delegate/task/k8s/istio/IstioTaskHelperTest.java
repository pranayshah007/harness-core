/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.istio;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.k8s.model.Kind.Deployment;
import static io.harness.rule.OwnerRule.ABOSII;
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
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.DestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.PortSelectorBuilder;
import io.fabric8.istio.api.networking.v1alpha3.Subset;
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
  public void testUpdateVirtualServiceWithDestinationWeights() throws IOException {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304));
    List<IstioDestinationWeight> destinationWeights =
        asList(IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("10").build(),
            IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("40").build(),
            IstioDestinationWeight.builder().destination("host: test\nsubset: default").weight("50").build());

    istioTaskHelper.updateVirtualServiceWithDestinationWeights(destinationWeights, service, executionLogCallback);
    List<HTTPRouteDestination> routes = service.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(10, 40, 50);
    assertThat(routes.stream().map(HTTPRouteDestination::getDestination).map(Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable, "default");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsMultipleRoutes() {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304, "0.0.0.0", 8030));
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only one route is allowed in VirtualService");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNoRoutes() {
    VirtualService service = virtualServiceWith(ImmutableMap.of());
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> istioTaskHelper.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Http route is not present in VirtualService. Only Http routes are allowed");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNonHttpRoutes() {
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
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceManifestFilesWithRoutesForCanary() throws IOException {
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
    VirtualService result = istioTaskHelper.updateVirtualServiceManifestFilesWithRoutesForCanary(
        resources, KubernetesConfig.builder().build(), executionLogCallback);
    List<HTTPRouteDestination> routes = result.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(100, 0);
    assertThat(routes.stream().map(HTTPRouteDestination::getDestination).map(Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackStable, HarnessLabelValues.trackCanary);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGenerateSubsetsForDestinationRule() {
    List<String> subsetNames = new ArrayList<>();
    subsetNames.add(HarnessLabelValues.trackCanary);
    subsetNames.add(HarnessLabelValues.trackStable);
    subsetNames.add(HarnessLabelValues.colorBlue);
    subsetNames.add(HarnessLabelValues.colorGreen);

    final List<Subset> result = istioTaskHelper.generateSubsetsForDestinationRule(subsetNames);

    assertThat(result.size()).isEqualTo(4);
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
}
