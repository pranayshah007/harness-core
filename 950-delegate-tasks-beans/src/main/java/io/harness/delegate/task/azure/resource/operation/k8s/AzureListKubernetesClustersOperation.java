/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.resource.operation.k8s;

import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.client.AzureResourceClient;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.resource.operation.AzureOperationName;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperation;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperationResponse;
import io.harness.delegate.task.azure.resource.operation.AzureResourceProvider;

import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@EqualsAndHashCode
@Slf4j
public class AzureListKubernetesClustersOperation implements AzureResourceOperation {
  private String subscriptionId;

  @Override
  public AzureResourceProvider getAzureResourceProvider() {
    return AzureResourceProvider.KUBERNETES;
  }

  @Override
  public AzureOperationName getOperationName() {
    return AzureOperationName.LIST_KUBERNETES_CLUSTERS;
  }

  @Override
  public <T extends AzureResourceClient> AzureResourceOperationResponse executeOperation(
      T resourceClient, AzureConfig azureConfig) {
    List<KubernetesCluster> kubernetesClusters =
        ((AzureKubernetesClient) resourceClient).listKubernetesClusters(azureConfig, subscriptionId);

    log.info("Start executing operation on delegate, operationName: {}, operationDetails: {}",
        getOperationName().getValue(), this);
    List<AzureKubernetesClusterDTO> kubernetesClusterList =
        kubernetesClusters.stream().map(this::toAzureKubernetesCluster).collect(Collectors.toList());
    return AzureListKubernetesClustersOperationResponse.builder().kubernetesClusterList(kubernetesClusterList).build();
  }

  private AzureKubernetesClusterDTO toAzureKubernetesCluster(KubernetesCluster kubernetesCluster) {
    return AzureKubernetesClusterDTO.builder()
        .name(kubernetesCluster.name())
        .resourceGroup(kubernetesCluster.resourceGroupName())
        .subscriptionId(subscriptionId)
        .type(kubernetesCluster.type())
        .id(kubernetesCluster.id())
        .build();
  }
}
