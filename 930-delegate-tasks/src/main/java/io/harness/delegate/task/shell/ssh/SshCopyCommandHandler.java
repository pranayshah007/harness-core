/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_CONFIG_FILE_PROVIDED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_CONFIG_FILE_PROVIDED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_CONFIG_FILE_PROVIDED_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_HINT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.FileBasedAbstractScriptExecutorNG;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ssh.FileSourceType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class SshCopyCommandHandler implements CommandHandler {
  @Inject private SshScriptExecutorFactory sshScriptExecutorFactory;

  @Override
  public CommandExecutionStatus handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    if (!(parameters instanceof SshCommandTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof CopyCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }
    CopyCommandUnit copyCommandUnit = (CopyCommandUnit) commandUnit;
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
            .artifactDelegateConfig(sshCommandTaskParameters.getArtifactDelegateConfig())
            .destinationPath(copyCommandUnit.getDestinationPath())
            .build();

    if (EmptyPredicate.isEmpty(copyCommandUnit.getDestinationPath())) {
      log.info("Destination path no provided for copy command unit");
      throw NestedExceptionUtils.hintWithExplanationException(
          format(NO_DESTINATION_PATH_SPECIFIED_HINT, copyCommandUnit.getName()),
          format(NO_DESTINATION_PATH_SPECIFIED_EXPLANATION, copyCommandUnit.getName()),
          new SshCommandExecutionException(NO_DESTINATION_PATH_SPECIFIED));
    }

    CommandExecutionStatus result = CommandExecutionStatus.SUCCESS;
    FileBasedAbstractScriptExecutorNG executor =
        (FileBasedAbstractScriptExecutorNG) sshScriptExecutorFactory.getFileBasedExecutor(context);
    if (FileSourceType.ARTIFACT.equals(copyCommandUnit.getSourceType())) {
      log.info("About to copy artifact");
      result = executor.copyFiles(context);
      if (result == CommandExecutionStatus.FAILURE) {
        log.info(
            "Failed to copy artifact with id: " + sshCommandTaskParameters.getArtifactDelegateConfig().getIdentifier());
      }
      return result;
    }

    if (FileSourceType.CONFIG.equals(copyCommandUnit.getSourceType())) {
      List<ConfigFileParameters> configFiles = getConfigFileParameters(sshCommandTaskParameters, copyCommandUnit);
      log.info(format("About to copy config %s files", configFiles.size()));
      for (ConfigFileParameters configFile : configFiles) {
        result = executor.copyConfigFiles(copyCommandUnit.getDestinationPath(), configFile);
        if (result == CommandExecutionStatus.FAILURE) {
          log.info("Failed to copy config file: " + configFile.getFileName());
          break;
        }
      }
    }

    return result;
  }

  private List<ConfigFileParameters> getConfigFileParameters(
      SshCommandTaskParameters sshCommandTaskParameters, CopyCommandUnit copyCommandUnit) {
    if (sshCommandTaskParameters.getFileDelegateConfig() == null) {
      throw NestedExceptionUtils.hintWithExplanationException(NO_CONFIG_FILE_PROVIDED_HINT,
          NO_CONFIG_FILE_PROVIDED_EXPLANATION, new SshCommandExecutionException(NO_CONFIG_FILE_PROVIDED));
    }

    List<ConfigFileParameters> configFiles = new ArrayList<>();
    for (StoreDelegateConfig storeDelegateConfig : sshCommandTaskParameters.getFileDelegateConfig().getStores()) {
      if (StoreDelegateConfigType.HARNESS.equals(storeDelegateConfig.getType())) {
        HarnessStoreDelegateConfig harnessStoreDelegateConfig = (HarnessStoreDelegateConfig) storeDelegateConfig;
        configFiles.addAll(harnessStoreDelegateConfig.getConfigFiles());
      }
    }

    return configFiles;
  }
}
