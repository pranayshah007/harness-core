/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GoogleCloudStorageRequestResponseMapper {
  public GoogleCloudStorageArtifactDelegateResponse toGoogleCloudStorageResponse(
      BuildDetails buildDetails, GoogleCloudStorageArtifactDelegateRequest request) {
    return GoogleCloudStorageArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT)
        .project(request.getProject())
        .bucket(request.getBucket())
        .artifactPath(buildDetails.getArtifactPath())
        .build();
  }
}
