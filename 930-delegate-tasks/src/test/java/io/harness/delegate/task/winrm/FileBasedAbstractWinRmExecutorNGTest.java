/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.utils.ExecutionLogWriter;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class FileBasedAbstractWinRmExecutorNGTest extends CategoryTest {
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock LogCallback logCallback;
  @Mock WinRmSessionConfig config;
  @Mock WinRmSession winRmSession;
  @Mock ExecutionLogWriter executionLogWriter;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doNothing().when(logCallback).saveExecutionLog(anyString(), any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetConfigFileBytesThrowsException() {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG = new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper);
    assertThatThrownBy(() -> fileBasedWinRmExecutorNG.getConfigFileBytes(null))
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetPowershell() {
    WinRmSessionConfig config = WinRmSessionConfig.builder().useNoProfile(true).build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG = new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper);
    assertThat(fileBasedWinRmExecutorNG.getPowershell()).isEqualTo("Powershell -NoProfile ");

    config = WinRmSessionConfig.builder().useNoProfile(false).build();
    fileBasedWinRmExecutorNG = new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper);
    assertThat(fileBasedWinRmExecutorNG.getPowershell()).isEqualTo("Powershell ");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSaveExecutionLog() {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG = new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper);
    fileBasedWinRmExecutorNG.saveExecutionLog("line", LogLevel.INFO);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSplitFileAndTransfer() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doNothing().when(fileBasedWinRmExecutorNGSpy).clearTargetFile(any(), any(), any(), anyString(), anyString());
    doReturn(CommandExecutionStatus.SUCCESS)
        .when(fileBasedWinRmExecutorNGSpy)
        .executeRemoteCommand(any(), any(), any(), anyString(), anyBoolean());

    ConfigFileMetaData configFileMetaData =
        ConfigFileMetaData.builder().filename("x").destinationDirectoryPath("y").build();
    byte[] fileBytes = new byte[] {1, 1, 1, 0};

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.splitFileAndTransfer(
        configFileMetaData, winRmSession, executionLogWriter, executionLogWriter, 4, fileBytes);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSplitFileAndTransferFailure() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doNothing().when(fileBasedWinRmExecutorNGSpy).clearTargetFile(any(), any(), any(), anyString(), anyString());
    doReturn(CommandExecutionStatus.FAILURE)
        .when(fileBasedWinRmExecutorNGSpy)
        .executeRemoteCommand(any(), any(), any(), anyString(), anyBoolean());

    ConfigFileMetaData configFileMetaData =
        ConfigFileMetaData.builder().filename("x").destinationDirectoryPath("y").build();
    byte[] fileBytes = new byte[] {1, 1, 1, 0};

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.splitFileAndTransfer(
        configFileMetaData, winRmSession, executionLogWriter, executionLogWriter, 4, fileBytes);
    assertThat(result).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSplitFileAndTransferNG() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doNothing().when(fileBasedWinRmExecutorNGSpy).clearTargetFile(any(), any(), any(), anyString(), anyString());
    doReturn(CommandExecutionStatus.SUCCESS)
        .when(fileBasedWinRmExecutorNGSpy)
        .executeRemoteCommand(any(), any(), any(), anyString(), anyBoolean());

    ConfigFileParameters configFileParameters =
        ConfigFileParameters.builder().fileName("x").destinationPath("y").fileSize(4).fileContent("test").build();

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.splitFileAndTransfer(
        configFileParameters, winRmSession, executionLogWriter, executionLogWriter);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testSplitFileAndTransferNGDisableEncoding() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, true, secretDecryptionService, artifactoryRequestMapper));
    doNothing().when(fileBasedWinRmExecutorNGSpy).clearTargetFile(any(), any(), any(), anyString(), anyString());

    ConfigFileParameters configFileParameters =
        ConfigFileParameters.builder().fileName("x").destinationPath("y").fileSize(4).fileContent("test").build();

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.splitFileAndTransfer(
        configFileParameters, winRmSession, executionLogWriter, executionLogWriter);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<String> executedCommandCaptor = ArgumentCaptor.forClass(String.class);
    verify(winRmSession, times(3)).executeCommandString(executedCommandCaptor.capture(), any(), any(), anyBoolean());
    List<String> commands = executedCommandCaptor.getAllValues();
    assertThat(commands).isNotEmpty();
    assertThat(commands.get(0))
        .isEqualTo(
            "Powershell  Invoke-Command  -command {[IO.File]::WriteAllText(\\\"%TEMP%\\\", \\\"`$ErrorActionPreference=`\\\"Stop`\\\"`n`$fileName = `\\\"y\\x`\\\"`n`$commandString = @'`ntest`n`n'@`n[IO.File]::AppendAllText(`$fileName, `$commandString,   [Text.Encoding]::UTF8)`nWrite-Host `\\\"Appended to config file on the host.`\\\"`r`n\\\" ) }");
    assertThat(commands.get(1)).isEqualTo("Powershell  -f \"%TEMP%\" ");
    assertThat(commands.get(2))
        .isEqualTo(
            "Powershell  Invoke-Command -command {$FILE_PATH=[System.Environment]::ExpandEnvironmentVariables(\\\"%TEMP%\\\") ;  Remove-Item -Path $FILE_PATH}");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSplitFileAndTransferNG_Failure() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doNothing().when(fileBasedWinRmExecutorNGSpy).clearTargetFile(any(), any(), any(), anyString(), anyString());
    doReturn(CommandExecutionStatus.FAILURE)
        .when(fileBasedWinRmExecutorNGSpy)
        .executeRemoteCommand(any(), any(), any(), anyString(), anyBoolean());

    ConfigFileParameters configFileParameters =
        ConfigFileParameters.builder().fileName("x").destinationPath("y").fileSize(4).fileContent("test").build();

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.splitFileAndTransfer(
        configFileParameters, winRmSession, executionLogWriter, executionLogWriter);
    assertThat(result).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testClearTarget_throwsException() throws IOException {
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doReturn(CommandExecutionStatus.FAILURE)
        .when(fileBasedWinRmExecutorNGSpy)
        .executeRemoteCommand(any(), any(), any(), anyString(), anyBoolean());

    assertThatThrownBy(()
                           -> fileBasedWinRmExecutorNGSpy.clearTargetFile(
                               winRmSession, executionLogWriter, executionLogWriter, "des", "x"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRemoteCommand() throws IOException {
    WinRmSessionConfig config = WinRmSessionConfig.builder().commandParameters(Collections.emptyList()).build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, false, secretDecryptionService, artifactoryRequestMapper));
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.executeRemoteCommand(
        winRmSession, executionLogWriter, executionLogWriter, "cmd", false);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRemoteCommandEncode() throws IOException {
    WinRmSessionConfig config = WinRmSessionConfig.builder().commandParameters(Collections.emptyList()).build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, true, secretDecryptionService, artifactoryRequestMapper));
    doReturn(0).when(winRmSession).executeCommandsList(any(), any(), any(), anyBoolean(), anyString());

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.executeRemoteCommand(
        winRmSession, executionLogWriter, executionLogWriter, "cmd", false);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRemoteCommandEncode_BulkMode() throws IOException {
    WinRmSessionConfig config = WinRmSessionConfig.builder().commandParameters(Collections.emptyList()).build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, true, secretDecryptionService, artifactoryRequestMapper));
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.executeRemoteCommand(
        winRmSession, executionLogWriter, executionLogWriter, "cmd", true);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteRemoteCommandEncode_BulkModePSScriptFileContainsSeparator() throws IOException {
    WinRmSessionConfig config = WinRmSessionConfig.builder()
                                    .commandParameters(Collections.emptyList())
                                    .workingDirectory("C:\\User\\temp")
                                    .build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, true, secretDecryptionService, artifactoryRequestMapper));
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.executeRemoteCommand(
        winRmSession, executionLogWriter, executionLogWriter, "cmd", true);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<String> executedCommand = ArgumentCaptor.forClass(String.class);
    verify(winRmSession, times(3)).executeCommandString(executedCommand.capture(), any(), any(), anyBoolean());
    String command = executedCommand.getValue();
    assertThat(command).contains("C:\\User\\temp" + File.separator);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteRemoteCommandEncode_BulkModePSScriptFileSeparatorProvided() throws IOException {
    WinRmSessionConfig config = WinRmSessionConfig.builder()
                                    .commandParameters(Collections.emptyList())
                                    .workingDirectory("C:\\User\\temp\\")
                                    .build();
    FileBasedWinRmExecutorNG fileBasedWinRmExecutorNGSpy = spy(new FileBasedWinRmExecutorNG(
        logCallback, false, config, true, secretDecryptionService, artifactoryRequestMapper));
    doReturn(0).when(winRmSession).executeCommandString(anyString(), any(), any(), anyBoolean());

    CommandExecutionStatus result = fileBasedWinRmExecutorNGSpy.executeRemoteCommand(
        winRmSession, executionLogWriter, executionLogWriter, "cmd", true);
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<String> executedCommand = ArgumentCaptor.forClass(String.class);
    verify(winRmSession, times(3)).executeCommandString(executedCommand.capture(), any(), any(), anyBoolean());
    String command = executedCommand.getValue();
    assertThat(command).contains("C:\\User\\temp\\");
  }
}
