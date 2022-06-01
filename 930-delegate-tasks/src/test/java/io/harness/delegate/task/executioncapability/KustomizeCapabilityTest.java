/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KustomizeCapabilityTest extends CategoryTest {
  private final KustomizeCapability capability = new KustomizeCapability("/home/kustomize_plugins/");

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getCapabilityType() {
    assertThat(capability.getCapabilityType()).isEqualTo(CapabilityType.KUSTOMIZE);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void fetchCapabilityBasis() {
    assertThat(capability.fetchCapabilityBasis()).isEqualTo("kustomizePluginDir:/home/kustomize_plugins/");
  }
}
