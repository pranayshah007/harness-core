/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.BambooRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.BambooBuildService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class BambooArtifactTaskHandler extends DelegateArtifactTaskHandler<BambooArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  @Inject private BambooBuildService bambooBuildService;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(BambooArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = bambooBuildService.validateArtifactServer(
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(BambooArtifactDelegateRequest attributesRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName(attributesRequest.getPlanKey())
                                                            .artifactPaths(attributesRequest.getArtifactPaths())
                                                            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
                                                            .build();
    List<BuildDetails> buildDetails = bambooBuildService.getBuilds(null, artifactStreamAttributes,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().buildDetails(buildDetails).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(BambooArtifactDelegateRequest attributesRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName(attributesRequest.getPlanKey())
                                                            .artifactPaths(attributesRequest.getArtifactPaths())
                                                            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
                                                            .build();
    BuildDetails buildDetails = bambooBuildService.getLastSuccessfulBuild(null, artifactStreamAttributes,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    BambooArtifactDelegateResponse bambooArtifactDelegateResponse =
        BambooRequestResponseMapper.toBambooArtifactDelegateResponse(buildDetails, attributesRequest);
    return getSuccessTaskExecutionResponse(
        Collections.singletonList(bambooArtifactDelegateResponse), Collections.singletonList(buildDetails));
  }

  public ArtifactTaskExecutionResponse getPlans(BambooArtifactDelegateRequest attributesRequest) {
    Map<String, String> plans = bambooBuildService.getPlans(
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().plans(plans).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getArtifactPaths(BambooArtifactDelegateRequest attributesRequest) {
    List<String> artifactPaths = bambooBuildService.getArtifactPaths(attributesRequest.getPlanKey(), null,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().artifactPath(artifactPaths).build();
  }

  @Override
  public void decryptRequestDTOs(BambooArtifactDelegateRequest bambooArtifactDelegateRequest) {
    if (bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth().getCredentials(),
          bambooArtifactDelegateRequest.getEncryptedDataDetails());
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<BambooArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }
}
