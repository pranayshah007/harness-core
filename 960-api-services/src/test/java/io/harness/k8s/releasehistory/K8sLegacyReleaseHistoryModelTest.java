/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class K8sLegacyReleaseHistoryModelTest extends CategoryTest {
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSuccessfulRelease() {
    io.harness.k8s.releasehistory.ReleaseHistory releaseHistory =
        io.harness.k8s.releasehistory.ReleaseHistory.createNew();
    assertThat(releaseHistory.getRelease(1)).isNull();

    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(1);
    releaseHistory.setReleaseStatus(Succeeded);
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(2);
    releaseHistory.setReleaseStatus(Succeeded);
    assertThat(releaseHistory.getRelease(1)).isNotNull();
    assertThat(releaseHistory.getRelease(1).getNumber()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCleanup() {
    io.harness.k8s.releasehistory.ReleaseHistory releaseHistory =
        io.harness.k8s.releasehistory.ReleaseHistory.createNew();
    // check no exception thrown when empty
    releaseHistory.cleanup();

    // should delete failed release
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(0);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Failed);
    releaseHistory.cleanup();
    assertThat(releaseHistory.getReleases()).isEmpty();

    // should keep only latest successful and remove failed
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(1);
    releaseHistory.setReleaseStatus(Succeeded);
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(2);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Failed);
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(3);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Failed);
    releaseHistory.createNewRelease(emptyList());
    releaseHistory.setReleaseNumber(4);
    releaseHistory.setReleaseStatus(Succeeded);
    releaseHistory.cleanup();
    assertThat(releaseHistory.getReleases()).hasSize(1);
    assertThat(releaseHistory.getLatestRelease().getNumber()).isEqualTo(4);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCloneInternal() {
    io.harness.k8s.releasehistory.K8sLegacyRelease release =
        io.harness.k8s.releasehistory.K8sLegacyRelease.builder().number(1).status(Succeeded).build();
    io.harness.k8s.releasehistory.ReleaseHistory releaseHistory = io.harness.k8s.releasehistory.ReleaseHistory.builder()
                                                                      .version("version1")
                                                                      .releases(ImmutableList.of(release))
                                                                      .build();

    io.harness.k8s.releasehistory.ReleaseHistory clonedReleaseHistory = releaseHistory.cloneInternal();
    assertThat(clonedReleaseHistory.getVersion()).isEqualTo(releaseHistory.getVersion());
    assertThat(clonedReleaseHistory.getReleases().get(0)).isEqualTo(release);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateNewReleaseWithResourceMap() {
    KubernetesResource kubernetesResource =
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(KubernetesResourceId.builder().name("resource-name").build())
            .build();
    io.harness.k8s.releasehistory.ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.createNewReleaseWithResourceMap(singletonList(kubernetesResource));
    assertThat(releaseHistory.getReleases().size()).isEqualTo(1);
    K8sLegacyRelease release = releaseHistory.getReleases().get(0);
    assertThat(release.getResources().get(0).getName()).isEqualTo("resource-name");
    assertThat(release.getResourcesWithSpec().get(0).getSpec()).isEqualTo("spec");
  }
}
