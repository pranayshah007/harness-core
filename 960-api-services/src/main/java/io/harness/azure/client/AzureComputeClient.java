/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureMachineImageArtifact;
import io.harness.azure.model.AzureOSType;
import io.harness.azure.model.AzureUserAuthVMInstanceData;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.model.VirtualMachineData;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.compute.GalleryImage;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNetworkInterface;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.resources.Subscription;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AzureComputeClient {
  /**
   * Get Virtual Machine Scale Set by Id.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param virtualMachineScaleSetId
   * @return
   */
  Optional<VirtualMachineScaleSet> getVirtualMachineScaleSetsById(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId);

  /**
   * Get Virtual Machine Scale Set by name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param virtualMachineScaleSetName
   * @return
   */
  Optional<VirtualMachineScaleSet> getVirtualMachineScaleSetByName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName);

  /**
   * List Virtual Machine Scale Sets by Resource Group Name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @return
   */
  List<VirtualMachineScaleSet> listVirtualMachineScaleSetsByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName);

  /**
   * Delete Virtual Machine Scale Set by Resource Group Name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param virtualScaleSetName
   */
  void deleteVirtualMachineScaleSetByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualScaleSetName);

  /**
   * Delete Virtual Machine Scale Set by Id.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param virtualMachineScaleSetId
   */
  void deleteVirtualMachineScaleSetById(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId);

  /**
   * Bulk delete Virtual Machine Scale Sets by Ids.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param vmssIds
   */
  void bulkDeleteVirtualMachineScaleSets(AzureConfig azureConfig, String subscriptionId, List<String> vmssIds);

  /**
   * List VMs of Virtual Machine Scale Set.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param virtualMachineScaleSetName
   * @return
   */
  List<VirtualMachineScaleSetVM> listVirtualMachineScaleSetVMs(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName);

  /**
   * List VMs of Virtual Machine Scale Set.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param virtualMachineScaleSetId
   * @return
   */
  List<VirtualMachineScaleSetVM> listVirtualMachineScaleSetVMs(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId);

  /**
   * List subscriptions.
   *
   * @param azureConfig
   * @return
   */
  List<Subscription> listSubscriptions(AzureConfig azureConfig);

  /**
   * List Web App Names by Subscription Id and ResourceGroup.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroup
   * @return
   */
  List<String> listWebAppNamesBySubscriptionIdAndResourceGroup(
      AzureConfig azureConfig, String subscriptionId, String resourceGroup);

  /**
   * List Web App Deployment slots by Subscription Id, ResourceGroup and Web App name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroup
   * @param webAppName
   * @return
   */
  List<DeploymentSlot> listWebAppDeploymentSlots(
      AzureConfig azureConfig, String subscriptionId, String resourceGroup, String webAppName);

  /**
   * List Resource Groups names by Subscription Id
   *
   * @param azureConfig
   * @param subscriptionId
   * @return
   */
  List<String> listResourceGroupsNamesBySubscriptionId(AzureConfig azureConfig, String subscriptionId);

  /**
   * Check if all VMSS Instances are stopped.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param virtualMachineScaleSetId
   * @param numberOfVMInstances
   * @return
   */
  boolean checkIsRequiredNumberOfVMInstances(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId, int numberOfVMInstances);

  /**
   * Update Virtual Machine Scale Set capacity.
   *
   * @param azureConfig
   * @param virtualMachineScaleSetName
   * @param subscriptionId
   * @param resourceGroupName
   * @param limit
   * @return
   */
  VirtualMachineScaleSet updateVMSSCapacity(AzureConfig azureConfig, String virtualMachineScaleSetName,
      String subscriptionId, String resourceGroupName, int limit);

  /**
   * Create a new Virtual Machine Scale Set based on base scale set.
   * @param azureConfig
   * @param subscriptionId
   * @param baseVirtualMachineScaleSet
   * @param newVirtualMachineScaleSetName
   * @param azureUserAuthVMInstanceData
   * @param imageArtifact
   * @param tags
   */
  void createVirtualMachineScaleSet(AzureConfig azureConfig, String subscriptionId,
      VirtualMachineScaleSet baseVirtualMachineScaleSet, String newVirtualMachineScaleSetName,
      AzureUserAuthVMInstanceData azureUserAuthVMInstanceData, AzureMachineImageArtifact imageArtifact,
      AzureVMSSTagsData tags);

  /**
   * Attach virtual machine scale set to load balancer backend pools.
   *
   * @param azureConfig
   * @param primaryInternetFacingLoadBalancer
   * @param subscriptionId
   * @param resourceGroupName
   * @param virtualMachineScaleSetName
   * @param backendPools
   * @return
   */
  VirtualMachineScaleSet attachVMSSToBackendPools(AzureConfig azureConfig,
      LoadBalancer primaryInternetFacingLoadBalancer, String subscriptionId, String resourceGroupName,
      String virtualMachineScaleSetName, String... backendPools);

  /**
   * Attach virtual machine scale set to load balancer backend pools.
   *
   * @param azureConfig
   * @param virtualMachineScaleSet
   * @param primaryInternetFacingLoadBalancer
   * @param backendPools
   * @return
   */
  VirtualMachineScaleSet attachVMSSToBackendPools(AzureConfig azureConfig,
      VirtualMachineScaleSet virtualMachineScaleSet, LoadBalancer primaryInternetFacingLoadBalancer,
      String... backendPools);

  /**
   * De-attache virtual machine scale set from load balancer backend pools.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param virtualMachineScaleSetName
   * @param backendPools
   * @return
   */
  VirtualMachineScaleSet detachVMSSFromBackendPools(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String virtualMachineScaleSetName, String... backendPools);

  /**
   * De-attache virtual machine scale set from load balancer backend pools.
   *
   * @param azureConfig
   * @param virtualMachineScaleSet
   * @param backendPools
   * @return
   */
  VirtualMachineScaleSet detachVMSSFromBackendPools(
      AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet, String... backendPools);

  /**
   * Update virtual machine scale set VM instances to apply the latest network or configuration changes.
   *
   * @param virtualMachineScaleSet
   * @param instanceIds
   */
  void updateVMInstances(VirtualMachineScaleSet virtualMachineScaleSet, String... instanceIds);

  /**
   * Attach virtual machine scale set to load balancer backend pools and update VMSS VMs to apply network changes
   * immediately. This is time consuming operation.
   *
   * @param azureConfig
   * @param virtualMachineScaleSet
   * @param backendPools
   */
  void forceDeAttachVMSSFromBackendPools(
      AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet, String... backendPools);

  /**
   * De-attach virtual machine scale set from load balancer backend pools and update VMSS VMs to apply network changes
   * immediately. This is time consuming operation.
   *
   * @param azureConfig
   * @param virtualMachineScaleSet
   * @param primaryInternetFacingLoadBalancer
   * @param backendPools
   */
  void forceAttachVMSSToBackendPools(AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet,
      LoadBalancer primaryInternetFacingLoadBalancer, String... backendPools);

  /**
   * Get Virtual Machine public IP address inner object for the network interface first in the list.
   *
   * @param vm
   * @return
   */
  Optional<PublicIPAddressInner> getVMPublicIPAddress(VirtualMachineScaleSetVM vm);

  /**
   * List Virtual Machine network interfaces.
   *
   * @param vm
   * @return
   */
  List<VirtualMachineScaleSetNetworkInterface> listVMVirtualMachineScaleSetNetworkInterfaces(
      VirtualMachineScaleSetVM vm);

  /**
   * Get Gallery image.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param galleryName
   * @param imageName
   * @return
   */
  Optional<GalleryImage> getGalleryImage(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String galleryName, String imageName);

  /**
   * List hosts for resource group by os type and tags
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroup
   * @param osType
   * @param tags
   * @return
   */
  List<VirtualMachineData> listHosts(AzureConfig azureConfig, String subscriptionId, String resourceGroup,
      AzureOSType osType, Map<String, String> tags);
}
