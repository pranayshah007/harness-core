/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.k8s.releasehistory.ReleaseHistory.createFromData;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sLegacyReleaseHistoryTest extends CategoryTest {
  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void smokeTest() throws Exception {
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.createNewRelease(
        ImmutableList.of(KubernetesResourceId.builder().kind("Deployment").name("nginx").namespace("default").build()));

    String yamlHistory = releaseHistory.getAsYaml();

    ReleaseHistory releaseHistory1 = createFromData(yamlHistory);
    assertThat(releaseHistory1.getAsYaml()).isEqualTo(yamlHistory);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void noReleaseTest() {
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();

    try {
      releaseHistory.getLatestRelease();
      fail("Should not reach here.");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("No existing release found.");
    }

    try {
      releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
      fail("Should not reach here.");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("No existing release found.");
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void createReleaseTest() {
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.createNewRelease(
        ImmutableList.of(KubernetesResourceId.builder().kind("Deployment").name("nginx").namespace("default").build()));
    K8sLegacyRelease release = releaseHistory.getLatestRelease();

    assertThat(release.getNumber()).isEqualTo(1);
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.InProgress);
    assertThat(release.getResources()).hasSize(1);
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("kind", "Deployment");
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("name", "nginx");
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("namespace", "default");

    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    release = releaseHistory.getLatestRelease();
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.Succeeded);

    releaseHistory.createNewRelease(ImmutableList.of(
        KubernetesResourceId.builder().kind("Deployment").name("nginx-1").namespace("default").build()));
    release = releaseHistory.getLatestRelease();

    assertThat(release.getNumber()).isEqualTo(2);
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.InProgress);
    assertThat(release.getResources()).hasSize(1);
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("kind", "Deployment");
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("name", "nginx-1");
    assertThat(release.getResources().get(0)).hasFieldOrPropertyWithValue("namespace", "default");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void getLastSuccessfulReleaseTest() {
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();

    K8sLegacyRelease release = releaseHistory.getLastSuccessfulRelease();
    assertThat(release).isNull();

    releaseHistory.createNewRelease(
        ImmutableList.of(KubernetesResourceId.builder().kind("Deployment").name("nginx").namespace("default").build()));

    release = releaseHistory.getLastSuccessfulRelease();
    assertThat(release).isNull();

    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    release = releaseHistory.getLastSuccessfulRelease();
    assertThat(release).isNotNull();
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.Succeeded);

    releaseHistory.setReleaseStatus(IK8sRelease.Status.Failed);
    release = releaseHistory.getLastSuccessfulRelease();
    assertThat(release).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void getBlueGreenStageReleaseTest() {
    // Non BG Workload
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    K8sLegacyRelease release = releaseHistory.getBlueGreenStageRelease();
    assertThat(release).isNull();

    // BG Workload but bgEnv not set
    releaseHistory = createNewRelease(ImmutableList.of(
        KubernetesResourceId.builder().kind("Deployment").name("nginx-blue").namespace("default").build()));
    release = releaseHistory.getBlueGreenStageRelease();
    assertThat(release).isNull();

    // Release is not dependent on status of stage environment
    releaseHistory = createNewBGRelease();
    release = releaseHistory.getBlueGreenStageRelease();
    assertThat(release).isNotNull();
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.InProgress);

    // Status set of primary env does not affect outcome of stage env
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    release = releaseHistory.getBlueGreenStageRelease();
    assertThat(release).isNotNull();
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.InProgress);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testSetReleaseToReleaseHistory() {
    ReleaseHistory releaseHistory = createNewBGRelease();
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    K8sLegacyRelease release = releaseHistory.getBlueGreenStageRelease();
    release.setManifestHash("sampleManifestHash");
    assertThat(release).isNotNull();
    assertThat(release.getStatus()).isEqualTo(IK8sRelease.Status.InProgress);
    assertThat(release.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgStageEnv);
    assertThat(release.getManifestHash()).isEqualTo("sampleManifestHash");
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .bgEnvironment(HarnessLabelValues.bgStageEnv)
                                            .manifestHash("differentManifestHash")
                                            .build();
    releaseHistory.addReleaseToReleaseHistory(k8sLegacyRelease);
    assertThat(releaseHistory.getReleases().size()).isEqualTo(3);
    assertThat(releaseHistory.getReleases().get(2)).isEqualTo(release);
    assertThat(releaseHistory.getReleases().get(1).getBgEnvironment()).isEqualTo(HarnessLabelValues.bgPrimaryEnv);
    assertThat(releaseHistory.getReleases().get(0)).isEqualTo(k8sLegacyRelease);
    assertThat(releaseHistory.getReleases().get(0).getManifestHash()).isEqualTo("differentManifestHash");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testSetReleaseToReleaseHistoryInsertionOrder() {
    ReleaseHistory releaseHistory = createNewBGRelease();
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    K8sLegacyRelease release = releaseHistory.getBlueGreenStageRelease();
    K8sLegacyRelease newk8sLegacyRelease =
        K8sLegacyRelease.builder()
            .managedWorkload(
                KubernetesResourceId.builder().kind("Deployment").name("todolist-blue").namespace("default").build())
            .status(IK8sRelease.Status.Succeeded)
            .build();
    releaseHistory.addReleaseToReleaseHistory(newk8sLegacyRelease);
    newk8sLegacyRelease.setBgEnvironment(HarnessLabelValues.bgStageEnv);
    assertThat(newk8sLegacyRelease).isNotNull();
    assertThat(newk8sLegacyRelease.getStatus()).isEqualTo(IK8sRelease.Status.Succeeded);
    assertThat(newk8sLegacyRelease.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgStageEnv);
    assertThat(releaseHistory.getBlueGreenStageRelease()).isEqualTo(newk8sLegacyRelease);

    newk8sLegacyRelease.setBgEnvironment(HarnessLabelValues.bgPrimaryEnv);
    assertThat(releaseHistory.getReleases().size()).isEqualTo(3);
    assertThat(releaseHistory.getBlueGreenStageRelease()).isEqualTo(release);
    assertThat(releaseHistory.getReleases().get(0).getBgEnvironment()).isEqualTo(HarnessLabelValues.bgPrimaryEnv);
  }

  private ReleaseHistory createNewRelease(List<KubernetesResourceId> resources) {
    List<K8sLegacyRelease> k8sLegacyReleases = new ArrayList<>();
    k8sLegacyReleases.add(
        0, K8sLegacyRelease.builder().status(IK8sRelease.Status.InProgress).managedWorkload(resources.get(0)).build());
    return ReleaseHistory.builder().releases(k8sLegacyReleases).build();
  }

  private ReleaseHistory createNewBGRelease() {
    List<K8sLegacyRelease> k8sLegacyReleases = new ArrayList<>();
    k8sLegacyReleases.add(0,
        K8sLegacyRelease.builder()
            .status(IK8sRelease.Status.InProgress)
            .managedWorkload(
                KubernetesResourceId.builder().kind("Deployment").name("nginx-blue").namespace("default").build())
            .bgEnvironment(HarnessLabelValues.bgPrimaryEnv)
            .build());
    k8sLegacyReleases.add(1,
        K8sLegacyRelease.builder()
            .status(IK8sRelease.Status.InProgress)
            .managedWorkload(
                KubernetesResourceId.builder().kind("Deployment").name("nginx-green").namespace("default").build())
            .bgEnvironment(HarnessLabelValues.bgStageEnv)
            .build());
    return ReleaseHistory.builder().releases(k8sLegacyReleases).build();
  }
}
