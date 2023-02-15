/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class BambooArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Bamboo Job build number */
  String build;

  /** Bamboo Job name */
  String planKey;

  /** Bamboo artifact path */
  String artifactPath;

  @Builder
  public BambooArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String planKey, String build, String artifactPath) {
    super(buildDetails, sourceType);
    this.build = build;
    this.planKey = planKey;
    this.artifactPath = artifactPath;
  }

  @Override
  public String describe() {
    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null) + "\nplanKey: " + getPlanKey()
        + "\nartifactPath: " + getArtifactPath() + "\nbuild: " + getBuild();
  }
}
