/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.k8s.KubernetesConvention.getBlueGreenIngressName;
import static io.harness.k8s.KubernetesConvention.getInternalHarnessConfigName;
import static io.harness.k8s.KubernetesConvention.getKubernetesRegistrySecretName;
import static io.harness.k8s.KubernetesConvention.getKubernetesServiceName;
import static io.harness.k8s.KubernetesConvention.getLabelValue;
import static io.harness.k8s.KubernetesConvention.getPrefixFromControllerName;
import static io.harness.k8s.KubernetesConvention.getPrimaryServiceName;
import static io.harness.k8s.KubernetesConvention.getRevisionFromControllerName;
import static io.harness.k8s.KubernetesConvention.getStageServiceName;
import static io.harness.k8s.KubernetesHelperService.printVirtualServiceRouteWeights;
import static io.harness.k8s.KubernetesHelperService.toDisplayYaml;
import static io.harness.k8s.KubernetesHelperService.toYaml;
import static io.harness.k8s.model.ContainerApiVersions.KUBERNETES_V1;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.beans.command.ContainerResizeCommandUnit.DASH_STRING;
import static software.wings.beans.container.KubernetesContainerTaskUtils.CONFIG_MAP_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.KubernetesContainerTaskUtils.SECRET_MAP_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.KubernetesServiceType.None;
import static software.wings.beans.container.Label.Builder.aLabel;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.atteo.evo.inflector.English.plural;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifacts.docker.service.DockerRegistryUtils;
import io.harness.concurrent.HTimeLimiter;
import io.harness.container.ContainerInfo;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesConvention;
import io.harness.k8s.model.ContainerApiVersions;
import io.harness.k8s.model.ImageDetails;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.AzureConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.LogColor;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.beans.container.ContainerDefinition;
import software.wings.beans.container.KubernetesContainerTaskUtils;
import software.wings.beans.container.KubernetesServiceSpecification;
import software.wings.beans.container.KubernetesServiceType;
import software.wings.beans.container.Label;
import software.wings.beans.dto.KubernetesContainerTask;
import software.wings.beans.dto.SettingAttribute;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleFluent;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceFluent.SpecNested;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpecFluent.HttpNested;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.LoadBalancerStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerSpecBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by brett on 3/3/17
 */
