/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;

import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
public class S3RequestResponseMapper {
  public S3ArtifactDelegateResponse toS3Response(BuildDetails buildDetails, S3ArtifactDelegateRequest request) {
    return S3ArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .bucketName(request.getBucketName())
        .filePath(buildDetails.getArtifactPath())
        .filePathRegex(request.getFilePathRegex())
        .sourceType(ArtifactSourceType.AMAZONS3)
        .metadata(buildDetails.getMetadata())
        .build();
  }
}
