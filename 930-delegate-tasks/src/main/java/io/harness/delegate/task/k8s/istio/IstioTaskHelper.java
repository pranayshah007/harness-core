/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.istio;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.LogCallback;
import io.harness.serializer.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleList;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.PortSelector;
import io.fabric8.istio.api.networking.v1alpha3.Subset;
import io.fabric8.istio.api.networking.v1alpha3.TCPRoute;
import io.fabric8.istio.api.networking.v1alpha3.TLSRoute;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceList;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class IstioTaskHelper {
  @Inject private KubernetesHelperService kubernetesHelperService;

  public static final String ISTIO_DESTINATION_TEMPLATE = "host: $ISTIO_DESTINATION_HOST_NAME\n"
      + "subset: $ISTIO_DESTINATION_SUBSET_NAME";

  public List<Subset> generateSubsetsForDestinationRule(List<String> subsetNames) {
    List<Subset> subsets = new ArrayList<>();

    for (String subsetName : subsetNames) {
      Subset subset = new Subset();
      subset.setName(subsetName);

      if (subsetName.equals(HarnessLabelValues.trackCanary)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackCanary);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.trackStable)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackStable);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorBlue)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorBlue);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorGreen)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorGreen);
        subset.setLabels(labels);
      }

      subsets.add(subset);
    }

    return subsets;
  }

  private String generateDestination(String host, String subset) {
    return ISTIO_DESTINATION_TEMPLATE.replace("$ISTIO_DESTINATION_HOST_NAME", host)
        .replace("$ISTIO_DESTINATION_SUBSET_NAME", subset);
  }

  private String getDestinationYaml(String destination, String host) {
    if (canaryDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackCanary);
    } else if (stableDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackStable);
    } else {
      return destination;
    }
  }

  private List<HTTPRouteDestination> generateDestinationWeights(
      List<IstioDestinationWeight> istioDestinationWeights, String host, PortSelector portSelector) throws IOException {
    List<HTTPRouteDestination> destinationWeights = new ArrayList<>();

    for (IstioDestinationWeight istioDestinationWeight : istioDestinationWeights) {
      String destinationYaml = getDestinationYaml(istioDestinationWeight.getDestination(), host);
      Destination destination = new YamlUtils().read(destinationYaml, Destination.class);
      destination.setPort(portSelector);

      HTTPRouteDestination destinationWeight = new HTTPRouteDestination();
      destinationWeight.setWeight(Integer.parseInt(istioDestinationWeight.getWeight()));
      destinationWeight.setDestination(destination);

      destinationWeights.add(destinationWeight);
    }

    return destinationWeights;
  }

  private String getHostFromRoute(List<HTTPRouteDestination> routes) {
    if (isEmpty(routes)) {
      throw new InvalidRequestException("No routes exist in VirtualService", USER);
    }

    if (null == routes.get(0).getDestination()) {
      throw new InvalidRequestException("No destination exist in VirtualService", USER);
    }

    if (isBlank(routes.get(0).getDestination().getHost())) {
      throw new InvalidRequestException("No host exist in VirtualService", USER);
    }

    return routes.get(0).getDestination().getHost();
  }

  private PortSelector getPortSelectorFromRoute(List<HTTPRouteDestination> routes) {
    return routes.get(0).getDestination().getPort();
  }

  private void validateRoutesInVirtualService(VirtualService virtualService) {
    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    List<TCPRoute> tcp = virtualService.getSpec().getTcp();
    List<TLSRoute> tls = virtualService.getSpec().getTls();

    if (isEmpty(http)) {
      throw new InvalidRequestException(
          "Http route is not present in VirtualService. Only Http routes are allowed", USER);
    }

    if (isNotEmpty(tcp) || isNotEmpty(tls)) {
      throw new InvalidRequestException("Only Http routes are allowed in VirtualService for Traffic split", USER);
    }

    if (http.size() > 1) {
      throw new InvalidRequestException("Only one route is allowed in VirtualService", USER);
    }
  }

  public void updateVirtualServiceWithDestinationWeights(List<IstioDestinationWeight> istioDestinationWeights,
      VirtualService virtualService, LogCallback executionLogCallback) throws IOException {
    validateRoutesInVirtualService(virtualService);

    executionLogCallback.saveExecutionLog("\nUpdating VirtualService with destination weights");

    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    if (isNotEmpty(http)) {
      String host = getHostFromRoute(http.get(0).getRoute());
      PortSelector portSelector = getPortSelectorFromRoute(http.get(0).getRoute());
      http.get(0).setRoute(generateDestinationWeights(istioDestinationWeights, host, portSelector));
    }
  }

  private VirtualService updateVirtualServiceManifestFilesWithRoutes(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, List<IstioDestinationWeight> istioDestinationWeights,
      LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> virtualServiceResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.VirtualService.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(virtualServiceResources)) {
      return null;
    }

    if (virtualServiceResources.size() > 1) {
      String msg = "\nMore than one VirtualService found. Only one VirtualService can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(VirtualService.class, VirtualServiceList.class);
      KubernetesResource kubernetesResource = virtualServiceResources.get(0);
      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      VirtualService virtualService = (VirtualService) kubernetesClient.load(inputStream).items().get(0);
      updateVirtualServiceWithDestinationWeights(istioDestinationWeights, virtualService, executionLogCallback);

      kubernetesResource.setSpec(KubernetesHelper.toYaml(virtualService));

      return virtualService;
    }
  }

  public VirtualService updateVirtualServiceManifestFilesWithRoutesForCanary(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<IstioDestinationWeight> istioDestinationWeights = new ArrayList<>();
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("100").build());
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("0").build());

    return updateVirtualServiceManifestFilesWithRoutes(
        resources, kubernetesConfig, istioDestinationWeights, executionLogCallback);
  }

  public DestinationRule updateDestinationRuleManifestFilesWithSubsets(List<KubernetesResource> resources,
      List<String> subsets, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> destinationRuleResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.DestinationRule.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(destinationRuleResources)) {
      return null;
    }

    if (destinationRuleResources.size() > 1) {
      String msg = "More than one DestinationRule found. Only one DestinationRule can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(DestinationRule.class, DestinationRuleList.class);

      KubernetesResource kubernetesResource = destinationRuleResources.get(0);
      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      DestinationRule destinationRule = (DestinationRule) kubernetesClient.load(inputStream).items().get(0);
      destinationRule.getSpec().setSubsets(generateSubsetsForDestinationRule(subsets));

      kubernetesResource.setSpec(KubernetesHelper.toYaml(destinationRule));

      return destinationRule;
    }
  }
}
