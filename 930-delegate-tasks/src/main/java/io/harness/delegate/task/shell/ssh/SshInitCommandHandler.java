/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.shell.SshInitCommandTemplates.EXECLAUNCHERV2_SH_FTL;
import static io.harness.delegate.task.shell.SshInitCommandTemplates.TAILWRAPPERV2_SH_FTL;

import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.SshInitCommandTemplates;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@OwnedBy(CDP)
@Singleton
public class SshInitCommandHandler implements CommandHandler {
  private static final String AZURE_CLI_CHECK_SCRIPT = "az devops -h";

  @Inject private SshScriptExecutorFactory sshScriptExecutorFactory;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof SshCommandTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof NgInitCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    if (taskContext == null) {
      taskContext = new LinkedHashMap<>();
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

    AbstractScriptExecutor executor = sshScriptExecutorFactory.getExecutor(context);

    try {
      checkIfDownloadAzureUniversalArtifactSupported(executor, sshCommandTaskParameters);
      CommandExecutionStatus commandExecutionStatus =
          initAndGenerateScriptCommand(sshCommandTaskParameters, executor, context, taskContext);
      closeLogStreamEmptyMsg(executor.getLogCallback(), commandExecutionStatus);
      return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
    } catch (Exception e) {
      closeLogStreamWithError(executor.getLogCallback());
      throw e;
    }
  }

  private CommandExecutionStatus initAndGenerateScriptCommand(SshCommandTaskParameters taskParameters,
      AbstractScriptExecutor executor, SshExecutorFactoryContext context, Map<String, Object> taskContext) {
    CommandExecutionStatus status = runPreInitCommand(taskParameters, executor, context, taskContext);
    if (!CommandExecutionStatus.SUCCESS.equals(status)) {
      return status;
    }

    for (NgCommandUnit commandUnit : taskParameters.getCommandUnits()) {
      if (NGCommandUnitType.SCRIPT.equals(commandUnit.getCommandUnitType())) {
        generateCommand(taskParameters, (ScriptCommandUnit) commandUnit);
      }
    }

    return CommandExecutionStatus.SUCCESS;
  }

  private void generateCommand(SshCommandTaskParameters taskParameters, ScriptCommandUnit commandUnit) {
    final String script = commandUnit.getScript();

    boolean includeTailFunctions = isNotEmpty(commandUnit.getTailFilePatterns())
        || StringUtils.contains(script, "harness_utils_start_tail_log_verification")
        || StringUtils.contains(script, "harness_utils_wait_for_tail_log_verification");

    try {
      String generatedExecLauncherV2Command =
          generateExecLauncherV2Command(taskParameters, commandUnit, includeTailFunctions);
      StringBuilder command = new StringBuilder(generatedExecLauncherV2Command);

      if (isEmpty(commandUnit.getTailFilePatterns())) {
        command.append(script);
      } else {
        command.append(' ').append(generateTailWrapperV2Command(commandUnit));
      }

      commandUnit.setCommand(command.toString());

    } catch (IOException | TemplateException e) {
      throw new CommandExecutionException("Failed to prepare script", e);
    }
  }

  private CommandExecutionStatus runPreInitCommand(SshCommandTaskParameters taskParameters,
      AbstractScriptExecutor executor, SshExecutorFactoryContext context, Map<String, Object> taskContext) {
    String cmd = String.format("mkdir -p %s", getExecutionStagingDir(taskParameters));
    CommandExecutionStatus commandExecutionStatus = executor.executeCommandString(cmd, true);

    StringBuffer envVariablesFromHost = new StringBuffer();
    commandExecutionStatus = commandExecutionStatus == CommandExecutionStatus.SUCCESS
        ? executor.executeCommandString("printenv", envVariablesFromHost)
        : commandExecutionStatus;

    Properties properties = new Properties();
    try {
      properties.load(new StringReader(envVariablesFromHost.toString().replaceAll("\\\\", "\\\\\\\\")));
      Map<String, String> mapOfEnvVariablesFromHost =
          properties.entrySet().stream().collect(toMap(o -> o.getKey().toString(), o -> o.getValue().toString()));
      context.addEnvVariables(mapOfEnvVariablesFromHost);
      context.addEnvVariables(taskParameters.getEnvironmentVariables());
      evaluateCommandUnitsVariables(context, taskParameters.getCommandUnits());
      taskContext.put(RESOLVED_ENV_VARIABLES_KEY, context.getEnvironmentVariables());

    } catch (IOException e) {
      throw new CommandExecutionException("Failed to process destination host env variables", e);
    }

    return commandExecutionStatus;
  }

