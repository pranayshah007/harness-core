/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudstorage;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class GoogleCloudStorageArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** refers to GCP Project*/
  String project;
  /** refers to GCS bucket*/
  String bucket;
  /** refers to artifact path in GCS bucket*/
  String artifactPath;

  @Builder
  public GoogleCloudStorageArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String project, String bucket, String artifactPath) {
    super(buildDetails, sourceType);

    this.project = project;
    this.bucket = bucket;
    this.artifactPath = artifactPath;
  }
}
