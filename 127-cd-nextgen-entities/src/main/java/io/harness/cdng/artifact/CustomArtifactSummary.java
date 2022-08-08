/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME)
@RecasterAlias("io.harness.cdng.artifact.CustomArtifactSummary")
public class CustomArtifactSummary implements ArtifactSummary {
  private String version;

  @Override
  public String getDisplayName() {
    return version;
  }

  @Override
  public String getType() {
    return ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME;
  }
}
