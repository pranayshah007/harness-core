/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;
import lombok.Builder;
import lombok.Data;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.S3_NAME)
public class S3ArtifactSummary implements ArtifactSummary {
  String bucketName;
  String filePath;

  @Override
  public String getDisplayName() {
    return bucketName + ":" + filePath;
  }

  @Override
  public String getType() {
    return ArtifactSourceConstants.S3_NAME;
  }
}
