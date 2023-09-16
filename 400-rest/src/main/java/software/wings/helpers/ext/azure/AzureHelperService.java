/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_AZURE_VAULT_CONFIGURATION;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.infrastructure.Host.Builder.aHost;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.utility.AzureUtils;
import io.harness.beans.PageResponse;
import io.harness.exception.AzureServiceException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.AzureAvailabilitySet;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.AzureTag;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.azure.AzureVirtualMachineScaleSet;
import software.wings.beans.infrastructure.Host;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.security.EncryptionService;

import com.azure.core.http.HttpClient;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.compute.models.PowerState;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.containerservice.models.OSType;
import com.azure.resourcemanager.keyvault.models.Vault;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.Subscription;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.aad.msal4j.MsalException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class AzureHelperService {
  @Inject private EncryptionService encryptionService;

  private AzureConfig validateAndGetAzureConfig(SettingAttribute computeProviderSetting) {
    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof AzureConfig)) {
      throw new InvalidArgumentsException(ImmutablePair.of("args", "InvalidConfiguration"));
    }

    return (AzureConfig) computeProviderSetting.getValue();
  }

  private AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
    if (azureEnvironmentType == null) {
      return AzureEnvironment.AZURE;
    }

    switch (azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return AzureEnvironment.AZURE_US_GOVERNMENT;

      case AZURE:
      default:
        return AzureEnvironment.AZURE;
    }
  }

  public void validateAzureAccountCredential(AzureConfig azureConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      if (isNotEmpty(encryptedDataDetails)) {
        encryptionService.decrypt(azureConfig, encryptedDataDetails, false);
      }

      if (azureConfig.getKey() == null && azureConfig.getEncryptedKey() != null) {
        throw new InvalidRequestException("Please input a valid encrypted key.");
      }

      AzureProfile azureProfile = AzureUtils.getAzureProfile(
          azureConfig.getTenantId(), null, getAzureEnvironment(azureConfig.getAzureEnvironmentType()));

      AzureResourceManager.Authenticated authenticated =
          AzureResourceManager.authenticate(AzureCGHelper.createCredentialsAndHttpPipeline(azureConfig), azureProfile);

      Subscription subscription = authenticated.subscriptions().list().stream().findFirst().get();
      authenticated.withSubscription(subscription.subscriptionId());
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
  }

  public List<VirtualMachine> listVms(AzureInfrastructureMapping azureInfrastructureMapping,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails) {
    notNullCheck("Infra mapping", azureInfrastructureMapping);

    String subscriptionId = azureInfrastructureMapping.getSubscriptionId();
    String resourceGroup = azureInfrastructureMapping.getResourceGroup();
    List<AzureTag> tags = azureInfrastructureMapping.getTags();

    Map<String, String> tagsMap = new HashMap<>();
    for (AzureTag tag : tags) {
      tagsMap.put(tag.getKey(), tag.getValue());
    }

    notNullCheck("Compute Provider", computeProviderSetting);
    AzureConfig azureConfig = validateAndGetAzureConfig(computeProviderSetting);
    if (DeploymentType.WINRM.name().equals(azureInfrastructureMapping.getDeploymentType())) {
      return listVmsByTagsAndResourceGroup(
          azureConfig, encryptedDataDetails, subscriptionId, resourceGroup, tagsMap, OSType.WINDOWS);
    } else if (DeploymentType.SSH.name().equals(azureInfrastructureMapping.getDeploymentType())) {
      return listVmsByTagsAndResourceGroup(
          azureConfig, encryptedDataDetails, subscriptionId, resourceGroup, tagsMap, OSType.LINUX);
    }
    return Collections.EMPTY_LIST;
  }

  public List<VirtualMachine> listVms(AzureInstanceInfrastructure azureInstanceInfrastructure,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      DeploymentType deploymentType) {
    notNullCheck("Azure InfraStructure Definition", azureInstanceInfrastructure, USER);

    String subscriptionId = azureInstanceInfrastructure.getSubscriptionId();
    String resourceGroup = azureInstanceInfrastructure.getResourceGroup();
    List<AzureTag> tags = azureInstanceInfrastructure.getTags();

    Map<String, String> tagsMap = new HashMap<>();

    if (tags != null) {
      tags.stream().map(tag -> tagsMap.put(tag.getKey(), tag.getValue()));
    }

    AzureConfig azureConfig = validateAndGetAzureConfig(computeProviderSetting);
    if (DeploymentType.WINRM == deploymentType) {
      return listVmsByTagsAndResourceGroup(
          azureConfig, encryptedDataDetails, subscriptionId, resourceGroup, tagsMap, OSType.WINDOWS);
    } else if (DeploymentType.SSH == deploymentType) {
      return listVmsByTagsAndResourceGroup(
          azureConfig, encryptedDataDetails, subscriptionId, resourceGroup, tagsMap, OSType.LINUX);
    }
    return Collections.EMPTY_LIST;
  }

  public List<AzureAvailabilitySet> listAvailabilitySets(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId) {
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);
    return azure.availabilitySets()
        .list()
        .stream()
        .map(as
            -> AzureAvailabilitySet.builder()
                   .name(as.name())
                   .resourceGroup(as.resourceGroupName())
                   .type(as.type())
                   .id(as.id())
                   .build())
        .collect(toList());
  }

  private OSType getVmOSType(VirtualMachine vm) {
    if (vm.osProfile() != null && vm.osProfile().windowsConfiguration() != null) {
      return OSType.WINDOWS;
    } else if (vm.osProfile() != null && vm.osProfile().linuxConfiguration() != null) {
      return OSType.LINUX;
    } else {
      return null;
    }
  }

  public List<VirtualMachine> listVmsByTagsAndResourceGroup(AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails, String subscriptionId, String resourceGroupName,
      Map<String, String> tags, OSType osType) {
    List<VirtualMachine> matchingVMs = new ArrayList<>();

    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);
    List<VirtualMachine> listVms =
        azure.virtualMachines().listByResourceGroup(resourceGroupName).stream().collect(toList());

    if (isEmpty(listVms)) {
      log.info("List VMs by Tags and Resource group did not find any matching VMs in Azure for subscription : "
          + subscriptionId);
      return Collections.emptyList();
    }

    // Filter VMs by OS type
    if (osType != null && (OSType.WINDOWS.equals(osType) || OSType.LINUX.equals(osType))) {
      listVms = listVms.stream()
                    .filter(vm -> {
                      OSType vmOSType = getVmOSType(vm);
                      return vmOSType != null && vmOSType.equals(osType) && isVmRunning(vm);
                    })
                    .collect(Collectors.toList());
    }

    // Filter by tags if present, tags are optional.
    for (VirtualMachine vm : listVms) {
      if (tags.isEmpty()) {
        matchingVMs.add(vm);
      } else if (vm.innerModel() != null && vm.innerModel().tags() != null) {
        if (vm.innerModel().tags().keySet().containsAll(tags.keySet())
            && vm.innerModel().tags().values().containsAll(tags.values())) {
          matchingVMs.add(vm);
        }
      }
    }

    return matchingVMs;
  }

  private boolean isVmRunning(VirtualMachine vm) {
    return vm.powerState().equals(PowerState.RUNNING);
  }

  public PageResponse<Host> listHosts(AzureInfrastructureMapping azureInfrastructureMapping,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      DeploymentType deploymentType) {
    List<VirtualMachine> vms = listVms(azureInfrastructureMapping, computeProviderSetting, encryptedDataDetails);

    if (isNotEmpty(vms)) {
      List<Host> azureHosts = new ArrayList<>();
      for (VirtualMachine vm : vms) {
        Host host = aHost()
                        .withHostName(vm.name())
                        .withPublicDns(computeHostConnectString(azureInfrastructureMapping.isUsePublicDns(),
                            azureInfrastructureMapping.isUsePrivateIp(), vm))
                        .withAppId(azureInfrastructureMapping.getAppId())
                        .withEnvId(azureInfrastructureMapping.getEnvId())
                        .withHostConnAttr(azureInfrastructureMapping.getHostConnectionAttrs())
                        .withWinrmConnAttr(DeploymentType.WINRM == deploymentType
                                ? azureInfrastructureMapping.getWinRmConnectionAttributes()
                                : null)
                        .withInfraMappingId(azureInfrastructureMapping.getUuid())
                        .withInfraDefinitionId(azureInfrastructureMapping.getInfrastructureDefinitionId())
                        .withServiceTemplateId(azureInfrastructureMapping.getServiceTemplateId())
                        .build();
        azureHosts.add(host);
      }
      return aPageResponse().withResponse(azureHosts).build();
    }

    return aPageResponse().withResponse(Collections.emptyList()).build();
  }

  public PageResponse<Host> listHosts(InfrastructureDefinition infrastructureDefinition,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      DeploymentType deploymentType) {
    AzureInstanceInfrastructure azureInstanceInfrastructure =
        (AzureInstanceInfrastructure) infrastructureDefinition.getInfrastructure();
    List<VirtualMachine> vms =
        listVms(azureInstanceInfrastructure, computeProviderSetting, encryptedDataDetails, deploymentType);

    if (isNotEmpty(vms)) {
      List<Host> azureHosts = new ArrayList<>();
      for (VirtualMachine vm : vms) {
        Host host = aHost()
                        .withHostName(vm.name())
                        .withPublicDns(computeHostConnectString(azureInstanceInfrastructure.isUsePublicDns(),
                            azureInstanceInfrastructure.isUsePrivateIp(), vm))
                        .withAppId(infrastructureDefinition.getAppId())
                        .withEnvId(infrastructureDefinition.getEnvId())
                        .withHostConnAttr(azureInstanceInfrastructure.getHostConnectionAttrs())
                        .withWinrmConnAttr(DeploymentType.WINRM == deploymentType
                                ? azureInstanceInfrastructure.getWinRmConnectionAttributes()
                                : null)
                        .withInfraMappingId(infrastructureDefinition.getUuid())
                        .withInfraDefinitionId(infrastructureDefinition.getUuid())
                        .build();
        azureHosts.add(host);
      }
      return aPageResponse().withResponse(azureHosts).build();
    }

    return aPageResponse().withResponse(Collections.emptyList()).build();
  }

  public List<AzureVirtualMachineScaleSet> listVirtualMachineScaleSets(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId) {
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);
    return azure.virtualMachineScaleSets()
        .list()
        .stream()
        .map(as
            -> AzureVirtualMachineScaleSet.builder()
                   .name(as.name())
                   .resourceGroup(as.resourceGroupName())
                   .type(as.type())
                   .id(as.id())
                   .build())
        .collect(toList());
  }

  @VisibleForTesting
  protected AzureResourceManager getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    try {
      AzureEnvironment azureEnvironment = getAzureEnvironment(azureConfig.getAzureEnvironmentType());
      HttpClient httpClient = AzureUtils.getAzureHttpClient();
      AzureProfile azureProfile = AzureUtils.getAzureProfile(azureConfig.getTenantId(), null, azureEnvironment);
      RetryPolicy retryPolicy =
          AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions()));

      ClientSecretCredential clientSecretCredential =
          new ClientSecretCredentialBuilder()
              .clientId(azureConfig.getClientId())
              .tenantId(azureConfig.getTenantId())
              .clientSecret(String.valueOf(azureConfig.getKey()))
              .httpClient(httpClient)
              .authorityHost(
                  AzureUtils.getAuthorityHost(azureConfig.getAzureEnvironmentType(), azureConfig.getTenantId()))
              .build();

      return AzureResourceManager
          .authenticate(AzureUtils.getAzureHttpPipeline(clientSecretCredential, azureProfile, retryPolicy, httpClient),
              azureProfile)
          .withSubscription(subscriptionId);
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }

  public void handleAzureAuthenticationException(Exception e) {
    log.error("HandleAzureAuthenticationException: Exception:" + e);

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof MsalException) {
        throw new InvalidRequestException("Invalid Azure credentials.", USER);
      }
    }

    throw new InvalidRequestException("Failed to connect to Azure cluster. " + ExceptionUtils.getMessage(e), USER);
  }

  public List<Vault> listVaults(String accountId, AzureVaultConfig azureVaultConfig) {
    try {
      return listVaultsInternal(accountId, azureVaultConfig);
    } catch (Exception ex) {
      log.error("Listing vaults failed for account Id {}", accountId, ex);
      throw new AzureServiceException("Failed to list vaults.", INVALID_AZURE_VAULT_CONFIGURATION, USER);
    }
  }

  public List<AzureImageGallery> listImageGalleries(AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails, String subscriptionId, String resourceGroupName) {
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);
    return azure.galleries()
        .listByResourceGroup(resourceGroupName)
        .stream()
        .map(ig
            -> AzureImageGallery.builder()
                   .name(ig.name())
                   .subscriptionId(subscriptionId)
                   .resourceGroupName(resourceGroupName)
                   .regionName(ig.regionName())
                   .build())
        .collect(Collectors.toList());
  }

  private List<Vault> listVaultsInternal(String accountId, AzureVaultConfig azureVaultConfig) {
    AzureResourceManager azureResourceManager;
    List<Vault> vaultList = new ArrayList<>();

    String subscriptionId = azureVaultConfig.getSubscription();
    AzureProfile azureProfile = AzureUtils.getAzureProfile(azureVaultConfig.getTenantId(), subscriptionId,
        getAzureEnvironment(azureVaultConfig.getAzureEnvironmentType()));

    AzureResourceManager.Authenticated authenticate = AzureResourceManager.authenticate(
        AzureCGHelper.createCredentialsAndHttpPipeline(azureVaultConfig), azureProfile);

    if (isEmpty(subscriptionId)) {
      Subscription subscription = authenticate.subscriptions().list().stream().findFirst().get();
      subscriptionId = subscription.subscriptionId();
    }

    azureResourceManager = authenticate.withSubscription(subscriptionId);

    log.info("Subscription {} is being used for account Id {}", azureResourceManager.subscriptionId(), accountId);

    for (ResourceGroup rGroup : azureResourceManager.resourceGroups().list()) {
      vaultList.addAll(azureResourceManager.vaults().listByResourceGroup(rGroup.name()).stream().collect(toList()));
    }
    log.info("Found azure vaults {} or account id: {}", vaultList, accountId);
    return vaultList;
  }

  private String computeHostConnectString(boolean isUsePublicDns, boolean isUsePrivateIp, VirtualMachine vm) {
    if (isUsePublicDns && vm.getPrimaryPublicIPAddress() != null) {
      return vm.getPrimaryPublicIPAddress().fqdn();
    }
    if (isUsePrivateIp && vm.getPrimaryNetworkInterface() != null) {
      return vm.getPrimaryNetworkInterface().primaryPrivateIP();
    }
    return vm.name();
  }
}
