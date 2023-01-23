/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.artifactsource;

import io.harness.delegate.task.artifacts.ArtifactSourceType;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@FieldNameConstants(innerTypeName = "DockerArtifactSourceKeys")
@EqualsAndHashCode(callSuper = true)
public class DockerArtifactSource extends ArtifactSource {
  /** Docker hub registry connector identifier. */
  @NotEmpty String connectorRef;
  /** Images in repos need to be referenced via a path */
  @NotEmpty String imagePath;

  @Builder
  public DockerArtifactSource(String uuid, String accountId, ArtifactSourceType sourceType, String uniqueHash,
      long createdAt, long lastUpdatedAt, Long version, String connectorRef, String imagePath) {
    super(uuid, accountId, sourceType, uniqueHash, createdAt, lastUpdatedAt, version);
    this.connectorRef = connectorRef;
    this.imagePath = imagePath;
  }
}
