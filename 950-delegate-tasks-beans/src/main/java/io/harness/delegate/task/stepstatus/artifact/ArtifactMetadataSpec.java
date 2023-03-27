/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.stepstatus.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(HarnessTeam.CI)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DockerArtifactMetadata.class, name = ArtifactMetadataTypes.DOCKER_ARTIFACT_METADATA)
  , @JsonSubTypes.Type(value = FileArtifactMetadata.class, name = ArtifactMetadataTypes.FILE_ARTIFACT_METADATA),
      @JsonSubTypes.Type(value = SscaArtifactMetadata.class, name = ArtifactMetadataTypes.SSCA_ARTIFACT_METADATA)
})
public interface ArtifactMetadataSpec {}
