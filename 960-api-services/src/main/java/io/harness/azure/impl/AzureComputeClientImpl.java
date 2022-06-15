/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.AZURE_MANAGEMENT_CLIENT_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BACKEND_POOLS_LIST_EMPTY_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BASE_VIRTUAL_MACHINE_SCALE_SET_IS_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.BG_GREEN_TAG_VALUE;
import static io.harness.azure.model.AzureConstants.BG_VERSION_TAG_NAME;
import static io.harness.azure.model.AzureConstants.GALLERY_IMAGE_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.GALLERY_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.HARNESS_AUTOSCALING_GROUP_TAG_NAME;
import static io.harness.azure.model.AzureConstants.NAME_TAG;
import static io.harness.azure.model.AzureConstants.NEW_VIRTUAL_MACHINE_SCALE_SET_NAME_IS_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.NUMBER_OF_VM_INSTANCES_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.PRIMARY_INTERNET_FACING_LOAD_BALANCER_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VIRTUAL_MACHINE_SCALE_SET_ID_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VIRTUAL_MACHINE_SCALE_SET_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VIRTUAL_SCALE_SET_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VMSS_AUTH_TYPE_DEFAULT;
import static io.harness.azure.model.AzureConstants.VMSS_AUTH_TYPE_SSH_PUBLIC_KEY;
import static io.harness.azure.model.AzureConstants.VMSS_CREATED_TIME_STAMP_TAG_NAME;
import static io.harness.azure.model.AzureConstants.VMSS_IDS_IS_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VM_INSTANCE_IDS_LIST_EMPTY_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VM_INSTANCE_IDS_NOT_NUMBERS_VALIDATION_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureMachineImageArtifact;
import io.harness.azure.model.AzureUserAuthVMInstanceData;
import io.harness.azure.model.AzureVMSSTagsData;
import io.harness.azure.model.image.AzureMachineImage;
import io.harness.azure.model.image.AzureMachineImageFactory;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.management.compute.GalleryImage;
import com.microsoft.azure.management.compute.SshConfiguration;
import com.microsoft.azure.management.compute.SshPublicKey;
import com.microsoft.azure.management.compute.UpgradeMode;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetOSProfile;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.compute.implementation.VirtualMachineScaleSetInner;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNetworkInterface;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import io.fabric8.utils.Objects;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
public class AzureComputeClientImpl extends AzureClient implements AzureComputeClient {
  @Override
  public List<VirtualMachineScaleSet> listVirtualMachineScaleSetsByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start getting Virtual Machine Scale Sets by resourceGroupName: {}, subscriptionId: {}",
        resourceGroupName, subscriptionId);
    Instant startListingVMSS = Instant.now();
    PagedList<VirtualMachineScaleSet> virtualMachineScaleSets =
        azure.virtualMachineScaleSets().listByResourceGroup(resourceGroupName);

    // Lazy listing https://github.com/Azure/azure-sdk-for-java/issues/860
    List<VirtualMachineScaleSet> virtualMachineScaleSetsList = new ArrayList<>(virtualMachineScaleSets);

    long elapsedTime = Duration.between(startListingVMSS, Instant.now()).toMillis();
    log.info(
        "Obtained Virtual Machine Scale Sets items: {} for elapsed time: {}, resourceGroupName: {}, subscriptionId: {} ",
        virtualMachineScaleSetsList.size(), elapsedTime, resourceGroupName, subscriptionId);

    return virtualMachineScaleSetsList;
  }

  @Override
  public void deleteVirtualMachineScaleSetByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualScaleSetName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(virtualScaleSetName)) {
      throw new IllegalArgumentException(VIRTUAL_SCALE_SET_NAME_NULL_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    Objects.notNull(azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, virtualScaleSetName),
        format("There is no virtual machine scale set with name %s", virtualScaleSetName));

    log.debug("Start deleting Virtual Machine Scale Sets by resourceGroupName: {}", resourceGroupName);
    azure.virtualMachineScaleSets().deleteByResourceGroup(resourceGroupName, virtualScaleSetName);
  }

  @Override
  public void bulkDeleteVirtualMachineScaleSets(AzureConfig azureConfig, String subscriptionId, List<String> vmssIDs) {
    Objects.notNull(vmssIDs, VMSS_IDS_IS_NULL_VALIDATION_MSG);
    if (vmssIDs.isEmpty()) {
      return;
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start bulk deleting Virtual Machine Scale Sets, ids: {}", vmssIDs);
    azure.virtualMachineScaleSets().deleteByIds(vmssIDs);
  }

  @Override
  public List<VirtualMachineScaleSetVM> listVirtualMachineScaleSetVMs(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName) {
    Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp =
        getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);
    return virtualMachineScaleSetOp.map(this::getVirtualMachineScaleSetVMs).orElse(Collections.emptyList());
  }

  @Override
  public List<VirtualMachineScaleSetVM> listVirtualMachineScaleSetVMs(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId) {
    Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp =
        getVirtualMachineScaleSetsById(azureConfig, subscriptionId, virtualMachineScaleSetId);
    if (!virtualMachineScaleSetOp.isPresent()) {
      return Collections.emptyList();
    }
    return virtualMachineScaleSetOp.map(this::getVirtualMachineScaleSetVMs).orElse(Collections.emptyList());
  }

  @NotNull
  private List<VirtualMachineScaleSetVM> getVirtualMachineScaleSetVMs(VirtualMachineScaleSet virtualMachineScaleSet) {
    List<VirtualMachineScaleSetVM> virtualMachineScaleSetVMsList = new ArrayList<>();
    PagedList<VirtualMachineScaleSetVM> virtualMachineScaleSetVMs = virtualMachineScaleSet.virtualMachines().list();
    for (VirtualMachineScaleSetVM scaleSetVM : virtualMachineScaleSetVMs) {
      virtualMachineScaleSetVMsList.add(scaleSetVM);
    }
    return virtualMachineScaleSetVMsList;
  }

  @Override
  public void deleteVirtualMachineScaleSetById(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId) {
    if (isBlank(virtualMachineScaleSetId)) {
      throw new IllegalArgumentException(VIRTUAL_MACHINE_SCALE_SET_ID_NULL_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    Objects.notNull(azure.virtualMachineScaleSets().getById(virtualMachineScaleSetId),
        format("There is no virtual machine scale set with virtualMachineScaleSetId %s", virtualMachineScaleSetId));

    log.debug("Start deleting Virtual Machine Scale Sets by virtualMachineScaleSetId: {}", virtualMachineScaleSetId);
    azure.virtualMachineScaleSets().deleteById(virtualMachineScaleSetId);
  }

  @Override
  public Optional<VirtualMachineScaleSet> getVirtualMachineScaleSetsById(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId) {
    if (isBlank(virtualMachineScaleSetId)) {
      throw new IllegalArgumentException(VIRTUAL_MACHINE_SCALE_SET_ID_NULL_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start getting Virtual Machine Scale Sets by virtualMachineScaleSetId: {}, subscriptionId: {}",
        virtualMachineScaleSetId, subscriptionId);
    VirtualMachineScaleSet vmss = azure.virtualMachineScaleSets().getById(virtualMachineScaleSetId);

    return vmss == null ? Optional.empty() : Optional.of(vmss);
  }

  @Override
  public Optional<VirtualMachineScaleSet> getVirtualMachineScaleSetByName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isEmpty(virtualMachineScaleSetName)) {
      return Optional.empty();
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug(
        "Start getting Virtual Machine Scale Sets name virtualMachineScaleSetName: {}, subscriptionId: {}, resourceGroupName: {}",
        virtualMachineScaleSetName, subscriptionId, resourceGroupName);
    VirtualMachineScaleSet vmss =
        azure.virtualMachineScaleSets().getByResourceGroup(resourceGroupName, virtualMachineScaleSetName);

    return vmss == null ? Optional.empty() : Optional.of(vmss);
  }

  @Override
  public List<Subscription> listSubscriptions(AzureConfig azureConfig) {
    Azure azure = getAzureClientWithDefaultSubscription(azureConfig);
    Objects.notNull(azure, AZURE_MANAGEMENT_CLIENT_NULL_VALIDATION_MSG);

    log.debug("Start listing subscriptions for tenantId {}", azureConfig.getTenantId());
    PagedList<Subscription> subscriptions = azure.subscriptions().list();
    return subscriptions.stream().collect(Collectors.toList());
  }

  @Override
  public List<String> listWebAppNamesBySubscriptionIdAndResourceGroup(
      AzureConfig azureConfig, String subscriptionId, String resourceGroup) {
    Azure azure = getAzureClient(azureConfig, subscriptionId);
    Objects.notNull(azure, AZURE_MANAGEMENT_CLIENT_NULL_VALIDATION_MSG);
    log.debug("Start listing Web App Names for tenantId {}", azureConfig.getTenantId());
    PagedList<WebApp> webAppNames = azure.webApps().listByResourceGroup(resourceGroup);
    return webAppNames.stream().map(HasName::name).collect(Collectors.toList());
  }

  @Override
  public List<DeploymentSlot> listWebAppDeploymentSlots(
      AzureConfig azureConfig, String subscriptionId, String resourceGroup, String webAppName) {
    Azure azure = getAzureClient(azureConfig, subscriptionId);
    Objects.notNull(azure, AZURE_MANAGEMENT_CLIENT_NULL_VALIDATION_MSG);
    log.debug("Start listing Web App deployment slots for tenantId {}", azureConfig.getTenantId());
    WebApp webApp = getWebApp(azure, resourceGroup, webAppName);

    PagedList<DeploymentSlot> deploymentSlots = webApp.deploymentSlots().list();
    return deploymentSlots.stream().collect(Collectors.toList());
  }

  @NotNull
  private WebApp getWebApp(Azure azure, String resourceGroupName, String webAppName) {
    WebApp webApp = azure.webApps().getByResourceGroup(resourceGroupName, webAppName);
    if (webApp == null) {
      throw new IllegalArgumentException(
          format("Not found Web App with name: %s, resource group name: %s", webAppName, resourceGroupName));
    }
    return webApp;
  }

  @Override
  public List<String> listResourceGroupsNamesBySubscriptionId(AzureConfig azureConfig, String subscriptionId) {
    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing resource groups names for subscriptionId {}", subscriptionId);
    List<ResourceGroup> resourceGroupList = azure.resourceGroups().list();
    return resourceGroupList.stream().map(HasName::name).collect(Collectors.toList());
  }

  @Override
  public boolean checkIsRequiredNumberOfVMInstances(
      AzureConfig azureConfig, String subscriptionId, String virtualMachineScaleSetId, int numberOfVMInstances) {
    if (isBlank(virtualMachineScaleSetId)) {
      throw new IllegalArgumentException(VIRTUAL_MACHINE_SCALE_SET_ID_NULL_VALIDATION_MSG);
    }
    if (numberOfVMInstances < 0) {
      throw new IllegalArgumentException(NUMBER_OF_VM_INSTANCES_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    VirtualMachineScaleSet virtualMachineScaleSet = azure.virtualMachineScaleSets().getById(virtualMachineScaleSetId);
    PagedList<VirtualMachineScaleSetVM> vmssInstanceList = virtualMachineScaleSet.virtualMachines().list();

    return (numberOfVMInstances == 0 ? vmssInstanceList.isEmpty() : vmssInstanceList.size() == numberOfVMInstances)
        || vmssInstanceList.stream().allMatch(
            instance -> instance.instanceView().statuses().get(0).displayStatus().equals("Provisioning succeeded"));
  }

  @Override
  public VirtualMachineScaleSet updateVMSSCapacity(AzureConfig azureConfig, String virtualMachineScaleSetName,
      String subscriptionId, String resourceGroupName, int newCapacity) {
    if (newCapacity < 0) {
      throw new IllegalArgumentException(format(
          "New VMSS capacity can't have negative value, virtualMachineScaleSetName: %s, subscriptionId: %s, resourceGroupName: %s,"
              + " newCapacity: %s",
          virtualMachineScaleSetName, subscriptionId, resourceGroupName, newCapacity));
    }

    Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp =
        getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    return virtualMachineScaleSetOp.map(vmss -> vmss.update().withCapacity(newCapacity).apply())
        .orElseThrow(
            ()
                -> new InvalidRequestException(format(
                    "There is no Virtual Machine Scale Set with name: %s, subscriptionId: %s, resourceGroupName: %s,"
                        + " newCapacity: %s",
                    virtualMachineScaleSetName, subscriptionId, resourceGroupName, newCapacity)));
  }

  @Override
  public void createVirtualMachineScaleSet(AzureConfig azureConfig, String subscriptionId,
      VirtualMachineScaleSet baseVirtualMachineScaleSet, String newVirtualMachineScaleSetName,
      AzureUserAuthVMInstanceData azureUserAuthVMInstanceData, AzureMachineImageArtifact imageArtifact,
      AzureVMSSTagsData tags) {
    if (isBlank(newVirtualMachineScaleSetName)) {
      throw new IllegalArgumentException(NEW_VIRTUAL_MACHINE_SCALE_SET_NAME_IS_NULL_VALIDATION_MSG);
    }

    Objects.notNull(baseVirtualMachineScaleSet, BASE_VIRTUAL_MACHINE_SCALE_SET_IS_NULL_VALIDATION_MSG);

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    Map<String, String> baseVMSSTags =
        getTagsForNewVMSS(baseVirtualMachineScaleSet, newVirtualMachineScaleSetName, tags);

    VirtualMachineScaleSetInner inner = baseVirtualMachineScaleSet.inner();

    updateTags(inner, baseVMSSTags);
    updateVMImage(inner, imageArtifact);
    updateUserData(inner, azureUserAuthVMInstanceData);
    updateCapacity(inner, 0);

    try {
      azure.virtualMachineScaleSets().inner().beginCreateOrUpdate(
          baseVirtualMachineScaleSet.resourceGroupName(), newVirtualMachineScaleSetName, inner);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Error while creating virtual machine scale set, newVirtualMachineScaleSetName: %s, "
                  + "harnessRevision: %s, infraMappingId: %s",
              newVirtualMachineScaleSetName, tags.getHarnessRevision(), tags.getInfraMappingId()),
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  Map<String, String> getTagsForNewVMSS(
      VirtualMachineScaleSet baseVirtualMachineScaleSet, String newVirtualMachineScaleSetName, AzureVMSSTagsData tags) {
    Integer harnessRevision = tags.getHarnessRevision();
    boolean isBlueGreen = tags.isBlueGreen();
    String infraMappingId = tags.getInfraMappingId();
    List<String> harnessTagsList = Arrays.asList(HARNESS_AUTOSCALING_GROUP_TAG_NAME, NAME_TAG);
    Map<String, String> baseVMSSTags = baseVirtualMachineScaleSet.tags()
                                           .entrySet()
                                           .stream()
                                           .filter(tagEntry -> !harnessTagsList.contains(tagEntry.getKey()))
                                           .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    baseVMSSTags.put(
        HARNESS_AUTOSCALING_GROUP_TAG_NAME, AzureResourceUtility.getRevisionTagValue(infraMappingId, harnessRevision));
    baseVMSSTags.put(NAME_TAG, newVirtualMachineScaleSetName);
    baseVMSSTags.put(VMSS_CREATED_TIME_STAMP_TAG_NAME, AzureResourceUtility.dateToISO8601BasicStr(new Date()));

    if (isBlueGreen) {
      baseVMSSTags.put(BG_VERSION_TAG_NAME, BG_GREEN_TAG_VALUE);
    }

    return baseVMSSTags;
  }

  private void updateTags(VirtualMachineScaleSetInner inner, Map<String, String> baseVMSSTags) {
    inner.withTags(baseVMSSTags);
  }

  private void updateVMImage(VirtualMachineScaleSetInner inner, AzureMachineImageArtifact imageArtifact) {
    AzureMachineImage azureImage = AzureMachineImageFactory.getAzureImage(imageArtifact);
    azureImage.updateVMSSInner(inner);
  }

  private void updateUserData(
      VirtualMachineScaleSetInner inner, AzureUserAuthVMInstanceData azureUserAuthVMInstanceData) {
    VirtualMachineScaleSetOSProfile osProfile = inner.virtualMachineProfile().osProfile();
    // Specialized images do not have an osProfile associated with them.
    // All osProfile user data has already built on image.
    if (osProfile == null) {
      return;
    }

    String vmssAuthType = azureUserAuthVMInstanceData.getVmssAuthType();

    if (osProfile.windowsConfiguration() != null && vmssAuthType.equals(VMSS_AUTH_TYPE_SSH_PUBLIC_KEY)) {
      throw new IllegalArgumentException("Unable to set SSH on Windows image");
    }

    // only applied on generalized images
    String userName = azureUserAuthVMInstanceData.getUserName();
    String rootUsername = isBlank(userName) ? osProfile.adminUsername() : userName;
    osProfile.withAdminUsername(rootUsername);

    if (vmssAuthType.equals(VMSS_AUTH_TYPE_DEFAULT)) {
      osProfile.withAdminPassword(azureUserAuthVMInstanceData.getPassword());
    } else if (vmssAuthType.equals(VMSS_AUTH_TYPE_SSH_PUBLIC_KEY)) {
      String publicKeyData = azureUserAuthVMInstanceData.getSshPublicKey();
      osProfile.linuxConfiguration().withDisablePasswordAuthentication(true);

      SshConfiguration sshConfiguration = new SshConfiguration();
      sshConfiguration.withPublicKeys(new ArrayList<>());
      osProfile.linuxConfiguration().withSsh(sshConfiguration);

      SshPublicKey sshPublicKey = new SshPublicKey();
      sshPublicKey.withKeyData(publicKeyData);
      sshPublicKey.withPath("/home/" + osProfile.adminUsername() + "/.ssh/authorized_keys");
      osProfile.linuxConfiguration().ssh().publicKeys().add(sshPublicKey);
    } else {
      throw new IllegalArgumentException(format("Unsupported Virtual machine Scale Set auth type %s", vmssAuthType));
    }
  }

  private void updateCapacity(VirtualMachineScaleSetInner inner, long capacity) {
    inner.sku().withCapacity(capacity);
  }

  @Override
  public VirtualMachineScaleSet attachVMSSToBackendPools(AzureConfig azureConfig,
      LoadBalancer primaryInternetFacingLoadBalancer, final String subscriptionId, final String resourceGroupName,
      final String virtualMachineScaleSetName, final String... backendPools) {
    Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp =
        getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    return virtualMachineScaleSetOp
        .map(vmss -> attachVMSSToBackendPools(azureConfig, vmss, primaryInternetFacingLoadBalancer, backendPools))
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Virtual machine scale set cannot be found with virtualMachineScaleSetName: %s,"
                                     + "on subscriptionId: %s, resourceGroupName: %s",
                                 virtualMachineScaleSetName, subscriptionId, resourceGroupName)));
  }

  @Override
  public VirtualMachineScaleSet attachVMSSToBackendPools(AzureConfig azureConfig,
      VirtualMachineScaleSet virtualMachineScaleSet, LoadBalancer primaryInternetFacingLoadBalancer,
      final String... backendPools) {
    Objects.notNull(virtualMachineScaleSet, VIRTUAL_MACHINE_SCALE_SET_NULL_VALIDATION_MSG);
    Objects.notNull(primaryInternetFacingLoadBalancer, PRIMARY_INTERNET_FACING_LOAD_BALANCER_NULL_VALIDATION_MSG);
    if (backendPools.length == 0) {
      throw new IllegalArgumentException(BACKEND_POOLS_LIST_EMPTY_VALIDATION_MSG);
    }

    log.debug("Start attaching virtual machine scale set with name {}, to backendPools: {}",
        virtualMachineScaleSet.name(), backendPools);
    return virtualMachineScaleSet.update()
        .withExistingPrimaryInternetFacingLoadBalancer(primaryInternetFacingLoadBalancer)
        .withPrimaryInternetFacingLoadBalancerBackends(backendPools)
        .apply();
  }

  @Override
  public VirtualMachineScaleSet detachVMSSFromBackendPools(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String virtualMachineScaleSetName, final String... backendPools) {
    Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp =
        getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    return virtualMachineScaleSetOp.map(vmss -> detachVMSSFromBackendPools(azureConfig, vmss, backendPools))
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Virtual machine scale set cannot be found with virtualMachineScaleSetName: %s,"
                                     + "on subscriptionId: %s, resourceGroupName: %s",
                                 virtualMachineScaleSetName, subscriptionId, resourceGroupName)));
  }

  @Override
  public VirtualMachineScaleSet detachVMSSFromBackendPools(
      AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet, final String... backendPools) {
    Objects.notNull(virtualMachineScaleSet, VIRTUAL_MACHINE_SCALE_SET_NULL_VALIDATION_MSG);
    if (backendPools.length == 0) {
      throw new IllegalArgumentException(BACKEND_POOLS_LIST_EMPTY_VALIDATION_MSG);
    }

    log.debug("Start de-attaching virtual machine scale set with name {}, from backendPools: {}",
        virtualMachineScaleSet.name(), backendPools);
    return backendPools[0].equals("*")
        ? virtualMachineScaleSet.update()
              .withoutPrimaryInternetFacingLoadBalancer()
              .withoutPrimaryInternetFacingLoadBalancerBackends()
              .apply()
        : virtualMachineScaleSet.update().withoutPrimaryInternetFacingLoadBalancerBackends(backendPools).apply();
  }

  @Override
  public void updateVMInstances(VirtualMachineScaleSet virtualMachineScaleSet, final String... instanceIds) {
    if (instanceIds.length == 0) {
      throw new IllegalArgumentException(VM_INSTANCE_IDS_LIST_EMPTY_VALIDATION_MSG);
    }
    if (!validateVMInstanceIds(instanceIds)) {
      throw new IllegalArgumentException(VM_INSTANCE_IDS_NOT_NUMBERS_VALIDATION_MSG);
    }

    if (virtualMachineScaleSet.upgradeModel() == UpgradeMode.MANUAL) {
      log.debug("Start updating VM instances of virtual machine scale set with name {}, instanceId: {}",
          virtualMachineScaleSet.name(), instanceIds);
      virtualMachineScaleSet.virtualMachines().updateInstances(instanceIds);
    }
  }

  private boolean validateVMInstanceIds(String[] instanceIds) {
    for (String id : instanceIds) {
      if (id.equals("*")) {
        continue;
      }
      try {
        Integer.parseInt(id);
      } catch (NumberFormatException e) {
        log.error(format("Unable to convert VM instance id to numeric type, id: %s instanceIds: %s", id,
                      Arrays.toString(instanceIds)),
            ExceptionMessageSanitizer.sanitizeException(e));
        return false;
      }
    }
    return true;
  }

  @Override
  public void forceDeAttachVMSSFromBackendPools(
      AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet, final String... backendPools) {
    VirtualMachineScaleSet deAttachedVMSS =
        detachVMSSFromBackendPools(azureConfig, virtualMachineScaleSet, backendPools);
    updateVMInstances(deAttachedVMSS, "*");
  }

  @Override
  public void forceAttachVMSSToBackendPools(AzureConfig azureConfig, VirtualMachineScaleSet virtualMachineScaleSet,
      LoadBalancer primaryInternetFacingLoadBalancer, final String... backendPools) {
    VirtualMachineScaleSet attachedVMSS =
        attachVMSSToBackendPools(azureConfig, virtualMachineScaleSet, primaryInternetFacingLoadBalancer, backendPools);
    updateVMInstances(attachedVMSS, "*");
  }

  @Override
  public Optional<PublicIPAddressInner> getVMPublicIPAddress(VirtualMachineScaleSetVM vm) {
    PagedList<VirtualMachineScaleSetNetworkInterface> vmScaleSetNetworkInterfaces = vm.listNetworkInterfaces();
    if (vmScaleSetNetworkInterfaces.isEmpty()) {
      return Optional.empty();
    }

    VirtualMachineScaleSetNetworkInterface virtualMachineScaleSetNetworkInterface = vmScaleSetNetworkInterfaces.get(0);
    PublicIPAddressInner publicIPAddressInner =
        virtualMachineScaleSetNetworkInterface.primaryIPConfiguration().inner().publicIPAddress();

    if (isNull(publicIPAddressInner)) {
      return Optional.empty();
    }

    return Optional.of(publicIPAddressInner);
  }

  @Override
  public List<VirtualMachineScaleSetNetworkInterface> listVMVirtualMachineScaleSetNetworkInterfaces(
      VirtualMachineScaleSetVM vm) {
    PagedList<VirtualMachineScaleSetNetworkInterface> vmScaleSetNetworkInterfaces = vm.listNetworkInterfaces();
    if (vmScaleSetNetworkInterfaces.isEmpty()) {
      return Collections.emptyList();
    }
    return new ArrayList<>(vmScaleSetNetworkInterfaces);
  }

  @Override
  @SuppressWarnings("PMD")
  public Optional<GalleryImage> getGalleryImage(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String galleryName, String imageName) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(galleryName)) {
      throw new IllegalArgumentException(GALLERY_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(imageName)) {
      throw new IllegalArgumentException(GALLERY_IMAGE_NAME_NULL_VALIDATION_MSG);
    }

    Azure azure = getAzureClient(azureConfig, subscriptionId);

    try {
      log.debug("Start getting gallery image, imageName {}, galleryName: {}, resourceGroupName: {}", imageName,
          galleryName, resourceGroupName);
      GalleryImage galleryImage = azure.galleryImages().getByGallery(resourceGroupName, galleryName, imageName);
      if (galleryImage.name() == null || galleryImage.id() == null) {
        return Optional.empty();
      }
      return Optional.of(galleryImage);
    } catch (NullPointerException npe) {
      log.warn("Image can't be found, imageName{}, galleryName: {}, resourceGroupName: {}", imageName, galleryName,
          resourceGroupName, npe);
      return Optional.empty();
    }
  }
}
