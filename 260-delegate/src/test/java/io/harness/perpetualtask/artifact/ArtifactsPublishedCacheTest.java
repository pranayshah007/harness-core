/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.artifact;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArtifactsPublishedCacheTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAddArtifactCollectionResult() {
    ArtifactsPublishedCache<BuildDetails> cache = prepareCache(asList("0.1.0", "0.2.0", "0.3.0"), false);
    cache.addCollectionResult(prepareBuildDetails(asList("0.1.0", "0.4.0", "0.5.0")));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isFalse();

    cache = prepareCache(asList("0.1.0", "0.2.0", "0.3.0"), true);
    cache.addCollectionResult(null);
    assertThat(cache.needsToPublish()).isFalse();

    cache.addCollectionResult(prepareBuildDetails(asList("0.1.0", "0.4.0", "0.5.0")));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isTrue();
    assertThat(cache.getToBeDeletedArtifactKeys()).containsExactly("0.2.0", "0.3.0");

    ImmutablePair<List<BuildDetails>, Boolean> res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().stream().map(BuildDetails::getNumber).collect(Collectors.toList()))
        .containsExactly("0.4.0", "0.5.0");
    assertThat(res.getRight()).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRemoveDeletedArtifactKeys() {
    ArtifactsPublishedCache<BuildDetails> cache = prepareCache(asList("0.1.0", "0.2.0", "0.3.0"), true);
    cache.addCollectionResult(prepareBuildDetails(asList("0.1.0", "0.4.0", "0.5.0")));
    cache.removeDeletedArtifactKeys(null);
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isTrue();
    assertThat(cache.getToBeDeletedArtifactKeys()).containsExactly("0.2.0", "0.3.0");

    cache.removeDeletedArtifactKeys(Collections.singletonList("0.2.0"));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isTrue();
    assertThat(cache.getToBeDeletedArtifactKeys()).containsExactly("0.3.0");

    cache.removeDeletedArtifactKeys(Collections.singletonList("0.3.0"));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isFalse();
    assertThat(cache.getToBeDeletedArtifactKeys()).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAddPublishedBuildDetails() {
    ArtifactsPublishedCache<BuildDetails> cache = prepareCache(asList("0.1.0", "0.2.0", "0.3.0"), true);
    cache.addCollectionResult(prepareBuildDetails(asList("0.1.0", "0.4.0", "0.5.0")));
    cache.removeDeletedArtifactKeys(asList("0.2.0", "0.3.0"));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasToBeDeletedArtifactKeys()).isFalse();

    cache.addPublishedBuildDetails(null);
    ImmutablePair<List<BuildDetails>, Boolean> res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().stream().map(BuildDetails::getNumber).collect(Collectors.toList()))
        .containsExactly("0.4.0", "0.5.0");
    assertThat(res.getRight()).isFalse();

    cache.addPublishedBuildDetails(prepareBuildDetails(Collections.singletonList("0.4.0")));
    assertThat(cache.needsToPublish()).isTrue();
    assertThat(cache.hasUnpublishedBuildDetails()).isTrue();

    res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().stream().map(BuildDetails::getNumber).collect(Collectors.toList()))
        .containsExactly("0.5.0");
    assertThat(res.getRight()).isFalse();

    cache.addPublishedBuildDetails(prepareBuildDetails(Collections.singletonList("0.5.0")));
    assertThat(cache.needsToPublish()).isFalse();
    assertThat(cache.hasUnpublishedBuildDetails()).isFalse();

    res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft()).isEmpty();
    assertThat(res.getRight()).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testEmptyInitialPublishedArtifactKeys() {
    ArtifactsPublishedCache<BuildDetails> cache = prepareCache(null, true);
    cache.addCollectionResult(prepareBuildDetails(asList("0.1.0", "0.4.0", "0.5.0")));
    assertThat(cache.hasToBeDeletedArtifactKeys()).isFalse();

    ImmutablePair<List<BuildDetails>, Boolean> res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().stream().map(BuildDetails::getNumber).collect(Collectors.toList()))
        .containsExactly("0.1.0", "0.4.0", "0.5.0");
    assertThat(res.getRight()).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testBatchingOfUnpublishedArtifacts() {
    ArtifactsPublishedCache<BuildDetails> cache = prepareCache(null, true);
    List<String> buildNos = IntStream.rangeClosed(1, ArtifactsPublishedCache.ARTIFACT_ONE_TIME_PUBLISH_LIMIT + 10)
                                .boxed()
                                .map(String::valueOf)
                                .collect(Collectors.toList());
    cache.addCollectionResult(prepareBuildDetails(buildNos));
    assertThat(cache.hasToBeDeletedArtifactKeys()).isFalse();

    ImmutablePair<List<BuildDetails>, Boolean> res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().size()).isEqualTo(ArtifactsPublishedCache.ARTIFACT_ONE_TIME_PUBLISH_LIMIT);
    assertThat(res.getRight()).isTrue();

    cache.addPublishedBuildDetails(res.getLeft());
    res = cache.getLimitedUnpublishedBuildDetails();
    assertThat(res).isNotNull();
    assertThat(res.getLeft()).isNotNull();
    assertThat(res.getLeft().size()).isEqualTo(10);
    assertThat(res.getRight()).isFalse();
  }

  private ArtifactsPublishedCache<BuildDetails> prepareCache(
      Collection<String> publishedArtifactKeys, boolean enableCleanup) {
    Function<BuildDetails, String> buildDetailsFunction = BuildDetails::getNumber;
    return new ArtifactsPublishedCache(publishedArtifactKeys, buildDetailsFunction, enableCleanup);
  }

  private List<BuildDetails> prepareBuildDetails(List<String> buildNos) {
    return buildNos == null
        ? null
        : buildNos.stream().map(buildNo -> aBuildDetails().withNumber(buildNo).build()).collect(Collectors.toList());
  }
}
