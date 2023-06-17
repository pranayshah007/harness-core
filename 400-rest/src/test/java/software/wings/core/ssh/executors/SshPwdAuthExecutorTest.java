/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.beans.FileBucket.CONFIGS;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.SOCKET_CONNECTION_ERROR;
import static io.harness.eraro.ErrorCode.SOCKET_CONNECTION_TIMEOUT;
import static io.harness.eraro.ErrorCode.SSH_SESSION_TIMEOUT;
import static io.harness.eraro.ErrorCode.UNKNOWN_HOST;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.FILE_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.shell.AccessType;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ExecutorType;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionManager;

import software.wings.WingsBaseTest;
import software.wings.beans.ConfigFile;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.rules.SshRule;
import software.wings.service.intfc.security.SSHVaultService;

import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockedStatic;

/**
 * Created by anubhaw on 2/10/16.
 */
/*
-1.  Successfully connect
-2.  Fail for unknown hosts
-3.  fail for invalid port
-4.  fail for bad user credentials
-5.  fail for connection timeout
-6.  fail for command timeout
 7.  fail for too much data written
 8.  test for logging collected
-9.  transfer file successfully
 10. fail to transfer too big file
 11. single/multi line banner handling
 12. Remote connection closed handling
 13. Remote connection closed resume
 14. Successfully release channel on success/failure/exceptions
-15. Return Success status on successful command execution
-16. Return Failure status on failed command execution
 17. Sudo app user
 18. su app user
*/

@OwnedBy(CDP)
public class SshPwdAuthExecutorTest extends WingsBaseTest {
  private static final String HOST = "localhost";
  /**
   * The Test folder.
   */
  @Rule public TemporaryFolder testFolder = new TemporaryFolder();
  /**
   * The Ssh root.
   */
  @Rule public TemporaryFolder sshRoot = new TemporaryFolder();
  /**
   * The Ssh rule.
   */
  @Rule public SshRule sshRule = new SshRule(sshRoot);
  private SshSessionConfig.Builder configBuilder;
  private BaseScriptExecutor executor;
  private FileBasedScriptExecutor fileBasedScriptExecutor;
  @Mock private DelegateFileManager fileService;
  @Mock private LogCallback logCallback;
  @Mock private SSHVaultService sshVaultService;
  @Inject ScmSecret scmSecret;

  /**
   * Sets the up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    sshRule.setPassword(scmSecret.decryptToString(new SecretName("ssh_rule_password")));
    configBuilder = aSshSessionConfig()
                        .withAppId("APP_ID")
                        .withExecutionId(generateUuid())
                        .withExecutorType(ExecutorType.PASSWORD_AUTH)
                        .withHost(HOST)
                        .withPort(sshRule.getPort())
                        .withUserName(sshRule.getUsername())
                        .withPassword(sshRule.getPassword().toCharArray())
                        .withSshConnectionTimeout(5000)
                        .withAccountId(ACCOUNT_ID)
                        .withCommandUnitName("test");
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());
    fileBasedScriptExecutor =
        new FileBasedSshScriptExecutor(fileService, logCallback, true, configBuilder.but().build());
  }

  /**
   * Should throw unknown host exception for invalid host.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowUnknownHostExceptionForInvalidHost() {
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().withHost("INVALID_HOST").build());
    assertThatThrownBy(() -> executor.executeCommandString("ls"))
        .isInstanceOf(WingsException.class)
        .hasMessage(UNKNOWN_HOST.name());
  }

  /**
   * Should throw unknown host exception for invalid port.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowUnknownHostExceptionForInvalidPort() {
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().withPort(3333).build());
    assertThatThrownBy(() -> executor.executeCommandString("ls"))
        .isInstanceOf(WingsException.class)
        .hasMessage(SOCKET_CONNECTION_ERROR.name());
  }

  /**
   * Should throw exception for invalid credential.
   */
  @Test
  @Owner(developers = {ANUBHAW, IVAN})
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForInvalidCredential() {
    executor = new ScriptSshExecutor(
        logCallback, true, configBuilder.but().withPassword("INVALID_PASSWORD".toCharArray()).build());
    assertThatThrownBy(() -> executor.executeCommandString("ls"))
        .isInstanceOf(WingsException.class)
        .hasMessageContaining(INVALID_CREDENTIAL.name());
  }

