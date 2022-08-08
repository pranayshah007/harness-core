/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureResourceGroup;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

@OwnedBy(CDC)
public interface AcrBuildService extends BuildService<AzureConfig> {
  @Override
  @DelegateTaskType(TaskType.ACR_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes, AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ACR_VALIDATE_ARTIFACT_STREAM)
  boolean validateArtifactSource(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes);

  @Override
  @DelegateTaskType(TaskType.ACR_GET_PLANS)
  Map<String, String> getPlans(AzureConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ACR_GET_ARTIFACT_PATHS)
  List<String> getArtifactPaths(
      String subscriptionId, String groupId, AzureConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ACR_GET_REGISTRIES)
  List<AzureContainerRegistry> listContainerRegistries(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId);

  @Override
  @DelegateTaskType(TaskType.ACR_GET_REGISTRY_NAMES)
  List<String> listContainerRegistryNames(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId);

  @Override
  @DelegateTaskType(TaskType.ACR_GET_REPOSITORIES)
  List<String> listRepositories(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String subscriptionId, String registryName);

  @Override
  @DelegateTaskType(TaskType.AZURE_MACHINE_IMAGE_GET_IMAGE_GALLERIES)
  List<AzureImageGallery> listImageGalleries(
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails, String subscriptionId, String resourceGroupName);

  @Override
  @DelegateTaskType(TaskType.AZURE_MACHINE_IMAGE_GET_IMAGE_DEFINITIONS)
  List<AzureImageDefinition> listImageDefinitions(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      String subscriptionId, String resourceGroupName, String galleryName);

  @Override
  @DelegateTaskType(TaskType.AZURE_MACHINE_IMAGE_GET_RESOURCE_GROUPS)
  List<AzureResourceGroup> listResourceGroups(
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails, String subscriptionId);

  @Override
  @DelegateTaskType(TaskType.AZURE_GET_SUBSCRIPTIONS)
  Map<String, String> listSubscriptions(AzureConfig config, List<EncryptedDataDetail> encryptionDetails);
}
