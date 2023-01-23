/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class K8sStepPassThroughDataTest extends CategoryTest {
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateOpenFetchFilesStreamStatus() {
    K8sStepPassThroughData k8sStepPassThroughData = K8sStepPassThroughData.builder().build();
    assertThat(k8sStepPassThroughData.getShouldOpenFetchFilesStream()).isEqualTo(null);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    assertThat(k8sStepPassThroughData.getShouldOpenFetchFilesStream()).isEqualTo(true);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    assertThat(k8sStepPassThroughData.getShouldOpenFetchFilesStream()).isEqualTo(false);
    k8sStepPassThroughData.updateOpenFetchFilesStreamStatus();
    assertThat(k8sStepPassThroughData.getShouldOpenFetchFilesStream()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateCloseFetchFilesStreamStatus() {
    StoreConfig harnessStore = HarnessStore.builder().build();
    StoreConfig httpStore = HttpStoreConfig.builder().build();
    K8sStepPassThroughData k8sStepPassThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcomeList(asList(ValuesManifestOutcome.builder().store(harnessStore).build(),
                ValuesManifestOutcome.builder().store(httpStore).build()))
            .manifestStoreTypeVisited(new HashSet<>())
            .build();
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isFalse();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEmpty();
    Set<String> harnessStoreTypeVisited = new HashSet<>(Collections.singletonList(ManifestStoreType.HARNESS));
    k8sStepPassThroughData.updateNativeHelmCloseFetchFilesStreamStatus(harnessStoreTypeVisited);
    Set<String> manifestStoreTypeVisited = new HashSet<>(harnessStoreTypeVisited);
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isFalse();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEqualTo(manifestStoreTypeVisited);
    k8sStepPassThroughData.updateNativeHelmCloseFetchFilesStreamStatus(ManifestStoreType.HelmChartRepo);
    manifestStoreTypeVisited.addAll(ManifestStoreType.HelmChartRepo);
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isTrue();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEqualTo(manifestStoreTypeVisited);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateCloseFetchFilesStreamStatusWithGitStore() {
    StoreConfig gitStore = GitStore.builder().build();
    StoreConfig harnessStore = HarnessStore.builder().build();
    K8sStepPassThroughData k8sStepPassThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcomeList(asList(ValuesManifestOutcome.builder().store(harnessStore).build(),
                ValuesManifestOutcome.builder().store(gitStore).build()))
            .manifestStoreTypeVisited(new HashSet<>())
            .build();
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isFalse();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEmpty();
    Set<String> harnessStoreTypeVisited = new HashSet<>(Collections.singletonList(ManifestStoreType.HARNESS));
    k8sStepPassThroughData.updateNativeHelmCloseFetchFilesStreamStatus(harnessStoreTypeVisited);
    Set<String> manifestStoreTypeVisited = new HashSet<>(harnessStoreTypeVisited);
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isFalse();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEqualTo(manifestStoreTypeVisited);
    k8sStepPassThroughData.updateNativeHelmCloseFetchFilesStreamStatus(ManifestStoreType.GitSubsetRepo);
    manifestStoreTypeVisited.addAll(ManifestStoreType.GitSubsetRepo);
    assertThat(k8sStepPassThroughData.isShouldCloseFetchFilesStream()).isTrue();
    assertThat(k8sStepPassThroughData.getManifestStoreTypeVisited()).isEqualTo(manifestStoreTypeVisited);
  }
}
