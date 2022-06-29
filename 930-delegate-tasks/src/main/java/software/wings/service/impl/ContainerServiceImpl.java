/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.infrastructure.instance.info.EcsContainerInfo.Builder.anEcsContainerInfo;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helm.HelmConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ContainerServiceImpl implements ContainerService {
  @Inject private GkeClusterService gkeClusterService;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private AwsClusterService awsClusterService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EcsContainerService ecsContainerService;
  @Inject private EncryptionService encryptionService;

  private boolean isKubernetesClusterConfig(SettingValue value) {
    return value instanceof AzureConfig || value instanceof GcpConfig || value instanceof KubernetesClusterConfig;
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCounts(ContainerServiceParams containerServiceParams) {
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();
    String controllerName = containerServiceParams.getContainerServiceName();
    if (isKubernetesClusterConfig(value)) {
      return kubernetesContainerService.getActiveServiceCounts(
          getKubernetesConfig(containerServiceParams, false), controllerName);
    } else if (value instanceof AwsConfig) {
      return awsClusterService.getActiveServiceCounts(containerServiceParams.getRegion(),
          containerServiceParams.getSettingAttribute(), containerServiceParams.getEncryptionDetails(),
          containerServiceParams.getClusterName(), controllerName);
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Unknown setting value type for container service: " + value.getType());
    }
  }

  private KubernetesConfig getKubernetesConfig(ContainerServiceParams containerServiceParams, boolean isInstanceSync) {
    KubernetesConfig kubernetesConfig;
    if (containerServiceParams.getSettingAttribute().getValue() instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(containerServiceParams.getSettingAttribute(),
          containerServiceParams.getEncryptionDetails(), containerServiceParams.getClusterName(),
          containerServiceParams.getNamespace(), isInstanceSync);
    } else if (containerServiceParams.getSettingAttribute().getValue() instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) containerServiceParams.getSettingAttribute().getValue();
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(azureConfig,
          containerServiceParams.getEncryptionDetails(), containerServiceParams.getSubscriptionId(),
          containerServiceParams.getResourceGroup(), containerServiceParams.getClusterName(),
          containerServiceParams.getNamespace(), isInstanceSync);
    } else {
      KubernetesClusterConfig kubernetesClusterConfig =
          (KubernetesClusterConfig) containerServiceParams.getSettingAttribute().getValue();
      encryptionService.decrypt(kubernetesClusterConfig, containerServiceParams.getEncryptionDetails(), isInstanceSync);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          kubernetesClusterConfig, containerServiceParams.getEncryptionDetails());
      kubernetesConfig = kubernetesClusterConfig.createKubernetesConfig(containerServiceParams.getNamespace());
    }

    return kubernetesConfig;
  }

  @Override
  public List<ContainerInfo> getContainerInfos(ContainerServiceParams containerServiceParams, boolean isInstanceSync) {
    String containerServiceName = containerServiceParams.getContainerServiceName();
    String accountId = containerServiceParams.getSettingAttribute().getAccountId();
    log.info("Getting container infos for account {}, controller: {}", accountId, containerServiceName);
    List<ContainerInfo> result = new ArrayList<>();
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();

    if (isKubernetesClusterConfig(value)) {
      log.info("Kubernetes cluster config for account {}, controller: {}", accountId, containerServiceName);
      KubernetesConfig kubernetesConfig = getKubernetesConfig(containerServiceParams, isInstanceSync);
      notNullCheck("KubernetesConfig", kubernetesConfig);
      if (isNotEmpty(containerServiceName)) {
        HasMetadata controller = kubernetesContainerService.getController(kubernetesConfig, containerServiceName);
        if (controller != null) {
          log.info("Got controller {} for account {}", controller.getMetadata().getName(), accountId);
          Map<String, String> labels =
              kubernetesContainerService.getPodTemplateSpec(controller).getMetadata().getLabels();
          Map<String, String> serviceLabels = new HashMap<>(labels);
          serviceLabels.remove(HARNESS_KUBERNETES_REVISION_LABEL_KEY);
          // Migrate to K8s Native Java Client
          List<io.fabric8.kubernetes.api.model.Service> services =
              kubernetesContainerService.getServices(kubernetesConfig, serviceLabels);
          String serviceName = services.isEmpty() ? "None" : services.get(0).getMetadata().getName();
          log.info("Got Service {} for controller {} for account {}", serviceName, containerServiceName, accountId);
          List<V1Pod> pods = kubernetesContainerService.getRunningPodsWithLabels(
              kubernetesConfig, containerServiceParams.getNamespace(), labels);
          log.info("Got {} pods for controller {} for account {}", pods != null ? pods.size() : 0, containerServiceName,
              accountId);
          if (isEmpty(pods)) {
            return result;
          }

          for (V1Pod pod : pods) {
            String phase = pod.getStatus().getPhase();
            log.info("Phase: {} for pod {} for controller {} for account {}", pod.getStatus().getPhase(),
                pod.getMetadata().getName(), containerServiceName, accountId);
            if ("Running".equals(phase)) {
              result.add(KubernetesContainerInfo.builder()
                             .clusterName(containerServiceParams.getClusterName())
                             .podName(pod.getMetadata().getName())
                             .ip(pod.getStatus().getPodIP())
                             .controllerName(containerServiceName)
                             .serviceName(serviceName)
                             .namespace(containerServiceParams.getNamespace())
                             .releaseName(containerServiceParams.getReleaseName())
                             .build());
            }
          }
        } else {
          log.info("Could not get controller {} for account {}", containerServiceName, accountId);
        }
      } else {
        if (isEmpty(containerServiceParams.getReleaseName())) {
          return Collections.emptyList();
        }
        final List<V1Pod> pods =
            kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, containerServiceParams.getNamespace(),
                ImmutableMap.of(HelmConstants.HELM_RELEASE_LABEL, containerServiceParams.getReleaseName()));
        return pods.stream()
            .map(pod
                -> KubernetesContainerInfo.builder()
                       .clusterName(containerServiceParams.getClusterName())
                       .podName(pod.getMetadata().getName())
                       .ip(pod.getStatus().getPodIP())
                       .namespace(containerServiceParams.getNamespace())
                       .releaseName(containerServiceParams.getReleaseName())
                       .build())
            .collect(toList());
      }
    } else if (value instanceof AwsConfig) {
      AwsConfig awsConfig = awsHelperService.validateAndGetAwsConfig(
          containerServiceParams.getSettingAttribute(), containerServiceParams.getEncryptionDetails(), isInstanceSync);
      notNullCheck("AwsConfig", awsConfig);

      List<Task> tasks;

      String nextToken = null;
      do {
        ListTasksRequest listTasksRequest = new ListTasksRequest()
                                                .withCluster(containerServiceParams.getClusterName())
                                                .withServiceName(containerServiceName)
                                                //.withMaxResults(100)
                                                .withNextToken(nextToken)
                                                .withDesiredStatus("RUNNING");
        ListTasksResult listTasksResult;
        try {
          listTasksResult = awsHelperService.listTasks(containerServiceParams.getRegion(), awsConfig,
              containerServiceParams.getEncryptionDetails(), listTasksRequest, isInstanceSync);
        } catch (WingsException ex) {
          // if the cluster / service has been deleted, we need to continue and check the rest of the service names
          ErrorCode errorCode = ex.getCode();
          if (errorCode != null) {
            if (ErrorCode.AWS_CLUSTER_NOT_FOUND == errorCode) {
              log.info("ECS Cluster not found for service name:" + containerServiceName);
              continue;
            } else if (ErrorCode.AWS_SERVICE_NOT_FOUND == errorCode) {
              log.info("ECS Service not found for service name:" + containerServiceName);
              continue;
            }
          }
          throw ex;
        }

        if (!listTasksResult.getTaskArns().isEmpty()) {
          DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest()
                                                          .withCluster(containerServiceParams.getClusterName())
                                                          .withTasks(listTasksResult.getTaskArns());
          DescribeTasksResult describeTasksResult = awsHelperService.describeTasks(containerServiceParams.getRegion(),
              awsConfig, containerServiceParams.getEncryptionDetails(), describeTasksRequest, isInstanceSync);
          tasks = describeTasksResult.getTasks();
          for (Task task : tasks) {
            if (task != null) {
              result.add(anEcsContainerInfo()
                             .withClusterName(containerServiceParams.getClusterName())
                             .withTaskDefinitionArn(task.getTaskDefinitionArn())
                             .withTaskArn(task.getTaskArn())
                             .withVersion(task.getVersion())
                             .withStartedAt(task.getStartedAt() == null ? 0L : task.getStartedAt().getTime())
                             .withStartedBy(task.getStartedBy())
                             .withServiceName(containerServiceName)
                             .build());
            }
          }
        }
        nextToken = listTasksResult.getNextToken();
      } while (nextToken != null);
    }
    return result;
  }

  @Override
  public Set<String> getControllerNames(ContainerServiceParams containerServiceParams, Map<String, String> labels) {
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();
    if (isKubernetesClusterConfig(value)) {
      KubernetesConfig kubernetesConfig = getKubernetesConfig(containerServiceParams, false);
      notNullCheck("KubernetesConfig", kubernetesConfig);
      List<? extends HasMetadata> controllers =
          kubernetesContainerService.getControllers(kubernetesConfig, labels)
              .stream()
              .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
              .collect(toList());

      if (isNotEmpty(controllers)) {
        return controllers.stream().map(controller -> controller.getMetadata().getName()).collect(Collectors.toSet());
      }
    }

    return Sets.newHashSet();
  }

  @Override
  public Boolean validate(ContainerServiceParams containerServiceParams, boolean useNewKubectlVersion) {
    String namespace = containerServiceParams.getNamespace();
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();
    if (value instanceof AwsConfig) {
      awsClusterService.getServices(containerServiceParams.getRegion(), containerServiceParams.getSettingAttribute(),
          containerServiceParams.getEncryptionDetails(), containerServiceParams.getClusterName());
      return true;
    } else if (value instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) containerServiceParams.getSettingAttribute().getValue();
      if (azureDelegateHelperService.isValidKubernetesCluster(azureConfig,
              containerServiceParams.getEncryptionDetails(), containerServiceParams.getSubscriptionId(),
              containerServiceParams.getResourceGroup(), containerServiceParams.getClusterName())) {
        KubernetesConfig kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(azureConfig,
            containerServiceParams.getEncryptionDetails(), containerServiceParams.getSubscriptionId(),
            containerServiceParams.getResourceGroup(), containerServiceParams.getClusterName(), namespace, false);
        kubernetesContainerService.validate(kubernetesConfig, useNewKubectlVersion);
        return true;
      } else {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, "Invalid Argument: Not a valid AKS cluster");
      }
    } else if (value instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig kubernetesClusterConfig = (KubernetesClusterConfig) value;
      encryptionService.decrypt(kubernetesClusterConfig, containerServiceParams.getEncryptionDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          kubernetesClusterConfig, containerServiceParams.getEncryptionDetails());
      KubernetesConfig kubernetesConfig = kubernetesClusterConfig.createKubernetesConfig(namespace);
      kubernetesContainerService.validate(kubernetesConfig, useNewKubectlVersion);
      return true;
    } else if (isKubernetesClusterConfig(value)) {
      KubernetesConfig kubernetesConfig = getKubernetesConfig(containerServiceParams, false);
      kubernetesContainerService.validate(kubernetesConfig, useNewKubectlVersion);
      return true;
    }
    throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
        .addParam("args", "Unknown setting value type: " + value.getType());
  }

  @Override
  public Boolean validateCE(ContainerServiceParams containerServiceParams) {
    KubernetesConfig kubernetesConfig = getKubernetesConfigFromParams(containerServiceParams);
    kubernetesContainerService.validateCEPermissions(kubernetesConfig);
    return true;
  }

  @Override
  public CEK8sDelegatePrerequisite validateCEK8sDelegate(ContainerServiceParams containerServiceParams) {
    KubernetesConfig kubernetesConfig = getKubernetesConfigFromParams(containerServiceParams);

    CEK8sDelegatePrerequisite.MetricsServerCheck metricsServerCheck =
        kubernetesContainerService.validateMetricsServer(kubernetesConfig);
    List<CEK8sDelegatePrerequisite.Rule> ruleList =
        kubernetesContainerService.validateCEResourcePermissions(kubernetesConfig);

    return CEK8sDelegatePrerequisite.builder().metricsServer(metricsServerCheck).permissions(ruleList).build();
  }

  private KubernetesConfig getKubernetesConfigFromParams(ContainerServiceParams containerServiceParams) {
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();
    Preconditions.checkState(
        value instanceof KubernetesClusterConfig, "SettingAttribute should be instanceof KubernetesClusterConfig.");

    KubernetesClusterConfig kubernetesClusterConfig = (KubernetesClusterConfig) value;
    return kubernetesClusterConfig.createKubernetesConfig(containerServiceParams.getNamespace());
  }

  @Override
  public List<String> listClusters(ContainerServiceParams containerServiceParams) {
    return gkeClusterService.listClusters(
        containerServiceParams.getSettingAttribute(), containerServiceParams.getEncryptionDetails());
  }

  @Override
  public String fetchMasterUrl(MasterUrlFetchTaskParameter masterUrlFetchTaskParameter) {
    ContainerServiceParams containerServiceParams = masterUrlFetchTaskParameter.getContainerServiceParams();
    SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
    SettingValue value = settingAttribute.getValue();
    KubernetesConfig kubernetesConfig;

    String clusterName = containerServiceParams.getClusterName();
    String namespace = containerServiceParams.getNamespace();
    String subscriptionId = containerServiceParams.getSubscriptionId();
    String resourceGroup = containerServiceParams.getResourceGroup();
    List<EncryptedDataDetail> edd = containerServiceParams.getEncryptionDetails();
    if (value instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(settingAttribute, edd, clusterName, namespace, false);
    } else if (value instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) value;
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(
          azureConfig, edd, subscriptionId, resourceGroup, clusterName, namespace, false);
    } else {
      throw new InvalidArgumentsException(
          Pair.of("Setting Value", "Unknown kubernetes cloud provider setting value: " + value.getType()));
    }
    return kubernetesConfig.getMasterUrl();
  }
}
