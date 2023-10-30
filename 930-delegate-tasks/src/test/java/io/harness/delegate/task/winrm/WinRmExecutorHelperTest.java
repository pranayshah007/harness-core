/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.ssh.SshHelperUtils;
import io.harness.ssh.WinRmCommandResult;

import com.google.common.collect.ImmutableMap;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class WinRmExecutorHelperTest extends CategoryTest {
  @Mock WinRmSession winRmSession;
  @Mock LogCallback logCallback;
  private MockedStatic<SshHelperUtils> sshHelperStatic;
  private MockedStatic<InstallUtils> aStatic;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    sshHelperStatic = mockStatic(SshHelperUtils.class);
    aStatic = mockStatic(InstallUtils.class);
    when(InstallUtils.getPath(any(), any())).thenAnswer(invocationOnMock -> "/tmp/dummypath/tool");
  }

  @After
  public void cleanup() {
    aStatic.close();
    sshHelperStatic.close();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutor() {
    String result = WinRmExecutorHelper.getScriptExecutingCommand("psScriptFile", "powershell");
    assertThat(result).isEqualTo("powershell -f \"psScriptFile\" ");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testConstructPSScriptWithCommandsBulk() {
    String cmd = "text $a $b &a \n zxc";
    List<String> result = WinRmExecutorHelper.constructPSScriptWithCommandsBulk(
        cmd, "psScriptFile", "powershell", Collections.emptyList());
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0)).contains("`$a `$b &a `n zxc`r`n");
    assertThat(result.get(1)).isEqualTo("powershell -f \"psScriptFile\" ");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testPrepareCommandForCopyingToRemoteFile() {
    String result = WinRmExecutorHelper.prepareCommandForCopyingToRemoteFile(
        "encodedScriptFilePath", "psScriptFile", "powershell", Collections.emptyList(), "executableFile");
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo("powershell Invoke-Command  -command {[IO.File]::AppendAllText(\\\"psScriptFile\\\","
        + " \\\"`$encodedScriptFile = [Environment]::ExpandEnvironmentVariables(`\\\"encodedScriptFilePath`\\\");`n"
        + "`$scriptExecutionFile = [Environment]::ExpandEnvironmentVariables(`\\\"executableFile`\\\");`n"
        + "`$encoded = get-content `$encodedScriptFile`n"
        + "`$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(`$encoded));`n"
        + "`$expanded = [Environment]::ExpandEnvironmentVariables(`$decoded);`n"
        + "Set-Content -Path `$scriptExecutionFile` -Value `$expanded -Encoding Unicode`n"
        + "if (Test-Path `$encodedScriptFile) {Remove-Item -Force -Path `$encodedScriptFile}\\\" ) }");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCleanupFiles() {
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());
    WinRmExecutorHelper.cleanupFiles(winRmSession, "file", "powershell", false, Collections.emptyList());
  }
  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCleanupFilesWithNewSessionCreated() {
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), any()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());

    WinRmSessionConfig config = WinRmSessionConfig.builder()
                                    .executionId("harnessExecutionId")
                                    .authenticationScheme(AuthenticationScheme.KERBEROS)
                                    .useKerberosUniqueCacheFile(true)
                                    .username("user")
                                    .password("password")
                                    .hostname("harness.internal")
                                    .domain("harness")
                                    .port(1234)
                                    .environment(ImmutableMap.of("CUSTOM", "value"))
                                    .build();
    doThrow(new RuntimeException()).when(winRmSession).checkConnectivity();
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());
    WinRmExecutorHelper.cleanupFiles(
        winRmSession, "test", "powershell", false, Collections.emptyList(), config, logCallback);
    verify(winRmSession, times(0)).executeCommandString(anyString(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCleanupFilesWithSameSession() {
    WinRmSessionConfig config = WinRmSessionConfig.builder().build();
    doNothing().when(winRmSession).checkConnectivity();
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());
    WinRmExecutorHelper.cleanupFiles(
        winRmSession, "test", "powershell", false, Collections.emptyList(), config, logCallback);
    verify(winRmSession, times(1)).executeCommandString(anyString(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCleanupFiles_throws_RuntimeException() {
    doThrow(new RuntimeException("test"))
        .when(winRmSession)
        .executeCommandString(anyString(), any(), any(), anyBoolean());
    assertThatThrownBy(
        () -> WinRmExecutorHelper.cleanupFiles(winRmSession, "file", "powershell", false, Collections.emptyList()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("test");
  }
}
