/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts;

import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class S3ArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Buckets in repos*/
  String bucketName;

  /** artifactPath refers to exact tag number */
  String artifactPath;

  @Builder
  public S3ArtifactDelegateResponse(
      ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType, String bucketName, String artifactPath) {
    super(buildDetails, sourceType);

    this.bucketName = bucketName;
    this.artifactPath = artifactPath;
  }
}
