/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.AbstractScriptExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class SshScriptCommandHandler implements CommandHandler {
  @Inject private SshScriptExecutorFactory sshScriptExecutorFactory;

  @Override
  public CommandExecutionStatus handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    if (!(parameters instanceof SshCommandTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }
    SshCommandTaskParameters sshCommandTaskParameters = (SshCommandTaskParameters) parameters;

    if (!(commandUnit instanceof ScriptCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) commandUnit;
    SshExecutorFactoryContext context =
        SshExecutorFactoryContext.builder()
            .accountId(sshCommandTaskParameters.getAccountId())
            .executionId(sshCommandTaskParameters.getExecutionId())
            .workingDirectory(scriptCommandUnit.getWorkingDirectory())
            .commandUnitName(scriptCommandUnit.getName())
            .commandUnitsProgress(commandUnitsProgress)
            .environment(sshCommandTaskParameters.getEnvironmentVariables())
            .encryptedDataDetailList(sshCommandTaskParameters.getSshInfraDelegateConfig().getEncryptionDataDetails())
            .sshKeySpecDTO(sshCommandTaskParameters.getSshInfraDelegateConfig().getSshKeySpecDto())
            .iLogStreamingTaskClient(logStreamingTaskClient)
            .executeOnDelegate(sshCommandTaskParameters.isExecuteOnDelegate())
            .host(sshCommandTaskParameters.getHost())
            .build();

    AbstractScriptExecutor executor = sshScriptExecutorFactory.getExecutor(context);

    return executor.executeCommandString(scriptCommandUnit.getCommand());
  }
}
