/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sReleaseHistoryHelperTest extends CategoryTest {
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseHistoryCleanup() {
    List<K8sRelease> releaseHistory = new ArrayList<>();
    releaseHistory.add(createRelease("1", Succeeded.name()));
    releaseHistory.add(createRelease("2", Failed.name()));
    releaseHistory.add(createRelease("3", Succeeded.name()));

    Set<String> releaseNumbersToClean = K8sReleaseHistoryHelper.getReleaseNumbersToClean(
        K8sReleaseHistory.builder().releaseHistory(releaseHistory).build(), 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("2"));

    releaseHistory.add(createRelease("4", Failed.name()));
    releaseHistory.add(createRelease("5", Succeeded.name()));
    releaseHistory.add(createRelease("6", Failed.name()));
    releaseHistory.add(createRelease("7", Succeeded.name()));
    releaseHistory.add(createRelease("8", Succeeded.name()));

    releaseNumbersToClean = K8sReleaseHistoryHelper.getReleaseNumbersToClean(
        K8sReleaseHistory.builder().releaseHistory(releaseHistory).build(), 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("2", "4", "6"));

    releaseHistory.add(createRelease("9", Succeeded.name()));

    releaseNumbersToClean = K8sReleaseHistoryHelper.getReleaseNumbersToClean(
        K8sReleaseHistory.builder().releaseHistory(releaseHistory).build(), 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("1", "2", "4", "6"));
  }

  private K8sRelease createRelease(String releaseNumber, String status) {
    return K8sRelease.builder().releaseSecret(createSecret(releaseNumber, status)).build();
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }
}
