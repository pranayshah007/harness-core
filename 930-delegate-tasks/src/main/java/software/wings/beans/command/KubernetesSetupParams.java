/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.k8s.model.ImageDetails;

import software.wings.beans.container.IstioConfig;
import software.wings.beans.container.KubernetesBlueGreenConfig;
import software.wings.beans.container.KubernetesPortProtocol;
import software.wings.beans.container.KubernetesServiceType;
import software.wings.beans.dto.ContainerTask;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesSetupParams extends ContainerSetupParams {
  private KubernetesServiceType serviceType;
  private Integer port;
  private Integer targetPort;
  private String portName;
  private KubernetesPortProtocol protocol;
  private String clusterIP;
  private String externalIPs;
  private String loadBalancerIP;
  private Integer nodePort;
  private String externalName;
  private String serviceYaml;
  private String namespace;
  private String controllerNamePrefix;
  private boolean rollback;
  private boolean useFixedInstances;
  private int maxInstances;
  private int fixedInstances;
  private boolean useAutoscaler;
  private int minAutoscaleInstances;
  private int maxAutoscaleInstances;
  private int targetCpuUtilizationPercentage;
  private String customMetricYamlConfig;
  private String subscriptionId;
  private String resourceGroup;
  private boolean useIngress;
  private String ingressYaml;
  private boolean useIstioRouteRule;
  private IstioConfig istioConfig;
  private boolean blueGreen;
  private KubernetesBlueGreenConfig blueGreenConfig;
  private String configMapYaml;
  private List<String[]> plainConfigFiles;
  private List<String[]> encryptedConfigFiles;
  private boolean useNewLabelMechanism;
  private String releaseName;
  private List<String[]> serviceCounts;

  // For supporting delegate capability framework in K8s
  private String masterUrl;

  public static final class KubernetesSetupParamsBuilder {
    private String serviceName;
    private String clusterName;
    private String appName;
    private String envName;
    private ImageDetails imageDetails;
    private ContainerTask containerTask;
    private String infraMappingId;
    private KubernetesServiceType serviceType;
    private Integer port;
    private Integer targetPort;
    private String portName;
    private KubernetesPortProtocol protocol;
    private String clusterIP;
    private String externalIPs;
    private String loadBalancerIP;
    private Integer nodePort;
    private String externalName;
    private String serviceYaml;
    private String namespace;
    private String controllerNamePrefix;
    private boolean rollback;
    private boolean useFixedInstances;
    private int maxInstances;
    private int fixedInstances;
    private int serviceSteadyStateTimeout;
    private boolean useAutoscaler;
    private int minAutoscaleInstances;
    private int maxAutoscaleInstances;
    private int targetCpuUtilizationPercentage;
    private String customMetricYamlConfig;
    private String subscriptionId;
    private String resourceGroup;
    private boolean useIngress;
    private String ingressYaml;
    private boolean useIstioRouteRule;
    private IstioConfig istioConfig;
    private boolean blueGreen;
    private KubernetesBlueGreenConfig blueGreenConfig;
    private String configMapYaml;
    private List<String[]> plainConfigFiles;
    private List<String[]> encryptedConfigFiles;
    private boolean useNewLabelMechanism;
    private String releaseName;
    private List<String[]> serviceCounts;
    private String masterUrl;

    private KubernetesSetupParamsBuilder() {}

    public static KubernetesSetupParamsBuilder aKubernetesSetupParams() {
      return new KubernetesSetupParamsBuilder();
    }

    public KubernetesSetupParamsBuilder withServiceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public KubernetesSetupParamsBuilder withClusterName(String clusterName) {
      this.clusterName = clusterName;
      return this;
    }

    public KubernetesSetupParamsBuilder withAppName(String appName) {
      this.appName = appName;
      return this;
    }

    public KubernetesSetupParamsBuilder withEnvName(String envName) {
      this.envName = envName;
      return this;
    }

    public KubernetesSetupParamsBuilder withImageDetails(ImageDetails imageDetails) {
      this.imageDetails = imageDetails;
      return this;
    }

    public KubernetesSetupParamsBuilder withContainerTask(ContainerTask containerTask) {
      this.containerTask = containerTask;
      return this;
    }

    public KubernetesSetupParamsBuilder withInfraMappingId(String infraMappingId) {
      this.infraMappingId = infraMappingId;
      return this;
    }

    public KubernetesSetupParamsBuilder withServiceType(KubernetesServiceType serviceType) {
      this.serviceType = serviceType;
      return this;
    }

    public KubernetesSetupParamsBuilder withPort(Integer port) {
      this.port = port;
      return this;
    }

    public KubernetesSetupParamsBuilder withTargetPort(Integer targetPort) {
      this.targetPort = targetPort;
      return this;
    }

    public KubernetesSetupParamsBuilder withPortName(String portName) {
      this.portName = portName;
      return this;
    }

    public KubernetesSetupParamsBuilder withProtocol(KubernetesPortProtocol protocol) {
      this.protocol = protocol;
      return this;
    }

    public KubernetesSetupParamsBuilder withClusterIP(String clusterIP) {
      this.clusterIP = clusterIP;
      return this;
    }

    public KubernetesSetupParamsBuilder withExternalIPs(String externalIPs) {
      this.externalIPs = externalIPs;
      return this;
    }

    public KubernetesSetupParamsBuilder withLoadBalancerIP(String loadBalancerIP) {
      this.loadBalancerIP = loadBalancerIP;
      return this;
    }

    public KubernetesSetupParamsBuilder withNodePort(Integer nodePort) {
      this.nodePort = nodePort;
      return this;
    }

    public KubernetesSetupParamsBuilder withExternalName(String externalName) {
      this.externalName = externalName;
      return this;
    }

    public KubernetesSetupParamsBuilder withServiceYaml(String serviceYaml) {
      this.serviceYaml = serviceYaml;
      return this;
    }

    public KubernetesSetupParamsBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public KubernetesSetupParamsBuilder withControllerNamePrefix(String controllerNamePrefix) {
      this.controllerNamePrefix = controllerNamePrefix;
      return this;
    }

    public KubernetesSetupParamsBuilder withRollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public KubernetesSetupParamsBuilder withUseFixedInstances(boolean useFixedInstances) {
      this.useFixedInstances = useFixedInstances;
      return this;
    }

    public KubernetesSetupParamsBuilder withMaxInstances(int maxInstances) {
      this.maxInstances = maxInstances;
      return this;
    }

    public KubernetesSetupParamsBuilder withFixedInstances(int fixedInstances) {
      this.fixedInstances = fixedInstances;
      return this;
    }

    public KubernetesSetupParamsBuilder withServiceSteadyStateTimeout(int serviceSteadyStateTimeout) {
      this.serviceSteadyStateTimeout = serviceSteadyStateTimeout;
      return this;
    }

    public KubernetesSetupParamsBuilder withUseAutoscaler(boolean useAutoscaler) {
      this.useAutoscaler = useAutoscaler;
      return this;
    }

    public KubernetesSetupParamsBuilder withMinAutoscaleInstances(int minAutoscaleInstances) {
      this.minAutoscaleInstances = minAutoscaleInstances;
      return this;
    }

    public KubernetesSetupParamsBuilder withMaxAutoscaleInstances(int maxAutoscaleInstances) {
      this.maxAutoscaleInstances = maxAutoscaleInstances;
      return this;
    }

    public KubernetesSetupParamsBuilder withTargetCpuUtilizationPercentage(int targetCpuUtilizationPercentage) {
      this.targetCpuUtilizationPercentage = targetCpuUtilizationPercentage;
      return this;
    }

    public KubernetesSetupParamsBuilder withCustomMetricYamlConfig(String customMetricYamlConfig) {
      this.customMetricYamlConfig = customMetricYamlConfig;
      return this;
    }

    public KubernetesSetupParamsBuilder withSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
      return this;
    }

    public KubernetesSetupParamsBuilder withResourceGroup(String resourceGroup) {
      this.resourceGroup = resourceGroup;
      return this;
    }

    public KubernetesSetupParamsBuilder withUseIngress(boolean useIngress) {
      this.useIngress = useIngress;
      return this;
    }

    public KubernetesSetupParamsBuilder withIngressYaml(String ingressYaml) {
      this.ingressYaml = ingressYaml;
      return this;
    }

    public KubernetesSetupParamsBuilder withUseIstioRouteRule(boolean useIstioRouteRule) {
      this.useIstioRouteRule = useIstioRouteRule;
      return this;
    }

    public KubernetesSetupParamsBuilder withIstioConfig(IstioConfig istioConfig) {
      this.istioConfig = istioConfig;
      return this;
    }

    public KubernetesSetupParamsBuilder withBlueGreen(boolean blueGreen) {
      this.blueGreen = blueGreen;
      return this;
    }

    public KubernetesSetupParamsBuilder withBlueGreenConfig(KubernetesBlueGreenConfig blueGreenConfig) {
      this.blueGreenConfig = blueGreenConfig;
      return this;
    }

    public KubernetesSetupParamsBuilder withConfigMapYaml(String configMapYaml) {
      this.configMapYaml = configMapYaml;
      return this;
    }

    public KubernetesSetupParamsBuilder withPlainConfigFiles(List<String[]> plainConfigFiles) {
      this.plainConfigFiles = plainConfigFiles;
      return this;
    }

    public KubernetesSetupParamsBuilder withEncryptedConfigFiles(List<String[]> encryptedConfigFiles) {
      this.encryptedConfigFiles = encryptedConfigFiles;
      return this;
    }

    public KubernetesSetupParamsBuilder withUseNewLabelMechanism(boolean useNewLabelMechanism) {
      this.useNewLabelMechanism = useNewLabelMechanism;
      return this;
    }

    public KubernetesSetupParamsBuilder withReleaseName(String releaseName) {
      this.releaseName = releaseName;
      return this;
    }

    public KubernetesSetupParamsBuilder withServiceCounts(List<String[]> serviceCounts) {
      this.serviceCounts = serviceCounts;
      return this;
    }

    public KubernetesSetupParamsBuilder withMasterUrl(String masterUrl) {
      this.masterUrl = masterUrl;
      return this;
    }

    public KubernetesSetupParamsBuilder but() {
      return aKubernetesSetupParams()
          .withServiceName(serviceName)
          .withClusterName(clusterName)
          .withAppName(appName)
          .withEnvName(envName)
          .withImageDetails(imageDetails)
          .withContainerTask(containerTask)
          .withInfraMappingId(infraMappingId)
          .withServiceType(serviceType)
          .withPort(port)
          .withTargetPort(targetPort)
          .withPortName(portName)
          .withProtocol(protocol)
          .withClusterIP(clusterIP)
          .withExternalIPs(externalIPs)
          .withLoadBalancerIP(loadBalancerIP)
          .withNodePort(nodePort)
          .withExternalName(externalName)
          .withServiceYaml(serviceYaml)
          .withNamespace(namespace)
          .withControllerNamePrefix(controllerNamePrefix)
          .withRollback(rollback)
          .withUseFixedInstances(useFixedInstances)
          .withMaxInstances(maxInstances)
          .withFixedInstances(fixedInstances)
          .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
          .withUseAutoscaler(useAutoscaler)
          .withMinAutoscaleInstances(minAutoscaleInstances)
          .withMaxAutoscaleInstances(maxAutoscaleInstances)
          .withTargetCpuUtilizationPercentage(targetCpuUtilizationPercentage)
          .withCustomMetricYamlConfig(customMetricYamlConfig)
          .withSubscriptionId(subscriptionId)
          .withResourceGroup(resourceGroup)
          .withUseIngress(useIngress)
          .withIngressYaml(ingressYaml)
          .withUseIstioRouteRule(useIstioRouteRule)
          .withIstioConfig(istioConfig)
          .withBlueGreen(blueGreen)
          .withBlueGreenConfig(blueGreenConfig)
          .withConfigMapYaml(configMapYaml)
          .withPlainConfigFiles(plainConfigFiles)
          .withEncryptedConfigFiles(encryptedConfigFiles)
          .withUseNewLabelMechanism(useNewLabelMechanism)
          .withReleaseName(releaseName)
          .withServiceCounts(serviceCounts)
          .withMasterUrl(masterUrl);
    }

    public KubernetesSetupParams build() {
      KubernetesSetupParams kubernetesSetupParams = new KubernetesSetupParams();
      kubernetesSetupParams.setServiceName(serviceName);
      kubernetesSetupParams.setClusterName(clusterName);
      kubernetesSetupParams.setAppName(appName);
      kubernetesSetupParams.setEnvName(envName);
      kubernetesSetupParams.setImageDetails(imageDetails);
      kubernetesSetupParams.setContainerTask(containerTask);
      kubernetesSetupParams.setInfraMappingId(infraMappingId);
      kubernetesSetupParams.setServiceType(serviceType);
      kubernetesSetupParams.setPort(port);
      kubernetesSetupParams.setTargetPort(targetPort);
      kubernetesSetupParams.setPortName(portName);
      kubernetesSetupParams.setProtocol(protocol);
      kubernetesSetupParams.setClusterIP(clusterIP);
      kubernetesSetupParams.setExternalIPs(externalIPs);
      kubernetesSetupParams.setLoadBalancerIP(loadBalancerIP);
      kubernetesSetupParams.setNodePort(nodePort);
      kubernetesSetupParams.setExternalName(externalName);
      kubernetesSetupParams.setServiceYaml(serviceYaml);
      kubernetesSetupParams.setNamespace(namespace);
      kubernetesSetupParams.setControllerNamePrefix(controllerNamePrefix);
      kubernetesSetupParams.setRollback(rollback);
      kubernetesSetupParams.setUseFixedInstances(useFixedInstances);
      kubernetesSetupParams.setMaxInstances(maxInstances);
      kubernetesSetupParams.setFixedInstances(fixedInstances);
      kubernetesSetupParams.setServiceSteadyStateTimeout(serviceSteadyStateTimeout);
      kubernetesSetupParams.setUseAutoscaler(useAutoscaler);
      kubernetesSetupParams.setMinAutoscaleInstances(minAutoscaleInstances);
      kubernetesSetupParams.setMaxAutoscaleInstances(maxAutoscaleInstances);
      kubernetesSetupParams.setTargetCpuUtilizationPercentage(targetCpuUtilizationPercentage);
      kubernetesSetupParams.setCustomMetricYamlConfig(customMetricYamlConfig);
      kubernetesSetupParams.setSubscriptionId(subscriptionId);
      kubernetesSetupParams.setResourceGroup(resourceGroup);
      kubernetesSetupParams.setUseIngress(useIngress);
      kubernetesSetupParams.setIngressYaml(ingressYaml);
      kubernetesSetupParams.setUseIstioRouteRule(useIstioRouteRule);
      kubernetesSetupParams.setIstioConfig(istioConfig);
      kubernetesSetupParams.setBlueGreen(blueGreen);
      kubernetesSetupParams.setBlueGreenConfig(blueGreenConfig);
      kubernetesSetupParams.setConfigMapYaml(configMapYaml);
      kubernetesSetupParams.setPlainConfigFiles(plainConfigFiles);
      kubernetesSetupParams.setEncryptedConfigFiles(encryptedConfigFiles);
      kubernetesSetupParams.setUseNewLabelMechanism(useNewLabelMechanism);
      kubernetesSetupParams.setReleaseName(releaseName);
      kubernetesSetupParams.setServiceCounts(serviceCounts);
      kubernetesSetupParams.setMasterUrl(masterUrl);
      return kubernetesSetupParams;
    }
  }
}
