/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class GoogleCloudStorageRequestResponseMapper {
  public List<GoogleCloudStorageArtifactDelegateResponse> toGoogleCloudStorageResponseList(
      List<BuildDetails> buildDetails, GoogleCloudStorageArtifactDelegateRequest request) {
    List<GoogleCloudStorageArtifactDelegateResponse> gcsArtifactDelegateResponseList = new ArrayList<>();
    for (BuildDetails buildDetail : buildDetails) {
      gcsArtifactDelegateResponseList.add(toGoogleCloudStorageResponse(buildDetail, request));
    }
    return gcsArtifactDelegateResponseList;
  }

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
