/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.mappers.DockerRequestResponseMapper;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class GithubPackagesArtifactTaskHandler
    extends DelegateArtifactTaskHandler<GithubPackagesArtifactDelegateRequest> {
  private static final int ARTIFACT_RETENTION_SIZE = 25;
  private static final int MAX_RETRY = 5;
  private final SecretDecryptionService secretDecryptionService;
  private final GithubPackagesRegistryService githubPackagesRegistryService;
  @Inject @Named("githubPackagesExecutor") private ExecutorService executor;

  public ArtifactTaskExecutionResponse getBuilds(GithubPackagesArtifactDelegateRequest attributes) {
    return null;
  }

  public void decryptRequestDTOs(GithubPackagesArtifactDelegateRequest attributes) {
    if (attributes.getGithubConnectorDTO().getAuthentication() != null) {
      secretDecryptionService.decrypt(attributes.getGithubConnectorDTO().getAuthentication().getCredentials(),
          attributes.getEncryptedDataDetails());
    }
  }

  boolean isRegex(GithubPackagesArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getVersionRegex());
  }
}
