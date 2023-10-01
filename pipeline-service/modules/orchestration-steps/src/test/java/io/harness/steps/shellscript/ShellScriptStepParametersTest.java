/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ShellScriptStepParametersTest extends CategoryTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getAllCommandUnits() {
    assertThat(ShellScriptStepParameters.infoBuilder().shellType(ShellType.Bash).build().getAllCommandUnits())
        .containsExactly("Execute");
    assertThat(
        new HashSet<>(
            ShellScriptStepParameters.infoBuilder().shellType(ShellType.PowerShell).build().getAllCommandUnits()))
        .containsExactlyInAnyOrder("Execute", "Initialize");
    assertThat(ShellScriptStepParameters.infoBuilder().build().getAllCommandUnits()).isEmpty();
  }
}
