/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.response;

import io.harness.delegate.task.artifacts.ArtifactSourceType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Interface for getting Dto response to create concrete Artifact.
 */
@Getter
@AllArgsConstructor
public abstract class ArtifactDelegateResponse implements ArtifactDelegateResponseBase {
  ArtifactBuildDetailsNG buildDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;

  @Override
  public String describe() {
    return "type: " + sourceType.getDisplayName();
  }
}
