/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.settings.SettingVariableTypes;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class WinRmSessionConfigTest extends CategoryTest {
  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetSettingType() {
    WinRmSessionConfig config = WinRmSessionConfig.builder().build();
    assertThat(config.getSettingType()).isEqualTo(SettingVariableTypes.WINRM_SESSION_CONFIG);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testIsDecrypted() {
    WinRmSessionConfig config = WinRmSessionConfig.builder().build();
    assertThat(config.isDecrypted()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSetDecrypted() {
    WinRmSessionConfig config = WinRmSessionConfig.builder().build();
    assertThatThrownBy(() -> config.setDecrypted(true)).isInstanceOf(IllegalStateException.class);
  }
}
