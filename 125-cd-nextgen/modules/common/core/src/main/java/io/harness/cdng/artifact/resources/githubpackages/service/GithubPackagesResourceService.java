/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public interface GithubPackagesResourceService {
  // List Packages
  GithubPackagesResponseDTO getPackageDetails(IdentifierRef connectorRef, String accountId, String orgIdentifier,
      String projectIdentifier, String packageType, String org);

  // List Versions from a Github Package
  List<BuildDetails> getVersionsOfPackage(IdentifierRef connectorRef, String packageName, String packageType,
      String versionRegex, String org, String accountId, String orgIdentifier, String projectIdentifier);
  BuildDetails getLastSuccessfulVersion(IdentifierRef connectorRef, String packageName, String packageType,
      String version, String versionRegex, String org, String accountId, String orgIdentifier,
      String projectIdentifier);
}
