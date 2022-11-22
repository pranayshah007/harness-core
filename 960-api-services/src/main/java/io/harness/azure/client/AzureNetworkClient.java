/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;

import com.azure.resourcemanager.network.models.LoadBalancer;
import com.azure.resourcemanager.network.models.LoadBalancerBackend;
import com.azure.resourcemanager.network.models.LoadBalancerProbe;
import com.azure.resourcemanager.network.models.LoadBalancerTcpProbe;
import com.azure.resourcemanager.network.models.LoadBalancingRule;
import java.util.List;
import java.util.Optional;

public interface AzureNetworkClient {
  /**
   * Get Load Balancer by name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param loadBalancerName
   * @return
   */
  Optional<LoadBalancer> getLoadBalancerByName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String loadBalancerName);

  /**
   * List load balancers by resource group.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @return
   */
  List<LoadBalancer> listLoadBalancersByResourceGroup(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName);

  /**
   * List load balancer backend pools.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param loadBalancerName
   * @return
   */
  List<LoadBalancerBackend> listLoadBalancerBackendPools(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String loadBalancerName);

  /**
   * List load balancer TCP probes.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param loadBalancerName
   * @return
   */
  List<LoadBalancerTcpProbe> listLoadBalancerTcpProbes(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String loadBalancerName);

  /**
   * List backend pool rules.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param loadBalancerName
   * @param backendPoolName
   * @return
   */
  List<LoadBalancingRule> listBackendPoolRules(AzureConfig azureConfig, String subscriptionId, String resourceGroupName,
      String loadBalancerName, String backendPoolName);

  /**
   * List backend pool probes.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param loadBalancerName
   * @param backendPoolName
   * @return
   */
  List<LoadBalancerProbe> listBackendPoolProbes(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String loadBalancerName, String backendPoolName);
}
