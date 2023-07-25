/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.service;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface AzureArtifactsRegistryService {
  boolean validateCredentials(AzureArtifactsInternalConfig toAzureArtifactsInternalConfig);

  List<BuildDetails> listPackageVersions(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String packageType,
      String packageName, String versionRegex, String feed, String project);

  BuildDetails getBuild(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String packageType, String packageId,
      String version, String feed, String project);

  BuildDetails getLastSuccessfulBuildFromRegex(AzureArtifactsInternalConfig azureArtifactsConfig, String packageType,
      String packageName, String versionRegex, String feed, String project, String scope);

  List<AzureDevopsProject> listProjects(AzureArtifactsInternalConfig azureArtifactsInternalConfig);

  List<AzureArtifactsPackage> listPackages(
      AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project, String feed, String packageType);

  List<AzureArtifactsFeed> listFeeds(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project);

  List<AzureArtifactsPackageFileInfo> listPackageFiles(AzureArtifactsInternalConfig azureArtifactsInternalConfig,
      String project, String feed, String packageType, String packageName, String version);

  Pair<String, InputStream> downloadArtifact(AzureArtifactsInternalConfig azureArtifactsInternalConfig, String project,
      String feed, String packageType, String packageName, String version);
}
