/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WithIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * wrapper object for dockerhub, gcr, etc element.
 * artifacts:
 *      primary:
 *             type: dockerhub
 *             spec:
 *      sidecars
 *          -sidecar:
 *              identifier:
 *              type: dockerhub
 *              spec:
 */
@OwnedBy(HarnessTeam.PIPELINE)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
public interface ArtifactConfig extends WithIdentifier, OverridesApplier<ArtifactConfig> {
  @JsonIgnore ArtifactSourceType getSourceType();
  @JsonIgnore String getUniqueHash();
  @JsonIgnore boolean isPrimaryArtifact();
  @JsonIgnore void setPrimaryArtifact(boolean primaryArtifact);
  void setIdentifier(String identifier);
  @Override @JsonIgnore String getIdentifier();
}
