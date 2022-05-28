/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.container.ContainerInfo.Status.SUCCESS;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.k8s.KubernetesConvention.getPrefixFromControllerName;
import static io.harness.k8s.KubernetesConvention.getRevisionFromControllerName;
import static io.harness.k8s.KubernetesConvention.getServiceNameFromControllerName;
import static io.harness.k8s.KubernetesHelperService.printVirtualServiceRouteWeights;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.atteo.evo.inflector.English.plural;

import io.harness.container.ContainerInfo;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;

import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentType;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Inject;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceFluent.SpecNested;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpec;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpecFluent.HttpNested;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by brett on 3/3/17
 */
@JsonTypeName("RESIZE_KUBERNETES")
public class KubernetesResizeCommandUnit extends ContainerResizeCommandUnit {
  @Inject @Transient private transient GkeClusterService gkeClusterService;
  @Inject @Transient private transient KubernetesContainerService kubernetesContainerService;
  @Inject @Transient private transient AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EncryptionService encryptionService;

  public KubernetesResizeCommandUnit() {
    super(CommandUnitType.RESIZE_KUBERNETES);
    setDeploymentType(DeploymentType.KUBERNETES.name());
  }

  @Override
  protected List<ContainerInfo> executeResize(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    String controllerName = containerServiceData.getName();
    HasMetadata controller = kubernetesContainerService.getController(kubernetesConfig, controllerName);
    if (controller == null) {
      throw new WingsException(GENERAL_ERROR).addParam("message", "No controller with name: " + controllerName);
    }
    if ("StatefulSet".equals(controller.getKind()) || "DaemonSet".equals(controller.getKind())) {
      executionLogCallback.saveExecutionLog(
          "\nResize Containers does not apply to Stateful Sets or Daemon Sets.\n", LogLevel.WARN);
      return emptyList();
    }

    if (resizeParams.isUseAutoscaler() && resizeParams.isRollback()) {
      HasMetadata autoscaler =
          kubernetesContainerService.getAutoscaler(kubernetesConfig, controllerName, resizeParams.getApiVersion());
      HorizontalPodAutoscaler v1AutoScaler = null;
      io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler v2Beta1AutoScaler = null;
      String scaleTargetRefName;

      if (autoscaler instanceof HorizontalPodAutoscaler) {
        v1AutoScaler = (HorizontalPodAutoscaler) autoscaler;
        scaleTargetRefName = v1AutoScaler.getSpec().getScaleTargetRef().getName();
      } else {
        v2Beta1AutoScaler = (io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler) autoscaler;
        scaleTargetRefName = v2Beta1AutoScaler.getSpec().getScaleTargetRef().getName();
      }
      if (autoscaler != null && controllerName.equals(scaleTargetRefName)) {
        executionLogCallback.saveExecutionLog("Deleting horizontal pod autoscaler: " + controllerName);
        kubernetesContainerService.deleteAutoscaler(kubernetesConfig, controllerName);
      }
    }

    int desiredCount = containerServiceData.getDesiredCount();
    int previousCount = containerServiceData.getPreviousCount();
    List<ContainerInfo> containerInfos = kubernetesContainerService.setControllerPodCount(kubernetesConfig,
        resizeParams.getClusterName(), controllerName, previousCount, desiredCount,
        resizeParams.getServiceSteadyStateTimeout(), executionLogCallback);

    boolean allContainersSuccess = containerInfos.stream().allMatch(info -> info.getStatus() == SUCCESS);

    if (containerInfos.size() != desiredCount || !allContainersSuccess) {
      try {
        if (containerInfos.size() != desiredCount) {
          executionLogCallback.saveExecutionLog(format("Expected data for %d %s but got %d", desiredCount,
                                                    plural("container", desiredCount), containerInfos.size()),
              LogLevel.ERROR);
        }
        List<ContainerInfo> failedContainers =
            containerInfos.stream().filter(info -> info.getStatus() != ContainerInfo.Status.SUCCESS).collect(toList());
        executionLogCallback.saveExecutionLog(
            format("The following %s did not have success status: %s", plural("container", failedContainers.size()),
                failedContainers.stream().map(ContainerInfo::getContainerId).collect(toList())),
            LogLevel.ERROR);
      } catch (Exception e) {
        Misc.logAllMessages(e, executionLogCallback);
      }
      throw new WingsException(GENERAL_ERROR).addParam("message", "Failed to resize controller");
    }

    return containerInfos;
  }

