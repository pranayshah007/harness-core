/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class JenkinsArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Jenkins Job build number */
  String build;

  /** Jenkins Job name */
  String jobName;

  /** Jenkins artifact path */
  String artifactPath;

  @Builder
  public JenkinsArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String jobName, String build, String artifactPath) {
    super(buildDetails, sourceType);
    this.build = build;
    this.jobName = jobName;
    this.artifactPath = artifactPath;
  }
}
