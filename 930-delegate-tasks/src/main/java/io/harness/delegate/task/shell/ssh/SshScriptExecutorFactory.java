/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.task.shell.FileBasedProcessScriptExecutorNG;
import io.harness.delegate.task.shell.FileBasedScriptExecutorNG;
import io.harness.delegate.task.shell.FileBasedSshScriptExecutorNG;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.SshExecutorFactoryNG;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class SshScriptExecutorFactory {
  @Inject private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;
  @Inject private Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers;

  public AbstractScriptExecutor getExecutor(SshExecutorFactoryContext context) {
    return context.isExecuteOnDelegate() ? getScriptProcessExecutor(context) : getScriptSshExecutor(context);
  }

  public FileBasedScriptExecutorNG getFileBasedExecutor(SshExecutorFactoryContext context) {
    return context.isExecuteOnDelegate() ? getFileBasedScriptProcessExecutor(context)
                                         : getFileBasedScriptSshExecutor(context);
  }

  private ScriptSshExecutor getScriptSshExecutor(SshExecutorFactoryContext context) {
    SshSessionConfig sshSessionConfig = generateSshSessionConfig(context);
    return sshExecutorFactoryNG.getExecutor(
        sshSessionConfig, context.getILogStreamingTaskClient(), context.getCommandUnitsProgress());
  }

  private FileBasedSshScriptExecutorNG getFileBasedScriptSshExecutor(SshExecutorFactoryContext context) {
    SshSessionConfig sshSessionConfig = generateSshSessionConfig(context);
    return sshExecutorFactoryNG.getFileBasedExecutor(sshSessionConfig, context.getILogStreamingTaskClient(),
        context.getCommandUnitsProgress(), artifactCommandHandlers);
  }

  private ScriptProcessExecutor getScriptProcessExecutor(SshExecutorFactoryContext context) {
    ShellExecutorConfig config = getShellExecutorConfig(context);
    return shellExecutorFactory.getExecutor(
        config, context.getILogStreamingTaskClient(), context.getCommandUnitsProgress());
  }

  private FileBasedProcessScriptExecutorNG getFileBasedScriptProcessExecutor(SshExecutorFactoryContext context) {
    ShellExecutorConfig config = getShellExecutorConfig(context);
    return shellExecutorFactory.getFileBasedExecutor(
        config, context.getILogStreamingTaskClient(), context.getCommandUnitsProgress(), artifactCommandHandlers);
  }

  private ShellExecutorConfig getShellExecutorConfig(SshExecutorFactoryContext context) {
    return ShellExecutorConfig.builder()
        .accountId(context.getAccountId())
        .executionId(context.getExecutionId())
        .commandUnitName(context.getCommandUnitName())
        .workingDirectory(context.getWorkingDirectory())
        .environment(context.getEnvironment())
        .scriptType(ScriptType.BASH)
        .build();
  }

  public SshSessionConfig generateSshSessionConfig(SshExecutorFactoryContext context) {
    SshSessionConfig sshSessionConfig =
        sshSessionConfigMapper.getSSHSessionConfig(context.getSshKeySpecDTO(), context.getEncryptedDataDetailList());

    sshSessionConfig.setAccountId(context.getAccountId());
    sshSessionConfig.setExecutionId(context.getExecutionId());
    sshSessionConfig.setHost(context.getHost());
    sshSessionConfig.setWorkingDirectory(context.getWorkingDirectory());
    sshSessionConfig.setCommandUnitName(context.getCommandUnitName());

    return sshSessionConfig;
  }
}
