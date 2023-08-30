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
import io.harness.k8s.model.istio.Destination;
import io.harness.k8s.model.istio.HttpRouteDestination;
import io.harness.k8s.model.istio.PortSelector;
import io.harness.k8s.model.istio.Subset;
import io.harness.logging.LogCallback;
import io.harness.serializer.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleList;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
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

  public List<Subset> getSubsets(List<String> subsetNames) {
    List<Subset> subsets = new ArrayList<>();

    for (String subsetName : subsetNames) {
      Subset subset = new Subset();
      subset.setName(subsetName);
      subset.setLabels(getLabelsFromSubsetName(subsetName));
      subsets.add(subset);
    }

    return subsets;
  }

  private Map<String, String> getLabelsFromSubsetName(String subsetName) {
    Map<String, String> labels = new HashMap<>();
    switch (subsetName) {
      case HarnessLabelValues.trackCanary:
        labels.put(HarnessLabels.track, HarnessLabelValues.trackCanary);
        break;
      case HarnessLabelValues.trackStable:
        labels.put(HarnessLabels.track, HarnessLabelValues.trackStable);
        break;
      case HarnessLabelValues.colorBlue:
        labels.put(HarnessLabels.color, HarnessLabelValues.colorBlue);
        break;
      case HarnessLabelValues.colorGreen:
        labels.put(HarnessLabels.color, HarnessLabelValues.colorGreen);
        break;
    }
    return labels;
  }

  public List<io.fabric8.istio.api.networking.v1alpha3.Subset> generateSubsetsForDestinationRule(
      List<String> subsetNames) {
    List<io.fabric8.istio.api.networking.v1alpha3.Subset> subsets = new ArrayList<>();

    for (String subsetName : subsetNames) {
      io.fabric8.istio.api.networking.v1alpha3.Subset subset = new io.fabric8.istio.api.networking.v1alpha3.Subset();
      subset.setName(subsetName);
      subset.setLabels(getLabelsFromSubsetName(subsetName));
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

  private List<HTTPRouteDestination> generateDestinationWeights(List<IstioDestinationWeight> istioDestinationWeights,
      String host, io.fabric8.istio.api.networking.v1alpha3.PortSelector portSelector) throws IOException {
    List<HTTPRouteDestination> destinationWeights = new ArrayList<>();

    for (IstioDestinationWeight istioDestinationWeight : istioDestinationWeights) {
      String destinationYaml = getDestinationYaml(istioDestinationWeight.getDestination(), host);
      io.fabric8.istio.api.networking.v1alpha3.Destination destination =
          new YamlUtils().read(destinationYaml, io.fabric8.istio.api.networking.v1alpha3.Destination.class);
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

  private io.fabric8.istio.api.networking.v1alpha3.PortSelector getPortSelectorFromRoute(
      List<HTTPRouteDestination> routes) {
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
      io.fabric8.istio.api.networking.v1alpha3.PortSelector portSelector =
          getPortSelectorFromRoute(http.get(0).getRoute());
      http.get(0).setRoute(generateDestinationWeights(istioDestinationWeights, host, portSelector));
    }
  }

  private void validateRoutesInVirtualService(KubernetesResource virtualService) {
    List<Object> http = (List<Object>) virtualService.getField("spec.http");
    List<Object> tcp = (List<Object>) virtualService.getField("spec.tcp");
    List<Object> tls = (List<Object>) virtualService.getField("spec.tls");

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

  private String getHostFromRoute(KubernetesResource virtualService) {
    if (isEmpty((List<Object>) virtualService.getField("spec.http[0].route"))) {
      throw new InvalidRequestException("No routes exist in VirtualService", USER);
    }

    if (null == virtualService.getField("spec.http[0].route[0].destination")) {
      throw new InvalidRequestException("No destination exist in VirtualService", USER);
    }

    if (isBlank((String) virtualService.getField("spec.http[0].route[0].destination.host"))) {
      throw new InvalidRequestException("No host exist in VirtualService", USER);
    }

    return (String) virtualService.getField("spec.http[0].route[0].destination.host");
  }

  private List<HttpRouteDestination> generateDestinationWeights(
      List<IstioDestinationWeight> istioDestinationWeights, String host, String portNumber) throws IOException {
    List<HttpRouteDestination> destinationWeights = new ArrayList<>();

    for (IstioDestinationWeight istioDestinationWeight : istioDestinationWeights) {
      String destinationYaml = getDestinationYaml(istioDestinationWeight.getDestination(), host);
      Destination destination = new YamlUtils().read(destinationYaml, Destination.class);
      if (isNotEmpty(portNumber)) {
        try {
          destination.setPort(PortSelector.builder().number(Integer.valueOf(portNumber)).build());
        } catch (NumberFormatException exception) {
          throw new InvalidRequestException(
              "Invalid format of port number. String cannot be converted to Integer format", USER);
        }
      }

      HttpRouteDestination destinationWeight = new HttpRouteDestination();
      destinationWeight.setWeight(Integer.parseInt(istioDestinationWeight.getWeight()));
      destinationWeight.setDestination(destination);

      destinationWeights.add(destinationWeight);
    }

    return destinationWeights;
  }

  public void updateVirtualServiceWithDestinationWeights(List<IstioDestinationWeight> istioDestinationWeights,
      KubernetesResource virtualService, LogCallback executionLogCallback) throws IOException {
    validateRoutesInVirtualService(virtualService);

    executionLogCallback.saveExecutionLog("\nUpdating VirtualService with destination weights");

    List<Object> http = (List<Object>) virtualService.getField("spec.http");
    if (isNotEmpty(http)) {
      String host = getHostFromRoute(virtualService);
      String portNumber = (String) virtualService.getField("spec.http[0].route[0].destination.port.number");
      virtualService.setField(
          "spec.http[0].route", generateDestinationWeights(istioDestinationWeights, host, portNumber));
    }
  }

  private void updateVirtualServiceManifestFilesWithRoutes(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, List<IstioDestinationWeight> istioDestinationWeights,
      LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> virtualServiceResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.VirtualService.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(virtualServiceResources)) {
      return;
    }

    if (virtualServiceResources.size() > 1) {
      String msg = "\nMore than one VirtualService found. Only one VirtualService can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    // TODO: remove the kubeconfig field in the method when FF: CDS_DISABLE_FABRIC8_NG is GA.
    // kubeconfig here is only being used to get fabric8 kubernetes client which convert the resource to VirtualService
    // format which can be achieved without it as well.
    KubernetesResource kubernetesResource = virtualServiceResources.get(0);
    if (kubernetesConfig == null) {
      updateVirtualServiceWithDestinationWeights(istioDestinationWeights, kubernetesResource, executionLogCallback);
      kubernetesResource.setSpec(KubernetesHelperService.toYaml(kubernetesResource.getValue()));
      return;
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(VirtualService.class, VirtualServiceList.class);
      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      VirtualService virtualService = (VirtualService) kubernetesClient.load(inputStream).items().get(0);
      updateVirtualServiceWithDestinationWeights(istioDestinationWeights, virtualService, executionLogCallback);

      kubernetesResource.setSpec(KubernetesHelper.toYaml(virtualService));
    }
  }

  public void updateVirtualServiceManifestFilesWithRoutesForCanary(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<IstioDestinationWeight> istioDestinationWeights = new ArrayList<>();
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("100").build());
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("0").build());

    updateVirtualServiceManifestFilesWithRoutes(
        resources, kubernetesConfig, istioDestinationWeights, executionLogCallback);
  }

  public void updateDestinationRuleManifestFilesWithSubsets(List<KubernetesResource> resources, List<String> subsets,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> destinationRuleResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.DestinationRule.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(destinationRuleResources)) {
      return;
    }

    if (destinationRuleResources.size() > 1) {
      String msg = "More than one DestinationRule found. Only one DestinationRule can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    // TODO: remove the kubeconfig field in the method when FF: CDS_DISABLE_FABRIC8_NG is GA.
    // kubeconfig here is only being used to get fabric8 kubernetes client which convert the resource to DestinationRule
    // format which can be achieved without it as well.
    KubernetesResource kubernetesResource = destinationRuleResources.get(0);
    if (kubernetesConfig == null) {
      kubernetesResource.setField("spec.subsets", getSubsets(subsets));
      kubernetesResource.setSpec(KubernetesHelperService.toYaml(kubernetesResource.getValue()));
      return;
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(DestinationRule.class, DestinationRuleList.class);

      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      DestinationRule destinationRule = (DestinationRule) kubernetesClient.load(inputStream).items().get(0);
      destinationRule.getSpec().setSubsets(generateSubsetsForDestinationRule(subsets));

      kubernetesResource.setSpec(KubernetesHelper.toYaml(destinationRule));
    }
  }
}
