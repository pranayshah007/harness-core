/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.docker;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class DockerArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Images in repos need to be referenced via a path */
  String imagePath;
  /** Tag refers to exact tag number */
  String tag;
  /** Label refers to tag */
  Map<String, String> label;

  @Builder
  public DockerArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String imagePath, String tag, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.imagePath = imagePath;
    this.tag = tag;
    this.label = label;
  }
}
