/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.equalCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureResourceGroup;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.azure.AcrService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.AcrBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AcrBuildServiceImpl implements AcrBuildService {
  @Inject private AcrService acrService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EncryptionService encryptionService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.ACR.name());
    return wrapNewBuildsWithLabels(acrService.getBuilds(azureConfig, encryptionDetails, artifactStreamAttributes, 50),
        artifactStreamAttributes, azureConfig);
  }

  @Override
  public List<JobDetails> getJobs(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    return Lists.newArrayList();
  }

  @Override
  public List<String> getArtifactPaths(
      String subscriptionId, String groupId, AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return listContainerRegistryNames(config, encryptionDetails, subscriptionId);
  }

  @Override
  public List<AzureContainerRegistry> listContainerRegistries(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId) {
    log.info("[ACR Build Service] List registries for subscriptionId {}", subscriptionId);
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    return azureDelegateHelperService.listContainerRegistries(azureConfig, subscriptionId);
  }

  @Override
  public List<String> listContainerRegistryNames(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId) {
    log.info("[ACR Build Service] List registry names for subscriptionId {}", subscriptionId);
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    return acrService.listRegistries(azureConfig, subscriptionId);
  }

  @Override
  public List<String> listRepositories(AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails,
      String subscriptionId, String registryName) {
    log.info("[ACR Build Service] List Repositories with subscriptionId {} & registry name {}", subscriptionId,
        registryName);
    encryptionService.decrypt(azureConfig, encryptionDetails, false);
    return azureDelegateHelperService.listRepositories(azureConfig, subscriptionId, registryName);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ACR Artifact Stream");
  }

  @Override
  public Map<String, String> getPlans(AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return getJobs(config, encryptionDetails, Optional.empty())
        .stream()
        .collect(Collectors.toMap(JobDetails::getJobName, JobDetails::getJobName));
  }

  @Override
  public Map<String, String> getPlans(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String reposiotoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(String jobName, AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ACR Artifact Stream");
  }

  @Override
  public boolean validateArtifactSource(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    encryptionService.decrypt(config, encryptionDetails, false);
    return acrService.verifyImageName(config, encryptionDetails, artifactStreamAttributes);
  }

  @Override
  public boolean validateArtifactServer(AzureConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      AzureConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ACR Artifact Stream");
  }

  @Override
  public List<String> getSmbPaths(AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Azure Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by Azure Build Service", WingsException.USER);
  }

  @Override
  public List<AzureImageGallery> listImageGalleries(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      String subscriptionId, String resourceGroupName) {
    return azureDelegateHelperService.listImageGalleries(config, encryptionDetails, subscriptionId, resourceGroupName);
  }

  @Override
  public List<AzureImageDefinition> listImageDefinitions(AzureConfig config,
      List<EncryptedDataDetail> encryptionDetails, String subscriptionId, String resourceGroupName,
      String galleryName) {
    return azureDelegateHelperService.listImageDefinitions(
        config, encryptionDetails, subscriptionId, resourceGroupName, galleryName);
  }

  @Override
  public List<AzureResourceGroup> listResourceGroups(
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails, String subscriptionId) {
    return azureDelegateHelperService.listResourceGroups(config, encryptionDetails, subscriptionId)
        .stream()
        .map(name -> AzureResourceGroup.builder().name(name).subscriptionId(subscriptionId).build())
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, String> listSubscriptions(AzureConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return azureDelegateHelperService.listSubscriptions(config, encryptionDetails);
  }
}
