/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactCorrelationDetails;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@OwnedBy(HarnessTeam.DX)
@FieldNameConstants(innerTypeName = "ArtifactDetailsKeys")
public class ArtifactDetails {
  private String displayName;
  private String artifactId;
  private String tag; // this corresponds to the build number of the artifact
  private ArtifactCorrelationDetails artifactIdentity;
}
