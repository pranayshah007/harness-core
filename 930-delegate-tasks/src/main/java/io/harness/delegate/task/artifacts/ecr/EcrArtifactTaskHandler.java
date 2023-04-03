/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.delegate.task.artifacts.ArtifactServiceConstant.ACCEPT_ALL_REGEX;

import static software.wings.helpers.ext.ecr.EcrService.MAX_NO_OF_TAGS_PER_IMAGE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDateDescending;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.EcrRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.ecr.EcrService;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class EcrArtifactTaskHandler extends DelegateArtifactTaskHandler<EcrArtifactDelegateRequest> {
  private final EcrService ecrService;
  private final AwsApiHelperService awsApiHelperService;
  private final SecretDecryptionService secretDecryptionService;
  @Inject AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Inject private final AwsNgConfigMapper awsNgConfigMapper;

  @Override
  public ArtifactTaskExecutionResponse getBuilds(EcrArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds;
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrimageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());
    builds = ecrService.getBuilds(awsInternalConfig, ecrimageUrl, attributesRequest.getRegion(),
        attributesRequest.getImagePath(), MAX_NO_OF_TAGS_PER_IMAGE);
    List<EcrArtifactDelegateResponse> ecrArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDateDescending()) // Sort by latest timestamp.
            .map(build -> EcrRequestResponseMapper.toEcrResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(ecrArtifactDelegateResponseList);
  }

  public void decryptRequestDTOs(EcrArtifactDelegateRequest ecrRequest) {
    if (ecrRequest.getAwsConnectorDTO().getCredential() != null
        && ecrRequest.getAwsConnectorDTO().getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) ecrRequest.getAwsConnectorDTO().getCredential().getConfig(),
          ecrRequest.getEncryptedDataDetails());
    }
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(EcrArtifactDelegateRequest attributesRequest) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrimageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());
    boolean validateCredentials = ecrService.validateCredentials(
        awsInternalConfig, ecrimageUrl, attributesRequest.getRegion(), attributesRequest.getImagePath());
    return ArtifactTaskExecutionResponse.builder()
        .isArtifactServerValid(validateCredentials)
        .artifactDelegateResponse(EcrArtifactDelegateResponse.builder().imageUrl(ecrimageUrl).build())
        .build();
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactImage(EcrArtifactDelegateRequest attributesRequest) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrimageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());
    boolean verifyImageName = ecrService.verifyImageName(
        awsInternalConfig, ecrimageUrl, attributesRequest.getRegion(), attributesRequest.getImagePath());
    return ArtifactTaskExecutionResponse.builder().isArtifactSourceValid(verifyImageName).build();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<EcrArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(EcrArtifactDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrimageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());
    if (EmptyPredicate.isNotEmpty(attributesRequest.getTagRegex())
        || attributesRequest.getTag().equals(ACCEPT_ALL_REGEX)) {
      String tagRegex = isRegex(attributesRequest) ? attributesRequest.getTagRegex() : attributesRequest.getTag();
      lastSuccessfulBuild = ecrService.getLastSuccessfulBuildFromRegex(
          awsInternalConfig, ecrimageUrl, attributesRequest.getRegion(), attributesRequest.getImagePath(), tagRegex);
    } else {
      lastSuccessfulBuild = ecrService.verifyBuildNumber(awsInternalConfig, ecrimageUrl, attributesRequest.getRegion(),
          attributesRequest.getImagePath(), attributesRequest.getTag());
    }
    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        EcrRequestResponseMapper.toEcrResponse(lastSuccessfulBuild, attributesRequest);
    return getSuccessTaskExecutionResponse(Collections.singletonList(ecrArtifactDelegateResponse));
  }

  public ArtifactTaskExecutionResponse getEcrImageUrl(EcrArtifactDelegateRequest attributesRequest) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrImageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());

    return getSuccessTaskExecutionResponse(
        Collections.singletonList(EcrArtifactDelegateResponse.builder().imageUrl(ecrImageUrl).build()));
  }

  public ArtifactTaskExecutionResponse getAmazonEcrAuthToken(EcrArtifactDelegateRequest attributesRequest) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    String ecrImageUrl = awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        awsInternalConfig, attributesRequest.getRegion(), attributesRequest.getImagePath());
    String authToken = awsEcrApiHelperServiceDelegate.getAmazonEcrAuthToken(
        awsInternalConfig, ecrImageUrl.substring(0, ecrImageUrl.indexOf('.')), attributesRequest.getRegion());

    List<EcrArtifactDelegateResponse> ecrArtifactDelegateResponseList = new ArrayList<>();
    ecrArtifactDelegateResponseList.add(EcrArtifactDelegateResponse.builder().authToken(authToken).build());
    return getSuccessTaskExecutionResponse(ecrArtifactDelegateResponseList);
  }

  @Override
  public ArtifactTaskExecutionResponse getImages(EcrArtifactDelegateRequest attributesRequest) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributesRequest);
    List<String> repoNames = ecrService.listEcrRegistry(awsInternalConfig, attributesRequest.getRegion());
    return ArtifactTaskExecutionResponse.builder().artifactImages(repoNames).build();
  }

  @VisibleForTesting
  protected AwsInternalConfig getAwsInternalConfig(EcrArtifactDelegateRequest attributesRequest) {
    AwsConnectorDTO awsConnectorDTO = attributesRequest.getAwsConnectorDTO();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(attributesRequest.getRegion());
    return awsInternalConfig;
  }

  boolean isRegex(EcrArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }
}