  @Override
  protected void postExecution(
      ContextData contextData, List<ContainerServiceData> allData, ExecutionLogCallback executionLogCallback) {
    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    boolean executedSomething = false;

    // Enable HPA
    if (!resizeParams.isRollback() && contextData.deployingToHundredPercent && resizeParams.isUseAutoscaler()) {
      HasMetadata hpa =
          kubernetesContainerService.createOrReplaceAutoscaler(kubernetesConfig, resizeParams.getAutoscalerYaml());
      if (hpa != null) {
        String hpaName = hpa.getMetadata().getName();
        executionLogCallback.saveExecutionLog("Horizontal pod autoscaler enabled: " + hpaName + "\n");
        executedSomething = true;
      }
    }

    // Edit weights for Istio route rule if applicable
    if (resizeParams.isUseIstioRouteRule()) {
      String controllerName = resizeParams.getContainerServiceName();
      String kubernetesServiceName = getServiceNameFromControllerName(controllerName);
      String controllerPrefix = getPrefixFromControllerName(controllerName);
      VirtualService existingVirtualService =
          kubernetesContainerService.getFabric8IstioVirtualService(kubernetesConfig, kubernetesServiceName);

      if (existingVirtualService == null) {
        throw new InvalidRequestException(format("Virtual Service [%s] not found", kubernetesServiceName));
      }

      VirtualService virtualServiceDefinition =
          createVirtualServiceDefinition(contextData, allData, existingVirtualService, kubernetesServiceName);

      if (!virtualServiceHttpRouteMatchesExisting(existingVirtualService, virtualServiceDefinition)) {
        executionLogCallback.saveExecutionLog("Setting Istio VirtualService Route destination weights:");
        printVirtualServiceRouteWeights(virtualServiceDefinition, controllerPrefix, executionLogCallback);
        kubernetesContainerService.createOrReplaceFabric8IstioVirtualService(
            kubernetesConfig, virtualServiceDefinition);
      } else {
        executionLogCallback.saveExecutionLog("No change to Istio VirtualService Route rules :");
        printVirtualServiceRouteWeights(existingVirtualService, controllerPrefix, executionLogCallback);
      }
      executionLogCallback.saveExecutionLog("");
      executedSomething = true;
    }
    if (executedSomething) {
      executionLogCallback.saveExecutionLog(DASH_STRING + "\n");
    }
  }

  private boolean virtualServiceHttpRouteMatchesExisting(
      VirtualService existingVirtualService, VirtualService virtualService) {
    if (existingVirtualService == null) {
      return false;
    }

    HTTPRoute virtualServiceHttpRoute = (((VirtualService) virtualService).getSpec()).getHttp().get(0);
    HTTPRoute existingVirtualServiceHttpRoute = (((VirtualService) existingVirtualService).getSpec()).getHttp().get(0);

    if ((virtualServiceHttpRoute == null || existingVirtualServiceHttpRoute == null)
        && virtualServiceHttpRoute != existingVirtualServiceHttpRoute) {
      return false;
    }

    List<HTTPRouteDestination> sorted = new ArrayList<>(virtualServiceHttpRoute.getRoute());
    List<HTTPRouteDestination> existingSorted = new ArrayList<>(existingVirtualServiceHttpRoute.getRoute());
    Comparator<HTTPRouteDestination> comparator =
        Comparator.comparing(a -> Integer.valueOf(a.getDestination().getSubset()));
    sorted.sort(comparator);
    existingSorted.sort(comparator);

    for (int i = 0; i < sorted.size(); i++) {
      HTTPRouteDestination dw1 = sorted.get(i);
      HTTPRouteDestination dw2 = existingSorted.get(i);
      if (!dw1.getDestination().getSubset().equals(dw2.getDestination().getSubset())
          || !dw1.getWeight().equals(dw2.getWeight())) {
        return false;
      }
    }

    return true;
  }

