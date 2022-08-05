/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static java.util.stream.Collectors.toList;

import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.GithubPackagesServerRuntimeException;

import software.wings.common.BuildDetailsComparatorAscending;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

public class GithubPackagesRegistryServiceImpl implements GithubPackagesRegistryService {
  @Override
  public List<BuildDetails> getBuilds(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName, int maxNoOfVersionsPerPackage) {
    List<BuildDetails> buildDetails;
    try {
      if (githubPackagesInternalConfig.hasCredentials()) {
        buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName);
      } else {
        buildDetails = null;
      }
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch vesions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, WingsException.USER));
    }

    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  private List<BuildDetails> getBuildDetails(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName) {
    return null;
  }
}
