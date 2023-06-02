/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.ssh.CommandHandler.RESOLVED_ENV_VARIABLES_KEY;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_SSH_CONFIG_FILE_HINT;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.FileBasedProcessScriptExecutorNG;
import io.harness.delegate.task.shell.FileBasedSshScriptExecutorNG;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.ssh.FileSourceType;

import com.google.inject.Inject;
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
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshCopyCommandHandlerTest extends CategoryTest {
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock FileBasedSshScriptExecutorNG fileBasedSshScriptExecutorNG;
  @Mock FileBasedProcessScriptExecutorNG fileBasedProcessScriptExecutorNG;
  @Mock SshScriptExecutorFactory sshScriptExecutorFactory;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback logCallback;
  @Mock Map<String, Object> taskContext;

  final SSHKeySpecDTO SSH_KEY_SPEC = SSHKeySpecDTO.builder().build();
  final List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

  final NgCommandUnit copyCommandUnit =
      CopyCommandUnit.builder().name("test").sourceType(FileSourceType.ARTIFACT).destinationPath("/test").build();
  final NgCommandUnit copyConfigCommandUnit =
      CopyCommandUnit.builder().name("test").sourceType(FileSourceType.CONFIG).destinationPath("/test").build();

  final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  @Inject @InjectMocks final SshCopyCommandHandler sshCopyCommandHandler = new SshCopyCommandHandler();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(logCallback).when(fileBasedSshScriptExecutorNG).getLogCallback();
    doReturn(logCallback).when(fileBasedProcessScriptExecutorNG).getLogCallback();
    doReturn(Collections.emptyMap()).when(taskContext).get(RESOLVED_ENV_VARIABLES_KEY);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSshFileExecutor() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status = sshCopyCommandHandler
                                        .handle(getParameters(false, true), copyCommandUnit, logStreamingTaskClient,
                                            commandUnitsProgress, taskContext)
                                        .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<SshExecutorFactoryContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(SshExecutorFactoryContext.class);
    verify(fileBasedSshScriptExecutorNG).copyFiles(contextArgumentCaptor.capture());
    assertContextData(contextArgumentCaptor.getValue(), false);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSshFileExecutorOnDelegate() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status = sshCopyCommandHandler
                                        .handle(getParameters(true, true), copyCommandUnit, logStreamingTaskClient,
                                            commandUnitsProgress, taskContext)
                                        .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<SshExecutorFactoryContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(SshExecutorFactoryContext.class);
    verify(fileBasedProcessScriptExecutorNG).copyFiles(contextArgumentCaptor.capture());
    assertContextData(contextArgumentCaptor.getValue(), true);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSshFileExecutorScpFailure() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.FAILURE);

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(false, true), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(FAILED_TO_COPY_ARTIFACT_HINT);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSshFileExecutorOnDelegateScpFailure() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.FAILURE);

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(true, true), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(FAILED_TO_COPY_ARTIFACT_HINT);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailCopyArtifactWithSshFileExecutorIfNoArtifact() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(false, false), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage("Please provide artifact details with the service definition");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailCopyArtifactWithSshFileExecutorOnDelegateIfNoArtifact() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(true, false), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage("Please provide artifact details with the service definition");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutor() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status = sshCopyCommandHandler
                                        .handle(getParameters(false, false), copyConfigCommandUnit,
                                            logStreamingTaskClient, commandUnitsProgress, taskContext)
                                        .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedSshScriptExecutorNG, times(2)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorOnDelegate() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status = sshCopyCommandHandler
                                        .handle(getParameters(true, false), copyConfigCommandUnit,
                                            logStreamingTaskClient, commandUnitsProgress, taskContext)
                                        .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedProcessScriptExecutorNG, times(2)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorScpError() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.FAILURE);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(false, false), copyConfigCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(FAILED_TO_COPY_SSH_CONFIG_FILE_HINT);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorOnDelegateScpError() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.FAILURE);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(true, false), copyConfigCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(FAILED_TO_COPY_SSH_CONFIG_FILE_HINT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testShouldCopyEmptyConfigFileWithSshFileExecutorOnDelegate() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status =
        sshCopyCommandHandler
            .handle(getParameters(true, null, getEmptyFileDelegateConfig()), copyConfigCommandUnit,
                logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedProcessScriptExecutorNG, times(1)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertEmptyConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldRecalculateConfigFileWithSshFileExecutorOnDelegate() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status =
        sshCopyCommandHandler
            .handle(getParameters(true, null, getFileDelegateConfigWithoutSize()), copyConfigCommandUnit,
                logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedProcessScriptExecutorNG, times(2)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidArguments() {
    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(WinrmTaskParameters.builder().build(), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");

    assertThatThrownBy(
        ()
            -> sshCopyCommandHandler.handle(getParameters(false, true), NgInitCommandUnit.builder().build(),
                logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithEmptyDestinationPath() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    NgCommandUnit copyCommandUnit = CopyCommandUnit.builder().name("test").sourceType(FileSourceType.ARTIFACT).build();

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(false, true), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSkipCopyArtifactDelegateConfig() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    SshWinRmArtifactDelegateConfig artifactDelegateConfig = ArtifactoryDockerArtifactDelegateConfig.builder().build();

    CommandExecutionStatus status = sshCopyCommandHandler
                                        .handle(getParameters(false, artifactDelegateConfig, getFileDelegateConfig()),
                                            copyCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext)
                                        .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testCopyArtifactWithCustomArtifactDelegateConfig_Fails() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CustomArtifactDelegateConfig artifactDelegateConfig = CustomArtifactDelegateConfig.builder().build();
    assertThatThrownBy(
        ()
            -> sshCopyCommandHandler.handle(getParameters(false, artifactDelegateConfig, getFileDelegateConfig()),
                copyCommandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorFromGit() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status =
        sshCopyCommandHandler
            .handle(getParameters(false, null, getFileDelegateConfigFromGit()), copyConfigCommandUnit,
                logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedSshScriptExecutorNG, times(2)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorFromGitOnDelegate() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SecretConfigFile.builder()
                        .encryptedConfigFile(SecretRefData.builder()
                                                 .identifier("secret-ref")
                                                 .decryptedValue("This is a secret".toCharArray())
                                                 .build())
                        .build());

    CommandExecutionStatus status =
        sshCopyCommandHandler
            .handle(getParameters(true, null, getFileDelegateConfigFromGit()), copyConfigCommandUnit,
                logStreamingTaskClient, commandUnitsProgress, taskContext)
            .getStatus();
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedSshScriptExecutorNG, times(2)).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  private CommandTaskParameters getParameters(boolean onDelegate, boolean withArtifact) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig =
        withArtifact ? ArtifactoryArtifactDelegateConfig.builder().build() : null;
    return getParameters(onDelegate, artifactDelegateConfig, getFileDelegateConfig());
  }

  private CommandTaskParameters getParameters(boolean onDelegate, SshWinRmArtifactDelegateConfig artifactDelegateConfig,
      FileDelegateConfig fileDelegateConfig) {
    return SshCommandTaskParameters.builder()
        .accountId("testAccount")
        .executeOnDelegate(onDelegate)
        .executionId("testExecution")
        .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder()
                                    .encryptionDataDetails(encryptedDataDetailList)
                                    .sshKeySpecDto(SSH_KEY_SPEC)
                                    .build())
        .artifactDelegateConfig(artifactDelegateConfig)
        .fileDelegateConfig(fileDelegateConfig)
        .commandUnits(Arrays.asList(copyCommandUnit))
        .host("host")
        .build();
  }

  private FileDelegateConfig getFileDelegateConfig() {
    return FileDelegateConfig.builder()
        .stores(Arrays.asList(
            HarnessStoreDelegateConfig.builder()
                .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                               .fileContent("hello world")
                                               .fileName("test.txt")
                                               .fileSize(11L)
                                               .build(),
                    ConfigFileParameters.builder()
                        .fileName("secret-ref")
                        .isEncrypted(true)
                        .encryptionDataDetails(Arrays.asList(encryptedDataDetail))
                        .secretConfigFile(
                            SecretConfigFile.builder()
                                .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                .build())
                        .build()))
                .build()))
        .build();
  }

  private FileDelegateConfig getFileDelegateConfigWithoutSize() {
    return FileDelegateConfig.builder()
        .stores(Arrays.asList(
            HarnessStoreDelegateConfig.builder()
                .configFiles(Arrays.asList(
                    ConfigFileParameters.builder().fileContent("hello world").fileName("test.txt").fileSize(0L).build(),
                    ConfigFileParameters.builder()
                        .fileName("secret-ref")
                        .isEncrypted(true)
                        .encryptionDataDetails(Arrays.asList(encryptedDataDetail))
                        .secretConfigFile(
                            SecretConfigFile.builder()
                                .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                .build())
                        .build()))
                .build()))
        .build();
  }

  private FileDelegateConfig getFileDelegateConfigFromGit() {
    return FileDelegateConfig.builder()
        .stores(Arrays.asList(
            GitFetchedStoreDelegateConfig.builder()
                .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                               .fileContent("hello world")
                                               .fileName("test.txt")
                                               .fileSize(11L)
                                               .build(),
                    ConfigFileParameters.builder()
                        .fileName("secret-ref")
                        .isEncrypted(true)
                        .encryptionDataDetails(Arrays.asList(encryptedDataDetail))
                        .secretConfigFile(
                            SecretConfigFile.builder()
                                .encryptedConfigFile(SecretRefData.builder().identifier("secret-ref").build())
                                .build())
                        .build()))
                .build()))
        .build();
  }

  private FileDelegateConfig getEmptyFileDelegateConfig() {
    return FileDelegateConfig.builder()
        .stores(Arrays.asList(HarnessStoreDelegateConfig.builder()
                                  .configFiles(Arrays.asList(
                                      ConfigFileParameters.builder().fileContent(null).fileName("test.txt").build()))
                                  .build()))
        .build();
  }

  private void assertConfigFile(ConfigFileParameters configFile) {
    assertThat(configFile).isNotNull();
    if (configFile.isEncrypted()) {
      assertThat(configFile.getFileContent()).isEqualTo("This is a secret");
      assertThat(configFile.getFileName()).isEqualTo("secret-ref");
      assertThat(configFile.getFileSize()).isEqualTo(16L);
    } else {
      assertThat(configFile.getFileContent()).isEqualTo("hello world");
      assertThat(configFile.getFileName()).isEqualTo("test.txt");
      assertThat(configFile.getFileSize()).isEqualTo(11L);
    }
  }

  private void assertEmptyConfigFile(ConfigFileParameters configFile) {
    assertThat(configFile).isNotNull();
    assertThat(configFile.getFileContent()).isNull();
    assertThat(configFile.getFileName()).isEqualTo("test.txt");
    assertThat(configFile.getFileSize()).isEqualTo(0L);
  }

  private void assertContextData(SshExecutorFactoryContext context, boolean onDelegate) {
    assertThat(context).isNotNull();
    assertThat(context.getAccountId()).isEqualTo("testAccount");
    assertThat(context.getExecutionId()).isEqualTo("testExecution");
    assertThat(context.getWorkingDirectory()).isNull();
    assertThat(context.getCommandUnitName()).isEqualTo("test");
    assertThat(context.getCommandUnitsProgress()).isEqualTo(commandUnitsProgress);
    assertThat(context.isExecuteOnDelegate()).isEqualTo(onDelegate);
    assertThat(context.getEncryptedDataDetailList()).isEqualTo(encryptedDataDetailList);
    assertThat(context.getSshKeySpecDTO()).isEqualTo(SSH_KEY_SPEC);
    assertThat(context.getHost()).isEqualTo("host");
    assertThat(context.getILogStreamingTaskClient()).isEqualTo(logStreamingTaskClient);
    assertThat(context.getDestinationPath()).isEqualTo("/test");
  }
}
