/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_RANCHER;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.perpetualtask.instancesync.helm.NativeHelmDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.k8s.KubernetesCloudClusterConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
@Slf4j
public class K8sAndHelmInfrastructureUtility {
  public K8sDeploymentReleaseDetails getK8sDeploymentReleaseDetails(DeploymentInfoDTO deploymentInfoDTO) {
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = (K8sDeploymentInfoDTO) deploymentInfoDTO;
    String subscriptionId = null;
    String resourceGroup = null;
    String clusterName = null;
    boolean useClusterAdminCredentials = false;
    if (k8sDeploymentInfoDTO.getCloudConfigMetadata() != null) {
      clusterName = k8sDeploymentInfoDTO.getCloudConfigMetadata().getClusterName();
      if (k8sDeploymentInfoDTO.getCloudConfigMetadata() instanceof K8sAzureCloudConfigMetadata) {
        K8sAzureCloudConfigMetadata k8sAzureCloudConfigMetadata =
            (K8sAzureCloudConfigMetadata) k8sDeploymentInfoDTO.getCloudConfigMetadata();
        subscriptionId = k8sAzureCloudConfigMetadata.getSubscription();
        resourceGroup = k8sAzureCloudConfigMetadata.getResourceGroup();
        useClusterAdminCredentials = k8sAzureCloudConfigMetadata.isUseClusterAdminCredentials();
      }
    }
    return K8sDeploymentReleaseDetails.builder()
        .releaseName(k8sDeploymentInfoDTO.getReleaseName())
        .namespaces(k8sDeploymentInfoDTO.getNamespaces())
        .k8sCloudClusterConfig(KubernetesCloudClusterConfig.builder()
                                   .clusterName(clusterName)
                                   .subscriptionId(subscriptionId)
                                   .resourceGroup(resourceGroup)
                                   .useClusterAdminCredentials(useClusterAdminCredentials)
                                   .build())
        .build();
  }

  public NativeHelmDeploymentReleaseDetails getNativeHelmDeploymentReleaseDetails(DeploymentInfoDTO deploymentInfoDTO) {
    NativeHelmDeploymentInfoDTO nativeHelmDeploymentInfoDTO = (NativeHelmDeploymentInfoDTO) deploymentInfoDTO;
    String subscriptionId = null;
    String resourceGroup = null;
    String clusterName = null;
    boolean useClusterAdminCredentials = false;
    if (nativeHelmDeploymentInfoDTO.getCloudConfigMetadata() != null) {
      clusterName = nativeHelmDeploymentInfoDTO.getCloudConfigMetadata().getClusterName();
      if (nativeHelmDeploymentInfoDTO.getCloudConfigMetadata() instanceof K8sAzureCloudConfigMetadata) {
        K8sAzureCloudConfigMetadata k8sAzureCloudConfigMetadata =
            (K8sAzureCloudConfigMetadata) nativeHelmDeploymentInfoDTO.getCloudConfigMetadata();
        subscriptionId = k8sAzureCloudConfigMetadata.getSubscription();
        resourceGroup = k8sAzureCloudConfigMetadata.getResourceGroup();
        useClusterAdminCredentials = k8sAzureCloudConfigMetadata.isUseClusterAdminCredentials();
      }
    }
    return NativeHelmDeploymentReleaseDetails.builder()
        .releaseName(nativeHelmDeploymentInfoDTO.getReleaseName())
        .namespaces(nativeHelmDeploymentInfoDTO.getNamespaces())
        .k8sCloudClusterConfig(KubernetesCloudClusterConfig.builder()
                                   .clusterName(clusterName)
                                   .subscriptionId(subscriptionId)
                                   .resourceGroup(resourceGroup)
                                   .useClusterAdminCredentials(useClusterAdminCredentials)
                                   .build())
        .helmVersion(nativeHelmDeploymentInfoDTO.getHelmVersion().toString())
        .build();
  }

  public InfrastructureOutcome getInfrastructureOutcome(
      String infrastructureKind, DeploymentInfoDTO deploymentInfoDTO, String connectorRef) {
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = (K8sDeploymentInfoDTO) deploymentInfoDTO;
    String namespace = k8sDeploymentInfoDTO.getNamespaces().stream().findAny().isPresent()
        ? k8sDeploymentInfoDTO.getNamespaces().stream().findAny().get()
        : DEFAULT_NAMESPACE;
    switch (infrastructureKind) {
      case KUBERNETES_DIRECT:
        return K8sDirectInfrastructureOutcome.builder()
            .releaseName(k8sDeploymentInfoDTO.getReleaseName())
            .connectorRef(connectorRef)
            .namespace(namespace)
            .build();
      case KUBERNETES_GCP:
        return K8sGcpInfrastructureOutcome.builder()
            .releaseName(k8sDeploymentInfoDTO.getReleaseName())
            .connectorRef(connectorRef)
            .cluster(k8sDeploymentInfoDTO.getCloudConfigMetadata().getClusterName())
            .namespace(namespace)
            .build();
      case KUBERNETES_AZURE:
        K8sAzureCloudConfigMetadata azureCloudConfigMetadata =
            (K8sAzureCloudConfigMetadata) k8sDeploymentInfoDTO.getCloudConfigMetadata();
        return K8sAzureInfrastructureOutcome.builder()
            .releaseName(k8sDeploymentInfoDTO.getReleaseName())
            .connectorRef(connectorRef)
            .resourceGroup(azureCloudConfigMetadata.getResourceGroup())
            .subscription(azureCloudConfigMetadata.getSubscription())
            .useClusterAdminCredentials(azureCloudConfigMetadata.isUseClusterAdminCredentials())
            .cluster(k8sDeploymentInfoDTO.getCloudConfigMetadata().getClusterName())
            .namespace(namespace)
            .build();
      case KUBERNETES_AWS:
        return K8sAwsInfrastructureOutcome.builder()
            .releaseName(k8sDeploymentInfoDTO.getReleaseName())
            .connectorRef(connectorRef)
            .cluster(k8sDeploymentInfoDTO.getCloudConfigMetadata().getClusterName())
            .namespace(namespace)
            .build();
      case KUBERNETES_RANCHER:
        return K8sRancherInfrastructureOutcome.builder()
            .releaseName(k8sDeploymentInfoDTO.getReleaseName())
            .connectorRef(connectorRef)
            .clusterName(k8sDeploymentInfoDTO.getCloudConfigMetadata().getClusterName())
            .namespace(namespace)
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported outcome for infrastructure kind: [%s]", infrastructureKind));
    }
  }

  public K8sCloudConfigMetadata getK8sCloudConfigMetadata(InfrastructureOutcome infrastructureOutcome) {
    return infrastructureOutcome.getInfraOutcomeMetadata();
  }
}
