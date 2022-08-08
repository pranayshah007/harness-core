/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.DOCKER_REGISTRY_NAME)
@RecasterAlias("io.harness.ngpipeline.pipeline.executions.beans.DockerArtifactSummary")
public class DockerArtifactSummary implements ArtifactSummary {
  String imagePath;
  String tag;

  @Override
  public String getDisplayName() {
    return imagePath + ":" + tag;
  }

  @Override
  public String getType() {
    return ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
  }
}
