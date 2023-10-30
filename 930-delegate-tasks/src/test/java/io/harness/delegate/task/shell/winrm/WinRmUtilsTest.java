/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.WinRmShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
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

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetWorkingDir() {
    final String workingDirectory = "C:\\workDir";
    final String destinationPath = "C:\\destinationPath";

    ScriptCommandUnit scriptCommandUnit = ScriptCommandUnit.builder().workingDirectory(workingDirectory).build();
    String ret = WinRmUtils.getWorkingDir(scriptCommandUnit);
    assertThat(ret).isEqualTo(workingDirectory);

    CopyCommandUnit copyCommandUnit = CopyCommandUnit.builder().destinationPath(destinationPath).build();
    ret = WinRmUtils.getWorkingDir(copyCommandUnit);
    assertThat(ret).isEqualTo(destinationPath);

    NgDownloadArtifactCommandUnit ngDownloadArtifactCommandUnit =
        NgDownloadArtifactCommandUnit.builder().destinationPath(destinationPath).build();
    ret = WinRmUtils.getWorkingDir(ngDownloadArtifactCommandUnit);
    assertThat(ret).isEqualTo(destinationPath);

    NgInitCommandUnit ngInitCommandUnit = NgInitCommandUnit.builder().build();
    ret = WinRmUtils.getWorkingDir(ngInitCommandUnit);
    assertThat(ret).isEqualTo(WINDOWS_HOME_DIR);

    NgCleanupCommandUnit ngCleanupCommandUnit = NgCleanupCommandUnit.builder().build();
    ret = WinRmUtils.getWorkingDir(ngCleanupCommandUnit);
    assertThat(ret).isEqualTo(WINDOWS_HOME_DIR);
  }
}
