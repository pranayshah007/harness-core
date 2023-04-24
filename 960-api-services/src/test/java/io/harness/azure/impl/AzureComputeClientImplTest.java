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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureClient;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureHostConnectionType;
import io.harness.azure.model.AzureOSType;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.model.VirtualMachineData;
import io.harness.category.element.UnitTests;
import io.harness.network.Http;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

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
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.DeploymentSlots;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.azure.resourcemanager.appservice.models.WebApps;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.azure.resourcemanager.compute.models.LinuxConfiguration;
import com.azure.resourcemanager.compute.models.OSProfile;
import com.azure.resourcemanager.compute.models.PowerState;
import com.azure.resourcemanager.compute.models.UpgradeMode;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSet;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVM;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVMs;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSets;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.network.models.LoadBalancer;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.ResourceGroups;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Subscriptions;
import com.google.common.util.concurrent.TimeLimiter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.powermock.core.classloader.annotations.PrepareForTest;
import reactor.core.publisher.Mono;

@PrepareForTest({AzureResourceManager.class, AzureClient.class, Http.class, TimeLimiter.class})
public class AzureComputeClientImplTest extends CategoryTest {
  @Mock private AzureResourceManager.Configurable configurable;
  @Mock private AzureResourceManager.Authenticated authenticated;
  @Mock private AzureResourceManager azure;

  @InjectMocks private AzureComputeClientImpl azureComputeClient;

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
    Mockito.when(configurable.withRetryPolicy(any())).thenReturn(configurable);
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
  public void testListSubscriptions() {
    when(azure.subscriptions()).thenReturn(getSubscriptions());
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
    List<ResourceGroup> responseList = new ArrayList<>();
    responseList.add(resourceGroup);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);
    when(azure.resourceGroups()).thenReturn(resourceGroups);
    when(resourceGroups.list()).thenReturn(getPagedIterable(simpleResponse));

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

    List<WebApp> responseList = new ArrayList<>();
    responseList.add(webApp);
    responseList.add(webApp1);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);
    when(webApp.name()).thenReturn("test-web-app-1");
    when(webApp1.name()).thenReturn("test-web-app-2");

    when(azure.webApps()).thenReturn(webApps);
    when(webApps.listByResourceGroup(anyString())).thenReturn(getPagedIterable(simpleResponse));

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

    List<DeploymentSlot> responseList = new ArrayList<>();
    responseList.add(deploymentSlot);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    when(webApp.name()).thenReturn("test-web-app-1");
    when(deploymentSlot.name()).thenReturn("test-deployment-slot");
    when(azure.webApps()).thenReturn(webApps);
    when(webApps.getByResourceGroup(anyString(), anyString())).thenReturn(webApp);
    when(webApp.deploymentSlots()).thenReturn(deploymentSlots);
    when(deploymentSlots.list()).thenReturn(getPagedIterable(simpleResponse));

    List<WebDeploymentSlotBasic> response = azureComputeClient.listWebAppDeploymentSlots(
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

    List<VirtualMachineScaleSet> responseList = new ArrayList<>();
    responseList.add(virtualMachineScaleSet);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.listByResourceGroup("resourceGroupName")).thenReturn(getPagedIterable(simpleResponse));

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

    List<VirtualMachineScaleSetVM> responseList = new ArrayList<>();
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    when(azure.virtualMachineScaleSets()).thenReturn(virtualMachineScaleSets);
    when(virtualMachineScaleSets.getById("virtualMachineSetId")).thenReturn(virtualMachineScaleSet);
    when(virtualMachineScaleSet.virtualMachines()).thenReturn(virtualMachineScaleSetVMs);
    when(virtualMachineScaleSetVMs.list()).thenReturn(getPagedIterable(simpleResponse));

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
    VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer withPrimaryLoadBalancer =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer.class);
    VirtualMachineScaleSet.UpdateStages
        .WithPrimaryInternetFacingLoadBalancerBackendOrNatPool loadBalancerBackendOrNatPool =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryInternetFacingLoadBalancerBackendOrNatPool.class);
    VirtualMachineScaleSet.UpdateStages.WithPrimaryInternetFacingLoadBalancerNatPool loadBalancerNatPool =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryInternetFacingLoadBalancerNatPool.class);

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
    VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer primaryLoadBalancer =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer.class);
    VirtualMachineScaleSet.UpdateStages.WithApply withoutPrimaryLoadBalancerBackend =
        mock(VirtualMachineScaleSet.UpdateStages.WithApply.class);

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
    List<VirtualMachine> responseList = new ArrayList<>();
    responseList.add(aVirtualMachineWithName("vm-hostname-1"));
    responseList.add(aVirtualMachineWithName("vm-hostname-2"));
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    VirtualMachines mockedVirtualMachines = mock(VirtualMachines.class);
    when(mockedVirtualMachines.listByResourceGroup(eq("resourceGroup"))).thenReturn(getPagedIterable(simpleResponse));
    when(azure.virtualMachines()).thenReturn(mockedVirtualMachines);

    // When
    List<VirtualMachineData> result = azureComputeClient.listHosts(getAzureComputeConfig(), "subscriptionId",
        "resourceGroup", AzureOSType.LINUX, Collections.emptyMap(), AzureHostConnectionType.HOSTNAME);

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
    List<VirtualMachine> responseList = new ArrayList<>();
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    VirtualMachines mockedVirtualMachines = mock(VirtualMachines.class);
    when(mockedVirtualMachines.listByResourceGroup(eq("resourceGroup"))).thenReturn(getPagedIterable(simpleResponse));
    when(azure.virtualMachines()).thenReturn(mockedVirtualMachines);

    // When
    List<VirtualMachineData> result = azureComputeClient.listHosts(getAzureComputeConfig(), "subscriptionId",
        "resourceGroup", AzureOSType.LINUX, Collections.emptyMap(), AzureHostConnectionType.PRIVATE_IP);

    // Then
    assertThat(result).isNotNull().isEmpty();
  }

  private VirtualMachine aVirtualMachineWithName(String name) {
    final VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
    final PublicIpAddress publicIPAddress = Mockito.mock(PublicIpAddress.class);
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

  private Subscriptions getSubscriptions() {
    Subscription subscription = mock(Subscription.class);
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
