/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.network.models.LoadBalancer;
import com.azure.resourcemanager.network.models.LoadBalancerBackend;
import com.azure.resourcemanager.network.models.LoadBalancerProbe;
import com.azure.resourcemanager.network.models.LoadBalancerTcpProbe;
import com.azure.resourcemanager.network.models.LoadBalancers;
import com.azure.resourcemanager.network.models.LoadBalancingRule;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Subscriptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import reactor.core.publisher.Mono;

public class AzureNetworkClientImplTest extends CategoryTest {
  @Mock private AzureResourceManager.Configurable configurable;
  @Mock private AzureResourceManager.Authenticated authenticated;
  @Mock private AzureResourceManager azure;

  @InjectMocks AzureNetworkClientImpl azureNetworkClient;

  @Before
  public void before() throws Exception {
    MockitoAnnotations.openMocks(this);

    ClientSecretCredential clientSecretCredential = Mockito.mock(ClientSecretCredential.class);
    PowerMockito.whenNew(ClientSecretCredential.class).withAnyArguments().thenReturn(clientSecretCredential);

    AccessToken accessToken = Mockito.mock(AccessToken.class);
    PowerMockito.whenNew(AccessToken.class).withAnyArguments().thenReturn(accessToken);

    Mockito.when(clientSecretCredential.getToken(any())).thenReturn(Mono.just(accessToken));

    MockedStatic<AzureResourceManager> azureResourceManagerMockedStatic = mockStatic(AzureResourceManager.class);

    azureResourceManagerMockedStatic.when(() -> AzureResourceManager.configure()).thenReturn(configurable);
    azureResourceManagerMockedStatic
        .when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
        .thenReturn(authenticated);

    Mockito.when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
    Mockito.when(configurable.withHttpClient(any())).thenReturn(configurable);
    when(configurable.withRetryPolicy(any())).thenReturn(configurable);
    Mockito.when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    Mockito.when(configurable.authenticate(any(ClientSecretCredential.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    Mockito.when(authenticated.subscriptions()).thenReturn(getSubscriptions());
    Mockito.when(authenticated.withSubscription(any())).thenReturn(azure);
    Mockito.when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLoadBalancersByResourceGroup() {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";

    LoadBalancers loadBalancers = mock(LoadBalancers.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    List<LoadBalancer> responseList = new ArrayList<>();
    responseList.add(loadBalancer);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    when(azure.loadBalancers()).thenReturn(loadBalancers);
    when(loadBalancers.listByResourceGroup(resourceGroupName)).thenReturn(getPagedIterable(simpleResponse));

    List<LoadBalancer> response =
        azureNetworkClient.listLoadBalancersByResourceGroup(getAzureComputeConfig(), subscriptionId, resourceGroupName);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(LoadBalancer.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLoadBalancerBackendPools() {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";

    LoadBalancers loadBalancers = mock(LoadBalancers.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    LoadBalancerBackend loadBalancerBackend = mock(LoadBalancerBackend.class);

    when(azure.loadBalancers()).thenReturn(loadBalancers);
    when(loadBalancers.getByResourceGroup(resourceGroupName, loadBalancerName)).thenReturn(loadBalancer);
    when(loadBalancer.backends()).thenReturn(new HashMap<String, LoadBalancerBackend>() {
      { put("loadBalancerBackendKey", loadBalancerBackend); }
    });

    List<LoadBalancerBackend> response = azureNetworkClient.listLoadBalancerBackendPools(
        getAzureComputeConfig(), subscriptionId, resourceGroupName, loadBalancerName);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(LoadBalancerBackend.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLoadBalancerTcpProbes() {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";

    LoadBalancers loadBalancers = mock(LoadBalancers.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    LoadBalancerTcpProbe loadBalancerTcpProbe = mock(LoadBalancerTcpProbe.class);

    when(azure.loadBalancers()).thenReturn(loadBalancers);
    when(loadBalancers.getByResourceGroup(resourceGroupName, loadBalancerName)).thenReturn(loadBalancer);
    when(loadBalancer.tcpProbes()).thenReturn(new HashMap<String, LoadBalancerTcpProbe>() {
      { put("loadBalancerTcpProbeKey", loadBalancerTcpProbe); }
    });

    List<LoadBalancerTcpProbe> response = azureNetworkClient.listLoadBalancerTcpProbes(
        getAzureComputeConfig(), subscriptionId, resourceGroupName, loadBalancerName);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(LoadBalancerTcpProbe.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListBackendPoolRules() {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";
    String backendPoolName = "backendPoolName";

    LoadBalancers loadBalancers = mock(LoadBalancers.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    LoadBalancerBackend loadBalancerBackend = mock(LoadBalancerBackend.class);
    LoadBalancingRule loadBalancingRule = mock(LoadBalancingRule.class);

    when(azure.loadBalancers()).thenReturn(loadBalancers);
    when(loadBalancers.getByResourceGroup(resourceGroupName, loadBalancerName)).thenReturn(loadBalancer);
    when(loadBalancer.backends()).thenReturn(new HashMap<String, LoadBalancerBackend>() {
      { put(backendPoolName, loadBalancerBackend); }
    });
    when(loadBalancerBackend.loadBalancingRules()).thenReturn(new HashMap<String, LoadBalancingRule>() {
      { put("loadBalancingRule", loadBalancingRule); }
    });

    List<LoadBalancingRule> response = azureNetworkClient.listBackendPoolRules(
        getAzureComputeConfig(), subscriptionId, resourceGroupName, loadBalancerName, backendPoolName);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(LoadBalancingRule.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListBackendPoolProbes() {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String loadBalancerName = "loadBalancerName";
    String backendPoolName = "backendPoolName";

    LoadBalancers loadBalancers = mock(LoadBalancers.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    LoadBalancerBackend loadBalancerBackend = mock(LoadBalancerBackend.class);
    LoadBalancingRule loadBalancingRule = mock(LoadBalancingRule.class);
    LoadBalancerProbe loadBalancerProbe = mock(LoadBalancerProbe.class);

    when(azure.loadBalancers()).thenReturn(loadBalancers);
    when(loadBalancers.getByResourceGroup(resourceGroupName, loadBalancerName)).thenReturn(loadBalancer);
    when(loadBalancer.backends()).thenReturn(new HashMap<String, LoadBalancerBackend>() {
      { put(backendPoolName, loadBalancerBackend); }
    });
    when(loadBalancerBackend.loadBalancingRules()).thenReturn(new HashMap<String, LoadBalancingRule>() {
      { put("loadBalancingRule", loadBalancingRule); }
    });
    when(loadBalancingRule.probe()).thenReturn(loadBalancerProbe);

    List<LoadBalancerProbe> response = azureNetworkClient.listBackendPoolProbes(
        getAzureComputeConfig(), subscriptionId, resourceGroupName, loadBalancerName, backendPoolName);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(LoadBalancerProbe.class);
  }

  private Subscriptions getSubscriptions() {
    Subscription subscription = PowerMockito.mock(Subscription.class);
    List<Subscription> responseList = new ArrayList<>();
    responseList.add(subscription);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    return new Subscriptions() {
      @Override
      public Subscription getById(String s) {
        return null;
      }

      @Override
      public Mono<Subscription> getByIdAsync(String s) {
        return null;
      }

      @Override
      public PagedIterable<Subscription> list() {
        return getPagedIterable(simpleResponse);
      }

      @Override
      public PagedFlux<Subscription> listAsync() {
        return null;
      }
    };
  }

  @NotNull
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }

  private AzureConfig getAzureComputeConfig() {
    return AzureConfig.builder()
        .clientId("clientId")
        .tenantId("tenantId")
        .key("key".toCharArray())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }
}
