/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class CustomArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Jenkins Job build number */
  String version;

  /** Jenkins artifact path */
  Map<String, String> metadata;

  @Builder
  public CustomArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String version, Map<String, String> metadata) {
    super(buildDetails, sourceType);
    this.version = version;
    this.metadata = metadata;
  }
}