@Slf4j
@JsonTypeName("KUBERNETES_SETUP")
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class KubernetesSetupCommandUnit extends ContainerSetupCommandUnit {
  public static final String HARNESS_KUBERNETES_MANAGED_LABEL_KEY = "harness.io/managed";
  public static final String HARNESS_KUBERNETES_APP_LABEL_KEY = "harness.io/application";
  public static final String HARNESS_KUBERNETES_SERVICE_LABEL_KEY = "harness.io/service";
  public static final String HARNESS_KUBERNETES_ENV_LABEL_KEY = "harness.io/environment";
  public static final String HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY = "harness.io/service-infra-id";
  @Transient private static final Pattern envVarPattern = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
  @Transient private static final String LOAD_BALANCER = "LoadBalancer";
  @Transient private static final String NODE_PORT = "NodePort";
  @Transient private static final String POLICY_LOCAL = "Local";

  @Transient private static final String SERVICE_NAME_PLACEHOLDER_REGEX = "\\$\\{SERVICE_NAME}";
  @Transient private static final String SERVICE_PORT_PLACEHOLDER_REGEX = "\\$\\{SERVICE_PORT}";

  @Transient private static final String PRIMARY_SERVICE_NAME_PLACEHOLDER_REGEX = "\\$\\{PRIMARY_SERVICE_NAME}";
  @Transient private static final String PRIMARY_SERVICE_PORT_PLACEHOLDER_REGEX = "\\$\\{PRIMARY_SERVICE_PORT}";

  @Transient private static final String STAGE_SERVICE_NAME_PLACEHOLDER_REGEX = "\\$\\{STAGE_SERVICE_NAME}";
  @Transient private static final String STAGE_SERVICE_PORT_PLACEHOLDER_REGEX = "\\$\\{STAGE_SERVICE_PORT}";
  @Transient private static final int MAX_ENV_VAR_LENGTH = 4000;
  @Transient private static final String CONTROLLER_YAML = "CONTROLLER_YAML";
  @Transient private static final String CONFIG_MAP_YAML = "CONFIG_MAP_YAML";
  @Transient private static final String SECRET_MAP_YAML = "SECRET_MAP_YAML";
  @Transient private static final String AUTOSCALER_YAML = "AUTOSCALER_YAML";

  @Inject private transient GkeClusterService gkeClusterService;
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private transient TimeLimiter timeLimiter;
  @Inject private transient Clock clock;
  @Inject private transient AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EncryptionService encryptionService;

  private Map<String, String> harnessAnnotations;
  private Map<String, String> lookupLabels;
  private int currentRevision;
  private boolean isNotVersioned;
  private String currentNamePrefix;

  public KubernetesSetupCommandUnit() {
    super(CommandUnitType.KUBERNETES_SETUP);
    setDeploymentType(DeploymentType.KUBERNETES.name());
  }

  @Override
  protected CommandExecutionStatus executeInternal(CommandExecutionContext context,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> edd, ContainerSetupParams containerSetupParams,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      ExecutionLogCallback executionLogCallback) {
    ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder =
        ContainerSetupCommandUnitExecutionData.builder();

    StringBuilder summaryOutput = new StringBuilder();

    try {
      KubernetesSetupParams setupParams = (KubernetesSetupParams) containerSetupParams;
      commandExecutionDataBuilder.namespace(setupParams.getNamespace());

      harnessAnnotations = getHarnessAnnotations(setupParams);
      lookupLabels = ImmutableMap.of(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY, setupParams.getReleaseName());

      KubernetesConfig kubernetesConfig;
      if (cloudProviderSetting.getValue() instanceof KubernetesClusterConfig) {
        KubernetesClusterConfig config = (KubernetesClusterConfig) cloudProviderSetting.getValue();
        encryptionService.decrypt(config, edd, false);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(config, edd);

        kubernetesConfig = config.createKubernetesConfig(setupParams.getNamespace());
      } else if (cloudProviderSetting.getValue() instanceof AzureConfig) {
        AzureConfig azureConfig = (AzureConfig) cloudProviderSetting.getValue();
        kubernetesConfig =
            azureDelegateHelperService.getKubernetesClusterConfig(azureConfig, edd, setupParams.getSubscriptionId(),
                setupParams.getResourceGroup(), setupParams.getClusterName(), setupParams.getNamespace(), false);
      } else {
        kubernetesConfig = gkeClusterService.getCluster(
            cloudProviderSetting, edd, setupParams.getClusterName(), setupParams.getNamespace(), false);
      }

      kubernetesContainerService.createNamespaceIfNotExist(kubernetesConfig);

      String internalConfigName = getInternalHarnessConfigName(setupParams.getInfraMappingId());

      if (!setupParams.isRollback()) {
        kubernetesContainerService.deleteSecretFabric8(kubernetesConfig, internalConfigName);
      }

      KubernetesContainerTask kubernetesContainerTask = (KubernetesContainerTask) setupParams.getContainerTask();
      if (kubernetesContainerTask == null) {
        ContainerDefinition containerDefinition = ContainerDefinition.builder().memory(256).cpu(1d).build();
        kubernetesContainerTask =
            KubernetesContainerTask.builder().containerDefinitions(Lists.newArrayList(containerDefinition)).build();
      }

      boolean isStatefulSet =
          KubernetesContainerTaskUtils.checkStatefulSet(kubernetesContainerTask.getAdvancedConfig());
      boolean isDaemonSet = KubernetesContainerTaskUtils.checkDaemonSet(kubernetesContainerTask.getAdvancedConfig());
      isNotVersioned = isDaemonSet || isStatefulSet;
      currentNamePrefix = setupParams.getControllerNamePrefix();

      String lastCtrlName;
      if (setupParams.isUseNewLabelMechanism()) {
        HasMetadata lastDeployment = getLastDeployment(kubernetesConfig);
        currentRevision = isNotVersioned ? 0 : getRevisionNumber(lastDeployment) + 1;
        lastCtrlName = lastDeployment != null ? lastDeployment.getMetadata().getName() : null;
      } else {
        lastCtrlName = lastController(kubernetesConfig, setupParams.getControllerNamePrefix());
        currentRevision = getRevisionFromControllerName(lastCtrlName).orElse(-1) + 1;
      }

      String containerServiceName = isNotVersioned
          ? isStatefulSet ? KubernetesConvention.getKubernetesServiceName(setupParams.getControllerNamePrefix())
                          : setupParams.getControllerNamePrefix()
          : KubernetesConvention.getControllerName(setupParams.getControllerNamePrefix(), currentRevision);

      Map<String, String> controllerLabels = isNotVersioned
          ? getLabels(setupParams)
          : getLabelsWithRevision(setupParams, Integer.toString(currentRevision));

      String dockerImageName = setupParams.getImageDetails().getName() + ":" + setupParams.getImageDetails().getTag();

      summaryOutput.append(format("%nCluster: %s", setupParams.getClusterName()));
      summaryOutput.append(format("%nNamespace: %s", setupParams.getNamespace()));
      summaryOutput.append(format("%nController: %s", containerServiceName));
      summaryOutput.append(format("%nDocker Image: %s", dockerImageName));

      if (setupParams.isRollback()) {
        executionLogCallback.saveExecutionLog("Rolling back setup");
        Secret previousConfig = kubernetesContainerService.getSecretFabric8(kubernetesConfig, internalConfigName);
        if (isNotVersioned) {
          performYamlRollback(executionLogCallback, previousConfig, kubernetesConfig, containerServiceName,
              setupParams.getServiceSteadyStateTimeout());
        } else {
          performResize(kubernetesConfig, setupParams.getClusterName(), lastCtrlName, setupParams.getServiceCounts(),
              setupParams.getServiceSteadyStateTimeout(), executionLogCallback);

          if (previousConfig != null && isNotBlank(previousConfig.getData().get(AUTOSCALER_YAML))) {
            rollbackAutoscaler(executionLogCallback, kubernetesConfig,
                decodeBase64ToString(previousConfig.getData().get(AUTOSCALER_YAML)));
          }
        }
        executionLogCallback.saveExecutionLog("Rollback complete");
        executionLogCallback.saveExecutionLog(DASH_STRING + "\n");

        context.setCommandExecutionData(
            commandExecutionDataBuilder.containerServiceName(setupParams.getControllerNamePrefix()).build());

        return CommandExecutionStatus.SUCCESS;
      }

      if (setupParams.isBlueGreen()) {
        executionLogCallback.saveExecutionLog("Blue/Green Service Setup\n");

        validateBlueGreenConfig(setupParams);

        cleanupStageDeployment(kubernetesConfig, setupParams, executionLogCallback);
      }

      String registrySecretName = "none";
      if (isNotBlank(setupParams.getImageDetails().getRegistryUrl())
          && isNotBlank(setupParams.getImageDetails().getUsername())
          && isNotBlank(setupParams.getImageDetails().getPassword())) {
        registrySecretName = getKubernetesRegistrySecretName(setupParams.getImageDetails());
        Secret registrySecret = createRegistrySecret(registrySecretName, setupParams.getNamespace(),
            setupParams.getImageDetails(), controllerLabels, executionLogCallback);
        kubernetesContainerService.createOrReplaceSecretFabric8(kubernetesConfig, registrySecret);
      }

      Map<String, String> previousYamlConfig = new HashMap<>();
      List<Pod> originalPods = null;
      Map<String, Integer> activeServiceCounts = new HashMap<>();
      Map<String, Integer> trafficWeights = new HashMap<>();
      String previousAutoscalerYaml = null;
      String lastAutoscaler = null;

      if (isNotVersioned) {
        previousYamlConfig.put(CONTROLLER_YAML, getControllerYaml(kubernetesConfig, containerServiceName));
        originalPods = kubernetesContainerService.getRunningPods(kubernetesConfig, containerServiceName);
        previousYamlConfig.put(CONFIG_MAP_YAML, getConfigMapYaml(kubernetesConfig, containerServiceName));
        previousYamlConfig.put(SECRET_MAP_YAML, getSecretMapYaml(kubernetesConfig, containerServiceName));
        previousAutoscalerYaml = getAutoscalerYaml(kubernetesConfig, containerServiceName);
        if (isNotBlank(previousAutoscalerYaml)) {
          lastAutoscaler = containerServiceName;
        }
      } else {
        if (setupParams.isUseNewLabelMechanism()) {
          activeServiceCounts =
              kubernetesContainerService.getActiveServiceCountsWithLabels(kubernetesConfig, lookupLabels);
        } else {
          activeServiceCounts =
              kubernetesContainerService.getActiveServiceCounts(kubernetesConfig, containerServiceName);
        }

        if (setupParams.isUseIstioRouteRule()) {
          trafficWeights = kubernetesContainerService.getTrafficWeights(kubernetesConfig, containerServiceName);
        }

        List<String> activeControllers = new ArrayList<>(activeServiceCounts.keySet());
        // Find most recent active controller to get the last autoscaler
        reverse(activeControllers);
        for (String runningControllerName : activeControllers) {
          if (activeServiceCounts.get(runningControllerName) > 0
              && isNotBlank(getAutoscalerYaml(kubernetesConfig, runningControllerName))) {
            previousAutoscalerYaml = getAutoscalerYaml(kubernetesConfig, runningControllerName);
            lastAutoscaler = runningControllerName;
            break;
          }
        }
      }

      if (isNotBlank(previousAutoscalerYaml)) {
        previousYamlConfig.put(AUTOSCALER_YAML, previousAutoscalerYaml);
      }

      if (isNotEmpty(previousYamlConfig)) {
        Secret yamlConfig = new SecretBuilder()
                                .withNewMetadata()
                                .withAnnotations(harnessAnnotations)
                                .withName(internalConfigName)
                                .withNamespace(setupParams.getNamespace())
                                .withLabels(controllerLabels)
                                .endMetadata()
                                .withStringData(previousYamlConfig)
                                .build();
        kubernetesContainerService.createOrReplaceSecretFabric8(kubernetesConfig, yamlConfig);
      }

      List<Label> labelArray = new ArrayList<>();
      for (Map.Entry<String, String> mapEntry : lookupLabels.entrySet()) {
        labelArray.add(aLabel().withName(mapEntry.getKey()).withValue(mapEntry.getValue()).build());
      }

      commandExecutionDataBuilder.containerServiceName(containerServiceName)
          .activeServiceCounts(integerMapToListOfStringArray(activeServiceCounts))
          .trafficWeights(integerMapToListOfStringArray(trafficWeights))
          .lookupLabels(labelArray);

      // Setup config map
      ConfigMap configMap = prepareConfigMap(kubernetesConfig, setupParams, containerServiceName, controllerLabels,
          safeDisplayServiceVariables, executionLogCallback);

      // Setup secret map
      Secret secretMap = prepareSecretMap(kubernetesConfig, setupParams, containerServiceName, controllerLabels,
          safeDisplayServiceVariables, serviceVariables, executionLogCallback);

      long replaceControllerStartTime = clock.millis();

      HasMetadata definition = createKubernetesControllerDefinition(kubernetesContainerTask, containerServiceName,
          controllerLabels, setupParams, registrySecretName, configMap, secretMap, originalPods, executionLogCallback);

      if (definition instanceof StatefulSet) {
        HasMetadata existing =
            kubernetesContainerService.getController(kubernetesConfig, definition.getMetadata().getName());
        if (existing != null && existing.getKind().equals("StatefulSet")) {
          StatefulSet newDefinition = (StatefulSet) definition;
          StatefulSet mergedDefinition = (StatefulSet) existing;
          mergedDefinition.getSpec().setReplicas(newDefinition.getSpec().getReplicas());
          mergedDefinition.getSpec().setTemplate(newDefinition.getSpec().getTemplate());
          mergedDefinition.getSpec().getTemplate().getMetadata().setLabels(
              mergedDefinition.getSpec().getSelector().getMatchLabels());
          mergedDefinition.getSpec().setUpdateStrategy(newDefinition.getSpec().getUpdateStrategy());
          definition = mergedDefinition;
        }
      }

      executionLogCallback.saveExecutionLog("Creating controller:\n\n" + toDisplayYaml(definition));
      // Setup controller
      HasMetadata controller = kubernetesContainerService.createOrReplaceController(kubernetesConfig, definition);

      // Delete old autoscaler
      if (isNotBlank(lastAutoscaler)) {
        executionLogCallback.saveExecutionLog("Removing previous autoscaler: " + lastAutoscaler);
        kubernetesContainerService.deleteAutoscaler(kubernetesConfig, lastAutoscaler);
      }

      // Create new autoscaler
      HasMetadata hpa = prepareHorizontalPodAutoscaler(setupParams, containerServiceName, controller.getKind(),
          controller.getApiVersion(), controllerLabels, executionLogCallback);

      commandExecutionDataBuilder.autoscalerYaml(toYaml(hpa));

      if (configMap != null) {
        summaryOutput.append(format("%nConfig Map: %s", configMap.getMetadata().getName()));
      }
      if (secretMap != null) {
        summaryOutput.append(format("%nSecret Map: %s", secretMap.getMetadata().getName()));
      }
      if (hpa != null) {
        summaryOutput.append(format("%nHorizontal Pod Autoscaler: %s", hpa.getMetadata().getName()));
      }

      Service service = setupServiceAndIngress(setupParams, kubernetesConfig, containerServiceName,
          executionLogCallback, summaryOutput, commandExecutionDataBuilder);

      setupServiceAndIngressForBlueGreen(setupParams, kubernetesConfig, containerServiceName,
          Integer.toString(currentRevision), executionLogCallback, summaryOutput, commandExecutionDataBuilder);

      if (isNotVersioned) {
        listContainerInfosWhenReady(setupParams.getServiceSteadyStateTimeout(), executionLogCallback, kubernetesConfig,
            containerServiceName, originalPods, replaceControllerStartTime, false);
        if (isStatefulSet) {
          if (hpa != null) {
            executionLogCallback.saveExecutionLog("Enabling autoscaler " + containerServiceName, LogLevel.INFO);
            hpa = kubernetesContainerService.createOrReplaceAutoscaler(kubernetesConfig, toYaml(hpa));
            if (hpa != null) {
              String hpaName = hpa.getMetadata().getName();
              executionLogCallback.saveExecutionLog(
                  "Created horizontal pod autoscaler " + hpaName + ":\n\n" + toDisplayYaml(hpa));
            }
          }
        }
      } else {
        try {
          // This should not halt workflow execution.
          downsizeOldOrUnhealthy(kubernetesConfig, containerServiceName, setupParams, executionLogCallback);
        } catch (Exception e) {
          log.warn("Cleaning up of old or unhealthy instances failed while setting up Kubernetes service: ",
              ExceptionMessageSanitizer.sanitizeException(e));
        }
        if (setupParams.isUseNewLabelMechanism()) {
          cleanupWithLabels(kubernetesConfig, executionLogCallback);

        } else {
          cleanup(kubernetesConfig, containerServiceName, executionLogCallback);
        }
      }

      Map<String, Integer> newActiveCounts = new HashMap<>();

      if (!isNotVersioned) {
        if (setupParams.isUseNewLabelMechanism()) {
          newActiveCounts = kubernetesContainerService.getActiveServiceCountsWithLabels(kubernetesConfig, lookupLabels);
        } else {
          newActiveCounts = kubernetesContainerService.getActiveServiceCounts(kubernetesConfig, containerServiceName);
        }
      }

      // Setup Istio routes
      prepareVirtualService(kubernetesConfig, setupParams,
          getKubernetesServiceName(setupParams.getControllerNamePrefix()), service, newActiveCounts,
          containerServiceName, executionLogCallback, summaryOutput);

      executionLogCallback.saveExecutionLog("\n\nSummary:");
      executionLogCallback.saveExecutionLog(summaryOutput.toString());

      return CommandExecutionStatus.SUCCESS;
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.error(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
      Misc.logAllMessages(sanitizedException, executionLogCallback);
      return CommandExecutionStatus.FAILURE;
    } finally {
      context.setCommandExecutionData(commandExecutionDataBuilder.build());
    }
  }

  @VisibleForTesting
  void validateBlueGreenConfig(KubernetesSetupParams setupParams) {
    if (setupParams.getServiceType() != null && setupParams.getServiceType() != None) {
      throw new InvalidRequestException("Service cannot be specified for Blue/Green deployment setup");
    }

    if (setupParams.getBlueGreenConfig() == null) {
      throw new InvalidRequestException("BlueGreenConfig is not specified");
    }

    if (setupParams.getBlueGreenConfig().getPrimaryService() == null
        || setupParams.getBlueGreenConfig().getPrimaryService().getServiceType() == None) {
      throw new InvalidRequestException("PrimaryService is not specified in BlueGreenConfig");
    }

    if (setupParams.getBlueGreenConfig().getStageService() == null
        || setupParams.getBlueGreenConfig().getStageService().getServiceType() == None) {
      throw new InvalidRequestException("StageService is not specified in BlueGreenConfig");
    }
  }

  private Service setupServiceAndIngress(KubernetesSetupParams setupParams, KubernetesConfig kubernetesConfig,
      String containerServiceName, ExecutionLogCallback executionLogCallback, StringBuilder summaryOutput,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder) {
    String kubernetesServiceName = getKubernetesServiceName(setupParams.getControllerNamePrefix());
    Map<String, String> labels = getLabels(setupParams);

    KubernetesServiceSpecification spec = new KubernetesServiceSpecification();
    spec.setServiceType(setupParams.getServiceType());
    spec.setClusterIP(setupParams.getClusterIP());
    spec.setExternalIPs(setupParams.getExternalIPs());
    spec.setExternalName(setupParams.getExternalName());
    spec.setLoadBalancerIP(setupParams.getLoadBalancerIP());
    spec.setNodePort(setupParams.getNodePort());
    spec.setPort(setupParams.getPort());
    spec.setProtocol(setupParams.getProtocol());
    spec.setTargetPort(setupParams.getTargetPort());
    spec.setServiceYaml(setupParams.getServiceYaml());

    if (!setupParams.isBlueGreen() && setupParams.getServiceType() != null && setupParams.getServiceType() != None) {
      executionLogCallback.saveExecutionLog(
          format("Setting Service with name %s with Type %s", kubernetesServiceName, setupParams.getServiceType()),
          LogLevel.INFO);
    }

    Map<String, String> selectors = getNewLabelSelectors(setupParams);

    Service service = prepareService(spec, kubernetesConfig, kubernetesServiceName, setupParams.getNamespace(), false,
        selectors, labels, setupParams.getServiceSteadyStateTimeout(), executionLogCallback, summaryOutput,
        commandExecutionDataBuilder);

    String ingressYaml = "";

    if (service != null && setupParams.isUseIngress()) {
      int port = (service.getSpec() != null && isNotEmpty(service.getSpec().getPorts()))
          ? service.getSpec().getPorts().get(0).getPort()
          : 80;
      ingressYaml = setupParams.getIngressYaml()
                        .replaceAll(SERVICE_NAME_PLACEHOLDER_REGEX, service.getMetadata().getName())
                        .replaceAll(SERVICE_PORT_PLACEHOLDER_REGEX, Integer.toString(port))
                        .replaceAll(CONFIG_MAP_NAME_PLACEHOLDER_REGEX, containerServiceName)
                        .replaceAll(SECRET_MAP_NAME_PLACEHOLDER_REGEX, containerServiceName);
    }

    prepareIngress(kubernetesConfig, setupParams.isUseIngress(), ingressYaml, kubernetesServiceName, labels,
        executionLogCallback, summaryOutput);

    return service;
  }

  private void setupServiceAndIngressForBlueGreen(KubernetesSetupParams setupParams, KubernetesConfig kubernetesConfig,
      String containerServiceName, String revision, ExecutionLogCallback executionLogCallback,
      StringBuilder summaryOutput, ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder) {
    String primaryServiceName = getPrimaryServiceName(getKubernetesServiceName(setupParams.getControllerNamePrefix()));
    String stageServiceName = getStageServiceName(getKubernetesServiceName(setupParams.getControllerNamePrefix()));
    String ingressName = getBlueGreenIngressName(setupParams.getControllerNamePrefix());
    Map<String, String> labels = getLabels(setupParams);
    Map<String, String> selectors = getNewLabelSelectorsWithRevision(setupParams, revision);

    KubernetesServiceSpecification primaryServiceSpecification =
        (setupParams.isBlueGreen() && setupParams.getBlueGreenConfig() != null)
        ? setupParams.getBlueGreenConfig().getPrimaryService()
        : null;

    if (setupParams.isBlueGreen()) {
      executionLogCallback.saveExecutionLog(
          format("Setting Primary Service with name %s", primaryServiceName), LogLevel.INFO);
    }

    Service primaryService = prepareService(primaryServiceSpecification, kubernetesConfig, primaryServiceName,
        setupParams.getNamespace(), true, selectors, labels, setupParams.getServiceSteadyStateTimeout(),
        executionLogCallback, summaryOutput, commandExecutionDataBuilder);

    KubernetesServiceSpecification stageServiceSpecification =
        (setupParams.isBlueGreen() && setupParams.getBlueGreenConfig() != null)
        ? setupParams.getBlueGreenConfig().getStageService()
        : null;

    if (setupParams.isBlueGreen()) {
      executionLogCallback.saveExecutionLog(
          format("Setting Stage Service with name %s", stageServiceName), LogLevel.INFO);
    }

    Service stageService = prepareService(stageServiceSpecification, kubernetesConfig, stageServiceName,
        setupParams.getNamespace(), false, selectors, labels, setupParams.getServiceSteadyStateTimeout(),
        executionLogCallback, summaryOutput, commandExecutionDataBuilder);

    boolean useIngress = setupParams.isBlueGreen() && setupParams.getBlueGreenConfig() != null
        && setupParams.getBlueGreenConfig().isUseIngress();

    String ingressYaml = "";

    if (primaryService != null && stageService != null && useIngress) {
      int primaryServicePort =
          isNotEmpty(primaryService.getSpec().getPorts()) ? primaryService.getSpec().getPorts().get(0).getPort() : 80;

      int stageServicePort =
          isNotEmpty(stageService.getSpec().getPorts()) ? stageService.getSpec().getPorts().get(0).getPort() : 80;

      ingressYaml = setupParams.getBlueGreenConfig()
                        .getIngressYaml()
                        .replaceAll(PRIMARY_SERVICE_NAME_PLACEHOLDER_REGEX, primaryService.getMetadata().getName())
                        .replaceAll(PRIMARY_SERVICE_PORT_PLACEHOLDER_REGEX, Integer.toString(primaryServicePort))
                        .replaceAll(STAGE_SERVICE_NAME_PLACEHOLDER_REGEX, stageService.getMetadata().getName())
                        .replaceAll(STAGE_SERVICE_PORT_PLACEHOLDER_REGEX, Integer.toString(stageServicePort))
                        .replaceAll(CONFIG_MAP_NAME_PLACEHOLDER_REGEX, containerServiceName)
                        .replaceAll(SECRET_MAP_NAME_PLACEHOLDER_REGEX, containerServiceName);
    }

    prepareIngress(kubernetesConfig, useIngress, ingressYaml, ingressName, labels, executionLogCallback, summaryOutput);

    if (primaryService != null && stageService != null && setupParams.isBlueGreen()) {
      String primaryRevision = getRevisionFromService(primaryService);
      String stageRevision = getRevisionFromService(stageService);

      executionLogCallback.saveExecutionLog("Primary Service is at revision: " + primaryRevision);
      executionLogCallback.saveExecutionLog("Stage Service is at revision: " + stageRevision);
    }
  }

  private String getRevisionFromService(Service service) {
    return service.getSpec().getSelector().get(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY);
  }

  private ImmutableMap<String, String> getLabels(KubernetesSetupParams setupParams) {
    return ImmutableMap.<String, String>builder()
        .put(HARNESS_KUBERNETES_MANAGED_LABEL_KEY, "true")
        .put(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY, setupParams.getReleaseName())
        .build();
  }

  private ImmutableMap<String, String> getNewLabelSelectors(KubernetesSetupParams setupParams) {
    return ImmutableMap.<String, String>builder()
        .put(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY, setupParams.getReleaseName())
        .build();
  }

  private ImmutableMap<String, String> getLabelsWithRevision(KubernetesSetupParams setupParams, String revision) {
    return ImmutableMap.<String, String>builder()
        .put(HARNESS_KUBERNETES_MANAGED_LABEL_KEY, "true")
        .put(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY, setupParams.getReleaseName())
        .put(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY, revision)
        .build();
  }

  private ImmutableMap<String, String> getHarnessAnnotations(KubernetesSetupParams setupParams) {
    return ImmutableMap.<String, String>builder()
        .put(HARNESS_KUBERNETES_APP_LABEL_KEY, getLabelValue(setupParams.getAppName()))
        .put(HARNESS_KUBERNETES_SERVICE_LABEL_KEY, getLabelValue(setupParams.getServiceName()))
        .put(HARNESS_KUBERNETES_ENV_LABEL_KEY, getLabelValue(setupParams.getEnvName()))
        .build();
  }

  private ImmutableMap<String, String> getNewLabelSelectorsWithRevision(
      KubernetesSetupParams setupParams, String revision) {
    return ImmutableMap.<String, String>builder()
        .put(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY, setupParams.getReleaseName())
        .put(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY, revision)
        .build();
  }

  private Service prepareService(KubernetesServiceSpecification serviceSpecification, KubernetesConfig kubernetesConfig,
      String kubernetesServiceName, String namespace, boolean keepExistingSelectors, Map<String, String> labelSelectors,
      Map<String, String> labels, int timeout, ExecutionLogCallback executionLogCallback, StringBuilder summaryOutput,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder) {
    Service service = null;
    String serviceClusterIP;
    String serviceLoadBalancerEndpoint = null;
    String nodePort = null;

    try {
      service = kubernetesContainerService.getServiceFabric8(kubernetesConfig, kubernetesServiceName);
    } catch (Exception e) {
      Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
    }

    if (serviceSpecification == null || serviceSpecification.getServiceType() == null
        || serviceSpecification.getServiceType() == None) {
      if (service != null) {
        try {
          if (service.getSpec().getSelector().containsKey(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY)) {
            executionLogCallback.saveExecutionLog("Deleting existing service " + kubernetesServiceName);
            kubernetesContainerService.deleteService(kubernetesConfig, kubernetesServiceName);
          }
        } catch (Exception e) {
          log.error(
              "Couldn't delete service {}", kubernetesServiceName, ExceptionMessageSanitizer.sanitizeException(e));
        }
      }
      return null;
    }

    if (service != null && keepExistingSelectors) {
      labelSelectors = service.getSpec().getSelector();
    }

    Service serviceToCreate = createServiceDefinition(
        kubernetesServiceName, namespace, labelSelectors, labels, serviceSpecification, executionLogCallback);
    if (service != null) {
      if (LOAD_BALANCER.equals(serviceToCreate.getSpec().getType())
          && LOAD_BALANCER.equals(service.getSpec().getType())) {
        LoadBalancerStatus loadBalancer = service.getStatus().getLoadBalancer();
        // Keep the previous load balancer IP if it exists and a new one was not specified
        if (isEmpty(serviceToCreate.getSpec().getLoadBalancerIP()) && loadBalancer != null
            && !loadBalancer.getIngress().isEmpty()) {
          serviceToCreate.getSpec().setLoadBalancerIP(loadBalancer.getIngress().get(0).getIp());
        }
      } else if (NODE_PORT.equals(serviceToCreate.getSpec().getType())
          && NODE_PORT.equals(service.getSpec().getType())) {
        Map<String, Integer> portNodePort = service.getSpec().getPorts().stream().collect(
            toMap(p -> p.getPort() + ":" + p.getTargetPort(), ServicePort::getNodePort));
        // Keep the previous node ports if they exist and were not specified
        for (ServicePort port : serviceToCreate.getSpec().getPorts()) {
          if (port.getNodePort() == null) {
            port.setNodePort(portNodePort.get(port.getPort() + ":" + port.getTargetPort()));
          }
        }
      }

      // When externalTrafficPolicy=Local the health check node port cannot change
      if (POLICY_LOCAL.equals(service.getSpec().getExternalTrafficPolicy())) {
        serviceToCreate.getSpec().setHealthCheckNodePort(service.getSpec().getHealthCheckNodePort());
      }
    }

    service = kubernetesContainerService.getServiceFabric8(kubernetesConfig, serviceToCreate.getMetadata().getName());
    log.info("{} service [{}]", service == null ? "Creating" : "Replacing", serviceToCreate.getMetadata().getName());
    service = kubernetesContainerService.createOrReplaceServiceFabric8(kubernetesConfig, serviceToCreate);
    serviceClusterIP = service.getSpec().getClusterIP();

    if (service.getSpec().getType().equals(LOAD_BALANCER)) {
      serviceLoadBalancerEndpoint = waitForLoadBalancerEndpoint(
          kubernetesConfig, service, serviceSpecification.getLoadBalancerIP(), timeout, executionLogCallback);
    } else if (service.getSpec().getType().equals(NODE_PORT)) {
      nodePort =
          Joiner.on(',').join(service.getSpec().getPorts().stream().map(ServicePort::getNodePort).collect(toList()));
    }

    summaryOutput.append(format("%nService Name: %s, ", kubernetesServiceName));

    if (isNotBlank(serviceClusterIP)) {
      summaryOutput.append(format("Cluster IP: %s ", serviceClusterIP));
    }
    if (isNotBlank(serviceLoadBalancerEndpoint)) {
      String lbUrlString = "";
      int precedence = 0;
      for (ServicePort servicePort : service.getSpec().getPorts()) {
        int portPrecedence = 2;
        String lbScheme = "http";
        String lbPort = "";
        if (servicePort.getPort() == 443) {
          lbScheme += 's';
          portPrecedence = 3;
        }
        if (servicePort.getPort() != 80 && servicePort.getPort() != 443) {
          lbPort = ":" + servicePort.getPort();
          portPrecedence = 1;
        }
        if (portPrecedence > precedence) {
          precedence = portPrecedence;
          lbUrlString = lbScheme + "://" + serviceLoadBalancerEndpoint + lbPort + "/";
        }
      }
      summaryOutput.append(format("Load Balancer: %s ", lbUrlString));
      commandExecutionDataBuilder.loadBalancer(lbUrlString);
    }
    if (isNotBlank(nodePort)) {
      summaryOutput.append(format("Node Port: %s ", nodePort));
    }

    return service;
  }

  private HorizontalPodAutoscaler prepareHorizontalPodAutoscaler(KubernetesSetupParams setupParams, String name,
      String kind, String apiVersion, Map<String, String> controllerLabels, ExecutionLogCallback executionLogCallback) {
    if (setupParams.isUseAutoscaler()) {
      HorizontalPodAutoscaler autoscalerDefinition = createAutoscaler(
          name, kind, apiVersion, setupParams.getNamespace(), controllerLabels, setupParams, executionLogCallback);

      if (autoscalerDefinition != null) {
        executionLogCallback.saveExecutionLog(
            format("Autoscaler %s - disabled until 100%% deployed", name), LogLevel.INFO);
        return autoscalerDefinition;
      }
    }
    return null;
  }

  @SuppressWarnings("squid:S00107")
  private void prepareVirtualService(KubernetesConfig kubernetesConfig, KubernetesSetupParams setupParams,
      String virtualServiceName, Service service, Map<String, Integer> activeControllers, String containerServiceName,
      ExecutionLogCallback executionLogCallback, StringBuilder summaryOutput) {
    if (setupParams.isUseIstioRouteRule() && service != null) {
      // Todo : find out generic way of creating istio resource rather than casting them and calli resource specific api
      List<HasMetadata> istioResourcesDefinition = createVirtualServiceAndDestinationRuleDefinition(
          setupParams, virtualServiceName, service.getMetadata().getLabels(), activeControllers, executionLogCallback);
      for (HasMetadata r : istioResourcesDefinition) {
        if (r instanceof VirtualService) {
          kubernetesContainerService.createOrReplaceFabric8IstioVirtualService(kubernetesConfig, (VirtualService) r);
        } else if (r instanceof DestinationRule) {
          kubernetesContainerService.createOrReplaceFabric8IstioDestinationRule(kubernetesConfig, (DestinationRule) r);
        } else {
          throw new InvalidArgumentsException(
              format("%s should either be VirtualService or DestinationRule", r.getFullResourceName()), USER);
        }
        summaryOutput.append(format("%nIstio %s: %s", r.getKind(), r.getMetadata().getName()));
        if (StringUtils.equals(r.getKind(), "VirtualService")) {
          printVirtualServiceRouteWeights(
              (VirtualService) r, getPrefixFromControllerName(containerServiceName), executionLogCallback);
        }
      }
    } else {
      try {
        VirtualService virtualService =
            kubernetesContainerService.getFabric8IstioVirtualService(kubernetesConfig, virtualServiceName);
        if (virtualService != null
            && virtualService.getMetadata().getLabels().containsKey(HARNESS_KUBERNETES_MANAGED_LABEL_KEY)) {
          executionLogCallback.saveExecutionLog("Deleting Istio VirtualService" + virtualServiceName);
          kubernetesContainerService.deleteIstioVirtualService(kubernetesConfig, virtualServiceName);
        }

        DestinationRule destinationRule =
            kubernetesContainerService.getFabric8IstioDestinationRule(kubernetesConfig, virtualServiceName);
        if (destinationRule != null
            && destinationRule.getMetadata().getLabels().containsKey(HARNESS_KUBERNETES_MANAGED_LABEL_KEY)) {
          executionLogCallback.saveExecutionLog("Deleting Istio DestinationRule" + virtualServiceName);
          kubernetesContainerService.deleteIstioDestinationRule(kubernetesConfig, virtualServiceName);
        }
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.error("Error checking for previous istio route", sanitizedException);
        Misc.logAllMessages(sanitizedException, executionLogCallback);
      }
    }
  }

  private Ingress prepareIngress(KubernetesConfig kubernetesConfig, boolean useIngress, String ingressYaml,
      String ingressName, Map<String, String> labels, ExecutionLogCallback executionLogCallback,
      StringBuilder summaryOutput) {
    Ingress ingress;
    if (useIngress) {
      ingress = kubernetesContainerService.createOrReplaceIngress(kubernetesConfig,
          createIngressDefinition(
              ingressYaml, ingressName, kubernetesConfig.getNamespace(), labels, executionLogCallback));
    } else {
      try {
        ingress = kubernetesContainerService.getIngress(kubernetesConfig, ingressName);
        if (ingress != null
            && ingress.getMetadata().getLabels().containsKey(HARNESS_KUBERNETES_INFRA_MAPPING_ID_LABEL_KEY)) {
          kubernetesContainerService.deleteIngress(kubernetesConfig, ingressName);
        }
      } catch (Exception e) {
        Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
      }
      ingress = null;
    }

    if (ingress != null) {
      summaryOutput.append(format("%nIngress Name: %s", ingress.getMetadata().getName()));
      printIngressRules(ingress, executionLogCallback, summaryOutput);
    }

    return ingress;
  }

  private Secret prepareSecretMap(KubernetesConfig kubernetesConfig, KubernetesSetupParams setupParams,
      String secretMapName, Map<String, String> controllerLabels, Map<String, String> safeDisplayServiceVariables,
      Map<String, String> serviceVariables, ExecutionLogCallback executionLogCallback) {
    Secret secretMap = new SecretBuilder()
                           .withNewMetadata()
                           .withAnnotations(harnessAnnotations)
                           .withName(secretMapName)
                           .withNamespace(setupParams.getNamespace())
                           .withLabels(controllerLabels)
                           .endMetadata()
                           .build();

    Map<String, String> secretData = new HashMap<>();

    Map<String, String> encryptedServiceVars =
        safeDisplayServiceVariables.entrySet()
            .stream()
            .filter(entry -> SECRET_MASK.equals(entry.getValue()))
            .collect(toMap(Entry::getKey, entry -> encodeBase64(serviceVariables.get(entry.getKey()))));

    if (isNotEmpty(encryptedServiceVars)) {
      secretData.putAll(encryptedServiceVars);
    }

    if (isNotEmpty(setupParams.getEncryptedConfigFiles())) {
      secretData.putAll(setupParams.getEncryptedConfigFiles().stream().collect(toMap(sa -> sa[0], sa -> sa[1])));
    }

    if (isEmpty(secretData)) {
      secretMap = null;
    }

    if (secretMap != null) {
      executionLogCallback.saveExecutionLog("Creating secret map:\n\n"
              + toDisplayYaml(
                  new SecretBuilder()
                      .withMetadata(secretMap.getMetadata())
                      .withData(secretData.entrySet().stream().collect(toMap(Entry::getKey, entry -> SECRET_MASK)))
                      .build()),
          LogLevel.INFO);
      secretMap.setData(secretData);
      secretMap = kubernetesContainerService.createOrReplaceSecretFabric8(kubernetesConfig, secretMap);
    }
    return secretMap;
  }

  private ConfigMap prepareConfigMap(KubernetesConfig kubernetesConfig, KubernetesSetupParams setupParams,
      String configMapName, Map<String, String> controllerLabels, Map<String, String> safeDisplayServiceVariables,
      ExecutionLogCallback executionLogCallback) {
    ConfigMap configMap;
    if (isNotBlank(setupParams.getConfigMapYaml())) {
      try {
        configMap = KubernetesHelper.loadYaml(setupParams.getConfigMapYaml());
        if (configMap == null) {
          throw new InvalidArgumentsException(
              "Couldn't parse Config Map YAML: " + setupParams.getConfigMapYaml(), USER);
        }
        ObjectMeta configMapMeta = Optional.ofNullable(configMap.getMetadata()).orElse(new ObjectMeta());
        configMapMeta.setName(configMapName);
        configMapMeta.setNamespace(setupParams.getNamespace());
        configMapMeta.setAnnotations(mergeMaps(configMapMeta.getAnnotations(), harnessAnnotations));
        configMapMeta.setLabels(mergeMaps(configMapMeta.getLabels(), controllerLabels));
        configMap.setMetadata(configMapMeta);
        if (configMap.getData() == null) {
          configMap.setData(new HashMap<>());
        }
      } catch (Exception e) {
        throw new WingsException("Error while loading configMap yaml", ExceptionMessageSanitizer.sanitizeException(e));
      }
    } else {
      configMap = new ConfigMapBuilder()
                      .withNewMetadata()
                      .withAnnotations(harnessAnnotations)
                      .withName(configMapName)
                      .withNamespace(setupParams.getNamespace())
                      .withLabels(controllerLabels)
                      .endMetadata()
                      .withData(new HashMap<>())
                      .build();
    }

    if (isNotEmpty(safeDisplayServiceVariables)) {
      configMap.getData().putAll(safeDisplayServiceVariables.entrySet()
                                     .stream()
                                     .filter(entry -> !SECRET_MASK.equals(entry.getValue()))
                                     .collect(toMap(Entry::getKey, Entry::getValue)));
    }

    if (isNotEmpty(setupParams.getPlainConfigFiles())) {
      configMap.getData().putAll(
          setupParams.getPlainConfigFiles().stream().collect(toMap(sa -> sa[0], sa -> decodeBase64ToString(sa[1]))));
    }

    if (isEmpty(configMap.getData())) {
      configMap = null;
    }
    if (configMap != null) {
      executionLogCallback.saveExecutionLog("Creating config map:\n\n" + toDisplayYaml(configMap));
      configMap = kubernetesContainerService.createOrReplaceConfigMapFabric8(kubernetesConfig, configMap);
    }
    return configMap;
  }

  private String getControllerYaml(KubernetesConfig kubernetesConfig, String containerServiceName) {
    HasMetadata controller = kubernetesContainerService.getController(kubernetesConfig, containerServiceName);
    if (controller != null) {
      try {
        return toYaml(controller);
      } catch (IOException e) {
        log.error("Error converting controller to yaml: {}", containerServiceName);
      }
    }
    return null;
  }

  private String getConfigMapYaml(KubernetesConfig kubernetesConfig, String containerServiceName) {
    ConfigMap configMap = kubernetesContainerService.getConfigMapFabric8(kubernetesConfig, containerServiceName);
    if (configMap != null) {
      try {
        return toYaml(configMap);
      } catch (IOException e) {
        log.error("Error converting configMap to yaml: {}", containerServiceName);
      }
    }
    return null;
  }

  private String getSecretMapYaml(KubernetesConfig kubernetesConfig, String containerServiceName) {
    Secret secretMap = kubernetesContainerService.getSecretFabric8(kubernetesConfig, containerServiceName);
    if (secretMap != null) {
      try {
        return toYaml(secretMap);
      } catch (IOException e) {
        log.error("Error converting secretMap to yaml: {}", containerServiceName);
      }
    }
    return null;
  }

  private String getAutoscalerYaml(KubernetesConfig kubernetesConfig, String containerServiceName) {
    HasMetadata hpa = getAutoscaler(kubernetesConfig, containerServiceName);
    if (hpa != null) {
      try {
        return toYaml(hpa);
      } catch (IOException e) {
        log.error("Error converting horizontal pod autoscaler to yaml: {}", containerServiceName);
      }
    }
    return null;
  }

  private Ingress createIngressDefinition(String ingressYaml, String ingressName, String namespace,
      Map<String, String> labels, ExecutionLogCallback executionLogCallback) {
    try {
      Ingress ingress = KubernetesHelper.loadYaml(ingressYaml);
      if (ingress == null) {
        throw new InvalidArgumentsException("Couldn't parse Ingress YAML: " + ingressYaml, USER);
      }
      ingress.getMetadata().setName(ingressName);
      ingress.getMetadata().setNamespace(namespace);
      ingress.getMetadata().setAnnotations(mergeMaps(ingress.getMetadata().getAnnotations(), harnessAnnotations));
      ingress.getMetadata().setLabels(mergeMaps(ingress.getMetadata().getLabels(), labels));
      executionLogCallback.saveExecutionLog("Setting ingress:\n\n" + toDisplayYaml(ingress));
      return ingress;
    } catch (IOException | RuntimeException e) {
      executionLogCallback.saveExecutionLog("Error reading Ingress from yaml: " + ingressName, LogLevel.ERROR);
    }
    return null;
  }

  @VisibleForTesting
  HorizontalPodAutoscaler createAutoscaler(String name, String kind, String apiVersion, String namespace,
      Map<String, String> serviceLabels, KubernetesSetupParams setupParams, ExecutionLogCallback executionLogCallback) {
    HorizontalPodAutoscaler horizontalPodAutoscaler;

    if (isNotBlank(setupParams.getCustomMetricYamlConfig())) {
      horizontalPodAutoscaler =
          getCustomMetricHorizontalPodAutoscaler(name, kind, apiVersion, namespace, serviceLabels, setupParams);
    } else {
      executionLogCallback.saveExecutionLog(
          format("Setting autoscaler min instances %d, max instances %d, with target CPU utilization %d%%",
              setupParams.getMinAutoscaleInstances(), setupParams.getMaxAutoscaleInstances(),
              setupParams.getTargetCpuUtilizationPercentage()),
          LogLevel.INFO);

      horizontalPodAutoscaler =
          getBasicHorizontalPodAutoscaler(name, kind, apiVersion, namespace, serviceLabels, setupParams);
    }

    executionLogCallback.saveExecutionLog(
        "Creating horizontal pod autoscaler:\n\n" + toDisplayYaml(horizontalPodAutoscaler));
    return horizontalPodAutoscaler;
  }

  private HorizontalPodAutoscaler getCustomMetricHorizontalPodAutoscaler(String name, String kind, String apiVersion,
      String namespace, Map<String, String> serviceLabels, KubernetesSetupParams setupParams) {
    try {
      HorizontalPodAutoscaler horizontalPodAutoscaler =
          KubernetesHelper.loadYaml(setupParams.getCustomMetricYamlConfig(), HorizontalPodAutoscaler.class);

      if (horizontalPodAutoscaler == null) {
        throw new InvalidArgumentsException(
            Pair.of("Horizontal Pod Autoscaler YAML", setupParams.getCustomMetricYamlConfig()));
      }
      horizontalPodAutoscaler.getSpec().getScaleTargetRef().setName(name);
      horizontalPodAutoscaler.getSpec().getScaleTargetRef().setKind(kind);
      horizontalPodAutoscaler.getSpec().getScaleTargetRef().setApiVersion(apiVersion);

      // create metadata
      ObjectMeta objectMeta = horizontalPodAutoscaler.getMetadata();
      if (objectMeta == null) {
        objectMeta = new ObjectMeta();
        horizontalPodAutoscaler.setMetadata(objectMeta);
      }

      // set labels, name and namespace
      objectMeta.setAnnotations(mergeMaps(objectMeta.getAnnotations(), harnessAnnotations));
      objectMeta.setLabels(mergeMaps(objectMeta.getLabels(), serviceLabels));
      objectMeta.setName(name);
      objectMeta.setNamespace(namespace);

      return horizontalPodAutoscaler;
    } catch (Exception e) {
      throw new WingsException("Error while loading custom yaml for horizontal pod autoscaler",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private HorizontalPodAutoscaler getBasicHorizontalPodAutoscaler(String name, String kind, String apiVersion,
      String namespace, Map<String, String> serviceLabels, KubernetesSetupParams setupParams) {
    HorizontalPodAutoscalerSpecBuilder spec =
        new HorizontalPodAutoscalerSpecBuilder()
            .withMinReplicas(setupParams.getMinAutoscaleInstances())
            .withMaxReplicas(setupParams.getMaxAutoscaleInstances())
            .withTargetCPUUtilizationPercentage(setupParams.getTargetCpuUtilizationPercentage())
            .withNewScaleTargetRef()
            .withKind(kind)
            .withName(name)
            .withApiVersion(apiVersion)
            .endScaleTargetRef();

    return new HorizontalPodAutoscalerBuilder()
        .withNewMetadata()
        .withAnnotations(harnessAnnotations)
        .withName(name)
        .withNamespace(namespace)
        .withLabels(serviceLabels)
        .endMetadata()
        .withSpec(spec.build())
        .build();
  }

  private List<HasMetadata> createVirtualServiceAndDestinationRuleDefinition(KubernetesSetupParams setupParams,
      String kubernetesServiceName, Map<String, String> labels, Map<String, Integer> activeControllers,
      ExecutionLogCallback executionLogCallback) {
    SpecNested<VirtualServiceBuilder> virtualServiceSpecNested =
        new VirtualServiceBuilder()
            .withApiVersion("networking.istio.io/v1alpha3")
            .withNewMetadata()
            .withAnnotations(harnessAnnotations)
            .withName(kubernetesServiceName)
            .withNamespace(setupParams.getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withHosts(setupParams.getIstioConfig() != null && !isEmpty(setupParams.getIstioConfig().getHosts())
                    ? setupParams.getIstioConfig().getHosts()
                    : singletonList(kubernetesServiceName));

    if (setupParams.getIstioConfig() != null && !isEmpty(setupParams.getIstioConfig().getGateways())) {
      virtualServiceSpecNested.addAllToGateways(setupParams.getIstioConfig().getGateways());
    }

    DestinationRuleFluent.SpecNested<DestinationRuleBuilder> destinationRuleSpecNested =
        new DestinationRuleBuilder()
            .withApiVersion("networking.istio.io/v1alpha3")
            .withNewMetadata()
            .withAnnotations(harnessAnnotations)
            .withName(kubernetesServiceName)
            .withNamespace(setupParams.getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withHost(kubernetesServiceName);

    HttpNested virtualServiceHttpNested = virtualServiceSpecNested.addNewHttp();

    if (activeControllers.isEmpty()) {
      Destination destination = new Destination();
      destination.setHost(kubernetesServiceName);
      destination.setSubset(String.valueOf(currentRevision));
      HTTPRouteDestination destinationWeight = new HTTPRouteDestination();
      destinationWeight.setWeight(100);
      destinationWeight.setDestination(destination);

      virtualServiceHttpNested.addToRoute(destinationWeight);

    } else {
      int totalInstances = activeControllers.values().stream().mapToInt(Integer::intValue).sum();
      for (Entry<String, Integer> entry : activeControllers.entrySet()) {
        Optional<Integer> revision = getRevisionFromControllerName(entry.getKey());
        if (revision.isPresent()) {
          int weight = (int) Math.round((entry.getValue() * 100.0) / totalInstances);
          if (weight > 0) {
            Destination destination = new Destination();
            destination.setHost(kubernetesServiceName);
            destination.setSubset(revision.get().toString());
            HTTPRouteDestination destinationWeight = new HTTPRouteDestination();
            destinationWeight.setWeight(weight);
            destinationWeight.setDestination(destination);
            virtualServiceHttpNested.addToRoute(destinationWeight);

            destinationRuleSpecNested.addNewSubset()
                .withName(revision.get().toString())
                .addToLabels(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY, revision.get().toString())
                .endSubset();
          }
        }
      }
    }

    destinationRuleSpecNested.addNewSubset()
        .withName(String.valueOf(currentRevision))
        .addToLabels(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY, String.valueOf(currentRevision))
        .endSubset();

    virtualServiceHttpNested.endHttp();

    HasMetadata virtualService = virtualServiceSpecNested.endSpec().build();
    HasMetadata destinationRule = destinationRuleSpecNested.endSpec().build();
    executionLogCallback.saveExecutionLog("Creating istio VirtualService:\n\n" + toDisplayYaml(virtualService));
    executionLogCallback.saveExecutionLog("Creating istio DestinationRule:\n\n" + toDisplayYaml(destinationRule));
    return asList(virtualService, destinationRule);
  }

  private void listContainerInfosWhenReady(int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback,
      KubernetesConfig kubernetesConfig, String containerServiceName, List<Pod> originalPods, long startTime,
      boolean isRollback) {
    Optional<Integer> controllerPodCount =
        kubernetesContainerService.getControllerPodCount(kubernetesConfig, containerServiceName);
    int desiredCount = controllerPodCount.isPresent() ? controllerPodCount.get() : 0;
    int previousCount =
        kubernetesContainerService.getController(kubernetesConfig, containerServiceName) != null ? desiredCount : 0;
    List<ContainerInfo> containerInfos = kubernetesContainerService.getContainerInfosWhenReady(kubernetesConfig,
        containerServiceName, previousCount, desiredCount, serviceSteadyStateTimeout, originalPods, true,
        executionLogCallback, true, startTime, kubernetesConfig.getNamespace());

    boolean allContainersSuccess =
        containerInfos.stream().allMatch(info -> info.getStatus() == ContainerInfo.Status.SUCCESS);
    if (containerInfos.size() != desiredCount || !allContainersSuccess) {
      if (containerInfos.size() != desiredCount) {
        String message = format("Expected data for %d %s but got %d", desiredCount, plural("container", desiredCount),
            containerInfos.size());
        executionLogCallback.saveExecutionLog(message, LogLevel.ERROR);
      }
      if (!allContainersSuccess) {
        List<ContainerInfo> failed =
            containerInfos.stream().filter(info -> info.getStatus() != ContainerInfo.Status.SUCCESS).collect(toList());
        String message = format("The following %s did not have success status: %s", plural("container", failed.size()),
            failed.stream().map(ContainerInfo::getContainerId).collect(toList()));
        executionLogCallback.saveExecutionLog(message, LogLevel.ERROR);
      }
      executionLogCallback.saveExecutionLog(
          format("Completed operation with errors%n%s%n", DASH_STRING), LogLevel.ERROR);
      throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE).addParam("message", "Pods failed to reach desired count");
    }
    executionLogCallback.saveExecutionLog("\nContainer IDs:");
    containerInfos.forEach(info
        -> executionLogCallback.saveExecutionLog(
            "  " + info.getHostName() + " - " + info.getIp() + " - " + info.getContainerId()));
    executionLogCallback.saveExecutionLog("");
    if (!isRollback) {
      executionLogCallback.saveExecutionLog(format("Completed operation%n%s%n", DASH_STRING));
    }
  }

  private void performYamlRollback(ExecutionLogCallback executionLogCallback, Secret previousConfig,
      KubernetesConfig kubernetesConfig, String controllerName, int steadyStateTimeout) {
    String controllerYaml = null;
    String configMapYaml = null;
    String secretMapYaml = null;
    String autoscalerYaml = null;

    if (previousConfig != null) {
      String encodedControllerYaml = previousConfig.getData().get(CONTROLLER_YAML);
      if (isNotBlank(encodedControllerYaml)) {
        controllerYaml = decodeBase64ToString(encodedControllerYaml);
      }
      String encodedConfigMapYaml = previousConfig.getData().get(CONFIG_MAP_YAML);
      if (isNotBlank(encodedConfigMapYaml)) {
        configMapYaml = decodeBase64ToString(encodedConfigMapYaml);
      }
      String encodedSecretMapYaml = previousConfig.getData().get(SECRET_MAP_YAML);
      if (isNotBlank(encodedSecretMapYaml)) {
        secretMapYaml = decodeBase64ToString(encodedSecretMapYaml);
      }
      String encodedAutoscalerYaml = previousConfig.getData().get(AUTOSCALER_YAML);
      if (isNotBlank(encodedAutoscalerYaml)) {
        autoscalerYaml = decodeBase64ToString(encodedAutoscalerYaml);
      }
    }

    if (isNotBlank(configMapYaml)) {
      executionLogCallback.saveExecutionLog("Rolling back configMap: " + controllerName);
      ConfigMap configMap;
      try {
        configMap = KubernetesHelper.loadYaml(configMapYaml);
      } catch (Exception e) {
        throw new InvalidArgumentsException(
            "Couldn't parse configMap YAML: " + configMapYaml, ExceptionMessageSanitizer.sanitizeException(e), USER);
      }
      executionLogCallback.saveExecutionLog("Setting configMap:\n\n" + toDisplayYaml(configMap));
      kubernetesContainerService.createOrReplaceConfigMapFabric8(kubernetesConfig, configMap);
    } else {
      ConfigMap configMap = kubernetesContainerService.getConfigMapFabric8(kubernetesConfig, controllerName);
      if (configMap != null) {
        executionLogCallback.saveExecutionLog(
            "ConfigMap " + controllerName + " did not exist previously. Deleting on rollback");
        try {
          kubernetesContainerService.deleteConfigMapFabric8(kubernetesConfig, controllerName);
        } catch (Exception e) {
          executionLogCallback.saveExecutionLog("Error deleting configMap: " + controllerName, LogLevel.ERROR);
          Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
        }
      }
    }

    if (isNotBlank(secretMapYaml)) {
      executionLogCallback.saveExecutionLog("Rolling back secretMap: " + controllerName);
      Secret secretMap;
      try {
        secretMap = KubernetesHelper.loadYaml(secretMapYaml);
      } catch (Exception e) {
        throw new InvalidArgumentsException(
            "Couldn't parse secretMap YAML: " + controllerName, ExceptionMessageSanitizer.sanitizeException(e), USER);
      }
      executionLogCallback.saveExecutionLog("Setting secretMap:\n\n"
          + toDisplayYaml(
              new SecretBuilder()
                  .withMetadata(secretMap.getMetadata())
                  .withData(secretMap.getData().entrySet().stream().collect(toMap(Entry::getKey, entry -> SECRET_MASK)))
                  .build()));
      kubernetesContainerService.createOrReplaceSecretFabric8(kubernetesConfig, secretMap);
    } else {
      Secret secretMap = kubernetesContainerService.getSecretFabric8(kubernetesConfig, controllerName);
      if (secretMap != null) {
        executionLogCallback.saveExecutionLog(
            "SecretMap " + controllerName + " did not exist previously. Deleting on rollback");
        try {
          kubernetesContainerService.deleteSecretFabric8(kubernetesConfig, controllerName);
        } catch (Exception e) {
          executionLogCallback.saveExecutionLog("Error deleting secretMap: " + controllerName, LogLevel.ERROR);
          Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
        }
      }
    }

    long startTime = clock.millis();
    List<Pod> originalPods = kubernetesContainerService.getRunningPods(kubernetesConfig, controllerName);
    if (isNotBlank(controllerYaml)) {
      HasMetadata controller;
      try {
        controller = KubernetesHelper.loadYaml(controllerYaml);
      } catch (Exception e) {
        throw new InvalidArgumentsException(
            "Couldn't parse controller YAML: " + controllerYaml, ExceptionMessageSanitizer.sanitizeException(e), USER);
      }
      executionLogCallback.saveExecutionLog("Rolling back controller " + controllerName);
      executionLogCallback.saveExecutionLog("Setting controller:\n\n" + toDisplayYaml(controller));
      kubernetesContainerService.createOrReplaceController(kubernetesConfig, controller);
      PodTemplateSpec podTemplateSpec = kubernetesContainerService.getPodTemplateSpec(controller);
      executionLogCallback.saveExecutionLog("Rolled back to controller with image: "
              + podTemplateSpec.getSpec().getContainers().stream().map(Container::getImage).collect(toList()),
          LogLevel.INFO);
      listContainerInfosWhenReady(
          steadyStateTimeout, executionLogCallback, kubernetesConfig, controllerName, originalPods, startTime, true);
    } else {
      executionLogCallback.saveExecutionLog(
          "Controller " + controllerName + " did not exist previously. Deleting on rollback");
      try {
        HasMetadata controller = kubernetesContainerService.getController(kubernetesConfig, controllerName);
        Map<String, String> labels = controller.getMetadata().getLabels();
        kubernetesContainerService.deleteController(kubernetesConfig, controllerName);
        kubernetesContainerService.waitForPodsToStop(
            kubernetesConfig, labels, steadyStateTimeout, originalPods, startTime, executionLogCallback);
      } catch (Exception e) {
        executionLogCallback.saveExecutionLog("Error deleting controller: " + controllerName, LogLevel.ERROR);
        Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
      }
    }

    rollbackAutoscaler(executionLogCallback, kubernetesConfig, autoscalerYaml);
  }

  private void rollbackAutoscaler(
      ExecutionLogCallback executionLogCallback, KubernetesConfig kubernetesConfig, String autoscalerYaml) {
    HasMetadata hpa = kubernetesContainerService.createOrReplaceAutoscaler(kubernetesConfig, autoscalerYaml);
    if (hpa != null) {
      String hpaName = hpa.getMetadata().getName();
      executionLogCallback.saveExecutionLog("Rolled back horizontal pod autoscaler " + hpaName);
      executionLogCallback.saveExecutionLog("Set horizontal pod autoscaler:\n\n" + toDisplayYaml(hpa));
    }
  }

  private String getApiVersionForHPA(String yamlConfig) {
    return isBlank(yamlConfig) ? ContainerApiVersions.KUBERNETES_V1.getVersionName()
                               : ContainerApiVersions.KUBERNETES_V2_BETA1.getVersionName();
  }

  private Secret createRegistrySecret(String secretName, String namespace, ImageDetails imageDetails,
      Map<String, String> controllerLabels, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Setting image pull secret " + secretName);
    String credentialData = DockerRegistryUtils.getDockerRegistryCredentials(imageDetails);
    Map<String, String> data = ImmutableMap.of(".dockercfg", encodeBase64(credentialData));
    return new SecretBuilder()
        .withNewMetadata()
        .withAnnotations(harnessAnnotations)
        .withLabels(controllerLabels)
        .withName(secretName)
        .withNamespace(namespace)
        .endMetadata()
        .withType("kubernetes.io/dockercfg")
        .withData(data)
        .build();
  }

  private String waitForLoadBalancerEndpoint(KubernetesConfig kubernetesConfig, Service service, String loadBalancerIP,
      int timeoutInMinutes, ExecutionLogCallback executionLogCallback) {
    String serviceName = service.getMetadata().getName();
    LoadBalancerStatus loadBalancer = service.getStatus().getLoadBalancer();
    if (loadBalancer != null
        && (loadBalancer.getIngress().isEmpty()
            || (isNotBlank(loadBalancerIP) && !loadBalancerIP.equals(loadBalancer.getIngress().get(0).getIp())))) {
      executionLogCallback.saveExecutionLog("Waiting for service " + serviceName + " load balancer to be ready");
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeoutInMinutes), () -> {
          while (true) {
            LoadBalancerStatus loadBalancerStatus =
                kubernetesContainerService.getServiceFabric8(kubernetesConfig, serviceName)
                    .getStatus()
                    .getLoadBalancer();
            if (!loadBalancerStatus.getIngress().isEmpty()
                && (isEmpty(loadBalancerIP) || loadBalancerIP.equals(loadBalancerStatus.getIngress().get(0).getIp()))) {
              return getLoadBalancerEndpoint(executionLogCallback, serviceName, loadBalancerStatus);
            }
            sleep(ofSeconds(1));
          }
        });
      } catch (UncheckedTimeoutException e) {
        executionLogCallback.saveExecutionLog(
            format("Timed out waiting for service [%s] load balancer to be ready", serviceName), LogLevel.ERROR);
      } catch (Exception e) {
        Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
      }
    } else if (loadBalancer != null && !loadBalancer.getIngress().isEmpty()) {
      return getLoadBalancerEndpoint(executionLogCallback, serviceName, loadBalancer);
    }
    return null;
  }

  private String getLoadBalancerEndpoint(
      ExecutionLogCallback executionLogCallback, String serviceName, LoadBalancerStatus loadBalancer) {
    LoadBalancerIngress loadBalancerIngress = loadBalancer.getIngress().get(0);
    String loadBalancerEndpoint =
        isNotBlank(loadBalancerIngress.getHostname()) ? loadBalancerIngress.getHostname() : loadBalancerIngress.getIp();
    executionLogCallback.saveExecutionLog(
        format("Service [%s] load balancer is ready with endpoint [%s]", serviceName, loadBalancerEndpoint),
        LogLevel.INFO);
    return loadBalancerEndpoint;
  }

  private String lastController(KubernetesConfig kubernetesConfig, String controllerNamePrefix) {
    final AtomicReference<HasMetadata> lastController = new AtomicReference<>();
    final AtomicInteger lastRevision = new AtomicInteger();
    kubernetesContainerService.listControllers(kubernetesConfig)
        .stream()
        .filter(ctrl -> ctrl.getMetadata().getName().startsWith(controllerNamePrefix))
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .forEach(ctrl -> {
          Optional<Integer> revision = getRevisionFromControllerName(ctrl.getMetadata().getName());
          if (revision.isPresent() && (lastController.get() == null || revision.get() > lastRevision.get())) {
            lastController.set(ctrl);
            lastRevision.set(revision.get());
          }
        });
    return lastController.get() != null ? lastController.get().getMetadata().getName() : null;
  }

  private HasMetadata getLastDeployment(KubernetesConfig kubernetesConfig) {
    final AtomicReference<HasMetadata> lastDeployment = new AtomicReference<>();
    final AtomicInteger lastRevision = new AtomicInteger(-1);
    kubernetesContainerService.getControllers(kubernetesConfig, lookupLabels).forEach(deployment -> {
      int revision = getRevisionNumber(deployment);
      if (revision > lastRevision.get()) {
        lastDeployment.set(deployment);
        lastRevision.set(revision);
      }
    });
    return lastDeployment.get();
  }

  private int getRevisionNumber(HasMetadata kubernetesResource) {
    return isNotVersioned || kubernetesResource == null
        ? 0
        : Integer.parseInt(
            kubernetesResource.getMetadata().getLabels().get(K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY));
  }

  private HasMetadata getAutoscaler(KubernetesConfig kubernetesConfig, String name) {
    return kubernetesContainerService.getAutoscaler(kubernetesConfig, name, KUBERNETES_V1.getVersionName());
  }

  private HasMetadata createKubernetesControllerDefinition(KubernetesContainerTask kubernetesContainerTask,
      String controllerName, Map<String, String> controllerLabels, KubernetesSetupParams setupParams,
      String registrySecretName, ConfigMap configMap, Secret secretMap, List<Pod> originalPods,
      ExecutionLogCallback executionLogCallback) {
    String containerName = KubernetesConvention.getContainerName(setupParams.getImageDetails().getName());
    String imageNameTag = setupParams.getImageDetails().getName() + ":" + setupParams.getImageDetails().getTag();
    String domainName = setupParams.getImageDetails().getDomainName();

    String configMapName = configMap != null ? configMap.getMetadata().getName() : "no-config-map";
    String secretMapName = secretMap != null ? secretMap.getMetadata().getName() : "no-secret-map";

    HasMetadata controller = KubernetesContainerTaskUtils.createController(kubernetesContainerTask.getAdvancedConfig(),
        kubernetesContainerTask.getContainerDefinitions(), containerName, imageNameTag, registrySecretName,
        configMapName, secretMapName, domainName);

    KubernetesHelper.setName(controller, controllerName);
    KubernetesHelper.setNamespace(controller, setupParams.getNamespace());
    KubernetesHelper.getOrCreateLabels(controller).putAll(controllerLabels);
    controller.getMetadata().setAnnotations(mergeMaps(controller.getMetadata().getAnnotations(), harnessAnnotations));

    configureTypeSpecificSpecs(
        controllerLabels, controller, configMap, secretMap, setupParams, originalPods, executionLogCallback);
    return controller;
  }

  private void configureTypeSpecificSpecs(Map<String, String> controllerLabels, HasMetadata kubernetesObj,
      ConfigMap configMap, Secret secretMap, KubernetesSetupParams setupParams, List<Pod> originalPods,
      ExecutionLogCallback executionLogCallback) {
    PodTemplateSpec podTemplateSpec = null;
    if (kubernetesObj instanceof ReplicationController) {
      ReplicationControllerSpec rcSpec = ((ReplicationController) kubernetesObj).getSpec();
      rcSpec.setSelector(controllerLabels);
      rcSpec.setReplicas(0);
      podTemplateSpec = rcSpec.getTemplate();
    } else if (kubernetesObj instanceof Deployment) {
      DeploymentSpec depSpec = ((Deployment) kubernetesObj).getSpec();
      depSpec.setSelector(new LabelSelectorBuilder().withMatchLabels(controllerLabels).build());
      depSpec.setReplicas(0);
      podTemplateSpec = depSpec.getTemplate();
    } else if (kubernetesObj instanceof DaemonSet) {
      DaemonSetSpec dsSpec = ((DaemonSet) kubernetesObj).getSpec();
      dsSpec.setSelector(new LabelSelectorBuilder().withMatchLabels(controllerLabels).build());
      podTemplateSpec = dsSpec.getTemplate();
    } else if (kubernetesObj instanceof ReplicaSet) {
      ReplicaSetSpec repSetSpec = ((ReplicaSet) kubernetesObj).getSpec();
      repSetSpec.setSelector(new LabelSelectorBuilder().withMatchLabels(controllerLabels).build());
      repSetSpec.setReplicas(0);
      podTemplateSpec = repSetSpec.getTemplate();
    } else if (kubernetesObj instanceof StatefulSet) {
      StatefulSetSpec stateSetSpec = ((StatefulSet) kubernetesObj).getSpec();
      stateSetSpec.setSelector(new LabelSelectorBuilder().withMatchLabels(controllerLabels).build());
      int maxInstances = setupParams.getMaxInstances();
      int totalActiveServiceCount = Optional.ofNullable(originalPods).orElse(emptyList()).size();
      if (totalActiveServiceCount > 0) {
        maxInstances = totalActiveServiceCount;
      }
      stateSetSpec.setReplicas(setupParams.isUseFixedInstances() ? setupParams.getFixedInstances() : maxInstances);
      podTemplateSpec = stateSetSpec.getTemplate();
    }

    Preconditions.checkNotNull(podTemplateSpec, "Pod template spec is missing in controller definition");
    Preconditions.checkNotNull(
        podTemplateSpec.getSpec(), "Pod spec in pod template spec is missing in controller definition");

    if (podTemplateSpec.getMetadata() == null) {
      podTemplateSpec.setMetadata(new ObjectMeta());
    }

    podTemplateSpec.getMetadata().setAnnotations(
        mergeMaps(podTemplateSpec.getMetadata().getAnnotations(), harnessAnnotations));
    podTemplateSpec.getMetadata().setLabels(mergeMaps(podTemplateSpec.getMetadata().getLabels(), controllerLabels));

    Map<String, EnvVar> configMapEnvVars = new HashMap<>();

    if (configMap != null) {
      for (String key : configMap.getData().keySet()) {
        String value = configMap.getData().get(key);
        if (envVarPattern.matcher(key).matches() && isNotBlank(value) && value.length() <= MAX_ENV_VAR_LENGTH) {
          EnvVarSource varSource = new EnvVarSourceBuilder()
                                       .withNewConfigMapKeyRef()
                                       .withName(configMap.getMetadata().getName())
                                       .withKey(key)
                                       .endConfigMapKeyRef()
                                       .build();
          configMapEnvVars.put(key, new EnvVarBuilder().withName(key).withValueFrom(varSource).build());
        } else {
          String msg = "";
          if (!envVarPattern.matcher(key).matches()) {
            msg = format("Key name [%s] from config map is not a valid environment variable name. Skipping...", key);
          } else if (isBlank(value)) {
            msg = format("Value for [%s] from config map is blank. Skipping as environment variable...", key);
          } else if (value.length() > MAX_ENV_VAR_LENGTH) {
            msg = format(
                "Value for [%s] from config map has length %d which exceeds the maximum environment variable length of %d. Skipping...",
                key, value.length(), MAX_ENV_VAR_LENGTH);
          }
          executionLogCallback.saveExecutionLog(msg, LogLevel.WARN);
        }
      }
    }

    Map<String, EnvVar> secretEnvVars = new HashMap<>();

    if (secretMap != null) {
      for (String key : secretMap.getData().keySet()) {
        byte[] value = decodeBase64(secretMap.getData().get(key));
        if (envVarPattern.matcher(key).matches() && isNotEmpty(value) && value.length <= MAX_ENV_VAR_LENGTH) {
          EnvVarSource varSource = new EnvVarSourceBuilder()
                                       .withNewSecretKeyRef()
                                       .withName(secretMap.getMetadata().getName())
                                       .withKey(key)
                                       .endSecretKeyRef()
                                       .build();
          secretEnvVars.put(key, new EnvVarBuilder().withName(key).withValueFrom(varSource).build());
        } else {
          String msg = "";
          if (!envVarPattern.matcher(key).matches()) {
            msg = format("Key name [%s] from secret map is not a valid environment variable name. Skipping...", key);
          } else if (isEmpty(value)) {
            msg = format("Value for [%s] from secret map is blank. Skipping as environment variable...", key);
          } else if (value.length > MAX_ENV_VAR_LENGTH) {
            msg = format(
                "Value for [%s] from secret map has length %d which exceeds the maximum environment variable length of %d. Skipping...",
                key, value.length, MAX_ENV_VAR_LENGTH);
          }
          executionLogCallback.saveExecutionLog(msg, LogLevel.WARN);
        }
      }
    }

    for (Container container : podTemplateSpec.getSpec().getContainers()) {
      if (isNotEmpty(configMapEnvVars) || isNotEmpty(secretEnvVars)) {
        Map<String, EnvVar> containerEnvVars = new HashMap<>();
        if (container.getEnv() != null) {
          container.getEnv().forEach(envVar -> containerEnvVars.put(envVar.getName(), envVar));
        }
        if (isNotEmpty(configMapEnvVars)) {
          containerEnvVars.putAll(configMapEnvVars);
        }
        if (isNotEmpty(secretEnvVars)) {
          containerEnvVars.putAll(secretEnvVars);
        }
        container.setEnv(new ArrayList<>(containerEnvVars.values()));
      }
    }
  }

  private Map<String, String> mergeMaps(Map<String, String> map1, Map<String, String> map2) {
    Map<String, String> newMap = new HashMap<>();
    if (map1 != null) {
      newMap.putAll(map1);
    }
    if (map2 != null) {
      newMap.putAll(map2);
    }
    return newMap;
  }

  private io.fabric8.kubernetes.api.model.Service createServiceDefinition(String serviceName, String namespace,
      Map<String, String> labelSelectors, Map<String, String> labels,
      KubernetesServiceSpecification serviceSpecification, ExecutionLogCallback executionLogCallback) {
    if (serviceSpecification.getServiceType() == KubernetesServiceType.Yaml) {
      try {
        Service service = KubernetesHelper.loadYaml(serviceSpecification.getServiceYaml());
        if (service == null) {
          throw new InvalidArgumentsException(
              "Couldn't parse Service YAML: " + serviceSpecification.getServiceYaml(), USER);
        }

        if (service.getMetadata() == null) {
          service.setMetadata(new ObjectMeta());
        }

        service.getMetadata().setAnnotations(mergeMaps(service.getMetadata().getAnnotations(), harnessAnnotations));
        service.getMetadata().setLabels(mergeMaps(service.getMetadata().getLabels(), labels));
        service.getMetadata().setName(serviceName);
        service.getMetadata().setNamespace(namespace);
        service.getSpec().setSelector(mergeMaps(service.getSpec().getSelector(), labelSelectors));
        executionLogCallback.saveExecutionLog("Setting service:\n\n" + toDisplayYaml(service));
        return service;
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        throw new InvalidArgumentsException(ExceptionUtils.getMessage(sanitizedException), sanitizedException, null);
      }
    } else {
      ServiceSpecBuilder spec =
          new ServiceSpecBuilder().withSelector(labelSelectors).withType(serviceSpecification.getServiceType().name());

      if (serviceSpecification.getServiceType() != KubernetesServiceType.ExternalName) {
        ServicePortBuilder servicePort =
            new ServicePortBuilder()
                .withProtocol(serviceSpecification.getProtocol().name())
                .withPort(serviceSpecification.getPort())
                .withNewTargetPort()
                .withValue(serviceSpecification.getTargetPort())
                .endTargetPort()
                .withName(isNotBlank(serviceSpecification.getPortName()) ? serviceSpecification.getPortName() : "http");
        if (serviceSpecification.getServiceType() == KubernetesServiceType.NodePort
            && serviceSpecification.getNodePort() != null) {
          servicePort.withNodePort(serviceSpecification.getNodePort());
        }
        spec.withPorts(ImmutableList.of(servicePort.build()));

        if (serviceSpecification.getServiceType() == KubernetesServiceType.LoadBalancer
            && isNotBlank(serviceSpecification.getLoadBalancerIP())) {
          spec.withLoadBalancerIP(serviceSpecification.getLoadBalancerIP());
        }

        if (serviceSpecification.getServiceType() == KubernetesServiceType.ClusterIP
            && isNotBlank(serviceSpecification.getClusterIP())) {
          spec.withClusterIP(serviceSpecification.getClusterIP());
        }
      } else {
        spec.withExternalName(serviceSpecification.getExternalName());
      }

      if (isNotBlank(serviceSpecification.getExternalIPs())) {
        spec.withExternalIPs(
            Arrays.stream(serviceSpecification.getExternalIPs().split(",")).map(String::trim).collect(toList()));
      }

      Service service = new ServiceBuilder()
                            .withNewMetadata()
                            .withAnnotations(harnessAnnotations)
                            .withName(serviceName)
                            .withNamespace(namespace)
                            .withLabels(labels)
                            .endMetadata()
                            .withSpec(spec.build())
                            .build();
      executionLogCallback.saveExecutionLog("Setting service:\n\n" + toDisplayYaml(service));
      if (serviceSpecification.getTargetPort() != null) {
        executionLogCallback.saveExecutionLog(
            color(format("Using targetPort: %d for service %s", serviceSpecification.getTargetPort(), serviceName),
                LogColor.Cyan, Bold));
      }
      return service;
    }
  }

  private void downsizeOldOrUnhealthy(KubernetesConfig kubernetesConfig, String containerServiceName,
      KubernetesSetupParams setupParams, ExecutionLogCallback executionLogCallback) {
    Map<String, Integer> activeCounts;

    if (setupParams.isUseNewLabelMechanism()) {
      activeCounts = kubernetesContainerService.getActiveServiceCountsWithLabels(kubernetesConfig, lookupLabels);
    } else {
      activeCounts = kubernetesContainerService.getActiveServiceCounts(kubernetesConfig, containerServiceName);
    }

    if (activeCounts.size() > 1) {
      List<String> unhealthyControllers = new ArrayList<>();
      executionLogCallback.saveExecutionLog("\nActive pods:");
      for (String activeControllerName : activeCounts.keySet()) {
        List<Pod> pods = kubernetesContainerService.getRunningPods(kubernetesConfig, activeControllerName);
        // Pass previous count of zero so that steady state check is performed
        List<ContainerInfo> containerInfos = kubernetesContainerService.getContainerInfosWhenReady(kubernetesConfig,
            activeControllerName, 0, pods.size(), 1, pods, false, executionLogCallback, false, clock.millis(),
            kubernetesConfig.getNamespace());
        boolean allContainersSuccess =
            containerInfos.stream().allMatch(info -> info.getStatus() == ContainerInfo.Status.SUCCESS);
        if (!allContainersSuccess) {
          unhealthyControllers.add(activeControllerName);
        }
      }

      // Get the max instance count of any controller, healthy or not
      int maxInstances =
          activeCounts.values().stream().mapToInt(Integer::intValue).max().orElse(setupParams.getMaxInstances());

      // Downsize all unhealthy
      for (String controllerName : unhealthyControllers) {
        executionLogCallback.saveExecutionLog("");
        kubernetesContainerService.setControllerPodCount(kubernetesConfig, setupParams.getClusterName(), controllerName,
            activeCounts.remove(controllerName), 0, setupParams.getServiceSteadyStateTimeout(), executionLogCallback);
      }

      int maxAllowed = setupParams.isUseFixedInstances() ? setupParams.getFixedInstances() : maxInstances;
      int totalHealthyInstances = activeCounts.values().stream().mapToInt(Integer::intValue).sum();
      int downsizeCount = Math.max(totalHealthyInstances - maxAllowed, 0);

      if (downsizeCount > 0) {
        // Downsize healthy from oldest to newest until at most maxAllowed instances remain
        for (Map.Entry<String, Integer> entry : activeCounts.entrySet()) {
          int previousCount = entry.getValue();
          int desiredCount = Math.max(previousCount - downsizeCount, 0);

          if (desiredCount < previousCount) {
            kubernetesContainerService.setControllerPodCount(kubernetesConfig, setupParams.getClusterName(),
                entry.getKey(), previousCount, desiredCount, setupParams.getServiceSteadyStateTimeout(),
                executionLogCallback);
          }

          downsizeCount -= previousCount - desiredCount;
        }
      }
    }
  }

  private void cleanup(
      KubernetesConfig kubernetesConfig, String containerServiceName, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("\nRemoving versions with no pods");
    Optional<Integer> revision = getRevisionFromControllerName(containerServiceName);
    if (revision.isPresent()) {
      String controllerNamePrefix = getPrefixFromControllerName(containerServiceName);
      kubernetesContainerService.listControllers(kubernetesConfig)
          .stream()
          .filter(ctrl -> ctrl.getMetadata().getName().startsWith(controllerNamePrefix))
          .filter(ctrl -> !ctrl.getMetadata().getName().equals(containerServiceName))
          .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
          .filter(ctrl -> kubernetesContainerService.getControllerPodCount(ctrl) == 0)
          .forEach(ctrl -> {
            String controllerName = ctrl.getMetadata().getName();
            Optional<Integer> ctrlRevision = getRevisionFromControllerName(controllerName);
            if (ctrlRevision.isPresent()) {
              log.info("Deleting old version: " + controllerName);
              executionLogCallback.saveExecutionLog("Deleting old version: " + controllerName);
              try {
                kubernetesContainerService.deleteController(kubernetesConfig, controllerName);
                kubernetesContainerService.deleteAutoscaler(kubernetesConfig, controllerName);
                kubernetesContainerService.deleteConfigMapFabric8(kubernetesConfig, controllerName);
                kubernetesContainerService.deleteSecretFabric8(kubernetesConfig, controllerName);
              } catch (Exception e) {
                Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
              }
            }
          });
    }
  }

  private void cleanupWithLabels(KubernetesConfig kubernetesConfig, ExecutionLogCallback executionLogCallback) {
    if (isNotVersioned) {
      return;
    }
    executionLogCallback.saveExecutionLog("\nRemoving versions with no pods");

    kubernetesContainerService.getControllers(kubernetesConfig, lookupLabels)
        .stream()
        .filter(ctrl
            -> kubernetesContainerService.getControllerPodCount(ctrl) == 0
                && getRevisionNumber(ctrl) != currentRevision)
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .forEach(ctrl -> {
          String controllerName = ctrl.getMetadata().getName();
          log.info("Deleting old version: " + controllerName);
          executionLogCallback.saveExecutionLog("Deleting old version: " + controllerName);
          try {
            kubernetesContainerService.deleteController(kubernetesConfig, controllerName);
            kubernetesContainerService.deleteAutoscaler(kubernetesConfig, controllerName);
            kubernetesContainerService.deleteConfigMapFabric8(kubernetesConfig, controllerName);
            kubernetesContainerService.deleteSecretFabric8(kubernetesConfig, controllerName);
          } catch (Exception e) {
            Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
          }
        });

    kubernetesContainerService.getServices(kubernetesConfig, lookupLabels)
        .stream()
        .filter(service -> !(service.getMetadata().getName().equals(getKubernetesServiceName(currentNamePrefix))))
        .filter(service
            -> !(service.getMetadata().getName().equals(
                getKubernetesServiceName(getPrimaryServiceName(currentNamePrefix)))))
        .filter(service
            -> !(service.getMetadata().getName().equals(
                getKubernetesServiceName(getStageServiceName(currentNamePrefix)))))
        .filter(
            service -> isEmpty(kubernetesContainerService.getPods(kubernetesConfig, service.getSpec().getSelector())))
        .forEach(service -> {
          String serviceName = service.getMetadata().getName();
          log.info("Deleting old service: " + serviceName);
          executionLogCallback.saveExecutionLog("Deleting old service: " + serviceName);
          try {
            kubernetesContainerService.deleteService(kubernetesConfig, serviceName);
            kubernetesContainerService.deleteIngress(kubernetesConfig, serviceName);
          } catch (Exception e) {
            Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
          }
        });
  }

  private void cleanupStageDeployment(
      KubernetesConfig kubernetesConfig, KubernetesSetupParams setupParams, ExecutionLogCallback executionLogCallback) {
    String primaryServiceName = getPrimaryServiceName(getKubernetesServiceName(setupParams.getControllerNamePrefix()));
    String stageServiceName = getStageServiceName(getKubernetesServiceName(setupParams.getControllerNamePrefix()));

    Service primaryService;
    Service stageService;

    try {
      primaryService = kubernetesContainerService.getServiceFabric8(kubernetesConfig, primaryServiceName);
      stageService = kubernetesContainerService.getServiceFabric8(kubernetesConfig, stageServiceName);
    } catch (Exception e) {
      Misc.logAllMessages(ExceptionMessageSanitizer.sanitizeException(e), executionLogCallback);
      return;
    }

    if (primaryService == null) {
      executionLogCallback.saveExecutionLog("No Primary Service found.");
      return;
    }

    if (stageService == null) {
      executionLogCallback.saveExecutionLog("No Stage Service found.");
      return;
    }

    String primaryRevision = getRevisionFromService(primaryService);
    String stageRevision = getRevisionFromService(stageService);

    executionLogCallback.saveExecutionLog("Primary Service is at revision: " + primaryRevision);
    executionLogCallback.saveExecutionLog("Stage Service is at revision: " + stageRevision);

    if (!StringUtils.equals(primaryRevision, stageRevision)) {
      executionLogCallback.saveExecutionLog("\nScaling down Stage Deployment [Revision: " + stageRevision + "] to 0");
      String controllerName = KubernetesConvention.getControllerName(
          setupParams.getControllerNamePrefix(), Integer.parseInt(stageRevision));

      Optional<Integer> podCount = kubernetesContainerService.getControllerPodCount(kubernetesConfig, controllerName);

      if (podCount.isPresent() && podCount.get() > 0) {
        kubernetesContainerService.setControllerPodCount(kubernetesConfig, setupParams.getClusterName(), controllerName,
            podCount.orElse(1), 0, setupParams.getServiceSteadyStateTimeout(), executionLogCallback);
      } else {
        executionLogCallback.saveExecutionLog("No Pods found for Stage Deployment. Continue..");
      }
    } else {
      executionLogCallback.saveExecutionLog("Skipping Cleanup as Primary and Stage are using same Deployment");
    }
  }

  private void printIngressRules(
      Ingress ingress, ExecutionLogCallback executionLogCallback, StringBuilder summaryOutput) {
    String path;
    String port;
    String serviceName;
    String host;
    try {
      for (IngressRule ingressRule : ingress.getSpec().getRules()) {
        HTTPIngressPath httpIngressPath = ingressRule.getHttp().getPaths().get(0);
        path = httpIngressPath.getPath();
        port = httpIngressPath.getBackend().getServicePort().getIntVal().toString();
        serviceName = httpIngressPath.getBackend().getServiceName();
        host = ingressRule.getHost();
        summaryOutput.append(
            format("%nIngress Rule: %s : %s %s → %s", isNotBlank(host) ? host : "", port, path, serviceName));
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Couldn't get path from ingress rule.", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Error getting Ingress rule - " + ExceptionUtils.getMessage(sanitizedException), LogLevel.WARN);
    }
  }

  private void performResize(KubernetesConfig kubernetesConfig, String clusterName, String newControllerName,
      List<String[]> serviceCounts, int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback) {
    if (isNotEmpty(serviceCounts)) {
      for (String[] serviceCount : serviceCounts) {
        Optional<Integer> controllerPodCount =
            kubernetesContainerService.getControllerPodCount(kubernetesConfig, serviceCount[0]);
        if (controllerPodCount.isPresent()) {
          kubernetesContainerService.setControllerPodCount(kubernetesConfig, clusterName, serviceCount[0],
              controllerPodCount.get(), Integer.parseInt(serviceCount[1]), serviceSteadyStateTimeout,
              executionLogCallback);
        }
      }
    }

    if (isNotBlank(newControllerName)) {
      Optional<Integer> controllerPodCount =
          kubernetesContainerService.getControllerPodCount(kubernetesConfig, newControllerName);
      if (controllerPodCount.isPresent()) {
        kubernetesContainerService.setControllerPodCount(kubernetesConfig, clusterName, newControllerName,
            controllerPodCount.get(), 0, serviceSteadyStateTimeout, executionLogCallback);
      }
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("KUBERNETES_SETUP")
  public static class Yaml extends ContainerSetupCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.KUBERNETES_SETUP.name());
    }

    @Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.KUBERNETES_SETUP.name(), deploymentType);
    }
  }
}
