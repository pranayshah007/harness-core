/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.AuthenticationScheme.KERBEROS;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.apache.commons.io.IOUtils.contentEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.ssh.SshHelperUtils;

import software.wings.beans.ConfigFile;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.utils.ExecutionLogWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Arrays;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;

@RunWith(JUnitParamsRunner.class)
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class FileBasedWinRmExecutorTest extends CategoryTest {
  @Mock private LogCallback logCallback;
  @Mock private DelegateFileManager delegateFileManager;
  FileBasedWinRmExecutor plainOldExecutor;
  FileBasedWinRmExecutor executorWithDisableEncoding;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    final WinRmSessionConfig winRmSessionConfig =
        WinRmSessionConfig.builder().authenticationScheme(KERBEROS).username("admin").domain("domain").build();
    plainOldExecutor =
        spy(new FileBasedWinRmExecutor(logCallback, delegateFileManager, false, winRmSessionConfig, false, false));
    executorWithDisableEncoding =
        spy(new FileBasedWinRmExecutor(logCallback, delegateFileManager, false, winRmSessionConfig, true, false));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters({"1", "491", "1024", "4096", "8492", "31297"})
  @Ignore(value = "TODO")
  public void copyConfigFilesOptimized(int size) throws IOException {
    testCopyConfigFilesForExecutor(size, plainOldExecutor);
    testCopyConfigFilesForExecutor(size, executorWithDisableEncoding);
  }

  private void testCopyConfigFilesForExecutor(int size, FileBasedWinRmExecutor executor) throws IOException {
    try (MockedStatic<SshHelperUtils> ignore = mockStatic(SshHelperUtils.class);
         MockedStatic<InstallUtils> ignore1 = mockStatic(InstallUtils.class)) {
      mockRemoteCommandStatus(executor, SUCCESS);
      when(SshHelperUtils.executeLocalCommand(
               anyString(), any(LogCallback.class), any(Writer.class), anyBoolean(), anyMap()))
          .thenAnswer(i -> true);
      when(InstallUtils.getPath(any(), any())).thenAnswer(i -> "/tmp/dummypath/tool");
      when(delegateFileManager.downloadByConfigFileId(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(buildByteInputStream(size));
      executor.copyConfigFiles(buildConfigFileMetadata(size));

      final ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
      verify(executor, atLeastOnce()).getCopyConfigCommand(captor.capture(), any(), any());

      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      captor.getAllValues().forEach(v -> {
        try {
          byteArrayOutputStream.write(v);
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
      assertThat(
          contentEquals(buildByteInputStream(size), new ByteArrayInputStream(byteArrayOutputStream.toByteArray())))
          .isTrue();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getDeleteFileCommand() {
    ConfigFileMetaData configFileMetaData = buildConfigFileMetadata(1);
    String command = plainOldExecutor.getDeleteFileCommandStr(
        configFileMetaData.getDestinationDirectoryPath(), configFileMetaData.getFilename());
    assertThat(command).isEqualTo("$decodedFile = \"TEST_PATH\\TEST_FILE_NAME\"\n"
        + "Write-Host \"Clearing target config file $decodedFile  on the host.\"\n"
        + "if ([IO.File]::Exists($decodedFile)) {\n"
        + "  [IO.File]::Delete($decodedFile)\n"
        + "}");
  }

  /**
   * Base64 encoding, split file optimization. Transfer file using AppendAllText
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCopyConfigEncodedAndOptimization() {
    ConfigFileMetaData configFileMetaData = buildConfigFileMetadata(1);
    String command = plainOldExecutor.getCopyConfigCommand("This is a test".getBytes(),
        configFileMetaData.getDestinationDirectoryPath(), configFileMetaData.getFilename());
    assertThat(command).isEqualTo("#### Convert Base64 string back to config file ####\n"
        + "\n"
        + "$DecodedString = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(\"VGhpcyBpcyBhIHRlc3Q=\"))\n"
        + "Write-Host \"Decoding config file on the host.\"\n"
        + "$decodedFile = \"" + configFileMetaData.getDestinationDirectoryPath() + "\\"
        + configFileMetaData.getFilename() + "\"\n"
        + "[IO.File]::AppendAllText($decodedFile, $DecodedString) \n"
        + "Write-Host \"Appended to config file on the host.\"\n");
  }

  /**
   * no encoding, split file optimization. Transfer file using AppendAllText in plaintext
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCopyConfigCommandDisableEncodingAndOptimization() {
    ConfigFileMetaData configFileMetaData = buildConfigFileMetadata(1);
    String command = executorWithDisableEncoding.getCopyConfigCommand("This is a test".getBytes(),
        configFileMetaData.getDestinationDirectoryPath(), configFileMetaData.getFilename());
    assertThat(command).isEqualTo("$fileName = \"" + configFileMetaData.getDestinationDirectoryPath() + "\\"
        + configFileMetaData.getFilename() + "\"\n"
        + "$commandString = @'\n" + new String("This` is` a` test".getBytes()) + "\n'@"
        + "\n[IO.File]::AppendAllText($fileName, $commandString,   [Text.Encoding]::UTF8)\n"
        + "Write-Host \"Appended to config file on the host.\"");
  }

  private ConfigFileMetaData buildConfigFileMetadata(long size) {
    ConfigFile configFile = ConfigFile.builder().encrypted(false).entityId("TEST_ID").build();
    configFile.setSize(size);
    configFile.setUuid("fileUuid");
    return ConfigFileMetaData.builder()
        .destinationDirectoryPath("TEST_PATH")
        .fileId(configFile.getUuid())
        .filename("TEST_FILE_NAME")
        .length(configFile.getSize())
        .encrypted(configFile.isEncrypted())
        .activityId("TEST_ACTIVITY_ID")
        .build();
  }

  private InputStream buildByteInputStream(int l) {
    final byte[] bytes = new byte[l];
    Arrays.fill(bytes, (byte) 'a');
    return new ByteArrayInputStream(bytes);
  }

  private void mockRemoteCommandStatus(FileBasedWinRmExecutor executor, CommandExecutionStatus status)
      throws IOException {
    when(executor.executeRemoteCommand(any(WinRmSession.class), any(ExecutionLogWriter.class),
             any(ExecutionLogWriter.class), anyString(), anyBoolean()))
        .thenReturn(status);
  }
}
