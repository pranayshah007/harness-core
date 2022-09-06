/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.HARNESS_AUTOSCALING_GROUP_TAG_NAME;
import static io.harness.azure.model.AzureConstants.NAME_TAG;
import static io.harness.azure.model.AzureConstants.VMSS_CREATED_TIME_STAMP_TAG_NAME;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import io.harness.CategoryTest;
import io.harness.azure.AzureClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureOSType;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.model.VirtualMachineData;
import io.harness.category.element.UnitTests;
import io.harness.network.Http;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.util.concurrent.TimeLimiter;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.DeploymentSlots;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.management.appservice.WebApps;
import com.microsoft.azure.management.compute.LinuxConfiguration;
import com.microsoft.azure.management.compute.OSProfile;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.UpgradeMode;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithApply;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithPrimaryInternetFacingLoadBalancerBackendOrNatPool;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithPrimaryInternetFacingLoadBalancerNatPool;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVMs;
import com.microsoft.azure.management.compute.VirtualMachineScaleSets;
import com.microsoft.azure.management.compute.VirtualMachines;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.network.PublicIPAddress;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.ResourceGroups;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.Subscriptions;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.RestException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Azure.class, AzureClient.class, Http.class, TimeLimiter.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class AzureComputeClientImplTest extends CategoryTest {
  @Mock private Azure.Configurable configurable;
  @Mock private Azure.Authenticated authenticated;
  @Mock private Azure azure;

  @InjectMocks private AzureComputeClientImpl azureComputeClient;

  @Before
  public void before() throws Exception {
    ApplicationTokenCredentials tokenCredentials = mock(ApplicationTokenCredentials.class);
    whenNew(ApplicationTokenCredentials.class).withAnyArguments().thenReturn(tokenCredentials);
    when(tokenCredentials.getToken(anyString())).thenReturn("tokenValue");
    mockStatic(Azure.class);
    when(Azure.configure()).thenReturn(configurable);
    when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
    when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
    when(authenticated.withSubscription(anyString())).thenReturn(azure);
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSubscriptions() throws Exception {
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
    Subscription subscription = mock(Subscription.class);
    Subscriptions subscriptions = mock(Subscriptions.class);
    PagedList<Subscription> pageList = getPageList();
    pageList.add(subscription);
    when(azure.subscriptions()).thenReturn(subscriptions);
    when(subscriptions.list()).thenReturn(pageList);

    List<Subscription> response = azureComputeClient.listSubscriptions(getAzureComputeConfig());

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListResourceGroupsNamesBySubscriptionId() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
    ResourceGroups resourceGroups = mock(ResourceGroups.class);
    ResourceGroup resourceGroup = mock(ResourceGroup.class);
    PagedList<ResourceGroup> pageList = getPageList();
    pageList.add(resourceGroup);
    when(azure.resourceGroups()).thenReturn(resourceGroups);
    when(resourceGroups.list()).thenReturn(pageList);

    List<String> response =
        azureComputeClient.listResourceGroupsNamesBySubscriptionId(getAzureComputeConfig(), "subscriptionId");

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesBySubscriptionIdAndResourceGroup() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
    WebApp webApp = mock(WebApp.class);
    WebApp webApp1 = mock(WebApp.class);
    WebApps webApps = mock(WebApps.class);

    PagedList<WebApp> pageList = getPageList();
    pageList.add(webApp);
    pageList.add(webApp1);
    when(webApp.name()).thenReturn("test-web-app-1");
    when(webApp1.name()).thenReturn("test-web-app-2");

    when(azure.webApps()).thenReturn(webApps);
    when(webApps.listByResourceGroup(anyString())).thenReturn(pageList);

    List<String> response = azureComputeClient.listWebAppNamesBySubscriptionIdAndResourceGroup(
        getAzureComputeConfig(), "subscriptionId", "resourceGroup");

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.get(0)).isEqualTo("test-web-app-1");
    assertThat(response.get(1)).isEqualTo("test-web-app-2");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlots() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
    WebApp webApp = mock(WebApp.class);
    WebApps webApps = mock(WebApps.class);

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    DeploymentSlots deploymentSlots = mock(DeploymentSlots.class);

    PagedList<DeploymentSlot> pageList = getPageList();
    pageList.add(deploymentSlot);

    when(webApp.name()).thenReturn("test-web-app-1");
    when(deploymentSlot.name()).thenReturn("test-deployment-slot");
    when(azure.webApps()).thenReturn(webApps);
    when(webApps.getByResourceGroup(anyString(), anyString())).thenReturn(webApp);
    when(webApp.deploymentSlots()).thenReturn(deploymentSlots);
    when(deploymentSlots.list()).thenReturn(pageList);

    List<DeploymentSlot> response = azureComputeClient.listWebAppDeploymentSlots(
        getAzureComputeConfig(), "subscriptionId", "resourceGroup", "webAppName");

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0).name()).isEqualTo("test-deployment-slot");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVirtualMachineScaleSetsById() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);

    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getById("virtualMachineSetId")).thenReturn(virtualMachineScaleSet);

    Optional<VirtualMachineScaleSet> response = azureComputeClient.getVirtualMachineScaleSetsById(
        getAzureComputeConfig(), "subscriptionId", "virtualMachineSetId");

    response.ifPresent(scaleSet -> assertThat(scaleSet).isNotNull());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVirtualMachineScaleSetsByName() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
    VirtualMachineScaleSet virtualMachineScaleSet =
        mockVirtualMachineScaleSet("resourceGroupName", "virtualMachineScaleSetName");

    Optional<VirtualMachineScaleSet> response = azureComputeClient.getVirtualMachineScaleSetByName(
        getAzureComputeConfig(), "subscriptionId", "resourceGroupName", "virtualMachineScaleSetName");

    response.ifPresent(vmm -> assertThat(vmm).isNotNull());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListVirtualMachineScaleSetsByResourceGroupName() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);

    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);

    PagedList<VirtualMachineScaleSet> pageList = getPageList();
    pageList.add(virtualMachineScaleSet);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.listByResourceGroup("resourceGroupName")).thenReturn(pageList);

    List<VirtualMachineScaleSet> response = azureComputeClient.listVirtualMachineScaleSetsByResourceGroupName(
        getAzureComputeConfig(), "subscriptionId", "resourceGroupName");

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteVirtualMachineScaleSetById() throws Exception {
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getById("virtualMachineScaleSet")).thenReturn(virtualMachineScaleSet);
    doNothing().when(virtualMachineScaleSets).deleteById("virtualMachineScaleSet");

    azureComputeClient.deleteVirtualMachineScaleSetById(
        getAzureComputeConfig(), "subscriptionId", "virtualMachineScaleSet");

    verify(azure, times(2)).virtualMachineScaleSets();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteVirtualMachineScaleSetByResourceGroupName() throws Exception {
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getByResourceGroup("resourceGroupName", "virtualMachineScaleSetName"))
        .thenReturn(virtualMachineScaleSet);

    doNothing().when(virtualMachineScaleSets).deleteByResourceGroup("resourceGroupName", "virtualMachineScaleSetName");

    azureComputeClient.deleteVirtualMachineScaleSetByResourceGroupName(
        getAzureComputeConfig(), "subscriptionId", "resourceGroupName", "virtualMachineScaleSetName");

    verify(azure, times(2)).virtualMachineScaleSets();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCheckIfAllVMSSInstancesAreInRunningState() throws Exception {
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);

    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSetVMs virtualMachineScaleSetVMs = mock(VirtualMachineScaleSetVMs.class);
    PagedList<VirtualMachineScaleSetVM> virtualMachineScaleSetVMPagedList = new PagedList<VirtualMachineScaleSetVM>() {
      @Override
      public Page<VirtualMachineScaleSetVM> nextPage(String s) throws RestException, IOException {
        return null;
      }
    };

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getById("virtualMachineSetId")).thenReturn(virtualMachineScaleSet);
    when(virtualMachineScaleSet.virtualMachines()).thenReturn(virtualMachineScaleSetVMs);
    when(virtualMachineScaleSetVMs.list()).thenReturn(virtualMachineScaleSetVMPagedList);

    assertThat(azureComputeClient.checkIsRequiredNumberOfVMInstances(
                   getAzureComputeConfig(), "subscriptionId", "virtualMachineSetId", 0))
        .isTrue();
    assertThat(virtualMachineScaleSet.virtualMachines().list()).isNotNull();
    verify(azure, times(1)).virtualMachineScaleSets();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetTagsForNewVMSS() throws Exception {
    VirtualMachineScaleSet virtualMachineScaleSet = Mockito.mock(VirtualMachineScaleSet.class);
    Mockito.when(virtualMachineScaleSet.tags()).thenReturn(new HashMap<String, String>() {
      {
        put(HARNESS_AUTOSCALING_GROUP_TAG_NAME, "infraMappingId__15");
        put("tag_name_1", "tag_value_1");
        put("tag_name_2", "tag_value_2");
      }
    });
    AzureVMSSTagsData azureVMSSTagsData =
        AzureVMSSTagsData.builder().harnessRevision(6).infraMappingId("infraMappingId").isBlueGreen(false).build();

    Map<String, String> result = azureComputeClient.getTagsForNewVMSS(
        virtualMachineScaleSet, "newVirtualMachineScaleSetName", azureVMSSTagsData);

    assertThat(result).isNotNull();
    assertThat(result.get("tag_name_1")).isEqualTo("tag_value_1");
    assertThat(result.get("tag_name_2")).isEqualTo("tag_value_2");
    assertThat(result.get(HARNESS_AUTOSCALING_GROUP_TAG_NAME)).isEqualTo("infraMappingId__6");
    assertThat(result.get(NAME_TAG)).isEqualTo("newVirtualMachineScaleSetName");
    assertThat(result.get(VMSS_CREATED_TIME_STAMP_TAG_NAME)).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAttachVMSSToBackendPools() throws IOException {
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);

    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String virtualMachineScaleSetName = "virtualMachineScaleSetName";
    String backendPools = "backendPools";

    LoadBalancer primaryInternetFacingLoadBalancer = mock(LoadBalancer.class);
    VirtualMachineScaleSet virtualMachineScaleSet =
        mockVirtualMachineScaleSet(resourceGroupName, virtualMachineScaleSetName);
    WithPrimaryLoadBalancer withPrimaryLoadBalancer = mock(WithPrimaryLoadBalancer.class);
    WithPrimaryInternetFacingLoadBalancerBackendOrNatPool loadBalancerBackendOrNatPool =
        mock(WithPrimaryInternetFacingLoadBalancerBackendOrNatPool.class);
    WithPrimaryInternetFacingLoadBalancerNatPool loadBalancerNatPool =
        mock(WithPrimaryInternetFacingLoadBalancerNatPool.class);

    when(virtualMachineScaleSet.name()).thenReturn(virtualMachineScaleSetName);
    when(virtualMachineScaleSet.update()).thenReturn(withPrimaryLoadBalancer);
    when(withPrimaryLoadBalancer.withExistingPrimaryInternetFacingLoadBalancer(primaryInternetFacingLoadBalancer))
        .thenReturn(loadBalancerBackendOrNatPool);
    when(loadBalancerBackendOrNatPool.withPrimaryInternetFacingLoadBalancerBackends(backendPools))
        .thenReturn(loadBalancerNatPool);
    when(loadBalancerNatPool.apply()).thenReturn(virtualMachineScaleSet);

    VirtualMachineScaleSet response = azureComputeClient.attachVMSSToBackendPools(getAzureComputeConfig(),
        primaryInternetFacingLoadBalancer, subscriptionId, resourceGroupName, virtualMachineScaleSetName, backendPools);

    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeAttachVMSSFromBackendPools() throws IOException {
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
    when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);

    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String virtualMachineScaleSetName = "virtualMachineScaleSetName";
    String backendPools = "backendPools";

    VirtualMachineScaleSet virtualMachineScaleSet =
        mockVirtualMachineScaleSet(resourceGroupName, virtualMachineScaleSetName);
    WithPrimaryLoadBalancer primaryLoadBalancer = mock(WithPrimaryLoadBalancer.class);
    WithApply withoutPrimaryLoadBalancerBackend = mock(WithApply.class);

    when(virtualMachineScaleSet.name()).thenReturn(virtualMachineScaleSetName);
    when(virtualMachineScaleSet.update()).thenReturn(primaryLoadBalancer);
    when(primaryLoadBalancer.withoutPrimaryInternetFacingLoadBalancerBackends(backendPools))
        .thenReturn(withoutPrimaryLoadBalancerBackend);
    when(withoutPrimaryLoadBalancerBackend.apply()).thenReturn(virtualMachineScaleSet);

    VirtualMachineScaleSet response = azureComputeClient.detachVMSSFromBackendPools(
        getAzureComputeConfig(), subscriptionId, resourceGroupName, virtualMachineScaleSetName, backendPools);

    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateVMInstances() {
    String instanceIds = "*";
    String virtualMachineScaleSetName = "virtualMachineScaleSetName";

    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSetVMs virtualMachines = mock(VirtualMachineScaleSetVMs.class);

    when(virtualMachineScaleSet.upgradeModel()).thenReturn(UpgradeMode.MANUAL);
    when(virtualMachineScaleSet.name()).thenReturn(virtualMachineScaleSetName);
    when(virtualMachineScaleSet.virtualMachines()).thenReturn(virtualMachines);
    doNothing().when(virtualMachines).updateInstances(instanceIds);

    azureComputeClient.updateVMInstances(virtualMachineScaleSet, instanceIds);

    verify(virtualMachines, times(1)).updateInstances(instanceIds);
  }

  @Test
  @Owner(developers = OwnerRule.FILIP)
  @Category(UnitTests.class)
  public void testListHosts() {
    // Given
    PagedList<VirtualMachine> pageList = getPageList();
    pageList.add(aVirtualMachineWithName("vm-hostname-1"));
    pageList.add(aVirtualMachineWithName("vm-hostname-2"));

    VirtualMachines mockedVirtualMachines = mock(VirtualMachines.class);
    when(mockedVirtualMachines.listByResourceGroup(eq("resourceGroup"))).thenReturn(pageList);
    when(azure.virtualMachines()).thenReturn(mockedVirtualMachines);

    // When
    List<VirtualMachineData> result = azureComputeClient.listHosts(
        getAzureComputeConfig(), "subscriptionId", "resourceGroup", AzureOSType.LINUX, Collections.emptyMap(), true);

    // Then
    assertThat(result)
        .isNotNull()
        .hasSize(2)
        .extracting(VirtualMachineData::getHostName)
        .containsExactlyInAnyOrder("vm-hostname-1", "vm-hostname-2");
  }

  @Test
  @Owner(developers = OwnerRule.FILIP)
  @Category(UnitTests.class)
  public void testListHostsNoHosts() {
    // Given
    PagedList<VirtualMachine> pageList = getPageList();

    VirtualMachines mockedVirtualMachines = mock(VirtualMachines.class);
    when(mockedVirtualMachines.listByResourceGroup(eq("resourceGroup"))).thenReturn(pageList);
    when(azure.virtualMachines()).thenReturn(mockedVirtualMachines);

    // When
    List<VirtualMachineData> result = azureComputeClient.listHosts(
        getAzureComputeConfig(), "subscriptionId", "resourceGroup", AzureOSType.LINUX, Collections.emptyMap(), true);

    // Then
    assertThat(result).isNotNull().isEmpty();
  }

  private VirtualMachine aVirtualMachineWithName(String name) {
    final VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
    final PublicIPAddress publicIPAddress = Mockito.mock(PublicIPAddress.class);
    Mockito.when(publicIPAddress.ipAddress()).thenReturn(name);

    Mockito.when(virtualMachine.name()).thenReturn(name);
    Mockito.when(virtualMachine.getPrimaryPublicIPAddress()).thenReturn(publicIPAddress);
    Mockito.when(virtualMachine.powerState()).thenReturn(PowerState.RUNNING);
    Mockito.when(virtualMachine.osProfile())
        .thenReturn(new OSProfile().withLinuxConfiguration(new LinuxConfiguration()));
    return virtualMachine;
  }

  public VirtualMachineScaleSet mockVirtualMachineScaleSet(
      String resourceGroupName, String virtualMachineScaleSetName) {
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSets virtualMachineScaleSets = mock(VirtualMachineScaleSets.class);
    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getByResourceGroup(resourceGroupName, virtualMachineScaleSetName))
        .thenReturn(virtualMachineScaleSet);
    return virtualMachineScaleSet;
  }

  @NotNull
  public <T> PagedList<T> getPageList() {
    return new PagedList<T>() {
      @Override
      public Page<T> nextPage(String s) {
        return new Page<T>() {
          @Override
          public String nextPageLink() {
            return null;
          }
          @Override
          public List<T> items() {
            return null;
          }
        };
      }
    };
  }

  private AzureConfig getAzureComputeConfig() {
    return AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
  }
}
