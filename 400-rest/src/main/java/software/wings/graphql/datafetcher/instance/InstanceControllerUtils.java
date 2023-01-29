/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.instance;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.persistence.HPersistence;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.graphql.datafetcher.artifact.ArtifactController;
import software.wings.graphql.schema.type.artifact.QLArtifact;
import software.wings.graphql.schema.type.artifact.QLArtifact.QLArtifactBuilder;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InstanceControllerUtils {
  public static final String DUMMY_ARTIFACT_SOURCE_ID = "DUMMY_ARTIFACT_SOURCE_ID";
  @Inject HPersistence persistence;

  public QLArtifact getQlArtifact(Instance instance) {
    Artifact artifact = persistence.get(Artifact.class, instance.getLastArtifactId());
    if (artifact == null) {
      return QLArtifact.builder()
          .buildNo(instance.getLastArtifactBuildNum())
          .artifactSourceId(EmptyPredicate.isNotEmpty(instance.getLastArtifactStreamId())
                  ? instance.getLastArtifactStreamId()
                  : DUMMY_ARTIFACT_SOURCE_ID)
          .id(instance.getLastArtifactId())
          .collectedAt(instance.getLastDeployedAt())
          .build();
    }
    QLArtifactBuilder qlArtifactBuilder = QLArtifact.builder();
    ArtifactController.populateArtifact(artifact, qlArtifactBuilder);
    return qlArtifactBuilder.build();
  }
}
