/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pcf.model.CfCliVersion;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(HarnessTeam.CDP)
public class CfCliDelegateResolverTest extends CategoryTest {
  public static final String CF_VERSIONING_COMMAND = "cf --version";
  public static final String PATH_TO_BINARY_CLI7 = "/path-to-cl7/cf7";
  public static final String PATH_TO_CLI6 = "/path-to-cl6/cf";
  public static final String CF_VERSIONING_CLI6_COMMAND_OUTPUT = "cf version 6.53.0+8e2b70a4a.2020-10-01";
  public static final String CF_VERSIONING_CLI7_COMMAND_OUTPUT = "cf version 7.2.0+be4a5ce2b.2020-12-10";
  public static final String CF_VERSIONING_CLI8_COMMAND_OUTPUT = "cf version 8.53.0+8e2b70a4a.2020-10-01";
  @Mock private DelegateConfiguration delegateConfiguration;
  @Spy @InjectMocks private CfCliDelegateResolver cfCliDelegateResolver;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAvailableCfCliPathOnDelegateWhenCli6OnlyInstalled() {
    cli6OnlyInstalledOnDelegateByPkgManager();

    Optional<String> availableCfCliPathOnDelegate =
        cfCliDelegateResolver.getAvailableCfCliPathOnDelegate(CfCliVersion.V6);

    String cfCliPath = availableCfCliPathOnDelegate.orElse(null);
    assertThat(cfCliPath).isNotEmpty();
    assertThat(cfCliPath).isEqualTo("cf");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAvailableCfCliPathOnDelegateWhenBinaryInstalled() {
    binaryCli6OnlyInstalledOnDelegate();

    Optional<String> availableCfCliPathOnDelegate =
        cfCliDelegateResolver.getAvailableCfCliPathOnDelegate(CfCliVersion.V6);

    String cfCliPath = availableCfCliPathOnDelegate.orElse(null);
    assertThat(cfCliPath).isNotEmpty();
    assertThat(cfCliPath).isEqualTo(PATH_TO_CLI6);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAvailableCfCli7PathOnDelegateWhenCli6OnlyInstalled() {
    cli6OnlyInstalledOnDelegateByPkgManager();

    Optional<String> availableCfCliPathOnDelegate =
        cfCliDelegateResolver.getAvailableCfCliPathOnDelegate(CfCliVersion.V7);

    String cfCliPath = availableCfCliPathOnDelegate.orElse(null);
    assertThat(cfCliPath).isNull();
  }

  // CF version 6
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli6WhenCl6OnlyInstalledByPkgMng() {
    cli6OnlyInstalledOnDelegateByPkgManager();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6);

    assertThat(delegateEligibleToExecuteCliCommand).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli6WhenBinaryCli6OnlyInstalled() {
    binaryCli6OnlyInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6);

    assertThat(delegateEligibleToExecuteCliCommand).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCliI6WhenBinaryCli6AndCli6byPckMngNotInstalled() {
    binaryCli6AndCli6ByPckMngNotInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli6WhenCli7OnlyInstalledByPkgMng() {
    cli7OnlyInstalledOnDelegateByPkgManager();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli6WhenBinaryCli7OnlyInstalled() {
    binaryCli7OnlyInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  // CF version 7
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli7WhenCl7OnlyInstalledByPkgMng() {
    cli7OnlyInstalledOnDelegateByPkgManager();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V7);

    assertThat(delegateEligibleToExecuteCliCommand).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli7WhenBinaryCli7OnlyInstalled() {
    binaryCli7OnlyInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V7);

    assertThat(delegateEligibleToExecuteCliCommand).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCliI7WhenBinaryCli7AndCli7ByPckMngNotInstalled() {
    binaryCli7AndCli7ByPckMngNotInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V7);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli7WhenCli6OnlyInstalledByPkgMng() {
    cli6OnlyInstalledOnDelegateByPkgManager();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V7);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli7WhenBinaryCli6OnlyInstalled() {
    binaryCli6OnlyInstalledOnDelegate();
    boolean delegateEligibleToExecuteCliCommand =
        cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V7);

    assertThat(delegateEligibleToExecuteCliCommand).isFalse();
  }

  // common
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCliWhenVersionIsNull() {
    assertThatThrownBy(() -> cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Parameter cliVersion cannot be null");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsDelegateEligibleToExecuteCli6WhenCl8InstalledByPkgMng() {
    cli8OnlyInstalledOnDelegateByPkgManager();
    assertThatThrownBy(() -> cfCliDelegateResolver.isDelegateEligibleToExecuteCfCliCommand(CfCliVersion.V6))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unsupported CF CLI version, version: 8.53.0+8e2b70a4a.2020-10-01");
  }

  public void cli6OnlyInstalledOnDelegateByPkgManager() {
    ProcessOutput processOutput = new ProcessOutput(CF_VERSIONING_CLI6_COMMAND_OUTPUT.getBytes());
    ProcessResult processResult = new ProcessResult(0, processOutput);
    doReturn(processResult).when(cfCliDelegateResolver).executeCommand(anyString());
  }

  public void binaryCli6OnlyInstalledOnDelegate() {
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand(CF_VERSIONING_COMMAND);

    doReturn(PATH_TO_CLI6).when(delegateConfiguration).getCfCli6Path();
    ProcessOutput processOutput = new ProcessOutput(CF_VERSIONING_CLI6_COMMAND_OUTPUT.getBytes());
    ProcessResult processResult = new ProcessResult(0, processOutput);
    doReturn(processResult).when(cfCliDelegateResolver).executeCommand("/path-to-cl6/cf --version");
  }

  public void binaryCli6AndCli6ByPckMngNotInstalledOnDelegate() {
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand(CF_VERSIONING_COMMAND);

    doReturn(PATH_TO_CLI6).when(delegateConfiguration).getCfCli6Path();
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand("/path-to-cl6/cf --version");
  }

  public void cli7OnlyInstalledOnDelegateByPkgManager() {
    ProcessOutput processOutput = new ProcessOutput(CF_VERSIONING_CLI7_COMMAND_OUTPUT.getBytes());
    ProcessResult processResult = new ProcessResult(0, processOutput);
    doReturn(processResult).when(cfCliDelegateResolver).executeCommand(anyString());
  }

  public void binaryCli7OnlyInstalledOnDelegate() {
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand(CF_VERSIONING_COMMAND);

    doReturn(PATH_TO_BINARY_CLI7).when(delegateConfiguration).getCfCli7Path();
    ProcessOutput processOutput = new ProcessOutput(CF_VERSIONING_CLI7_COMMAND_OUTPUT.getBytes());
    ProcessResult processResult = new ProcessResult(0, processOutput);
    doReturn(processResult).when(cfCliDelegateResolver).executeCommand("/path-to-cl7/cf7 --version");
  }

  public void binaryCli7AndCli7ByPckMngNotInstalledOnDelegate() {
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand(CF_VERSIONING_COMMAND);

    doReturn(PATH_TO_BINARY_CLI7).when(delegateConfiguration).getCfCli7Path();
    doReturn(new ProcessResult(1, null)).when(cfCliDelegateResolver).executeCommand("/path-to-cl7/cf7 --version");
  }

  public void cli8OnlyInstalledOnDelegateByPkgManager() {
    ProcessOutput processOutput = new ProcessOutput(CF_VERSIONING_CLI8_COMMAND_OUTPUT.getBytes());
    ProcessResult processResult = new ProcessResult(0, processOutput);
    doReturn(processResult).when(cfCliDelegateResolver).executeCommand(anyString());
  }
}
