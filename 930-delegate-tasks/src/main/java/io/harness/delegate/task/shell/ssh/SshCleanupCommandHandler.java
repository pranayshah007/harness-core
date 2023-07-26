/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(CDP)
@Singleton
public class SshCleanupCommandHandler implements CommandHandler {
  @Inject private SshScriptExecutorFactory sshScriptExecutorFactory;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof SshCommandTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof NgCleanupCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    SshCommandTaskParameters sshCommandTaskParameters = (SshCommandTaskParameters) parameters;
    SshExecutorFactoryContext context =
        SshExecutorFactoryContext.builder()
            .accountId(sshCommandTaskParameters.getAccountId())
            .executionId(sshCommandTaskParameters.getExecutionId())
            .workingDirectory(commandUnit.getWorkingDirectory())
            .commandUnitName(commandUnit.getName())
            .commandUnitsProgress(commandUnitsProgress)
            .environment(sshCommandTaskParameters.getEnvironmentVariables())
            .encryptedDataDetailList(sshCommandTaskParameters.getSshInfraDelegateConfig().getEncryptionDataDetails())
            .sshKeySpecDTO(sshCommandTaskParameters.getSshInfraDelegateConfig().getSshKeySpecDto())
            .iLogStreamingTaskClient(logStreamingTaskClient)
            .executeOnDelegate(sshCommandTaskParameters.isExecuteOnDelegate())
            .host(sshCommandTaskParameters.getHost())
            .build();
    context.getEnvironmentVariables().putAll((Map<String, String>) taskContext.get(RESOLVED_ENV_VARIABLES_KEY));

    AbstractScriptExecutor executor = sshScriptExecutorFactory.getExecutor(context);

    try {
      CommandExecutionStatus commandExecutionStatus = cleanup(sshCommandTaskParameters, executor);
      closeLogStreamWithSuccessEmptyMsg(executor.getLogCallback());
      return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
    } catch (Exception e) {
      closeLogStreamWithError(executor.getLogCallback());
      throw e;
    }
  }

  private CommandExecutionStatus cleanup(SshCommandTaskParameters taskParameters, AbstractScriptExecutor executor) {
    String cmd = String.format("rm -rf %s", getExecutionStagingDir(taskParameters));
    executor.executeCommandString(cmd, true);
    return CommandExecutionStatus.SUCCESS;
  }
}
