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
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class StoreTypeGroupingStrategyTest {
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCanApplyThisStrategy_WhenManifestStoreTypeNotInherit_ShouldReturnTrue() {
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
    StoreTypeGroupingStrategy strategy = new StoreTypeGroupingStrategy();
    assertThat(strategy.canApply(k8sManifestOutcome)).isTrue();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCanApplyThisStrategy_WhenManifestStoreTypeIsInherit_ShouldReturnFalse() {
    ValuesManifestOutcome manifest =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    StoreTypeGroupingStrategy strategy = new StoreTypeGroupingStrategy();
    assertThat(strategy.canApply(manifest)).isFalse();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testIsSameGroup_WhenStoreKindsAreSame_ShouldReturnTrue() {
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome values = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();
    StoreTypeGroupingStrategy strategy = new StoreTypeGroupingStrategy();
    assertThat(strategy.isSameGroup(k8sManifestOutcome, values)).isTrue();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testIsSameGroup_WhenStoreKindsAreDifferent_ShouldReturnFalse() {
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome values =
        ValuesManifestOutcome.builder().store(CustomRemoteStoreConfig.builder().build()).build();
    StoreTypeGroupingStrategy strategy = new StoreTypeGroupingStrategy();
    assertThat(strategy.isSameGroup(k8sManifestOutcome, values)).isFalse();
  }
}
