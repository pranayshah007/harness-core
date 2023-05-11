/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_WINRM_CONFIG_FILE_HINT;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.delegate.task.winrm.FileBasedWinRmExecutorNG;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.ssh.FileSourceType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.Silent.class)
public class WinRmCopyCommandHandlerTest {
  @Mock private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private FileBasedWinRmExecutorNG fileBasedWinRmExecutorNG;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private Map<String, Object> taskContext;

  @InjectMocks private WinRmCopyCommandHandler winRmCopyCommandHandler;

  final CopyCommandUnit copyConfigCommandUnit =
      CopyCommandUnit.builder().name("test").sourceType(FileSourceType.CONFIG).destinationPath("/test").build();

  @Before
  public void setup() {
    when(winRmExecutorFactoryNG.getFiledBasedWinRmExecutor(any(), anyBoolean(), any(), any()))
        .thenReturn(fileBasedWinRmExecutorNG);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(
            SecretConfigFile.builder()
                .encryptedConfigFile(
                    SecretRefData.builder().identifier("identifier").decryptedValue(new char[] {'a', 'b'}).build())
                .build());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigWithWinRmExecutor() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters = getWinrmTaskParameters(copyConfigCommandUnit, outputVariables);
    when(fileBasedWinRmExecutorNG.copyConfigFiles(any(ConfigFileParameters.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus result = winRmCopyCommandHandler
                                        .handle(winrmTaskParameters, copyConfigCommandUnit, iLogStreamingTaskClient,
                                            CommandUnitsProgress.builder().build(), taskContext)
                                        .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigWithWinRmExecutorProcessError() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters = getWinrmTaskParameters(copyConfigCommandUnit, outputVariables);
    when(fileBasedWinRmExecutorNG.copyConfigFiles(any(ConfigFileParameters.class)))
        .thenReturn(CommandExecutionStatus.FAILURE);

    assertThatThrownBy(()
                           -> winRmCopyCommandHandler.handle(winrmTaskParameters, copyConfigCommandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(FAILED_TO_COPY_WINRM_CONFIG_FILE_HINT);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldCopyConfigWithWinRmExecutorFromGit() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters =
        getWinRmTaskParameters(copyConfigCommandUnit, outputVariables, getFileDelegateConfigFromGit(true));
    when(fileBasedWinRmExecutorNG.copyConfigFiles(any(ConfigFileParameters.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus result = winRmCopyCommandHandler
                                        .handle(winrmTaskParameters, copyConfigCommandUnit, iLogStreamingTaskClient,
                                            CommandUnitsProgress.builder().build(), taskContext)
                                        .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldRecalculateSizeAndCopyConfigWithWinRmExecutor() {
    List<String> outputVariables = Collections.singletonList("variable");
    WinrmTaskParameters winrmTaskParameters =
        getWinRmTaskParameters(copyConfigCommandUnit, outputVariables, getFileDelegateConfig(false));
    when(fileBasedWinRmExecutorNG.copyConfigFiles(any(ConfigFileParameters.class)))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus result = winRmCopyCommandHandler
                                        .handle(winrmTaskParameters, copyConfigCommandUnit, iLogStreamingTaskClient,
                                            CommandUnitsProgress.builder().build(), taskContext)
                                        .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> contextArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);

    verify(fileBasedWinRmExecutorNG, times(2)).copyConfigFiles(contextArgumentCaptor.capture());
    assertConfigFile(contextArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleThrowsExceptionWrongTaskParameters() {
    CommandTaskParameters wrongTaskParameters = SshCommandTaskParameters.builder().build();
    assertThatThrownBy(()
                           -> winRmCopyCommandHandler.handle(wrongTaskParameters, copyConfigCommandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleThrowsExceptionWrongCommandUnit() {
    CommandTaskParameters taskParameters = WinrmTaskParameters.builder().build();
    NgCleanupCommandUnit ngCleanupCommandUnit = NgCleanupCommandUnit.builder().build();

    assertThatThrownBy(()
                           -> winRmCopyCommandHandler.handle(taskParameters, ngCleanupCommandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testHandleThrowsExceptionEmptyDestinationPath() {
    CommandTaskParameters taskParameters = WinrmTaskParameters.builder().build();
    CopyCommandUnit copyConfigCommandUnit = CopyCommandUnit.builder().build();

    assertThatThrownBy(()
                           -> winRmCopyCommandHandler.handle(taskParameters, copyConfigCommandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(HintException.class);
  }

  private WinrmTaskParameters getWinrmTaskParameters(
      CopyCommandUnit copyConfigCommandUnit, List<String> outputVariables) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    return WinrmTaskParameters.builder()
        .commandUnits(Collections.singletonList(copyConfigCommandUnit))
        .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
        .executeOnDelegate(true)
        .disableWinRMCommandEncodingFFSet(true)
        .outputVariables(outputVariables)
        .fileDelegateConfig(getFileDelegateConfig(true))
        .build();
  }

  private WinrmTaskParameters getWinRmTaskParameters(
      CopyCommandUnit copyConfigCommandUnit, List<String> outputVariables, FileDelegateConfig fileDelegateConfig) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig = mock(WinRmInfraDelegateConfig.class);
    return WinrmTaskParameters.builder()
        .commandUnits(Collections.singletonList(copyConfigCommandUnit))
        .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
        .executeOnDelegate(true)
        .disableWinRMCommandEncodingFFSet(true)
        .outputVariables(outputVariables)
        .fileDelegateConfig(fileDelegateConfig)
        .build();
  }

  private FileDelegateConfig getFileDelegateConfig(boolean withSize) {
    return FileDelegateConfig.builder()
        .stores(singletonList(
            HarnessStoreDelegateConfig.builder()
                .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                               .fileContent("hello world")
                                               .fileName("test.txt")
                                               .fileSize(withSize ? 11L : 0L)
                                               .build(),
                    ConfigFileParameters.builder()
                        .fileName("secret-ref")
                        .isEncrypted(true)
                        .encryptionDataDetails(singletonList(encryptedDataDetail))
                        .secretConfigFile(
                            SecretConfigFile.builder()
                                .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                .build())
                        .build()))
                .build()))
        .build();
  }

  private FileDelegateConfig getFileDelegateConfigFromGit(boolean withSize) {
    return FileDelegateConfig.builder()
        .stores(singletonList(
            GitFetchedStoreDelegateConfig.builder()
                .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                               .fileContent("hello world")
                                               .fileName("test.txt")
                                               .fileSize(withSize ? 11L : 0L)
                                               .build(),
                    ConfigFileParameters.builder()
                        .fileName("secret-ref")
                        .isEncrypted(true)
                        .encryptionDataDetails(singletonList(encryptedDataDetail))
                        .secretConfigFile(
                            SecretConfigFile.builder()
                                .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                .build())
                        .build()))
                .build()))
        .build();
  }

  private void assertConfigFile(ConfigFileParameters configFile) {
    assertThat(configFile).isNotNull();
    if (configFile.isEncrypted()) {
      assertThat(configFile.getFileContent()).isEqualTo("ab");
      assertThat(configFile.getFileName()).isEqualTo("secret-ref");
      assertThat(configFile.getFileSize()).isEqualTo(2L);
    } else {
      assertThat(configFile.getFileContent()).isEqualTo("hello world");
      assertThat(configFile.getFileName()).isEqualTo("test.txt");
      assertThat(configFile.getFileSize()).isEqualTo(11L);
    }
  }
}
