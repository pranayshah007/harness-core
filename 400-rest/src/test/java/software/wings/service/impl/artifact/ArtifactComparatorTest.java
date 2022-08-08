/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class ArtifactComparatorTest extends CategoryTest {
  private final Artifact.Builder artifactBuilder = anArtifact()
                                                       .withAppId(APP_ID)
                                                       .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                       .withRevision("1.0")
                                                       .withDisplayName("DISPLAY_NAME")
                                                       .withCreatedAt(System.currentTimeMillis())
                                                       .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSortArtifactDescendingOrder() {
    List<Artifact> artifacts =
        asList(artifactBuilder
                   .withMetadata(
                       new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "todolist-1.0-1.x86_64.rpm")))
                   .but()
                   .build(),
            artifactBuilder
                .withMetadata(
                    new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "todolist-1.0-10.x86_64.rpm")))
                .but()
                .build(),
            artifactBuilder
                .withMetadata(
                    new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "todolist-1.0-5.x86_64.rpm")))
                .but()
                .build(),
            artifactBuilder
                .withMetadata(
                    new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "todolist-1.0-15.x86_64.rpm")))
                .but()
                .build());
    assertThat(artifacts.stream().sorted(new ArtifactComparator()).collect(toList()))
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-15.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-1.x86_64.rpm");
  }
}
