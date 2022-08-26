/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.k8s.manifest.ManifestHelper.processYaml;
import static io.harness.k8s.model.Release.Status.Failed;
import static io.harness.k8s.model.Release.Status.Succeeded;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class K8sReleaseServiceTest extends CategoryTest {
  @Spy @InjectMocks K8sReleaseService releaseService;

  @Mock KubernetesContainerService kubernetesContainerService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseStatusUpdate() {
    V1Secret release =
        new V1SecretBuilder()
            .withMetadata(new V1ObjectMetaBuilder().withLabels(Map.of(RELEASE_STATUS_LABEL_KEY, "Before")).build())
            .build();
    release = releaseService.updateReleaseStatus(release, "After");
    assertThat(release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY)).isEqualTo("After");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSaveRelease() {
    doReturn(null).when(kubernetesContainerService).createOrReplaceSecret(any(), any());
    V1Secret release = new V1Secret();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    releaseService.saveRelease(release, kubernetesConfig);
    verify(kubernetesContainerService).createOrReplaceSecret(kubernetesConfig, release);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseHistoryCleanup() {
    List<V1Secret> releaseHistory = new ArrayList<>();
    releaseHistory.add(createSecret("1", Succeeded.name()));
    releaseHistory.add(createSecret("2", Failed.name()));
    releaseHistory.add(createSecret("3", Succeeded.name()));

    Set<String> releaseNumbersToClean = releaseService.getReleaseNumbersToClean(releaseHistory, 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("2"));

    releaseHistory.add(createSecret("4", Failed.name()));
    releaseHistory.add(createSecret("5", Succeeded.name()));
    releaseHistory.add(createSecret("6", Failed.name()));
    releaseHistory.add(createSecret("7", Succeeded.name()));
    releaseHistory.add(createSecret("8", Succeeded.name()));

    releaseNumbersToClean = releaseService.getReleaseNumbersToClean(releaseHistory, 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("2", "4", "6"));

    releaseHistory.add(createSecret("9", Succeeded.name()));

    releaseNumbersToClean = releaseService.getReleaseNumbersToClean(releaseHistory, 10);
    assertThat(releaseNumbersToClean).isEqualTo(Set.of("1", "2", "4", "6"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetCurrentReleaseNumber() {
    assertThat(releaseService.getCurrentReleaseNumber(emptyList())).isEqualTo(1);

    List<V1Secret> releaseHistory = new ArrayList<>();
    releaseHistory.add(createSecret("11", Succeeded.name()));
    releaseHistory.add(createSecret("12", Failed.name()));
    releaseHistory.add(createSecret("13", Succeeded.name()));
    assertThat(releaseService.getCurrentReleaseNumber(releaseHistory)).isEqualTo(14);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulRelease() {
    List<V1Secret> releaseHistory = new ArrayList<>();
    releaseHistory.add(createSecret("1", Succeeded.name()));
    releaseHistory.add(createSecret("2", Failed.name()));
    releaseHistory.add(createSecret("3", Succeeded.name()));
    releaseHistory.add(createSecret("4", Failed.name()));
    releaseHistory.add(createSecret("5", Failed.name()));

    V1Secret lastSuccessfulRelease = releaseService.getLastSuccessfulRelease(releaseHistory, 5);
    assertThat(lastSuccessfulRelease.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY)).isEqualTo("3");

    releaseHistory.clear();
    releaseHistory.add(createSecret("1", Failed.name()));
    releaseHistory.add(createSecret("2", Failed.name()));
    assertThat(releaseService.getLastSuccessfulRelease(releaseHistory, 3)).isNull();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetLatestRelease() {
    assertThat(releaseService.getLatestRelease(emptyList())).isNull();

    List<V1Secret> releaseHistory = new ArrayList<>();
    releaseHistory.add(createSecret("1", Succeeded.name()));
    releaseHistory.add(createSecret("2", Failed.name()));
    releaseHistory.add(createSecret("3", Succeeded.name()));
    releaseHistory.add(createSecret("4", Failed.name()));
    releaseHistory.add(createSecret("5", Failed.name()));

    V1Secret latestRelease = releaseService.getLatestRelease(releaseHistory);
    assertThat(latestRelease.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY)).isEqualTo("5");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetReleasesMatchingColor() {
    String primaryColor = "primary";
    String stageColor = "stage";
    V1Secret primary1 = createSecret("1", Succeeded.name());
    V1Secret primary2 = createSecret("3", Succeeded.name());
    V1Secret stage1 = createSecret("2", Succeeded.name());
    V1Secret stage2 = createSecret("4", Succeeded.name());
    List<V1Secret> releases = List.of(primary1, primary2, stage1, stage2);

    doReturn(true).when(releaseService).checkReleaseForColoredWorkloads(stage1, stageColor);
    doReturn(false).when(releaseService).checkReleaseForColoredWorkloads(stage1, primaryColor);
    doReturn(true).when(releaseService).checkReleaseForColoredWorkloads(stage2, stageColor);
    doReturn(false).when(releaseService).checkReleaseForColoredWorkloads(stage2, primaryColor);

    doReturn(false).when(releaseService).checkReleaseForColoredWorkloads(primary1, stageColor);
    doReturn(true).when(releaseService).checkReleaseForColoredWorkloads(primary1, primaryColor);
    doReturn(false).when(releaseService).checkReleaseForColoredWorkloads(primary2, stageColor);
    doReturn(true).when(releaseService).checkReleaseForColoredWorkloads(primary2, primaryColor);

    List<V1Secret> primaryReleases = releaseService.getReleasesMatchingColor(releases, primaryColor, 5);
    assertThat(primaryReleases).contains(primary1, primary2);

    List<V1Secret> stageReleases = releaseService.getReleasesMatchingColor(releases, stageColor, 5);
    assertThat(stageReleases).contains(stage1, stage2);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEncodeDecodeRelease() throws IOException {
    List<KubernetesResource> resources = getKubernetesResourcesFromFiles(singletonList("/k8s/deployment.yaml"));
    V1Secret release = createSecret("1", "status");

    release = releaseService.setResourcesInRelease(release, resources);
    List<KubernetesResource> resourcesFromRelease = releaseService.getResourcesFromRelease(release);

    assertThat(resourcesFromRelease.get(0)).isEqualTo(resources.get(0));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExtractReleaseNumbers() {
    List<V1Secret> releaseHistory = new ArrayList<>();
    releaseHistory.add(createSecret("1", Succeeded.name()));
    releaseHistory.add(createSecret("2", Failed.name()));
    releaseHistory.add(createSecret("3", Succeeded.name()));

    assertThat(releaseService.getReleaseNumbers(releaseHistory)).isEqualTo(Set.of("1", "2", "3"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExtractLabelValueFromRelease() {
    V1Secret release = createSecret("1", "Status");
    assertThat(releaseService.getReleaseLabelValue(release, RELEASE_NUMBER_LABEL_KEY)).isEqualTo("1");
    assertThat(releaseService.getReleaseLabelValue(release, "SomeUnknownKey")).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCheckReleaseForColoredWorkloads() {
    KubernetesResource workload1 =
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("ends-with-stageColor").kind("Deployment").build())
            .build();
    KubernetesResource workload2 =
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("ends-with-stageColor").kind("Deployment").build())
            .build();

    doReturn(List.of(workload1, workload2)).when(releaseService).getResourcesFromRelease(any());

    assertThat(releaseService.checkReleaseForColoredWorkloads(new V1Secret(), "stageColor")).isTrue();
    assertThat(releaseService.checkReleaseForColoredWorkloads(new V1Secret(), "primaryColor")).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sArgConstruction() {
    assertThat(releaseService.createSetBasedArg("k1", Set.of("v1"))).isEqualTo("k1 in (v1)");
    assertThat(releaseService.createListBasedArg("k1", "v1")).isEqualTo("k1=v1");
    assertThat(releaseService.createCommaSeparatedKeyValueList(Map.of("k1", "v1"))).isEqualTo("k1=v1");
    assertThat(releaseService.generateName("releaseName", 1)).isEqualTo("release.releaseName.1");

    Map<String, String> labels = releaseService.generateLabels("name", 1, "status");
    assertThat(labels).containsEntry(RELEASE_KEY, "name");
    assertThat(labels).containsEntry(RELEASE_NUMBER_LABEL_KEY, "1");
    assertThat(labels).containsEntry(RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE);
    assertThat(labels).containsEntry(RELEASE_STATUS_LABEL_KEY, "status");
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }

  private List<KubernetesResource> getKubernetesResourcesFromFiles(List<String> fileNames) {
    List<KubernetesResource> resources = new ArrayList<>();
    fileNames.forEach(filename -> {
      URL url = this.getClass().getResource(filename);
      String fileContents = null;
      try {
        fileContents = Resources.toString(url, StandardCharsets.UTF_8);
      } catch (IOException e) {
        e.printStackTrace();
      }
      resources.add(processYaml(fileContents).get(0));
    });
    return resources;
  }
}
