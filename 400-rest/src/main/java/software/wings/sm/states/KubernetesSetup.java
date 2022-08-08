/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.k8s.KubernetesConvention.getNormalizedInfraMappingIdLabelValue;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder.aKubernetesSetupParams;
import static software.wings.sm.StateType.KUBERNETES_SETUP;
import static software.wings.yaml.YamlHelper.trimYaml;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.k8s.KubernetesConvention;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.ImageDetails;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder;
import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ConfigFile;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.KubernetesSetupParams;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.ContainerTaskMapper;
import software.wings.beans.container.IstioConfig;
import software.wings.beans.container.KubernetesBlueGreenConfig;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesPortProtocol;
import software.wings.beans.container.KubernetesServiceType;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.sm.ExecutionContext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by brett on 3/1/17
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class KubernetesSetup extends ContainerServiceSetup {
  // *** Note: UI Schema specified in
  // wingsui/src/containers/WorkflowEditor/custom/ServiceSetup/KubernetesSetup/KubernetesSetup.js

  public static final String PORT_KEY = "port";
  public static final String PROTOCOL_KEY = "protocol";
  public static final String TARGET_PORT_KEY = "targetPort";
  public static final String SERVICE_TYPE_KEY = "serviceType";

  @Transient @Inject private transient ConfigService configService;
  @Transient @Inject private transient ServiceTemplateService serviceTemplateService;

  private String replicationControllerName;
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
  private boolean useAutoscaler;
  private int minAutoscaleInstances;
  private int maxAutoscaleInstances;
  private int targetCpuUtilizationPercentage;
  private boolean useIngress;
  private String ingressYaml;
  private String customMetricYamlConfig;
  private boolean useIstioRouteRule;
  private IstioConfig istioConfig;
  private String releaseName;

  private boolean blueGreen;
  private KubernetesBlueGreenConfig blueGreenConfig;

  private String commandName = "Setup Replication Controller";

  public KubernetesSetup(String name) {
    super(name, KUBERNETES_SETUP.name());
  }

  @Override
  protected ContainerSetupParams buildContainerSetupParams(ExecutionContext context, String serviceName,
      ImageDetails imageDetails, Application app, Environment env, Service service,
      ContainerInfrastructureMapping infrastructureMapping, ContainerTask containerTask, String clusterName) {
    String serviceTemplateId =
        (String) serviceTemplateService.getTemplateRefKeysByService(app.getUuid(), service.getUuid(), env.getUuid())
            .get(0)
            .getId();

    List<ConfigFile> computedConfigFiles =
        serviceTemplateService.computedConfigFiles(app.getUuid(), env.getUuid(), serviceTemplateId);
    List<ConfigFile> plainConfigFileList =
        computedConfigFiles.stream().filter(configFile -> !configFile.isEncrypted()).collect(toList());
    List<ConfigFile> encryptedConfigFileList =
        computedConfigFiles.stream().filter(ConfigFile::isEncrypted).collect(toList());

    List<String[]> plainConfigFiles = null;
    if (isNotEmpty(plainConfigFileList)) {
      plainConfigFiles = getConfigFileContent(app, plainConfigFileList);
    }

    List<String[]> encryptedConfigFiles = null;
    if (isNotEmpty(encryptedConfigFileList)) {
      encryptedConfigFiles = getConfigFileContent(app, encryptedConfigFileList);
    }

    String configMapYaml = serviceTemplateService.computeConfigMapYaml(app.getUuid(), env.getUuid(), serviceTemplateId);

    String configMapYamlEvaluated = null;
    if (isNotBlank(configMapYaml)) {
      configMapYamlEvaluated = context.renderExpression(configMapYaml);
    }

    boolean useNewLabelMechanism = true;

    if (containerTask != null) {
      KubernetesContainerTask kubernetesContainerTask = (KubernetesContainerTask) containerTask;
      kubernetesContainerTask.getContainerDefinitions()
          .stream()
          .filter(containerDefinition -> isNotEmpty(containerDefinition.getCommands()))
          .forEach(containerDefinition
              -> containerDefinition.setCommands(
                  containerDefinition.getCommands().stream().map(context::renderExpression).collect(toList())));
      if (kubernetesContainerTask.getAdvancedConfig() != null) {
        kubernetesContainerTask.setAdvancedConfig(
            context.renderExpression(kubernetesContainerTask.getAdvancedConfig()));
      }
    }

    String controllerNamePrefix = isNotBlank(replicationControllerName)
        ? KubernetesConvention.normalize(context.renderExpression(replicationControllerName))
        : KubernetesConvention.getControllerNamePrefix(app.getName(), serviceName, env.getName());

    String ingressYamlEvaluated = null;
    if (isNotBlank(ingressYaml)) {
      ingressYamlEvaluated = context.renderExpression(ingressYaml);
    }

    String customMetricYamlEvaluated = null;
    if (isNotBlank(customMetricYamlConfig)) {
      customMetricYamlEvaluated = context.renderExpression(customMetricYamlConfig);
    }

    int serviceSteadyStateTimeout =
        getServiceSteadyStateTimeout() > 0 ? getServiceSteadyStateTimeout() : DEFAULT_STEADY_STATE_TIMEOUT;

    String subscriptionId = null;
    String resourceGroup = null;

    if (infrastructureMapping instanceof AzureKubernetesInfrastructureMapping) {
      subscriptionId = ((AzureKubernetesInfrastructureMapping) infrastructureMapping).getSubscriptionId();
      resourceGroup = ((AzureKubernetesInfrastructureMapping) infrastructureMapping).getResourceGroup();
    }

    String namespace = isNotBlank(infrastructureMapping.getNamespace())
        ? context.renderExpression(infrastructureMapping.getNamespace())
        : "default";

    KubernetesHelperService.validateNamespace(namespace);

    String serviceYamlEvaluated = null;
    if (serviceType == KubernetesServiceType.Yaml && isNotBlank(serviceYaml)) {
      serviceYamlEvaluated = context.renderExpression(serviceYaml);
    }

    int maxInstances = computeMaxInstances(context);
    int fixedInstances = computeFixedInstances(context, maxInstances);

    IstioConfig evaluatedIstioConfig = null;

    if (istioConfig != null) {
      evaluatedIstioConfig = new IstioConfig();
      evaluatedIstioConfig.setGateways(context.renderExpressionList(istioConfig.getGateways()));
      evaluatedIstioConfig.setHosts(context.renderExpressionList(istioConfig.getHosts()));
    }

    if (blueGreenConfig != null) {
      if (isNotBlank(blueGreenConfig.getIngressYaml())) {
        blueGreenConfig.setIngressYaml(context.renderExpression(blueGreenConfig.getIngressYaml()));
      }
      if (blueGreenConfig.getPrimaryService() != null) {
        if (isNotBlank(blueGreenConfig.getPrimaryService().getServiceYaml())) {
          blueGreenConfig.getPrimaryService().setServiceYaml(
              context.renderExpression(blueGreenConfig.getPrimaryService().getServiceYaml()));
        }
      }
      if (blueGreenConfig.getStageService() != null) {
        if (isNotBlank(blueGreenConfig.getStageService().getServiceYaml())) {
          blueGreenConfig.getStageService().setServiceYaml(
              context.renderExpression(blueGreenConfig.getStageService().getServiceYaml()));
        }
      }
    }

    String evaluatedReleaseName;
    if (isNotBlank(releaseName)) {
      evaluatedReleaseName = context.renderExpression(releaseName);
    } else {
      // This takes care of existing workflows which have null label selector
      evaluatedReleaseName = getNormalizedInfraMappingIdLabelValue(infrastructureMapping.getUuid());
    }

    return aKubernetesSetupParams()
        .withAppName(app.getName())
        .withEnvName(env.getName())
        .withServiceName(serviceName)
        .withClusterName(clusterName)
        .withNamespace(namespace)
        .withImageDetails(imageDetails)
        .withClusterIP(clusterIP)
        .withContainerTask(ContainerTaskMapper.toKubernetesContainerTaskDTO((KubernetesContainerTask) containerTask))
        .withExternalIPs(externalIPs)
        .withExternalName(externalName)
        .withServiceYaml(serviceYamlEvaluated)
        .withInfraMappingId(infrastructureMapping.getUuid())
        .withLoadBalancerIP(loadBalancerIP)
        .withNodePort(nodePort)
        .withPort(port)
        .withProtocol(protocol)
        .withServiceType(serviceType)
        .withTargetPort(targetPort)
        .withPortName(portName)
        .withControllerNamePrefix(controllerNamePrefix)
        .withUseFixedInstances(ContainerServiceSetupKeys.fixedInstances.equals(getDesiredInstanceCount()))
        .withFixedInstances(fixedInstances)
        .withMaxInstances(maxInstances)
        .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
        .withUseAutoscaler(useAutoscaler)
        .withMinAutoscaleInstances(minAutoscaleInstances)
        .withMaxAutoscaleInstances(maxAutoscaleInstances)
        .withTargetCpuUtilizationPercentage(targetCpuUtilizationPercentage)
        .withCustomMetricYamlConfig(customMetricYamlEvaluated)
        .withSubscriptionId(subscriptionId)
        .withResourceGroup(resourceGroup)
        .withUseIngress(useIngress)
        .withIngressYaml(ingressYamlEvaluated)
        .withUseIstioRouteRule(useIstioRouteRule)
        .withIstioConfig(evaluatedIstioConfig)
        .withBlueGreen(blueGreen)
        .withBlueGreenConfig(blueGreenConfig)
        .withPlainConfigFiles(plainConfigFiles)
        .withEncryptedConfigFiles(encryptedConfigFiles)
        .withConfigMapYaml(configMapYamlEvaluated)
        .withUseNewLabelMechanism(useNewLabelMechanism)
        .withReleaseName(evaluatedReleaseName)
        .build();
  }

  private List<String[]> getConfigFileContent(Application app, List<ConfigFile> configFiles) {
    return configFiles.stream()
        .map(cf -> {
          String fileName = isNotBlank(cf.getRelativeFilePath()) ? cf.getRelativeFilePath() : cf.getFileName();
          byte[] fileContent = configService.getFileContent(app.getUuid(), cf);
          return new String[] {fileName, encodeBase64(fileContent)};
        })
        .collect(toList());
  }

  @Override
  protected ContainerServiceElement buildContainerServiceElement(ExecutionContext context,
      CommandExecutionResult executionResult, ExecutionStatus status, ImageDetails imageDetails) {
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    KubernetesSetupParams setupParams = (KubernetesSetupParams) executionData.getContainerSetupParams();
    int maxInstances = computeMaxInstances(context);
    int fixedInstances = computeFixedInstances(context, maxInstances);
    ResizeStrategy resizeStrategy = getResizeStrategy() == null ? RESIZE_NEW_FIRST : getResizeStrategy();
    int serviceSteadyStateTimeout =
        getServiceSteadyStateTimeout() > 0 ? getServiceSteadyStateTimeout() : DEFAULT_STEADY_STATE_TIMEOUT;

    ContainerServiceElementBuilder containerServiceElementBuilder =
        ContainerServiceElement.builder()
            .uuid(executionData.getServiceId())
            .image(imageDetails.getName() + ":" + imageDetails.getTag())
            .useFixedInstances(ContainerServiceSetupKeys.fixedInstances.equals(getDesiredInstanceCount()))
            .fixedInstances(fixedInstances)
            .maxInstances(maxInstances)
            .resizeStrategy(resizeStrategy)
            .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
            .clusterName(executionData.getClusterName())
            .namespace(setupParams.getNamespace())
            .deploymentType(DeploymentType.KUBERNETES)
            .infraMappingId(setupParams.getInfraMappingId())
            .controllerNamePrefix(setupParams.getControllerNamePrefix())
            .useIstioRouteRule(useIstioRouteRule);
    if (executionResult != null) {
      ContainerSetupCommandUnitExecutionData setupExecutionData =
          (ContainerSetupCommandUnitExecutionData) executionResult.getCommandExecutionData();
      if (setupExecutionData != null) {
        containerServiceElementBuilder.name(setupExecutionData.getContainerServiceName())
            .lookupLabels(setupExecutionData.getLookupLabels())
            .autoscalerYaml(setupExecutionData.getAutoscalerYaml())
            .activeServiceCounts(setupExecutionData.getActiveServiceCounts())
            .trafficWeights(setupExecutionData.getTrafficWeights())
            .loadBalancer(setupExecutionData.getLoadBalancer());
        int totalActiveServiceCount = Optional.ofNullable(setupExecutionData.getActiveServiceCounts())
                                          .orElse(emptyList())
                                          .stream()
                                          .mapToInt(item -> Integer.valueOf(item[1]))
                                          .sum();
        if (totalActiveServiceCount > 0) {
          containerServiceElementBuilder.maxInstances(totalActiveServiceCount);
        }
      }
    }

    if (useAutoscaler) {
      String customMetricYamlEvaluated = null;
      if (isNotBlank(customMetricYamlConfig)) {
        customMetricYamlEvaluated = context.renderExpression(customMetricYamlConfig);
      }
      containerServiceElementBuilder.useAutoscaler(true)
          .minAutoscaleInstances(minAutoscaleInstances)
          .maxAutoscaleInstances(maxAutoscaleInstances)
          .targetCpuUtilizationPercentage(targetCpuUtilizationPercentage)
          .customMetricYamlConfig(customMetricYamlEvaluated);
    }

    return containerServiceElementBuilder.build();
  }

  private int computeFixedInstances(ExecutionContext context, int maxInstances) {
    int evaluatedFixedInstances = isNotBlank(getFixedInstances())
        ? Integer.parseInt(context.renderExpression(getFixedInstances()))
        : maxInstances;
    return evaluatedFixedInstances == 0 ? maxInstances : evaluatedFixedInstances;
  }

  private int computeMaxInstances(ExecutionContext context) {
    Integer maxVal = null;
    if (isNotBlank(getMaxInstances())) {
      try {
        maxVal = Integer.valueOf(context.renderExpression(getMaxInstances()));
      } catch (NumberFormatException e) {
        log.error(
            format("Invalid number format for max instances: %s", context.renderExpression(getMaxInstances())), e);
      }
    }
    int evaluatedMaxInstances = maxVal != null ? maxVal : DEFAULT_MAX;
    return evaluatedMaxInstances == 0 ? DEFAULT_MAX : evaluatedMaxInstances;
  }

  @Override
  protected boolean isValidInfraMapping(InfrastructureMapping infrastructureMapping) {
    return infrastructureMapping instanceof GcpKubernetesInfrastructureMapping
        || infrastructureMapping instanceof AzureKubernetesInfrastructureMapping
        || infrastructureMapping instanceof DirectKubernetesInfrastructureMapping;
  }

  @Override
  protected String getDeploymentType() {
    return DeploymentType.KUBERNETES.name();
  }

  @Override
  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  public String getReplicationControllerName() {
    return replicationControllerName;
  }

  public void setReplicationControllerName(String replicationControllerName) {
    this.replicationControllerName = replicationControllerName;
  }

  public String getServiceType() {
    return serviceType.name();
  }

  public void setServiceType(String serviceType) {
    try {
      this.serviceType = KubernetesServiceType.valueOf(serviceType);
    } catch (IllegalArgumentException e) {
      this.serviceType = KubernetesServiceType.None;
    }
  }

  public String getPort() {
    return port.toString();
  }

  public void setPort(String port) {
    this.port = Integer.parseInt(port);
  }

  public String getTargetPort() {
    return targetPort.toString();
  }

  public void setTargetPort(String targetPort) {
    this.targetPort = Integer.parseInt(targetPort);
  }

  public String getPortName() {
    return portName;
  }

  public void setPortName(String portName) {
    this.portName = portName;
  }

  public String getProtocol() {
    return protocol.name();
  }

  public void setProtocol(String protocol) {
    try {
      this.protocol = KubernetesPortProtocol.valueOf(protocol);
    } catch (IllegalArgumentException e) {
      this.protocol = KubernetesPortProtocol.TCP;
    }
  }

  public String getServiceYaml() {
    return serviceYaml;
  }

  public void setServiceYaml(String serviceYaml) {
    this.serviceYaml = trimYaml(serviceYaml);
  }

  public String getClusterIP() {
    return clusterIP;
  }

  public void setClusterIP(String clusterIP) {
    this.clusterIP = clusterIP;
  }

  public String getExternalIPs() {
    return externalIPs;
  }

  public void setExternalIPs(String externalIPs) {
    this.externalIPs = externalIPs;
  }

  public String getLoadBalancerIP() {
    return loadBalancerIP;
  }

  public void setLoadBalancerIP(String loadBalancerIP) {
    this.loadBalancerIP = loadBalancerIP;
  }

  public String getNodePort() {
    return nodePort.toString();
  }

  public void setNodePort(String nodePort) {
    this.nodePort = Integer.parseInt(nodePort);
  }

  public String getExternalName() {
    return externalName;
  }

  public void setExternalName(String externalName) {
    this.externalName = externalName;
  }

  public boolean isUseAutoscaler() {
    return useAutoscaler;
  }

  public void setUseAutoscaler(boolean useAutoscaler) {
    this.useAutoscaler = useAutoscaler;
  }

  public int getMinAutoscaleInstances() {
    return minAutoscaleInstances;
  }

  public void setMinAutoscaleInstances(int minAutoscaleInstances) {
    this.minAutoscaleInstances = minAutoscaleInstances;
  }

  public int getMaxAutoscaleInstances() {
    return maxAutoscaleInstances;
  }

  public void setMaxAutoscaleInstances(int maxAutoscaleInstances) {
    this.maxAutoscaleInstances = maxAutoscaleInstances;
  }

  public int getTargetCpuUtilizationPercentage() {
    return targetCpuUtilizationPercentage;
  }

  public void setTargetCpuUtilizationPercentage(int targetCpuUtilizationPercentage) {
    this.targetCpuUtilizationPercentage = targetCpuUtilizationPercentage;
  }

  public String getCustomMetricYamlConfig() {
    return customMetricYamlConfig;
  }

  public void setCustomMetricYamlConfig(String customMetricYamlConfig) {
    this.customMetricYamlConfig = trimYaml(customMetricYamlConfig);
  }

  public boolean isUseIngress() {
    return useIngress;
  }

  public void setUseIngress(boolean useIngress) {
    this.useIngress = useIngress;
  }

  public String getIngressYaml() {
    return ingressYaml;
  }

  public void setIngressYaml(String ingressYaml) {
    this.ingressYaml = trimYaml(ingressYaml);
  }

  public boolean isUseIstioRouteRule() {
    return useIstioRouteRule;
  }

  public void setUseIstioRouteRule(boolean useIstioRouteRule) {
    this.useIstioRouteRule = useIstioRouteRule;
  }

  public IstioConfig getIstioConfig() {
    return istioConfig;
  }

  public void setIstioConfig(IstioConfig istioConfig) {
    this.istioConfig = istioConfig;
  }

  public boolean isBlueGreen() {
    return blueGreen;
  }

  public void setBlueGreen(boolean blueGreen) {
    this.blueGreen = blueGreen;
  }

  public KubernetesBlueGreenConfig getBlueGreenConfig() {
    return blueGreenConfig;
  }

  public void setBlueGreenConfig(KubernetesBlueGreenConfig blueGreenConfig) {
    this.blueGreenConfig = blueGreenConfig;
  }

  public void setReleaseName(String releaseName) {
    this.releaseName = releaseName;
  }

  public String getReleaseName() {
    return releaseName;
  }
}
