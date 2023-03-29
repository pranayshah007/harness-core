/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.NexusRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.nexus.NexusRepositories;
import software.wings.helpers.ext.nexus.NexusService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class NexusArtifactTaskHandler extends DelegateArtifactTaskHandler<NexusArtifactDelegateRequest> {
  private final NexusRegistryService nexusRegistryService;
  private final SecretDecryptionService secretDecryptionService;
  private final NexusService nexusService;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(NexusArtifactDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    NexusRequest nexusConfig = NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest);
    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild =
          nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, attributesRequest.getRepositoryName(),
              attributesRequest.getRepositoryPort(), attributesRequest.getArtifactPath(),
              attributesRequest.getRepositoryFormat(), attributesRequest.getTagRegex(), attributesRequest.getGroupId(),
              attributesRequest.getArtifactName(), attributesRequest.getExtension(), attributesRequest.getClassifier(),
              attributesRequest.getPackageName(), attributesRequest.getGroup(), attributesRequest.getMaxBuilds());
    } else {
      lastSuccessfulBuild = nexusRegistryService.verifyBuildNumber(nexusConfig, attributesRequest.getRepositoryName(),
          attributesRequest.getRepositoryPort(), attributesRequest.getArtifactPath(),
          attributesRequest.getRepositoryFormat(), attributesRequest.getTag(), attributesRequest.getGroupId(),
          attributesRequest.getArtifactName(), attributesRequest.getExtension(), attributesRequest.getClassifier(),
          attributesRequest.getPackageName(), attributesRequest.getGroup(), attributesRequest.getMaxBuilds());
    }

    NexusArtifactDelegateResponse nexusArtifactDelegateResponse =
        NexusRequestResponseMapper.toNexusResponse(lastSuccessfulBuild, attributesRequest);
    return getSuccessTaskExecutionResponse(Collections.singletonList(nexusArtifactDelegateResponse));
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(NexusArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds = nexusRegistryService.getBuilds(
        NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest), attributesRequest.getRepositoryName(),
        attributesRequest.getRepositoryPort(), attributesRequest.getArtifactPath(),
        attributesRequest.getRepositoryFormat(), attributesRequest.getGroupId(), attributesRequest.getArtifactName(),
        attributesRequest.getExtension(), attributesRequest.getClassifier(), attributesRequest.getPackageName(),
        attributesRequest.getGroup(), attributesRequest.getMaxBuilds());
    List<NexusArtifactDelegateResponse> nexusArtifactDelegateResponseList =
        builds.stream()
            .map(build -> NexusRequestResponseMapper.toNexusResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(nexusArtifactDelegateResponseList);
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<NexusArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(NexusArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated =
        nexusRegistryService.validateCredentials(NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  public ArtifactTaskExecutionResponse getRepositories(NexusArtifactDelegateRequest attributesRequest) {
    Map<String, String> repositories = nexusRegistryService.getRepository(
        NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest), attributesRequest.getRepositoryFormat());
    List<NexusRepositories> nexusRepositories = repositories.entrySet()
                                                    .stream()
                                                    .filter(repository -> !repositories.isEmpty())
                                                    .map(repository
                                                        -> NexusRepositories.builder()
                                                               .repositoryId(repository.getKey())
                                                               .repositoryName(repository.getValue())
                                                               .build())
                                                    .collect(Collectors.toList());
    return ArtifactTaskExecutionResponse.builder().repositories(nexusRepositories).build();
  }

  public ArtifactTaskExecutionResponse getGroupIds(NexusArtifactDelegateRequest attributesRequest) {
    NexusRequest nexusRequest = NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest);

    List<String> groupIds = nexusService.getGroupIdPaths(
        nexusRequest, attributesRequest.getRepositoryName(), attributesRequest.getRepositoryFormat());

    return ArtifactTaskExecutionResponse.builder().nexusGroupIds(groupIds).build();
  }

  public ArtifactTaskExecutionResponse getArtifactIds(NexusArtifactDelegateRequest attributesRequest) {
    NexusRequest nexusRequest = NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest);

    List<String> artifactIds;

    if (isBlank(attributesRequest.getGroupId())) {
      artifactIds = nexusService.getArtifactPaths(nexusRequest, attributesRequest.getRepositoryName());
    } else {
      if (attributesRequest.getSourceType().equals(ArtifactSourceType.NEXUS3_REGISTRY)) {
        artifactIds = nexusService.getArtifactNames(nexusRequest, attributesRequest.getRepositoryName(),
            attributesRequest.getGroupId(), attributesRequest.getRepositoryFormat());
      } else {
        artifactIds = nexusService.getArtifactNames(
            nexusRequest, attributesRequest.getRepositoryName(), attributesRequest.getGroupId());
      }
    }

    return ArtifactTaskExecutionResponse.builder().nexusArtifactIds(artifactIds).build();
  }

  boolean isRegex(NexusArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }

  public void decryptRequestDTOs(NexusArtifactDelegateRequest nexusRequest) {
    if (nexusRequest.getNexusConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(
          nexusRequest.getNexusConnectorDTO().getAuth().getCredentials(), nexusRequest.getEncryptedDataDetails());
    }
  }
}
