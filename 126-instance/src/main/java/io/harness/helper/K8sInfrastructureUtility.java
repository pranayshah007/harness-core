/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.k8s.KubernetesCloudClusterConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
@Slf4j
public class K8sInfrastructureUtility {
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

  public K8sCloudConfigMetadata getK8sCloudConfigMetadata(InfrastructureOutcome infrastructureOutcome) {
    return infrastructureOutcome.getInfraOutcomeMetadata();
  }
}
