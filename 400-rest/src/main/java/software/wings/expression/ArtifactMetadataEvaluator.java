/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.beans.ArtifactMetadata;
import io.harness.logging.AutoLogContext;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.service.intfc.BuildSourceService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ArtifactMetadataEvaluator extends ArtifactMetadata {
  private transient ArtifactMetaInfo metaInfo;
  private transient String buildNo;
  private transient ArtifactStream artifactStream;
  private transient BuildSourceService buildSourceService;

  public ArtifactMetadataEvaluator(
      ArtifactMetadata metadata, String buildNo, ArtifactStream artifactStream, BuildSourceService buildSourceService) {
    super(metadata);
    this.buildNo = buildNo;
    this.artifactStream = artifactStream;
    this.buildSourceService = buildSourceService;
  }

  public synchronized void fetchArtifactMetaInfo() {
    if (this.metaInfo != null) {
      return;
    }
    try (AutoLogContext ignore2 = new ArtifactStreamLogContext(
             artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
      metaInfo = buildSourceService.getArtifactMetaInfo(artifactStream, buildNo);
    }
  }

  @Override
  public synchronized String getSHA() {
    fetchArtifactMetaInfo();
    return metaInfo == null ? null : metaInfo.getSha();
  }
}
