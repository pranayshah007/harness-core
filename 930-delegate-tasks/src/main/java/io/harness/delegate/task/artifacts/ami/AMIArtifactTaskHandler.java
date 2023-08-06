/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ami;
import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.ami.service.AMIRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.AMIRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AMIArtifactTaskHandler extends DelegateArtifactTaskHandler<AMIArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;

  private final AMIRegistryService amiRegistryService;

  @Inject private final AwsNgConfigMapper awsNgConfigMapper;

  @Override
  public ArtifactTaskExecutionResponse getBuilds(AMIArtifactDelegateRequest attributes) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributes);

    List<BuildDetails> builds = amiRegistryService.listBuilds(awsInternalConfig, attributes.getRegion(),
        attributes.getTags(), attributes.getFilters(), attributes.getVersionRegex());

    List<AMIArtifactDelegateResponse> amiArtifactDelegateResponseList = new ArrayList<>();

    for (BuildDetails b : builds) {
      AMIArtifactDelegateResponse artifactDelegateResponse =
          AMIArtifactDelegateResponse.builder().version(b.getNumber()).sourceType(attributes.getSourceType()).build();

      amiArtifactDelegateResponseList.add(artifactDelegateResponse);
    }

    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(amiArtifactDelegateResponseList)
        .buildDetails(builds)
        .build();
  }

  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(AMIArtifactDelegateRequest attributes) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributes);

    BuildDetails lastSuccessfulBuild;

    if (isRegex(attributes)) {
      lastSuccessfulBuild = amiRegistryService.getLastSuccessfulBuild(awsInternalConfig, attributes.getRegion(),
          attributes.getTags(), attributes.getFilters(), attributes.getVersionRegex());
    } else {
      lastSuccessfulBuild = amiRegistryService.getBuild(awsInternalConfig, attributes.getRegion(), attributes.getTags(),
          attributes.getFilters(), attributes.getVersion());
    }

    if (lastSuccessfulBuild == null) {
      lastSuccessfulBuild = new BuildDetails();
    }

    AMIArtifactDelegateResponse amiArtifactDelegateResponse =
        AMIRequestResponseMapper.toAMIArtifactResponse(lastSuccessfulBuild, attributes);

    return getSuccessTaskExecutionResponse(
        Collections.singletonList(amiArtifactDelegateResponse), Collections.singletonList(lastSuccessfulBuild));
  }

  public void decryptRequestDTOs(AMIArtifactDelegateRequest attributes) {
    AwsConnectorDTO awsConnectorDTO = attributes.getAwsConnectorDTO();

    List<EncryptedDataDetail> encryptionDetails = attributes.getEncryptedDataDetails();

    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<AMIArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(AMIArtifactDelegateRequest artifactDelegateRequest) {
    return StringUtils.isNotEmpty(artifactDelegateRequest.getVersionRegex());
  }

  private AwsInternalConfig getAwsInternalConfig(AMIArtifactDelegateRequest request) {
    AwsConnectorDTO awsConnectorDTO = request.getAwsConnectorDTO();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(request.getRegion());
    return awsInternalConfig;
  }

  public ArtifactTaskExecutionResponse listTags(AMIArtifactDelegateRequest attributes) {
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(attributes);

    AMITagsResponse tagsResponse = amiRegistryService.listTags(awsInternalConfig, attributes.getRegion());

    return ArtifactTaskExecutionResponse.builder().amiTags(tagsResponse).build();
  }
}
