/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.shell.FileBasedProcessScriptExecutorNG;
import io.harness.delegate.task.shell.FileBasedScriptExecutorNG;
import io.harness.delegate.task.shell.FileBasedSshScriptExecutorNG;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.SshExecutorFactoryNG;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;

import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshScriptExecutorFactoryTest extends CategoryTest {
  @Mock private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Mock private ShellExecutorFactoryNG shellExecutorFactory;
  @Mock private SshSessionConfigMapper sshSessionConfigMapper;
  @Mock private Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers;

  @InjectMocks SshScriptExecutorFactory sshScriptExecutorFactory;

  @Mock ScriptSshExecutor scriptSshExecutor;

  @Mock ScriptProcessExecutor scriptProcessExecutor;

  @Mock FileBasedSshScriptExecutorNG fileBasedSshScriptExecutorNG;

  @Mock FileBasedProcessScriptExecutorNG fileBasedProcessScriptExecutorNG;

  static String accountId = "accountId";
  static String executionId = "executionId";
  static String host = "host";
  static String commandUnitName = "commandUnitName";
  static String workingDirectory = "/home";

  @Before
  public void setup() {
    doReturn(SshSessionConfig.Builder.aSshSessionConfig().build())
        .when(sshSessionConfigMapper)
        .getSSHSessionConfig(any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGenerateSshSessionConfig() {
    SshExecutorFactoryContext ctx = getContext();

    SshSessionConfig result = sshScriptExecutorFactory.generateSshSessionConfig(ctx);

    assertThat(result.getAccountId()).isEqualTo(accountId);
    assertThat(result.getExecutionId()).isEqualTo(executionId);
    assertThat(result.getHost()).isEqualTo(host);
    assertThat(result.getCommandUnitName()).isEqualTo(commandUnitName);
    assertThat(result.getWorkingDirectory()).isEqualTo(workingDirectory);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutor() {
    SshExecutorFactoryContext ctx = getContext();
    doReturn(scriptSshExecutor).when(sshExecutorFactoryNG).getExecutor(any(), any(), any());
    AbstractScriptExecutor result = sshScriptExecutorFactory.getExecutor(ctx);
    assertThat(result).isEqualTo(scriptSshExecutor);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetExecutorOnDelegate() {
    SshExecutorFactoryContext ctx = getContext(true);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any(), anyBoolean());
    AbstractScriptExecutor result = sshScriptExecutorFactory.getExecutor(ctx);
    assertThat(result).isEqualTo(scriptProcessExecutor);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFileBasedExecutor() {
    SshExecutorFactoryContext ctx = getContext();
    doReturn(fileBasedSshScriptExecutorNG).when(sshExecutorFactoryNG).getFileBasedExecutor(any(), any(), any(), any());
    FileBasedScriptExecutorNG result = sshScriptExecutorFactory.getFileBasedExecutor(ctx);
    assertThat(result).isEqualTo(fileBasedSshScriptExecutorNG);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFileBasedExecutorOnDelegate() {
    SshExecutorFactoryContext ctx = getContext(true);
    doReturn(fileBasedProcessScriptExecutorNG)
        .when(shellExecutorFactory)
        .getFileBasedExecutor(any(), any(), any(), any());
    FileBasedScriptExecutorNG result = sshScriptExecutorFactory.getFileBasedExecutor(ctx);
    assertThat(result).isEqualTo(fileBasedProcessScriptExecutorNG);
  }

  private SshExecutorFactoryContext getContext() {
    return getContext(false);
  }

  private SshExecutorFactoryContext getContext(boolean executeOnDelegate) {
    return SshExecutorFactoryContext.builder()
        .accountId(accountId)
        .executionId(executionId)
        .host(host)
        .commandUnitName(commandUnitName)
        .workingDirectory(workingDirectory)
        .executeOnDelegate(executeOnDelegate)
        .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
        .encryptedDataDetailList(Collections.emptyList())
        .commandUnitsProgress(CommandUnitsProgress.builder().build())
        .build();
  }
}
