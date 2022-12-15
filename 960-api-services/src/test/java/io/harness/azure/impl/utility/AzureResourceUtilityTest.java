/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl.utility;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDP)
@PrepareForTest({AzureResourceUtility.class})
public class AzureResourceUtilityTest extends CategoryTest {
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFixDeploymentSlotName() {
    assertThat(AzureResourceUtility.fixDeploymentSlotName("appname-test", "AppName-test")).isEqualTo("production");
    assertThat(AzureResourceUtility.fixDeploymentSlotName("appname-test", "appname-test")).isEqualTo("production");
    assertThat(AzureResourceUtility.fixDeploymentSlotName("appname-test-stage", "AppName-test")).isEqualTo("stage");
    assertThat(AzureResourceUtility.fixDeploymentSlotName("appname-test-stage", "appname-test")).isEqualTo("stage");
  }
}
