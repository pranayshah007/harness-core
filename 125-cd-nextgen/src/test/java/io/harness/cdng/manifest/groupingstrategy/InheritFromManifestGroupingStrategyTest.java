/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.groupingstrategy;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.AzureRepoStore;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class InheritFromManifestGroupingStrategyTest {
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCanApplyThisStrategyWhenManifestStoreTypeIsInherit() {
    ValuesManifestOutcome manifest =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    InheritFromManifestGroupingStrategy strategy = new InheritFromManifestGroupingStrategy();
    assertThat(strategy.canApply(manifest)).isTrue();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCanApplyThisStrategyWhenManifestStoreTypeIsNotInherit() {
    ValuesManifestOutcome manifest = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();
    InheritFromManifestGroupingStrategy strategy = new InheritFromManifestGroupingStrategy();
    assertThat(strategy.canApply(manifest)).isFalse();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testIsSameGroupWhenManifestsInGroupAreOneOfManifestTypes() {
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
    ValuesManifestOutcome values =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    InheritFromManifestGroupingStrategy strategy = new InheritFromManifestGroupingStrategy();
    boolean result = strategy.isSameGroup(k8sManifestOutcome, values);
    assertThat(result).isEqualTo(true);
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder().store(S3StoreConfig.builder().build()).build();
    result = strategy.isSameGroup(helmChartManifestOutcome, values);
    assertThat(result).isEqualTo(true);
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder().store(BitbucketStore.builder().build()).build();
    result = strategy.isSameGroup(kustomizeManifestOutcome, values);
    assertThat(result).isEqualTo(true);
    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder().store(AzureRepoStore.builder().build()).build();
    result = strategy.isSameGroup(openshiftManifestOutcome, values);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testIsSameGroupWhenManifestsInGroupAreOfValuesType() {
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    ValuesManifestOutcome values =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    InheritFromManifestGroupingStrategy strategy = new InheritFromManifestGroupingStrategy();
    boolean result = strategy.isSameGroup(valuesManifestOutcome, values);
    assertThat(result).isEqualTo(false);
    KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome =
        KustomizePatchesManifestOutcome.builder().store(GitStore.builder().build()).build();
    result = strategy.isSameGroup(kustomizePatchesManifestOutcome, values);
    assertThat(result).isEqualTo(false);
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome =
        OpenshiftParamManifestOutcome.builder().store(AzureRepoStore.builder().build()).build();
    result = strategy.isSameGroup(openshiftParamManifestOutcome, values);
    assertThat(result).isEqualTo(false);
    valuesManifestOutcome = ValuesManifestOutcome.builder().store(GitStore.builder().build()).build();
    result = strategy.isSameGroup(valuesManifestOutcome, values);
    assertThat(result).isEqualTo(false);
  }
}