  private KubernetesConfig getKubernetesConfig(ContextData contextData) {
    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    KubernetesConfig kubernetesConfig;
    if (contextData.settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig config = (KubernetesClusterConfig) contextData.settingAttribute.getValue();
      encryptionService.decrypt(config, contextData.encryptedDataDetails, false);

      kubernetesConfig = ((KubernetesClusterConfig) contextData.settingAttribute.getValue())
                             .createKubernetesConfig(resizeParams.getNamespace());
    } else if (contextData.settingAttribute.getValue() instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) contextData.settingAttribute.getValue();
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(azureConfig,
          contextData.encryptedDataDetails, resizeParams.getSubscriptionId(), resizeParams.getResourceGroup(),
          resizeParams.getClusterName(), resizeParams.getNamespace(), false);
    } else if (contextData.settingAttribute.getValue() instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(contextData.settingAttribute, contextData.encryptedDataDetails,
          resizeParams.getClusterName(), resizeParams.getNamespace(), false);
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args",
              "Unknown kubernetes cloud provider setting value: " + contextData.settingAttribute.getValue().getType());
    }

    return kubernetesConfig;
  }

  @Override
  protected Map<String, Integer> getActiveServiceCounts(ContextData contextData) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    return kubernetesContainerService.getActiveServiceCountsWithLabels(
        kubernetesConfig, resizeParams.getLookupLabels());
  }

  @Override
  protected Map<String, String> getActiveServiceImages(ContextData contextData) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    String controllerName = resizeParams.getContainerServiceName();
    String imagePrefix = substringBefore(contextData.resizeParams.getImage(), ":");
    return kubernetesContainerService.getActiveServiceImages(kubernetesConfig, controllerName, imagePrefix);
  }

  @Override
  protected Optional<Integer> getServiceDesiredCount(ContextData contextData) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    return kubernetesContainerService.getControllerPodCount(
        kubernetesConfig, contextData.resizeParams.getContainerServiceName());
  }

  @Override
  protected Map<String, Integer> getTrafficWeights(ContextData contextData) {
    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    if (!resizeParams.isUseIstioRouteRule()) {
      return new HashMap<>();
    }

    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    String controllerName = resizeParams.getContainerServiceName();
    return kubernetesContainerService.getTrafficWeights(kubernetesConfig, controllerName);
  }

  @Override
  protected int getPreviousTrafficPercent(ContextData contextData) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(contextData);

    KubernetesResizeParams resizeParams = (KubernetesResizeParams) contextData.resizeParams;
    String controllerName = resizeParams.getContainerServiceName();
    return kubernetesContainerService.getTrafficPercent(kubernetesConfig, controllerName);
  }

  @Override
  protected Integer getDesiredTrafficPercent(ContextData contextData) {
    return ((KubernetesResizeParams) contextData.resizeParams).getTrafficPercent();
  }

  private VirtualService createVirtualServiceDefinition(ContextData contextData, List<ContainerServiceData> allData,
      VirtualService existingVirtualService, String kubernetesServiceName) {
    VirtualServiceSpec existingVirtualServiceSpec = ((VirtualService) existingVirtualService).getSpec();

    SpecNested<VirtualServiceBuilder> virtualServiceSpecNested =
        new VirtualServiceBuilder()
            .withApiVersion(existingVirtualService.getApiVersion())
            .withKind(existingVirtualService.getKind())
            .withNewMetadata()
            .withName(existingVirtualService.getMetadata().getName())
            .withNamespace(existingVirtualService.getMetadata().getNamespace())
            .withAnnotations(existingVirtualService.getMetadata().getAnnotations())
            .withLabels(existingVirtualService.getMetadata().getLabels())
            .endMetadata()
            .withNewSpec()
            .withHosts(existingVirtualServiceSpec.getHosts())
            .withGateways(existingVirtualServiceSpec.getGateways());

    HttpNested virtualServiceHttpNested = virtualServiceSpecNested.addNewHttp();

    for (ContainerServiceData containerServiceData : allData) {
      String controllerName = containerServiceData.getName();
      Optional<Integer> revision = getRevisionFromControllerName(controllerName);
      if (revision.isPresent()) {
        int weight = containerServiceData.getDesiredTraffic();
        if (weight > 0) {
          Destination destination = new Destination();
          destination.setHost(kubernetesServiceName);
          destination.setSubset(Integer.toString(revision.get()));
          HTTPRouteDestination destinationWeight = new HTTPRouteDestination();
          destinationWeight.setWeight(weight);
          destinationWeight.setDestination(destination);
          virtualServiceHttpNested.addToRoute(destinationWeight);
        }
      }
    }
    virtualServiceHttpNested.endHttp();
    return virtualServiceSpecNested.endSpec().build();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("RESIZE_KUBERNETES")
  public static class Yaml extends ContainerResizeCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.RESIZE_KUBERNETES.name());
    }

    @Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.RESIZE_KUBERNETES.name(), deploymentType);
    }
  }
}
