/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.azure.utility.AzureUtils.AZURE_GOV_REGIONS_NAMES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ff.FeatureFlagService;

import software.wings.app.MainConfiguration;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureKubernetesCluster;
import software.wings.beans.AzureResourceGroup;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.azure.AzureHelperService;
import software.wings.service.impl.azure.manager.resource.ACRResourceProvider;
import software.wings.service.impl.azure.manager.resource.AzureK8sResourceProvider;
import software.wings.service.intfc.AzureResourceService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.azure.core.management.Region;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
public class AzureResourceServiceImpl implements AzureResourceService {
  @Inject private MainConfiguration mainConfiguration;

  @Inject private AzureHelperService azureHelperService;
  @Inject private ACRResourceProvider containerRegistryResourceProvider;
  @Inject private AzureK8sResourceProvider kubernetesResourceProvider;
  @Inject private SettingsService settingService;
  @Inject private SecretManager secretManager;
  @Inject private BuildSourceService buildSourceService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public Map<String, String> listSubscriptions(String accountId, String cloudProviderId) {
    return buildSourceService.listSubscriptions(cloudProviderId);
  }

  @Override
  public List<String> listContainerRegistryNames(String cloudProviderId, String subscriptionId) {
    return buildSourceService.listAzureContainerRegistryNames(cloudProviderId, subscriptionId);
  }

  @Override
  public List<AzureContainerRegistry> listContainerRegistries(String cloudProviderId, String subscriptionId) {
    return buildSourceService.listAzureContainerRegistries(cloudProviderId, subscriptionId);
  }

  @Override
  public List<String> listRepositories(String cloudProviderId, String subscriptionId, String registryName) {
    return buildSourceService.listAcrRepositories(cloudProviderId, subscriptionId, registryName);
  }

  @Override
  public List<String> listRepositoryTags(
      String cloudProviderId, String subscriptionId, String registryName, String repositoryName) {
    SettingAttribute cloudProviderSetting = settingService.get(cloudProviderId);
    AzureConfig azureConfig = validateAndGetAzureConfig(cloudProviderSetting);
    return containerRegistryResourceProvider.listRepositoryTags(azureConfig,
        secretManager.getEncryptionDetails(azureConfig, null, null), subscriptionId, registryName, repositoryName);
  }

  @Override
  public List<AzureKubernetesCluster> listKubernetesClusters(String cloudProviderId, String subscriptionId) {
    SettingAttribute cloudProviderSetting = settingService.get(cloudProviderId);
    AzureConfig azureConfig = validateAndGetAzureConfig(cloudProviderSetting);
    return kubernetesResourceProvider.listKubernetesClusters(
        azureConfig, secretManager.getEncryptionDetails(azureConfig, null, null), subscriptionId);
  }

  @Override
  public List<AzureResourceGroup> listResourceGroups(String accountId, String cloudProviderId, String subscriptionId) {
    return buildSourceService.listResourceGroups(cloudProviderId, subscriptionId);
  }

  @Override
  public List<AzureImageGallery> listImageGalleries(
      String accountId, String cloudProviderId, String subscriptionId, String resourceGroupName) {
    return buildSourceService.listImageGalleries(cloudProviderId, subscriptionId, resourceGroupName);
  }

  @Override
  public List<AzureImageDefinition> listImageDefinitions(
      String accountId, String cloudProviderId, String subscriptionId, String resourceGroupName, String galleryName) {
    return buildSourceService.listImageDefinitions(cloudProviderId, subscriptionId, resourceGroupName, galleryName);
  }

  private AzureConfig validateAndGetAzureConfig(SettingAttribute cloudProviderSetting) {
    if (cloudProviderSetting == null || !(cloudProviderSetting.getValue() instanceof AzureConfig)) {
      throw new InvalidArgumentsException(Pair.of("args", "No cloud provider exist or not of type Azure"));
    }

    return (AzureConfig) cloudProviderSetting.getValue();
  }

  public List<NameValuePair> listAzureRegions() {
    return Region.values()
        .stream()
        .filter(nonGovernmentAzureRegionsNamesFilter())
        .map(toNameValuePair())
        .collect(Collectors.toList());
  }

  @NotNull
  private Predicate<Region> nonGovernmentAzureRegionsNamesFilter() {
    return region -> !AZURE_GOV_REGIONS_NAMES.contains(region.name());
  }

  @NotNull
  private Function<Region, NameValuePair> toNameValuePair() {
    return region -> NameValuePair.builder().name(region.label()).value(region.name()).build();
  }
}
