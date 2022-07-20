/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Data
@Value
@Builder
@TypeAlias("gcpArtifactUploadInfo")
@RecasterAlias("io.harness.beans.GCPArtifactUploadInfo")
public class GCPArtifactUploadInfo implements ArtifactUploadInfo {
  private ArtifactUploadInfo.Type type = Type.GCP;

  GCPArtifactUploadInfo() {}

  @Override
  public ArtifactUploadInfo.Type getType() {
    return type;
  }
}
