/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.s3;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.mappers.S3RequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsApiHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class S3ArtifactTaskHandler extends DelegateArtifactTaskHandler<S3ArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  private final AwsApiHelperService awsApiHelperService;
  @Inject private final AwsNgConfigMapper awsNgConfigMapper;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(S3ArtifactDelegateRequest s3ArtifactDelegateRequest) {
    String filePath = s3ArtifactDelegateRequest.getFilePath();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse;

    if (EmptyPredicate.isNotEmpty(filePath)) {
      AwsConnectorDTO awsConnectorDTO = s3ArtifactDelegateRequest.getAwsConnectorDTO();
      AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

      BuildDetails buildDetails = awsApiHelperService.getBuild(awsInternalConfig, s3ArtifactDelegateRequest.getRegion(),
          s3ArtifactDelegateRequest.getBucketName(), s3ArtifactDelegateRequest.getFilePath());

      if (buildDetails == null) {
        throw new InvalidRequestException("No build exists");
      }

      artifactTaskExecutionResponse =
          ArtifactTaskExecutionResponse.builder()
              .artifactDelegateResponse(S3RequestResponseMapper.toS3Response(buildDetails, s3ArtifactDelegateRequest))
              .build();

    } else {
      AwsConnectorDTO awsConnectorDTO = s3ArtifactDelegateRequest.getAwsConnectorDTO();
      AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

      List<BuildDetails> builds =
          awsApiHelperService.listBuilds(awsInternalConfig, s3ArtifactDelegateRequest.getRegion(),
              s3ArtifactDelegateRequest.getBucketName(), s3ArtifactDelegateRequest.getFilePathRegex());

      if (builds.isEmpty()) {
        throw new InvalidRequestException("No last successful build");
      }

      BuildDetails buildDetails = builds.get(builds.size() - 1);

      artifactTaskExecutionResponse =
          ArtifactTaskExecutionResponse.builder()
              .artifactDelegateResponse(S3RequestResponseMapper.toS3Response(buildDetails, s3ArtifactDelegateRequest))
              .build();
    }
    return artifactTaskExecutionResponse;
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(S3ArtifactDelegateRequest s3ArtifactDelegateRequest) {
    AwsConnectorDTO awsConnectorDTO = s3ArtifactDelegateRequest.getAwsConnectorDTO();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

    List<BuildDetails> builds = awsApiHelperService.listBuilds(awsInternalConfig, s3ArtifactDelegateRequest.getRegion(),
        s3ArtifactDelegateRequest.getBucketName(), s3ArtifactDelegateRequest.getFilePathRegex());

    if (builds.isEmpty()) {
      throw new InvalidRequestException("No builds");
    }

    List<S3ArtifactDelegateResponse> s3ArtifactDelegateResponseList =
        builds.stream()
            .map(build -> S3RequestResponseMapper.toS3Response(build, s3ArtifactDelegateRequest))
            .collect(Collectors.toList());

    return getSuccessTaskExecutionResponse(s3ArtifactDelegateResponseList);
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<S3ArtifactDelegateResponse> s3ArtifactDelegateResponseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(s3ArtifactDelegateResponseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  @Override
  public void decryptRequestDTOs(S3ArtifactDelegateRequest request) {
    if (request.getAwsConnectorDTO().getCredential() != null) {
      secretDecryptionService.decrypt(
          request.getAwsConnectorDTO().getCredential().getConfig(), request.getEncryptedDataDetails());
    }
  }
}
