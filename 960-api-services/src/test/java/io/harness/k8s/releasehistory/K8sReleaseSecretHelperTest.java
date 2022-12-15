/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
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
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sReleaseSecretHelperTest extends CategoryTest {
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExtractLabelValueFromRelease() {
    V1Secret release = createSecret("1", "Status");
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, RELEASE_NUMBER_LABEL_KEY)).isEqualTo("1");
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, "SomeUnknownKey")).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sArgConstruction() {
    assertThat(K8sReleaseSecretHelper.createSetBasedArg("k1", Set.of("v1"))).isEqualTo("k1 in (v1)");
    assertThat(K8sReleaseSecretHelper.createListBasedArg("k1", "v1")).isEqualTo("k1=v1");
    assertThat(K8sReleaseSecretHelper.createCommaSeparatedKeyValueList(Map.of("k1", "v1"))).isEqualTo("k1=v1");
    assertThat(K8sReleaseSecretHelper.generateName("releaseName", 1)).isEqualTo("release.releaseName.1");

    Map<String, String> labels = K8sReleaseSecretHelper.generateLabels("name", 1, "status");
    assertThat(labels).containsEntry(RELEASE_KEY, "name");
    assertThat(labels).containsEntry(RELEASE_NUMBER_LABEL_KEY, "1");
    assertThat(labels).containsEntry(RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE);
    assertThat(labels).containsEntry(RELEASE_STATUS_LABEL_KEY, "status");

    Map<String, String> labelsMap = K8sReleaseSecretHelper.createLabelsMap("release");
    assertThat(labelsMap).containsEntry(RELEASE_KEY, "release");
    assertThat(labelsMap).containsEntry(RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE);

    V1Secret secret = K8sReleaseSecretHelper.putLabelsItem(createSecret("1", "status"), "k1", "v1");
    assertThat(secret.getMetadata().getLabels()).containsEntry("k1", "v1");
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }
}
