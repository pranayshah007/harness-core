/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

public class GithubPackagesResourceServiceImpl implements GithubPackagesResourceService {
  @Override
  public GithubPackagesResponseDTO getPackageDetails(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public List<BuildDetails> getVersionsOfPackage(IdentifierRef connectorRef, String packageName, String versionRegex,
      String accountId, String orgIdentifier, String projectIdentifier) {
    return null;
  }
}
