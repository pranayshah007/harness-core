/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.stepstatus.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@JsonTypeName(ArtifactMetadataTypes.DOCKER_ARTIFACT_METADATA)
@OwnedBy(HarnessTeam.CI)
public class DockerArtifactMetadata implements ArtifactMetadataSpec {
  String registryType;
  String registryUrl;
  @Singular List<DockerArtifactDescriptor> dockerArtifacts;
  String type;
}