  private String generateExecLauncherV2Command(SshCommandTaskParameters taskParameters, ScriptCommandUnit commandUnit,
      boolean includeTailFunctions) throws IOException, TemplateException {
    String workingDir =
        isNotBlank(commandUnit.getWorkingDirectory()) ? "'" + commandUnit.getWorkingDirectory().trim() + "'" : "";
    try (StringWriter stringWriter = new StringWriter()) {
      Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                               .put("executionId", taskParameters.getExecutionId())
                                               .put("executionStagingDir", getExecutionStagingDir(taskParameters))
                                               .put("envVariables", taskParameters.getEnvironmentVariables())
                                               .put("safeEnvVariables", taskParameters.getEnvironmentVariables())
                                               .put("scriptWorkingDirectory", workingDir)
                                               .put("includeTailFunctions", includeTailFunctions)
                                               .build();
      SshInitCommandTemplates.getTemplate(EXECLAUNCHERV2_SH_FTL).process(templateParams, stringWriter);
      return stringWriter.toString();
    }
  }

  private String generateTailWrapperV2Command(ScriptCommandUnit commandUnit) throws IOException, TemplateException {
    try (StringWriter stringWriter = new StringWriter()) {
      Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                               .put("tailPatterns", commandUnit.getTailFilePatterns())
                                               .put("commandString", commandUnit.getScript())
                                               .build();
      SshInitCommandTemplates.getTemplate(TAILWRAPPERV2_SH_FTL).process(templateParams, stringWriter);
      return stringWriter.toString();
    }
  }

  @VisibleForTesting
  void evaluateCommandUnitsVariables(SshExecutorFactoryContext context, List<NgCommandUnit> ngCommandUnits) {
    ngCommandUnits.forEach(commandUnit -> {
      String commandUnitType = commandUnit.getCommandUnitType();
      if (NGCommandUnitType.SCRIPT.equals(commandUnitType)) {
        ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) commandUnit;
        scriptCommandUnit.setWorkingDirectory(context.evaluateVariable(scriptCommandUnit.getWorkingDirectory()));
      }

      if (NGCommandUnitType.COPY.equals(commandUnitType)) {
        CopyCommandUnit copyCommandUnit = (CopyCommandUnit) commandUnit;
        copyCommandUnit.setDestinationPath(context.evaluateVariable(copyCommandUnit.getDestinationPath()));
      }

      if (NGCommandUnitType.DOWNLOAD_ARTIFACT.equals(commandUnitType)) {
        NgDownloadArtifactCommandUnit downloadArtifactCommandUnit = (NgDownloadArtifactCommandUnit) commandUnit;
        downloadArtifactCommandUnit.setDestinationPath(
            context.evaluateVariable(downloadArtifactCommandUnit.getDestinationPath()));
      }
    });
  }

  private void checkIfDownloadAzureUniversalArtifactSupported(
      AbstractScriptExecutor executor, SshCommandTaskParameters sshCommandTaskParameters) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = sshCommandTaskParameters.getArtifactDelegateConfig();
    if (artifactDelegateConfig instanceof AzureArtifactDelegateConfig) {
      AzureArtifactDelegateConfig azureArtifactDelegateConfig = (AzureArtifactDelegateConfig) artifactDelegateConfig;
      if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
        for (NgCommandUnit cu : sshCommandTaskParameters.getCommandUnits()) {
          if (NGCommandUnitType.DOWNLOAD_ARTIFACT.equals(cu.getCommandUnitType())) {
            checkIfAzureCliInstalled(executor);
            break;
          }
        }
      }
    }
  }

  private void checkIfAzureCliInstalled(AbstractScriptExecutor executor) {
    CommandExecutionStatus status = executor.executeCommandString(AZURE_CLI_CHECK_SCRIPT, true);

    if (!CommandExecutionStatus.SUCCESS.equals(status)) {
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_HINT,
          SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_EXPLANATION,
          new SshCommandExecutionException(SshExceptionConstants.AZURE_CLI_INSTALLATION_CHECK_FAILED));
    }
  }
}
