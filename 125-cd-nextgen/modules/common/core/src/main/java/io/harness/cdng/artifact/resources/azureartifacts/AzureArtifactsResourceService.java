/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.azureartifacts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public interface AzureArtifactsResourceService {
  List<AzureArtifactsPackage> listAzureArtifactsPackages(IdentifierRef connectorRef, String accountId,
      String orgIdentifier, String projectIdentifier, String project, String feed, String packageType);

  List<BuildDetails> listVersionsOfAzureArtifactsPackage(IdentifierRef connectorRef, String accountId,
      String orgIdentifier, String projectIdentifier, String project, String feed, String packageType,
      String packageName, String versionRegex);

  BuildDetails getLastSuccessfulVersion(IdentifierRef connectorRef, String accountId, String orgIdentifier,
      String projectIdentifier, String project, String feed, String packageType, String packageName, String version,
      String versionRegex);

  List<AzureDevopsProject> listAzureArtifactsProjects(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier);

  List<AzureArtifactsFeed> listAzureArtifactsFeeds(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier, String project);
}
