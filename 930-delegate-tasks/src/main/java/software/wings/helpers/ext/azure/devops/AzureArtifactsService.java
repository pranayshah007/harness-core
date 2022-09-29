/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure.devops;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDC)
public interface AzureArtifactsService {
  boolean validateArtifactServer(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails, boolean validateUrl);

  boolean validateArtifactSource(AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes);

  List<BuildDetails> getBuilds(ArtifactStreamAttributes artifactStreamAttributes,
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails);

  List<AzureDevopsProject> listProjects(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails);

  List<AzureArtifactsFeed> listFeeds(
      AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails, String project);

  List<AzureArtifactsPackage> listPackages(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, String project, String feed, String protocolType);

  List<AzureArtifactsPackageFileInfo> listFiles(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes,
      Map<String, String> artifactMetadata, boolean nameOnly);

  void downloadArtifact(AzureArtifactsConfig azureArtifactsConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata, String delegateId,
      String taskId, String accountId, ListNotifyResponseData notifyResponseData);

  Pair<String, InputStream> downloadArtifact(AzureArtifactsConfig azureArtifactsConfig,
      List<EncryptedDataDetail> encryptionDetails, ArtifactStreamAttributes artifactStreamAttributes,
      Map<String, String> artifactMetadata);
}
