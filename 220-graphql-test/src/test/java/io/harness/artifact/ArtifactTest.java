/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.generator.artifactstream.ArtifactStreamManager.ArtifactStreams;
import io.harness.rule.Owner;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Application;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.graphql.schema.type.artifact.QLArtifact.QLArtifactKeys;
import software.wings.service.intfc.ArtifactService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class ArtifactTest extends GraphQLTest {
  @Inject private ArtifactService artifactService;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject ArtifactStreamManager artifactStreamManager;

  @Test
  @Owner(developers = SRINIVAS)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryArtifact() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    final Application application = applicationGenerator.ensureApplication(
        seed, owners, Application.Builder.anApplication().name("Artifact App").build());
    assertThat(application).isNotNull();
    String accountId = application.getAccountId();
    owners.add(application);
    final ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_ECHO_WAR);

    assertThat(artifactStream).isNotNull();

    Artifact artifact = Artifact.Builder.anArtifact()
                            .withAppId(application.getUuid())
                            .withArtifactStreamId(artifactStream.getUuid())
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "1.2")))
                            .withDisplayName("Some artifact")
                            .build();

    assertThat(artifactService.create(artifact, true)).isNotNull();
    String query = $GQL(/*
{
  artifact(artifactId: "%s") {
    id
    buildNo
    collectedAt
  }
}*/ artifact.getUuid());

    QLTestObject qlArtifact = qlExecute(query, accountId);
    assertThat(qlArtifact.get(QLArtifactKeys.id)).isEqualTo(artifact.getUuid());
    assertThat(qlArtifact.get(QLArtifactKeys.buildNo)).isEqualTo(artifact.getBuildNo());
    assertThat(qlArtifact.get(QLArtifactKeys.collectedAt)).isEqualTo(artifact.getCreatedAt());
  }
}
