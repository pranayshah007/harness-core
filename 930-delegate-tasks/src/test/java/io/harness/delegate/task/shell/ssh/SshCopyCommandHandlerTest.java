/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
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
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.ssh.FileSourceType;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshCopyCommandHandlerTest extends CategoryTest {
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock FileBasedSshScriptExecutorNG fileBasedSshScriptExecutorNG;
  @Mock FileBasedProcessScriptExecutorNG fileBasedProcessScriptExecutorNG;
  @Mock SshScriptExecutorFactory sshScriptExecutorFactory;

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
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyArtifactWithSshFileExecutor() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyFiles(any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status = sshCopyCommandHandler.handle(
        getParameters(false), copyCommandUnit, logStreamingTaskClient, commandUnitsProgress);
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

    CommandExecutionStatus status = sshCopyCommandHandler.handle(
        getParameters(true), copyCommandUnit, logStreamingTaskClient, commandUnitsProgress);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<SshExecutorFactoryContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(SshExecutorFactoryContext.class);
    verify(fileBasedProcessScriptExecutorNG).copyFiles(contextArgumentCaptor.capture());
    assertContextData(contextArgumentCaptor.getValue(), true);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutor() {
    doReturn(fileBasedSshScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedSshScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status = sshCopyCommandHandler.handle(
        getParameters(false), copyConfigCommandUnit, logStreamingTaskClient, commandUnitsProgress);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedSshScriptExecutorNG).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCopyConfigFileWithSshFileExecutorOnDelegate() {
    doReturn(fileBasedProcessScriptExecutorNG).when(sshScriptExecutorFactory).getFileBasedExecutor(any());
    when(fileBasedProcessScriptExecutorNG.copyConfigFiles(any(), any())).thenReturn(CommandExecutionStatus.SUCCESS);

    CommandExecutionStatus status = sshCopyCommandHandler.handle(
        getParameters(true), copyConfigCommandUnit, logStreamingTaskClient, commandUnitsProgress);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<ConfigFileParameters> configFileArgumentCaptor = ArgumentCaptor.forClass(ConfigFileParameters.class);
    verify(fileBasedProcessScriptExecutorNG).copyConfigFiles(eq("/test"), configFileArgumentCaptor.capture());
    assertConfigFile(configFileArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidArguments() {
    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(WinrmTaskParameters.builder().build(), copyCommandUnit,
                               logStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task parameters submitted for command task.");

    assertThatThrownBy(()
                           -> sshCopyCommandHandler.handle(getParameters(false), NgInitCommandUnit.builder().build(),
                               logStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  private CommandTaskParameters getParameters(boolean onDelegate) {
    return SshCommandTaskParameters.builder()
        .accountId("testAccount")
        .executeOnDelegate(onDelegate)
        .executionId("testExecution")
        .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder()
                                    .encryptionDataDetails(encryptedDataDetailList)
                                    .sshKeySpecDto(SSH_KEY_SPEC)
                                    .build())
        .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
        .fileDelegateConfig(FileDelegateConfig.builder()
                                .stores(Arrays.asList(HarnessStoreDelegateConfig.builder()
                                                          .configFiles(Arrays.asList(ConfigFileParameters.builder()
                                                                                         .fileContent("hello world")
                                                                                         .fileName("test.txt")
                                                                                         .fileSize(11L)
                                                                                         .build()))
                                                          .build()))
                                .build())
        .commandUnits(Arrays.asList(copyCommandUnit))
        .host("host")
        .build();
  }

  private void assertConfigFile(ConfigFileParameters configFile) {
    assertThat(configFile).isNotNull();
    assertThat(configFile.getFileContent()).isEqualTo("hello world");
    assertThat(configFile.getFileName()).isEqualTo("test.txt");
    assertThat(configFile.getFileSize()).isEqualTo(11L);
  }

  private void assertContextData(SshExecutorFactoryContext context, boolean onDelegate) {
    assertThat(context).isNotNull();
    assertThat(context.getAccountId()).isEqualTo("testAccount");
    assertThat(context.getExecutionId()).isEqualTo("testExecution");
    assertThat(context.getWorkingDirectory()).isEqualTo("/test");
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
