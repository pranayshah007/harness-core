package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helm.HelmConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;

import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class ContainerInstancesDetailsFetcher implements InstanceDetailsFetcher {
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private EncryptionService encryptionService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private GkeClusterService gkeClusterService;
  @Override
  public List<InstanceInfo> fetchRunningInstanceDetails(
      PerpetualTaskId taskId, K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(config, true);
    notNullCheck("KubernetesConfig", kubernetesConfig);
    try {
      final List<V1Pod> pods =
          kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, config.getNamespace(),
              ImmutableMap.of(HelmConstants.HELM_RELEASE_LABEL, k8sInstanceSyncTaskDetails.getReleaseName()));
      return pods.stream()
          .map(pod
              -> KubernetesContainerInfo.builder()
                     .clusterName(config.getClusterName())
                     .podName(pod.getMetadata().getName())
                     .ip(pod.getStatus().getPodIP())
                     .namespace(k8sInstanceSyncTaskDetails.getNamespace())
                     .releaseName(k8sInstanceSyncTaskDetails.getReleaseName())
                     .build())
          .collect(toList());
    } catch (Exception exception) {
      throw new InvalidRequestException(String.format("Failed to fetch containers info for namespace: [%s] ",
                                            k8sInstanceSyncTaskDetails.getNamespace()),
          exception);
    }
  }

  private KubernetesConfig getKubernetesConfig(K8sClusterConfig config, boolean isInstanceSync) {
    SettingValue cloudProvider = config.getCloudProvider();
    SettingAttribute settingAttribute = aSettingAttribute().withValue(cloudProvider).build();
    KubernetesConfig kubernetesConfig;

    if (cloudProvider instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(settingAttribute, config.getCloudProviderEncryptionDetails(),
          config.getGcpKubernetesCluster().getClusterName(), config.getNamespace(), isInstanceSync);
    } else if (cloudProvider instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) cloudProvider;
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(azureConfig,
          config.getCloudProviderEncryptionDetails(), config.getAzureKubernetesCluster().getSubscriptionId(),
          config.getAzureKubernetesCluster().getResourceGroup(), config.getClusterName(), config.getNamespace(),
          isInstanceSync);
    } else {
      KubernetesClusterConfig kubernetesClusterConfig = (KubernetesClusterConfig) cloudProvider;
      encryptionService.decrypt(kubernetesClusterConfig, config.getCloudProviderEncryptionDetails(), isInstanceSync);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          kubernetesClusterConfig, config.getCloudProviderEncryptionDetails());
      kubernetesConfig = kubernetesClusterConfig.createKubernetesConfig(config.getNamespace());
    }

    return kubernetesConfig;
  }
}
