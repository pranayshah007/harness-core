/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class KustomizeCapabilityCheckTest {
  private final KustomizeCapability capability = new KustomizeCapability("/plugins/kustomize");
  private final KustomizeCapabilityCheck underTest = new KustomizeCapabilityCheck();

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void pluginsExist() {
    try (MockedStatic<KustomizeCapabilityCheck> capabilityCheck = Mockito.mockStatic(KustomizeCapabilityCheck.class)) {
      capabilityCheck.when(() -> KustomizeCapabilityCheck.doesKustomizePluginDirExist("/plugins/kustomize"))
          .thenReturn(true);
      assertThat(underTest.performCapabilityCheck(capability))
          .isEqualTo(CapabilityResponse.builder().validated(true).delegateCapability(capability).build());
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void pluginsDoNotExist() {
    try (MockedStatic<KustomizeCapabilityCheck> capabilityCheck = Mockito.mockStatic(KustomizeCapabilityCheck.class)) {
      capabilityCheck.when(() -> KustomizeCapabilityCheck.doesKustomizePluginDirExist("/plugins/kustomize"))
          .thenReturn(false);
      assertThat(underTest.performCapabilityCheck(capability))
          .isEqualTo(CapabilityResponse.builder().validated(false).delegateCapability(capability).build());
    }
  }
}
