/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.BACKEND_POOL_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.LOAD_BALANCER_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.model.AzureConfig;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.network.models.LoadBalancer;
import com.azure.resourcemanager.network.models.LoadBalancerBackend;
import com.azure.resourcemanager.network.models.LoadBalancerProbe;
import com.azure.resourcemanager.network.models.LoadBalancerTcpProbe;
import com.azure.resourcemanager.network.models.LoadBalancingRule;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
public class AzureNetworkClientImpl extends AzureClient implements AzureNetworkClient {
  @Override
  public Optional<LoadBalancer> getLoadBalancerByName(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String loadBalancerName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(loadBalancerName)) {
      throw new IllegalArgumentException(LOAD_BALANCER_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start getting load balancer by resourceGroupName: {}, loadBalancerName: {}", resourceGroupName,
        loadBalancerName);
    LoadBalancer loadBalancer = azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName);

    if (isNull(loadBalancer)) {
      return Optional.empty();
    }

    return Optional.of(loadBalancer);
  }

  @Override
  public List<LoadBalancer> listLoadBalancersByResourceGroup(
      AzureConfig azureConfig, final String subscriptionId, final String resourceGroupName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing load balancers by resourceGroupName {}", resourceGroupName);
    return azure.loadBalancers().listByResourceGroup(resourceGroupName).stream().collect(Collectors.toList());
  }

  @Override
  public List<LoadBalancerBackend> listLoadBalancerBackendPools(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String loadBalancerName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(loadBalancerName)) {
      throw new IllegalArgumentException(LOAD_BALANCER_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing load balancer backend pools by resourceGroupName {}, loadBalancerName: {}",
        resourceGroupName, loadBalancerName);
    LoadBalancer loadBalancer = azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName);
    if (loadBalancer == null) {
      return Collections.emptyList();
    }

    return new ArrayList<>(loadBalancer.backends().values());
  }

  @Override
  public List<LoadBalancerTcpProbe> listLoadBalancerTcpProbes(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String loadBalancerName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(loadBalancerName)) {
      throw new IllegalArgumentException(LOAD_BALANCER_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing load balancer TCP probes for loadBalancerName {}, resourceGroupName: {}", loadBalancerName,
        resourceGroupName);
    LoadBalancer loadBalancer = azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName);
    if (loadBalancer == null) {
      return Collections.emptyList();
    }

    return new ArrayList<>(loadBalancer.tcpProbes().values());
  }

  @Override
  public List<LoadBalancingRule> listBackendPoolRules(AzureConfig azureConfig, String subscriptionId,
      final String resourceGroupName, final String loadBalancerName, final String backendPoolName) {
    Optional<LoadBalancerBackend> loadBalancerBackendPoolOp =
        getLoadBalancerBackendPool(azureConfig, subscriptionId, resourceGroupName, loadBalancerName, backendPoolName);

    if (!loadBalancerBackendPoolOp.isPresent()) {
      return Collections.emptyList();
    }

    log.debug("Start listing backend pool rules for backendPoolName {}, loadBalancerName {}, resourceGroupName: {}",
        backendPoolName, loadBalancerName, resourceGroupName);
    return new ArrayList<>(loadBalancerBackendPoolOp.get().loadBalancingRules().values());
  }

  @Override
  public List<LoadBalancerProbe> listBackendPoolProbes(AzureConfig azureConfig, String subscriptionId,
      final String resourceGroupName, final String loadBalancerName, final String backendPoolName) {
    Optional<LoadBalancerBackend> loadBalancerBackendPoolOp =
        getLoadBalancerBackendPool(azureConfig, subscriptionId, resourceGroupName, loadBalancerName, backendPoolName);

    log.debug("Start listing backend pool probes for backendPoolName {}, loadBalancerName {}, resourceGroupName: {}",
        backendPoolName, loadBalancerName, resourceGroupName);
    return loadBalancerBackendPoolOp.map(this::toLoadBalancerBackendProbes).orElse(Collections.emptyList());
  }

  @NotNull
  private List<LoadBalancerProbe> toLoadBalancerBackendProbes(LoadBalancerBackend loadBalancerBackend) {
    return loadBalancerBackend.loadBalancingRules()
        .values()
        .stream()
        .map(LoadBalancingRule::probe)
        .collect(Collectors.toList());
  }

  public Optional<LoadBalancerBackend> getLoadBalancerBackendPool(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String loadBalancerName, String backendPoolName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(loadBalancerName)) {
      throw new IllegalArgumentException(LOAD_BALANCER_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(backendPoolName)) {
      throw new IllegalArgumentException(BACKEND_POOL_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug(
        "Start getting load balancer backend pool, backendPoolName {}, loadBalancerName {}, resourceGroupName: {}",
        backendPoolName, loadBalancerName, resourceGroupName);
    LoadBalancerBackend loadBalancerBackendPool =
        azure.loadBalancers().getByResourceGroup(resourceGroupName, loadBalancerName).backends().get(backendPoolName);

    if (loadBalancerBackendPool == null) {
      return Optional.empty();
    }
    return Optional.of(loadBalancerBackendPool);
  }
}
