/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sReleaseHistoryTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testLastSuccessfulRelease() {
    assertThat(K8sReleaseHistory.builder().releaseHistory(Collections.emptyList()).build().getLastSuccessfulRelease())
        .isNull();

    List<K8sRelease> releases = new ArrayList<>();
    releases.add(K8sRelease.builder().releaseSecret(createSecret("1", "Succeeded")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("2", "InProgress")).build());
    K8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().releaseHistory(releases).build();
    assertThat(releaseHistory.getLastSuccessfulRelease().getReleaseNumber()).isEqualTo(1);
    assertThat(releaseHistory.getLastSuccessfulRelease().getReleaseStatus()).isEqualTo(Succeeded);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testLastSuccessfulReleaseWithReleaseNumber() {
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(K8sRelease.builder().releaseSecret(createSecret("1", "Succeeded")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("2", "Failed")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("3", "Failed")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("4", "InProgress")).build());
    K8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().releaseHistory(releases).build();
    assertThat(releaseHistory.getLastSuccessfulRelease(4).getReleaseNumber()).isEqualTo(1);
    assertThat(releaseHistory.getLastSuccessfulRelease(4).getReleaseStatus()).isEqualTo(Succeeded);
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }
}
