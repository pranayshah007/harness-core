/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GithubPackagesRequestResponseMapper {
  public GithubPackagesInternalConfig toGithubPackagesInternalConfig(GithubPackagesArtifactDelegateRequest request) {
    return new GithubPackagesInternalConfig();
  }

  public GithubPackagesArtifactDelegateResponse toGithubPackagesResponse(
      BuildDetailsInternal buildDetailsInternal, GithubPackagesArtifactDelegateRequest request) {
    return GithubPackagesArtifactDelegateResponse.builder().build();
  }
}
