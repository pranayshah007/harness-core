/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureKubeconfigFormat;

import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import java.util.List;

public interface AzureKubernetesClient extends AzureResourceClient {
  /**
   * List Kubernetes clusters by subscription id.
   *
   * @param azureConfig Azure config
   * @param subscriptionId subscription id
   * @return list of Kubernetes clusters
   */
  List<KubernetesCluster> listKubernetesClusters(AzureConfig azureConfig, String subscriptionId);

  String getClusterCredentials(AzureConfig azureConfig, String accessToken, String subscriptionId, String resourceGroup,
      String aksClusterName, boolean shouldGetAdminCredentials, AzureKubeconfigFormat azureKubeconfigFormat);
}
