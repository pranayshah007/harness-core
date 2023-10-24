/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.k8s.K8sReleaseDiffCalculator.CURRENT_OWNER_FORMAT;
import static io.harness.k8s.K8sReleaseDiffCalculator.DIFF_FORMAT;
import static io.harness.k8s.K8sReleaseDiffCalculator.DIFF_KEY_ENV_ID;
import static io.harness.k8s.K8sReleaseDiffCalculator.DIFF_KEY_INFRA_ID;
import static io.harness.k8s.K8sReleaseDiffCalculator.DIFF_KEY_SVC_ID;
import static io.harness.k8s.K8sReleaseDiffCalculator.ORIGINAL_OWNER_FORMAT;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_ENV;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_INFRA_ID;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_INFRA_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_SERVICE;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
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

public class K8sReleaseDiffCalculatorTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEdgeCaseConflicts() {
    IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);
    ReleaseMetadata releaseMetadata = mock(ReleaseMetadata.class);
    when(releaseHistory.isEmpty()).thenReturn(true);
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(null, releaseHistory, true)).isFalse();
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(releaseMetadata, null, true)).isFalse();
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(releaseMetadata, releaseHistory, true)).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseConflict() {
    ReleaseMetadata sampleMetadata1 =
        ReleaseMetadata.builder().serviceId("svcId1").envId("envId").infraId("infraId").infraKey("infraKey").build();
    ReleaseMetadata sampleMetadata2 =
        ReleaseMetadata.builder().serviceId("svcId2").envId("envId").infraId("infraId").infraKey("infraKey").build();

    List<K8sRelease> releases = new ArrayList<>();
    releases.add(
        K8sRelease.builder().releaseSecret(createSecretWithAnnotations("1", "Succeeded", sampleMetadata1)).build());
    IK8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().releaseHistory(releases).build();
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(sampleMetadata1, releaseHistory, false)).isFalse();
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(sampleMetadata2, releaseHistory, false)).isTrue();

    // simulate canary release
    releases.add(
        K8sRelease.builder().releaseSecret(createSecretWithAnnotations("2", "InProgress", sampleMetadata1)).build());
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(sampleMetadata1, releaseHistory, true)).isFalse();
    assertThat(K8sReleaseDiffCalculator.releaseConflicts(sampleMetadata2, releaseHistory, true)).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEmptyDiffCases() {
    ReleaseMetadata sampleMetadata =
        ReleaseMetadata.builder().serviceId("svcId").envId("envId").infraId("infraId").infraKey("infraKey").build();
    ReleaseMetadata emptyMetadata = ReleaseMetadata.builder().build();
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(null, sampleMetadata)).isEmpty();
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(emptyMetadata, sampleMetadata)).isEmpty();
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(sampleMetadata, null)).isEmpty();
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(sampleMetadata, emptyMetadata)).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testPartialMetadataDiff() {
    ReleaseMetadata sampleMetadata1 =
        ReleaseMetadata.builder().serviceId("svcId1").envId("envId").infraId("infraId").infraKey("infraKey").build();
    ReleaseMetadata sampleMetadata2 =
        ReleaseMetadata.builder().serviceId("svcId2").envId("envId").infraId("infraId").infraKey("infraKey").build();

    String expectedMessage = String.format(DIFF_FORMAT, DIFF_KEY_SVC_ID, "svcId1", "svcId2")
        + String.format(ORIGINAL_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata1.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata1.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata1.getInfraId())
        + String.format(CURRENT_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata2.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata2.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata2.getInfraId());
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(sampleMetadata2, sampleMetadata1))
        .isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFullDiff() {
    ReleaseMetadata sampleMetadata1 =
        ReleaseMetadata.builder().serviceId("svcId1").envId("envId1").infraId("infraId1").infraKey("infraKey1").build();
    ReleaseMetadata sampleMetadata2 =
        ReleaseMetadata.builder().serviceId("svcId2").envId("envId2").infraId("infraId2").infraKey("infraKey2").build();

    String expectedMessage = String.format(DIFF_FORMAT, DIFF_KEY_SVC_ID, "svcId1", "svcId2")
        + String.format(DIFF_FORMAT, DIFF_KEY_ENV_ID, "envId1", "envId2")
        + String.format(DIFF_FORMAT, DIFF_KEY_INFRA_ID, "infraId1", "infraId2")
        + String.format(ORIGINAL_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata1.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata1.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata1.getInfraId())
        + String.format(CURRENT_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata2.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata2.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata2.getInfraId());
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(sampleMetadata2, sampleMetadata1))
        .isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataEmptyHistory() {
    IK8sReleaseHistory emptyHistory = K8sReleaseHistory.builder().releaseHistory(Collections.emptyList()).build();
    assertThat(K8sReleaseDiffCalculator.getPreviousReleaseMetadata(emptyHistory, true)).isNull();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataWhenSuccessfulReleaseDoesNotExist() {
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(K8sRelease.builder().releaseSecret(createSecret("1", "Failed")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("2", "Failed")).build());
    IK8sReleaseHistory history = K8sReleaseHistory.builder().releaseHistory(releases).build();

    ReleaseMetadata previousReleaseMetadata = K8sReleaseDiffCalculator.getPreviousReleaseMetadata(history, false);
    assertThat(previousReleaseMetadata.getServiceId()).isEmpty();
    assertThat(previousReleaseMetadata.getEnvId()).isEmpty();
    assertThat(previousReleaseMetadata.getInfraId()).isEmpty();
    assertThat(previousReleaseMetadata.getInfraKey()).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataWhenSuccessfulReleaseDoesNotExistInCanaryWorkflow() {
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(K8sRelease.builder().releaseSecret(createSecret("1", "Failed")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("2", "Failed")).build());
    IK8sReleaseHistory history = K8sReleaseHistory.builder().releaseHistory(releases).build();

    assertThat(K8sReleaseDiffCalculator.getPreviousReleaseMetadata(history, true)).isNull();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataWhenSuccessfulReleaseExists() {
    ReleaseMetadata releaseMetadata =
        ReleaseMetadata.builder().serviceId("svcId").envId("envId").infraId("infraId").infraKey("infraKey").build();
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(
        K8sRelease.builder().releaseSecret(createSecretWithAnnotations("1", "Succeeded", releaseMetadata)).build());
    IK8sReleaseHistory history = K8sReleaseHistory.builder().releaseHistory(releases).build();

    assertThat(K8sReleaseDiffCalculator.getPreviousReleaseMetadata(history, false)).isEqualTo(releaseMetadata);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataWhenSuccessfulReleaseExistsInCanaryWorkflow() {
    ReleaseMetadata releaseMetadata =
        ReleaseMetadata.builder().serviceId("svcId").envId("envId").infraId("infraId").infraKey("infraKey").build();
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(
        K8sRelease.builder().releaseSecret(createSecretWithAnnotations("1", "Succeeded", releaseMetadata)).build());
    releases.add(
        K8sRelease.builder().releaseSecret(createSecretWithAnnotations("2", "Failed", releaseMetadata)).build());
    IK8sReleaseHistory history = K8sReleaseHistory.builder().releaseHistory(releases).build();

    assertThat(K8sReleaseDiffCalculator.getPreviousReleaseMetadata(history, true)).isEqualTo(releaseMetadata);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseMetadataWhenMetadataDoesNotExist() {
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(K8sRelease.builder().releaseSecret(createSecret("1", "Succeeded")).build());
    releases.add(K8sRelease.builder().releaseSecret(createSecret("2", "Succeeded")).build());
    IK8sReleaseHistory history = K8sReleaseHistory.builder().releaseHistory(releases).build();

    ReleaseMetadata previousReleaseMetadata = K8sReleaseDiffCalculator.getPreviousReleaseMetadata(history, false);
    assertThat(previousReleaseMetadata.getServiceId()).isEmpty();
    assertThat(previousReleaseMetadata.getEnvId()).isEmpty();
    assertThat(previousReleaseMetadata.getInfraId()).isEmpty();
    assertThat(previousReleaseMetadata.getInfraKey()).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseMetadataEquality() {
    ReleaseMetadata releaseMetadata1 =
        ReleaseMetadata.builder().serviceId("svcId").envId("envId").infraId("infraId").infraKey("key1").build();
    ReleaseMetadata releaseMetadata2 =
        ReleaseMetadata.builder().serviceId("svcId").envId("envId").infraId("infraId").infraKey("key2").build();

    assertThat(releaseMetadata1).isEqualTo(releaseMetadata2);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseMetadataDiffWhenSomeMetadataAttributesAreNull() {
    ReleaseMetadata sampleMetadata1 = ReleaseMetadata.builder().serviceId("svcId1").build();
    ReleaseMetadata sampleMetadata2 = ReleaseMetadata.builder().serviceId("svcId2").build();
    String expectedMessage = String.format(DIFF_FORMAT, DIFF_KEY_SVC_ID, "svcId1", "svcId2")
        + String.format(ORIGINAL_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata1.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata1.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata1.getInfraId())
        + String.format(CURRENT_OWNER_FORMAT, DIFF_KEY_SVC_ID, sampleMetadata2.getServiceId(), DIFF_KEY_ENV_ID,
            sampleMetadata2.getEnvId(), DIFF_KEY_INFRA_ID, sampleMetadata2.getInfraId());
    assertThat(K8sReleaseDiffCalculator.calculateDiffForLogging(sampleMetadata2, sampleMetadata1))
        .isEqualTo(expectedMessage);
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }

  private V1Secret createSecretWithAnnotations(String releaseNumber, String status, ReleaseMetadata releaseMetadata) {
    return new V1SecretBuilder()
        .withMetadata(
            new V1ObjectMetaBuilder()
                .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                .withAnnotations(Map.of(RELEASE_SECRET_ANNOTATION_SERVICE, releaseMetadata.getServiceId(),
                    RELEASE_SECRET_ANNOTATION_ENV, releaseMetadata.getEnvId(), RELEASE_SECRET_ANNOTATION_INFRA_ID,
                    releaseMetadata.getInfraId(), RELEASE_SECRET_ANNOTATION_INFRA_KEY, releaseMetadata.getInfraKey()))
                .build())
        .build();
  }
}
