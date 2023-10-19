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
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_ENV;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_INFRA_ID;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_INFRA_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_ANNOTATION_SERVICE;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_HELM_CHART_NAME_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_HELM_CHART_REPO_URL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_HELM_CHART_SUB_CHART_PATH_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_HELM_CHART_VERSION_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_COLOR_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.ReleaseMetadata;
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
  private static final String SECRET_NAME = "secretName";
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExtractLabelValueFromRelease() {
    V1Secret release = createSecret("1", "Status");
    release.getMetadata()
        .putLabelsItem(RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY, "primary")
        .putLabelsItem(RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY, "sampleManifestHash");
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, RELEASE_NUMBER_LABEL_KEY)).isEqualTo("1");
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, "SomeUnknownKey")).isEmpty();
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY))
        .isEqualTo("primary");
    assertThat(K8sReleaseSecretHelper.getReleaseLabelValue(release, RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY))
        .isEqualTo("sampleManifestHash");

    V1Secret secret =
        new V1SecretBuilder().withMetadata(new V1ObjectMetaBuilder().withName(SECRET_NAME).build()).build();
    assertThat(K8sReleaseSecretHelper.getSecretName(secret)).isEqualTo(SECRET_NAME);
    assertThat(K8sReleaseSecretHelper.getSecretName(new V1Secret())).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sArgConstruction() {
    assertThat(K8sReleaseSecretHelper.createSetBasedArg("k1", Set.of("v1"))).isEqualTo("k1 in (v1)");
    assertThat(K8sReleaseSecretHelper.createListBasedArg("k1", "v1")).isEqualTo("k1=v1");
    assertThat(K8sReleaseSecretHelper.createCommaSeparatedKeyValueList(Map.of("k1", "v1"))).isEqualTo("k1=v1");
    assertThat(K8sReleaseSecretHelper.generateName("releaseName", 1)).isEqualTo("harness.release.releaseName.1");

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
    assertThat(secret.getMetadata().getAnnotations()).isNull();
    secret = K8sReleaseSecretHelper.putAnnotationsItem(createSecret("1", "status"), "annotation1", "v1");
    assertThat(secret.getMetadata().getAnnotations()).containsEntry("annotation1", "v1");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testColoredWorkloadsFromRelease() {
    V1Secret release = createSecret("1", "Status");
    release.getMetadata().putLabelsItem(RELEASE_SECRET_RELEASE_COLOR_KEY, "primary");

    assertThat(K8sReleaseSecretHelper.checkReleaseColor(release, "primary")).isTrue();
    assertThat(K8sReleaseSecretHelper.checkReleaseColor(release, "nonexistingcolor")).isFalse();
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testAnnotationsForHelmChart() {
    V1Secret release = createSecret("1", "Status");
    String helmRepoUrl = "repoUrl";
    String helmChartVersion = "1.0.2";
    String helmChartName = "chartName";
    String helmSubChartPath = "subChartPath";
    release.getMetadata()
        .putAnnotationsItem(RELEASE_SECRET_HELM_CHART_NAME_KEY, helmChartName)
        .putAnnotationsItem(RELEASE_SECRET_HELM_CHART_VERSION_KEY, helmChartVersion)
        .putAnnotationsItem(RELEASE_SECRET_HELM_CHART_SUB_CHART_PATH_KEY, helmSubChartPath)
        .putAnnotationsItem(RELEASE_SECRET_HELM_CHART_REPO_URL_KEY, helmRepoUrl);
    HelmChartInfoDTO helmChartInfoDTO = HelmChartInfoDTO.builder()
                                            .subChartPath(helmSubChartPath)
                                            .name(helmChartName)
                                            .version(helmChartVersion)
                                            .repoUrl(helmRepoUrl)
                                            .build();
    assertThat(K8sReleaseSecretHelper.getHelmChartInfo(release)).isEqualTo(helmChartInfoDTO);
    assertThat(K8sReleaseSecretHelper.getHelmChartInfo(new V1Secret())).isNull();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseMetadataNoData() {
    V1Secret release = createSecret("1", "Status");
    K8sReleaseSecretHelper.putHarnessReleaseMetadata(release, null);

    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_SERVICE)).isEmpty();
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_ENV)).isEmpty();
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_INFRA_ID)).isEmpty();
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_INFRA_KEY))
        .isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseMetadata() {
    String svcId = "svcId";
    String envId = "envId";
    String infraId = "infraId";
    String infraKey = "infraKey";
    V1Secret release = createSecret("1", "Status");
    ReleaseMetadata releaseMetadata =
        ReleaseMetadata.builder().envId(envId).infraKey(infraKey).infraId(infraId).serviceId(svcId).build();
    K8sReleaseSecretHelper.putHarnessReleaseMetadata(release, releaseMetadata);

    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_SERVICE))
        .isEqualTo(svcId);
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_ENV))
        .isEqualTo(envId);
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_INFRA_ID))
        .isEqualTo(infraId);
    assertThat(K8sReleaseSecretHelper.getReleaseAnnotationValue(release, RELEASE_SECRET_ANNOTATION_INFRA_KEY))
        .isEqualTo(infraKey);
  }
}
