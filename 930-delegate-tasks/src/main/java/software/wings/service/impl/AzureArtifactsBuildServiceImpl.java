/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.equalCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.AzureArtifactsBuildService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AzureArtifactsBuildServiceImpl implements AzureArtifactsBuildService {
  @Inject AzureArtifactsService azureArtifactsService;

  @Override
  public boolean validateArtifactServer(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails) {
    return azureArtifactsService.validateArtifactServer(azureArtifactsConfig, encryptionDetails, true);
  }

  @Override
  public boolean validateArtifactSource(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes) {
    return azureArtifactsService.validateArtifactSource(
        azureArtifactsConfig, encryptionDetails, artifactStreamAttributes);
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.AZURE_ARTIFACTS.name());
    return wrapNewBuildsWithLabels(
        azureArtifactsService.getBuilds(artifactStreamAttributes, azureArtifactsConfig, encryptionDetails),
        artifactStreamAttributes, azureArtifactsConfig);
  }

  @Override
  public List<AzureDevopsProject> getProjects(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails) {
    return azureArtifactsService.listProjects(azureArtifactsConfig, encryptionDetails);
  }

  @Override
  public List<AzureArtifactsFeed> getFeeds(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails, String project) {
    return azureArtifactsService.listFeeds(azureArtifactsConfig, encryptionDetails, project);
  }

  @Override
  public List<AzureArtifactsPackage> getPackages(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, String project, String feed, String protocolType) {
    return azureArtifactsService.listPackages(azureArtifactsConfig, encryptionDetails, project, feed, protocolType);
  }
}