  /**
   * Should return success for successful command execution.
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  // Too unstable to keep even with repeats
  public void shouldReturnSuccessForSuccessfulCommandExecution() {
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());

    String fileName = generateUuid();
    CommandExecutionStatus execute = executor.executeCommandString("pwd && whoami");
    assertThat(execute).isEqualTo(SUCCESS);
  }

  /**
   * Should return failure for failed command execution.
   */
  @Test
  @Owner(developers = {ANUBHAW, IVAN})
  @Category(UnitTests.class)
  public void shouldReturnFailureForFailedCommandExecution() throws JSchException, IOException {
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());
    try (MockedStatic<SshSessionManager> sessionManager = mockStatic(SshSessionManager.class)) {
      Session session = mock(Session.class);
      Channel channel = mock(ChannelExec.class);

      sessionManager.when(() -> SshSessionManager.getCachedSession(any(SshSessionConfig.class), any(LogCallback.class)))
          .thenReturn(session);
      doReturn(channel).when(session).openChannel(any());
      doReturn(true).when(channel).isClosed();
      doReturn(-1).when(channel).getExitStatus();
      doReturn(new ByteArrayInputStream("".getBytes())).when(channel).getInputStream();
      doReturn(new ByteArrayOutputStream()).when(channel).getOutputStream();

      CommandExecutionStatus execute = executor.executeCommandString(format("rm %s", "FILE_DOES_NOT_EXIST"));
      assertThat(execute).isEqualTo(FAILURE);
    }
  }

  /**
   * Should throw exception for connection timeout.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForConnectionTimeout() {
    for (int i = 0; i < 10; ++i) {
      try {
        executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().withSshConnectionTimeout(1).build());
        executor.executeCommandString("sleep 10");
      } catch (WingsException exception) {
        if (exception.getMessage().equals(SOCKET_CONNECTION_TIMEOUT.name())) {
          break;
        }
      }
    }
  }

  /**
   * Should throw exception for session timeout.
   */
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldThrowExceptionForSessionTimeout() {
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().withSshSessionTimeout(1).build());
    assertThatThrownBy(() -> executor.executeCommandString("sleep 10"))
        .isInstanceOf(WingsException.class)
        .hasMessage(SSH_SESSION_TIMEOUT.name());
  }

  /**
   * Should throw exception for connect timeout.
   */
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForConnectTimeout() {
    executor = new ScriptSshExecutor(logCallback, true,
        configBuilder.but().withHost("host1.app.com").withPort(22).withSocketConnectTimeout(2000).build());
    assertThatThrownBy(() -> executor.executeCommandString("ls"))
        .isInstanceOf(WingsException.class)
        .hasMessage(SOCKET_CONNECTION_TIMEOUT.name());
  }

  /**
   * Test scp.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = AADITI)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldTransferGridFSFile() throws IOException, ExecutionException {
    File file = testFolder.newFile();
    CharStreams.asWriter(new FileWriter(file)).append("ANY_TEXT").close();

    ConfigFile appConfigFile =
        ConfigFile.builder().templateId("TEMPLATE_ID").entityId("ENTITY_ID").relativeFilePath("/configs/").build();
    appConfigFile.setName("FILE_NAME");
    appConfigFile.setFileName("text.txt");
    FileInputStream fileInputStream = new FileInputStream(file);
    when(fileService.getMetaInfo(any(FileBucket.class), anyString(), anyString()))
        .thenReturn(aDelegateFile().withFileName("text.txt").withLength(file.length()).build());
    when(fileService.downloadArtifactByFileId(any(FileBucket.class), anyString(), anyString()))
        .thenReturn(fileInputStream);
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());

    assertThat(fileBasedScriptExecutor.copyGridFsFiles("/", CONFIGS, asList(Pair.of(FILE_ID, null))))
        .isEqualTo(SUCCESS);

    assertThat(new File(sshRoot.getRoot(), "text.txt")).hasSameTextualContentAs(file).canRead().canWrite();
  }

  /**
   * Should transfer grid fs file with different name.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = AADITI)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldTransferGridFSFileWithDifferentName() throws IOException, ExecutionException {
    File file = testFolder.newFile();
    CharStreams.asWriter(new FileWriter(file)).append("ANY_TEXT").close();

    ConfigFile appConfigFile =
        ConfigFile.builder().templateId("TEMPLATE_ID").entityId("ENTITY_ID").relativeFilePath("/configs/").build();
    appConfigFile.setName("FILE_NAME");
    appConfigFile.setFileName("text.txt");
    FileInputStream fileInputStream = new FileInputStream(file);
    when(fileService.getMetaInfo(any(FileBucket.class), anyString(), anyString()))
        .thenReturn(aDelegateFile().withFileName("text.txt").withLength(file.length()).build());
    when(fileService.downloadArtifactByFileId(any(FileBucket.class), anyString(), anyString()))
        .thenReturn(fileInputStream);
    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());

    assertThat(fileBasedScriptExecutor.copyGridFsFiles("/", CONFIGS, asList(Pair.of(FILE_ID, "text1.txt"))))
        .isEqualTo(SUCCESS);

    assertThat(new File(sshRoot.getRoot(), "text1.txt")).hasSameTextualContentAs(file).canRead().canWrite();
  }

  /**
   * Should transfer file.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = AADITI)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldTransferFile() throws IOException {
    File file = testFolder.newFile();
    CharStreams.asWriter(new FileWriter(file)).append("ANY_TEXT").close();

    executor = new ScriptSshExecutor(logCallback, true, configBuilder.but().build());

    assertThat(fileBasedScriptExecutor.copyFiles("/tmp/", asList(file.getAbsolutePath()))).isEqualTo(SUCCESS);

    assertThat(new File(sshRoot.getRoot(), file.getName())).hasSameTextualContentAs(file).canRead().canWrite();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldThrowUnknownHostExceptionForInvalidHostUsingUsernamePassword() {
    SshSessionConfig sessionConfig = configBuilder.but()
                                         .withHost("INVALID_HOST")
                                         .withAccessType(AccessType.USER_PASSWORD)
                                         .withSshPassword("TEST_PASS".toCharArray())
                                         .build();
    executor = new ScriptSshExecutor(logCallback, true, sessionConfig);
    assertThatThrownBy(() -> executor.executeCommandString("ls"))
        .isInstanceOf(WingsException.class)
        .hasMessage(UNKNOWN_HOST.name());
  }
}
