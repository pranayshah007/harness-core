/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.WinRmShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class WinRmUtilsTest {
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDisableWinRmEnvVarEscaping() {
    boolean isDisabledEnvVarEscaping =
        WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters.builder()
                                                         .disableWinRMCommandEncodingFFSet(true)
                                                         .winrmScriptCommandSplit(true)
                                                         .disableWinRmEnvVarEscaping(true)
                                                         .build());
    assertThat(isDisabledEnvVarEscaping).isTrue();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters.builder()
                                                                                .disableWinRMCommandEncodingFFSet(false)
                                                                                .winrmScriptCommandSplit(true)
                                                                                .disableWinRmEnvVarEscaping(true)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldEnableWinRmEnvVarEscaping() {
    boolean isDisabledEnvVarEscaping =
        WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters.builder()
                                                         .disableWinRMCommandEncodingFFSet(true)
                                                         .winrmScriptCommandSplit(true)
                                                         .disableWinRmEnvVarEscaping(false)
                                                         .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters.builder()
                                                                                .disableWinRMCommandEncodingFFSet(false)
                                                                                .winrmScriptCommandSplit(true)
                                                                                .disableWinRmEnvVarEscaping(false)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters.builder()
                                                                                .disableWinRMCommandEncodingFFSet(true)
                                                                                .winrmScriptCommandSplit(false)
                                                                                .disableWinRmEnvVarEscaping(true)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDisableWinRmEnvVarEscapingShellParameters() {
    boolean isDisabledEnvVarEscaping =
        WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG.builder()
                                                         .disableCommandEncoding(true)
                                                         .winrmScriptCommandSplit(true)
                                                         .disableWinRmEnvVarEscaping(true)
                                                         .build());
    assertThat(isDisabledEnvVarEscaping).isTrue();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG.builder()
                                                                                .disableCommandEncoding(false)
                                                                                .winrmScriptCommandSplit(true)
                                                                                .disableWinRmEnvVarEscaping(true)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldEnableWinRmEnvVarEscapingShellParameters() {
    boolean isDisabledEnvVarEscaping =
        WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG.builder()
                                                         .disableCommandEncoding(true)
                                                         .winrmScriptCommandSplit(true)
                                                         .disableWinRmEnvVarEscaping(false)
                                                         .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG.builder()
                                                                                .disableCommandEncoding(false)
                                                                                .winrmScriptCommandSplit(true)
                                                                                .disableWinRmEnvVarEscaping(false)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();

    isDisabledEnvVarEscaping = WinRmUtils.shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG.builder()
                                                                                .disableCommandEncoding(true)
                                                                                .winrmScriptCommandSplit(false)
                                                                                .disableWinRmEnvVarEscaping(true)
                                                                                .build());
    assertThat(isDisabledEnvVarEscaping).isFalse();
  }
}
