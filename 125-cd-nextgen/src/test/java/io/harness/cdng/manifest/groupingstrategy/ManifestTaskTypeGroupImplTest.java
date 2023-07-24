/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.groupingstrategy;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ManifestTaskTypeGroupImplTest {
  Set<ManifestGroupingStrategy> manifestGroupingStrategies = new HashSet<>();

  @Before
  public void setup() {
    manifestGroupingStrategies.add(new StoreTypeGroupingStrategy());
    manifestGroupingStrategies.add(new InheritFromManifestGroupingStrategy());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void shouldGroupManifestsCorrectlyGitWithK8sManifest() {
    ManifestTaskTypeGroupImpl taskTypeGroup = new ManifestTaskTypeGroupImpl(manifestGroupingStrategies);
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome values = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome inheritValues =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    ValuesManifestOutcome valuesDifferent =
        ValuesManifestOutcome.builder().store(S3StoreConfig.builder().build()).build();

    List<ManifestOutcome> manifests = new ArrayList<>();
    manifests.add(k8sManifestOutcome);
    manifests.add(values);
    manifests.add(inheritValues);
    manifests.add(valuesDifferent);

    List<ManifestOutcome> groupedManifests = taskTypeGroup.group(manifests);

    assertThat(groupedManifests.size()).isEqualTo(3);
    assertThat(groupedManifests).contains(k8sManifestOutcome);
    assertThat(groupedManifests).contains(values);
    assertThat(groupedManifests).contains(inheritValues);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void groupShouldHandleEmptyInput() {
    ManifestTaskTypeGroupImpl taskTypeGroup = new ManifestTaskTypeGroupImpl(manifestGroupingStrategies);
    List<ManifestOutcome> emptyManifests = new ArrayList<>();
    List<ManifestOutcome> groupedManifests = taskTypeGroup.group(emptyManifests);
    assertThat(groupedManifests.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void shouldGroupManifestsCorrectlyWithoutInheritFromManifest() {
    ManifestTaskTypeGroupImpl taskTypeGroup = new ManifestTaskTypeGroupImpl(manifestGroupingStrategies);
    ValuesManifestOutcome values = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome inheritValues =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    ValuesManifestOutcome values2 = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();

    List<ManifestOutcome> manifests = new ArrayList<>();
    manifests.add(values);
    manifests.add(inheritValues);
    manifests.add(values2);

    List<ManifestOutcome> groupedManifests = taskTypeGroup.group(manifests);

    assertThat(groupedManifests.size()).isEqualTo(2);
    assertThat(groupedManifests).contains(values);
    assertThat(groupedManifests).contains(values2);
  }
}
