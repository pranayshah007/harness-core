/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.artifact.QLArtifact.QLArtifactBuilder;
import software.wings.persistence.artifact.Artifact;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ArtifactController {
  public static void populateArtifact(Artifact artifact, QLArtifactBuilder qlArtifactBuilder) {
    qlArtifactBuilder.id(artifact.getUuid())
        .buildNo(artifact.getBuildNo())
        .collectedAt(artifact.getCreatedAt())
        .artifactSourceId(artifact.getArtifactStreamId());
  }
}
