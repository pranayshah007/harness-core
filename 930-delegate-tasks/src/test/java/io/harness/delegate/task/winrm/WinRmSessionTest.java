/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.WinRmSession.FILE_CACHE_TYPE;
import static io.harness.delegate.task.winrm.WinRmSession.KERBEROS_CACHE_NAME_ENV;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.ssh.SshHelperUtils;
import io.harness.ssh.WinRmCommandResult;

import com.google.common.collect.ImmutableMap;
import com.jcraft.jsch.JSchException;
import io.cloudsoft.winrm4j.client.ShellCommand;
import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClientBuilder;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class WinRmSessionTest extends CategoryTest {
  private WinRmClientBuilder winRmClientBuilder;
  private static final String SCRIPT_EXEC_COMMAND = "powershell -f file.ps1";
  @Mock private Writer writer;
  @Mock private Writer error;
  @Mock private LogCallback logCallback;
  private MockedStatic<InstallUtils> aStatic;
  private MockedStatic<SshHelperUtils> sshHelperStatic;
  private MockedStatic<WinRmClient> winRmClientMockedStatic;

  private WinRmSessionConfig winRmSessionConfig;

  private WinRmSession winRmSession;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    winRmClientBuilder = spy(WinRmClient.builder("http://localhost"));
    aStatic = mockStatic(InstallUtils.class);
    sshHelperStatic = mockStatic(SshHelperUtils.class);
    winRmClientMockedStatic = mockStatic(WinRmClient.class, CALLS_REAL_METHODS);
    when(InstallUtils.getPath(any(), any())).thenAnswer(invocationOnMock -> "/tmp/dummypath/tool");
  }

  @After
  public void cleanup() {
    aStatic.close();
    sshHelperStatic.close();
    winRmClientMockedStatic.close();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecuteCommandString() throws JSchException {
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .password("pwd")
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    sshHelperStatic.when(() -> SshHelperUtils.executeLocalCommand(anyString(), any(), any(), anyBoolean(), any()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSession = new WinRmSession(winRmSessionConfig, logCallback);

    int status = winRmSession.executeCommandString("ls", writer, error, false);

    sshHelperStatic.verify(() -> SshHelperUtils.generateTGT(eq("TestUser@KRB.LOCAL"), any(), any(), any(), any()));
    SshHelperUtils.executeLocalCommand(anyString(), eq(logCallback), eq(writer), eq(false), anyMap());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRemoteHost() throws JSchException {
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSession = new WinRmSession(winRmSessionConfig, logCallback);
    sshHelperStatic.verify(()
                               -> SshHelperUtils.executeLocalCommand(anyString(), any(LogCallback.class),
                                   nullable(Writer.class), anyBoolean(), anyMap()));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRemoteHostCheckNegativeCase() throws JSchException {
    winRmSessionConfig = WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), any(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(false).build());
    try {
      winRmSession = new WinRmSession(winRmSessionConfig, logCallback);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo("Cannot reach remote host");
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetUserPrincipal() throws JSchException {
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSession = new WinRmSession(winRmSessionConfig, logCallback);

    String userPrincipal = winRmSession.getUserPrincipal("test", "domain");

    assertThat(userPrincipal).isEqualTo("test@DOMAIN");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetUserPrincipalWithDomainInUsername() throws JSchException {
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSession = new WinRmSession(winRmSessionConfig, logCallback);

    String userPrincipal = winRmSession.getUserPrincipal("test@oldDomain", "domain");

    assertThat(userPrincipal).isEqualTo("test@DOMAIN");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetUserPrincipalWithUsernameNull() throws JSchException {
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), any(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> new WinRmSession(winRmSessionConfig, logCallback))
        .withMessageContaining("Username or domain cannot be null");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetUserPrincipalWithDomainNull() throws JSchException {
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), any(Writer.class), anyBoolean(), anyMap()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> new WinRmSession(winRmSessionConfig, logCallback))
        .withMessageContaining("Username or domain cannot be null");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testAutoClosable() throws JSchException {
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      any(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), any()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .domain("KRB.LOCAL")
                             .skipCertChecks(true)
                             .username("TestUser")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .authenticationScheme(AuthenticationScheme.KERBEROS)
                             .build();
    io.harness.delegate.task.winrm.WinRmSession session = new WinRmSession(winRmSessionConfig, logCallback);
    WinRmClientContext context = Mockito.mock(WinRmClientContext.class);
    WinRmClient client = Mockito.mock(WinRmClient.class);
    on(session).set("context", context);
    on(session).set("client", client);
    session.close();
    verify(context).shutdown();
    verify(client).close();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteCommandsListWithScriptExecCommandKerberos() throws JSchException, IOException {
    List<List<String>> commandsList = new ArrayList<>();
    List<String> commands = new ArrayList<>();
    commands.add("command");
    commandsList.add(commands);
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);
    io.harness.delegate.task.winrm.PyWinrmArgs pyWinrmArgs = mock(io.harness.delegate.task.winrm.PyWinrmArgs.class);
    setupMocks(commands, shell, winRmTool, pyWinrmArgs, AuthenticationScheme.KERBEROS);

    winRmSession.executeCommandsList(commandsList, writer, error, false, "executeCommand");
    assertThat(commandsList.get(commandsList.size() - 1).get(1)).isEqualTo("executeCommand");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteCommandsListWithScriptExecCommand() throws JSchException, IOException {
    List<List<String>> commandsList = new ArrayList<>();
    List<String> commands = Collections.singletonList("command");
    commandsList.add(commands);
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);

    setupMocks(commands, shell, winRmTool, null, AuthenticationScheme.NTLM);

    winRmSession.executeCommandsList(commandsList, writer, error, false, "executeCommand");
    verify(winRmTool).executeCommand(commands);
    verify(shell).execute("executeCommand", writer, error);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testExecuteCopyScriptToRemote() throws JSchException, IOException {
    List<String> commands = Collections.singletonList("command");
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);

    setupMocks(commands, shell, winRmTool, null, AuthenticationScheme.NTLM);

    winRmSession.copyScriptToRemote(commands, writer, error);
    verify(shell).execute("command", writer, error);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testExecuteCopyScriptToRemoteKerberos() throws JSchException, IOException {
    List<String> commands = new ArrayList<>();
    commands.add("command");
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);
    io.harness.delegate.task.winrm.PyWinrmArgs pyWinrmArgs = mock(io.harness.delegate.task.winrm.PyWinrmArgs.class);
    setupMocks(commands, shell, winRmTool, pyWinrmArgs, AuthenticationScheme.KERBEROS);

    WinRmSession spy = spy(winRmSession);
    spy.copyScriptToRemote(commands, writer, error);
    verify(spy).executeCommandWithKerberos("command", writer, false);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testExecuteScript() throws JSchException, IOException {
    List<String> commands = Collections.singletonList("command");
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);

    setupMocks(commands, shell, winRmTool, null, AuthenticationScheme.NTLM);

    winRmSession.executeScript(SCRIPT_EXEC_COMMAND, writer, error);
    verify(shell).execute("powershell -f file.ps1", writer, error);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testExecuteScriptWithKerberos() throws JSchException, IOException {
    List<String> commands = Collections.singletonList("command");
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);
    io.harness.delegate.task.winrm.PyWinrmArgs pyWinrmArgs = mock(io.harness.delegate.task.winrm.PyWinrmArgs.class);
    setupMocks(commands, shell, winRmTool, pyWinrmArgs, AuthenticationScheme.KERBEROS);

    WinRmSession spy = spy(winRmSession);
    spy.executeScript(SCRIPT_EXEC_COMMAND, writer, error);
    verify(spy).executeCommandWithKerberos("powershell -f file.ps1", writer, false);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteCommandsListWithoutScriptExecCommand() throws JSchException, IOException {
    List<List<String>> commandsList = new ArrayList<>();
    List<String> commands = Collections.singletonList("command");
    commandsList.add(commands);
    ShellCommand shell = mock(ShellCommand.class);
    WinRmTool winRmTool = mock(WinRmTool.class);

    setupMocks(commands, shell, winRmTool, null, AuthenticationScheme.NTLM);

    winRmSession.executeCommandsList(commandsList, writer, error, false, null);
    verify(winRmTool).executeCommand(commands);
    verifyNoInteractions(shell);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateWinRMSessionWithUniqueCacheFile() throws Exception {
    sshHelperStatic
        .when(()
                  -> SshHelperUtils.executeLocalCommand(
                      anyString(), any(LogCallback.class), nullable(Writer.class), anyBoolean(), any()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());

    WinRmSessionConfig sessionConfig = WinRmSessionConfig.builder()
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

    File sessionCacheFile = new File(sessionConfig.getSessionCacheFilePath().toString());
    try (WinRmSession session = new WinRmSession(sessionConfig, logCallback)) {
      // Create dummy file that will simulate kinit behavior
      Files.write(sessionConfig.getSessionCacheFilePath(), "credentials***".getBytes(StandardCharsets.UTF_8));
      assertThat(sessionCacheFile).exists();
      PyWinrmArgs args = on(session).get("args");
      assertThat(args).isNotNull();
      Map<String, String> expectedEnvMap = ImmutableMap.of("CUSTOM", "value", KERBEROS_CACHE_NAME_ENV,
          format("%s:%s", FILE_CACHE_TYPE, sessionConfig.getSessionCacheFilePath()));
      assertThat(args.getEnvironmentMap()).containsExactlyInAnyOrderEntriesOf(expectedEnvMap);
    }

    // session cache file should be deleted after session close
    assertThat(sessionCacheFile).doesNotExist();

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    sshHelperStatic.verify(
        () -> SshHelperUtils.generateTGT(anyString(), anyString(), any(), eq(logCallback), captor.capture()));
    Map<String, String> passedEnvVariables = captor.getValue();
    assertThat(passedEnvVariables)
        .containsExactlyInAnyOrderEntriesOf(ImmutableMap.of(
            KERBEROS_CACHE_NAME_ENV, format("%s:%s", FILE_CACHE_TYPE, sessionConfig.getSessionCacheFilePath())));
  }

  private void setupMocks(List<String> commands, ShellCommand shell, WinRmTool winRmTool, PyWinrmArgs pyWinrmArgs,
      AuthenticationScheme authenticationScheme) throws JSchException {
    sshHelperStatic.when(() -> SshHelperUtils.executeLocalCommand(any(), any(), any(), anyBoolean(), any()))
        .thenReturn(WinRmCommandResult.builder().success(true).build());

    sshHelperStatic.when(() -> SshHelperUtils.generateTGT(any(), any(), any(), any(), any()))
        .thenAnswer((Answer<Void>) invocation -> null);

    WinRmClient winRmClient = mock(WinRmClient.class);

    winRmClientMockedStatic.when(() -> WinRmClient.builder(anyString()))
        .thenAnswer(invocationOnMock -> winRmClientBuilder);
    doReturn(winRmClient).when(winRmClientBuilder).build();
    when(winRmClient.createShell()).thenReturn(shell);
    winRmSessionConfig = io.harness.delegate.task.winrm.WinRmSessionConfig.builder()
                             .skipCertChecks(true)
                             .username("TestUser")
                             .password("password")
                             .domain("test.domain")
                             .environment(new HashMap<>())
                             .hostname("localhost")
                             .workingDirectory("workingDirectory")
                             .authenticationScheme(authenticationScheme)
                             .build();
    winRmSession = new WinRmSession(winRmSessionConfig, logCallback);
    on(winRmSession).set("args", pyWinrmArgs);
    on(winRmSession).set("shell", shell);
    on(winRmSession).set("winRmTool", winRmTool);
    when(winRmTool.executeCommand(commands)).thenReturn(new WinRmToolResponse("", "", 0));
    when(winRmTool.executeCommand(any(String.class))).thenReturn(new WinRmToolResponse("", "", 0));
  }
}
