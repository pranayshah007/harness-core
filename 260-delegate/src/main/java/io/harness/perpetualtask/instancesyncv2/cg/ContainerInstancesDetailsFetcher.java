package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helm.HelmConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class ContainerInstancesDetailsFetcher implements InstanceDetailsFetcher {
  private final KryoSerializer kryoSerializer;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private EncryptionService encryptionService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private GkeClusterService gkeClusterService;
  @Override
  public List<InstanceInfo> fetchRunningInstanceDetails(
      PerpetualTaskId taskId, CgInstanceSyncTaskParams params, String releaseDetails) {
    K8sClusterConfig config =
        (K8sClusterConfig) kryoSerializer.asObject(params.getCloudProviderDetails().toByteArray());
    SettingAttribute settingAttribute = aSettingAttribute().withValue(config.getCloudProvider()).build();
    KubernetesConfig kubernetesConfig = getKubernetesConfig(config, true);
    notNullCheck("KubernetesConfig", kubernetesConfig);
    List<ContainerInfo> result = new ArrayList<>();

    // get deployment release details using manager API call

    final List<V1Pod> pods = kubernetesContainerService.getRunningPodsWithLabels(
        kubernetesConfig, config.getNamespace(), ImmutableMap.of(HelmConstants.HELM_RELEASE_LABEL, releaseDetails));
    return pods.stream()
        .map(pod
            -> KubernetesContainerInfo.builder()
                   .clusterName(config.getClusterName())
                   .podName(pod.getMetadata().getName())
                   .ip(pod.getStatus().getPodIP())
                   .namespace(config.getNamespace())
                   .releaseName(releaseDetails)
                   .build())
        .collect(toList());
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
