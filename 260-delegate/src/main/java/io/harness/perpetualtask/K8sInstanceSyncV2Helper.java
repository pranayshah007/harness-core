/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.K8sContainerToHelmServiceInstanceInfoMapper;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.instancesync.k8s.KubernetesCloudClusterConfig;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sInstanceSyncV2Helper {
  public static final String CLASS_CAST_EXCEPTION_ERROR = "Unsupported Connector provided is of type: [%s]";
  private static final int DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT = 5;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerBaseHelper;

  public KubernetesConfig getKubernetesConfig(
      ConnectorInfoDTO connectorDTO, KubernetesCloudClusterConfig kubernetesCloudClusterConfig, String namespace) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig =
        getK8sInfraDelegateConfig(connectorDTO, kubernetesCloudClusterConfig, namespace);
    return containerBaseHelper.createKubernetesConfig(k8sInfraDelegateConfig, null);
  }

  private K8sInfraDelegateConfig getK8sInfraDelegateConfig(
      ConnectorInfoDTO connectorDTO, KubernetesCloudClusterConfig kubernetesCloudClusterConfig, String namespace) {
    try {
      KubernetesHelperService.validateNamespace(namespace);
      switch (connectorDTO.getConnectorType()) {
        case KUBERNETES_CLUSTER:
          return DirectK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) connectorDTO.getConnectorConfig())
              .useSocketCapability(true)
              .build();

        case GCP:
          KubernetesHelperService.validateCluster(kubernetesCloudClusterConfig.getClusterName());
          return GcpK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(kubernetesCloudClusterConfig.getClusterName())
              .gcpConnectorDTO((GcpConnectorDTO) connectorDTO.getConnectorConfig())
              .build();

        case AZURE:
          KubernetesHelperService.validateSubscription(kubernetesCloudClusterConfig.getSubscriptionId());
          KubernetesHelperService.validateResourceGroup(kubernetesCloudClusterConfig.getResourceGroup());
          KubernetesHelperService.validateCluster(kubernetesCloudClusterConfig.getClusterName());
          return AzureK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(kubernetesCloudClusterConfig.getClusterName())
              .subscription(kubernetesCloudClusterConfig.getSubscriptionId())
              .resourceGroup(kubernetesCloudClusterConfig.getResourceGroup())
              .azureConnectorDTO((AzureConnectorDTO) connectorDTO.getConnectorConfig())
              .useClusterAdminCredentials(kubernetesCloudClusterConfig.isUseClusterAdminCredentials())
              .build();

        case AWS:
          KubernetesHelperService.validateCluster(kubernetesCloudClusterConfig.getClusterName());
          return EksK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(kubernetesCloudClusterConfig.getClusterName())
              .awsConnectorDTO((AwsConnectorDTO) connectorDTO.getConnectorConfig())
              .build();

        case RANCHER:
          KubernetesHelperService.validateCluster(kubernetesCloudClusterConfig.getClusterName());
          return RancherK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(kubernetesCloudClusterConfig.getClusterName())
              .rancherConnectorDTO((RancherConnectorDTO) connectorDTO.getConnectorConfig())
              .build();

        default:
          throw new UnsupportedOperationException(
              format("Unsupported Connector Type type: [%s]", connectorDTO.getConnectorType()));
      }
    } catch (ClassCastException ex) {
      if (Set.of(KUBERNETES_CLUSTER, GCP, AZURE, AWS).contains(connectorDTO.getConnectorType())) {
        throw new InvalidArgumentsException(
            Pair.of("connectorRef", String.format(CLASS_CAST_EXCEPTION_ERROR, connectorDTO.getConnectorType())));
      }
      throw ex;
    }
  }

  public List<ServerInstanceInfo> getServerInstanceInfoList(
      K8sInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest requestData) throws Exception {
    long timeoutMillis =
        K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT);

    List<K8sPod> k8sPodList = k8sTaskHelperBase.getPodDetails(
        requestData.getKubernetesConfig(), requestData.getNamespace(), requestData.getReleaseName(), timeoutMillis);
    return K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(k8sPodList);
  }
  public List<ServerInstanceInfo> getServerInstanceInfoList(
      NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest requestData) throws Exception {
    long timeoutMillis =
        K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT);

    List<ContainerInfo> containerInfoList = k8sTaskHelperBase.getContainerInfos(
        requestData.getKubernetesConfig(), requestData.getReleaseName(), requestData.getNamespace(), timeoutMillis);
    return K8sContainerToHelmServiceInstanceInfoMapper.toServerInstanceInfoList(
        containerInfoList, requestData.getHelmChartInfo(), HelmVersion.valueOf(requestData.getHelmVersion()));
  }
}
