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
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.instancesync.K8sDeploymentReleaseDetails;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.Collections;
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

  public KubernetesConfig getKubernetesConfig(ConnectorInfoDTO connectorDTO, K8sDeploymentReleaseDetails releaseDetails,
      String namespace, List<EncryptedDataDetail> encryptedDataDetails) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig =
        getK8sInfraDelegateConfig(connectorDTO, releaseDetails, namespace, encryptedDataDetails);
    containerBaseHelper.decryptK8sInfraDelegateConfig(k8sInfraDelegateConfig);
    return containerBaseHelper.createKubernetesConfig(k8sInfraDelegateConfig, null);
  }

  private K8sInfraDelegateConfig getK8sInfraDelegateConfig(ConnectorInfoDTO connectorDTO,
      K8sDeploymentReleaseDetails releaseDetails, String namespace, List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      switch (connectorDTO.getConnectorType()) {
        case KUBERNETES_CLUSTER:
          KubernetesHelperService.validateNamespace(namespace);
          return DirectK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(encryptedDataDetails)
              .useSocketCapability(true)
              .build();

        case GCP:
          KubernetesHelperService.validateNamespace(namespace);
          KubernetesHelperService.validateCluster(releaseDetails.getCloudClusterConfig().getClusterName());
          return GcpK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(releaseDetails.getCloudClusterConfig().getClusterName())
              .gcpConnectorDTO((GcpConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(encryptedDataDetails)
              .build();

        case AZURE:
          KubernetesHelperService.validateNamespace(namespace);
          KubernetesHelperService.validateSubscription(releaseDetails.getCloudClusterConfig().getSubscriptionId());
          KubernetesHelperService.validateResourceGroup(releaseDetails.getCloudClusterConfig().getResourceGroup());
          KubernetesHelperService.validateCluster(releaseDetails.getCloudClusterConfig().getClusterName());
          return AzureK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(releaseDetails.getCloudClusterConfig().getClusterName())
              .subscription(releaseDetails.getCloudClusterConfig().getSubscriptionId())
              .resourceGroup(releaseDetails.getCloudClusterConfig().getResourceGroup())
              .azureConnectorDTO((AzureConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(encryptedDataDetails)
              .useClusterAdminCredentials(releaseDetails.getCloudClusterConfig().getUseClusterAdminCredentials())
              .build();

        case AWS:
          KubernetesHelperService.validateNamespace(namespace);
          KubernetesHelperService.validateCluster(releaseDetails.getCloudClusterConfig().getClusterName());
          return EksK8sInfraDelegateConfig.builder()
              .namespace(namespace)
              .cluster(releaseDetails.getCloudClusterConfig().getClusterName())
              .awsConnectorDTO((AwsConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(encryptedDataDetails)
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
      K8sInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest requestData) {
    long timeoutMillis =
        K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT);
    try {
      List<K8sPod> k8sPodList = k8sTaskHelperBase.getPodDetails(
          requestData.getKubernetesConfig(), requestData.getNamespace(), requestData.getReleaseName(), timeoutMillis);
      return K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(k8sPodList);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances, namespace: {}, releaseName: {}", requestData.getNamespace(),
          requestData.getReleaseName(), ex);
      return Collections.emptyList();
    }
  }
}
