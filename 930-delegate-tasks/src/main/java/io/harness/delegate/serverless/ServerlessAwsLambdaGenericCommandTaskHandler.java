/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.awscli.AwsCliClient;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult.ServerlessAwsLambdaDeployResultBuilder;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.serverless.*;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessGenericRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessGenericResponse;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.K8sConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.*;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.*;
import static io.harness.logging.LogLevel.ERROR;
import static java.lang.String.format;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ServerlessAwsLambdaGenericCommandTaskHandler extends ServerlessCommandTaskHandler {

  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  @Inject private AwsCliClient awsCliClient;

  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private long timeoutInMillis;
  private String serverlessAwsLambdaCredentialType;
  private boolean crossAccountAccessFlag;
  private Map<String, String> environmentVariables;

  @Override
  protected ServerlessCommandResponse executeTaskInternal(ServerlessCommandRequest serverlessCommandRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(serverlessCommandRequest instanceof ServerlessGenericRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessGenericRequest"));
    }
    ServerlessGenericRequest serverlessGenericRequest= (ServerlessGenericRequest) serverlessCommandRequest;
    if (!(serverlessGenericRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessGenericRequest.getServerlessManifestConfig() instanceof ServerlessAwsLambdaManifestConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessManifestConfig", "Must be instance of ServerlessAwsLambdaManifestConfig"));
    }
    if (!(serverlessGenericRequest.getShellScriptTaskParametersNG() instanceof ShellScriptTaskParametersNG)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessDeployConfig", "Must be instance of ShellScriptTaskParametersNG"));
    }

    timeoutInMillis = serverlessGenericRequest.getTimeoutIntervalInMin() * 60000;
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessGenericRequest.getServerlessInfraConfig();
    serverlessAwsLambdaCredentialType =
        serverlessInfraConfigHelper.getServerlessAwsLambdaCredentialType(serverlessAwsLambdaInfraConfig);

    crossAccountAccessFlag = serverlessInfraConfigHelper.getAwsCrossAccountFlag(serverlessAwsLambdaInfraConfig);

    environmentVariables =
        serverlessAwsCommandTaskHelper.getAwsCredentialsEnvironmentVariables(serverlessDelegateTaskParams);

    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
            serverlessGenericRequest.getServerlessInfraConfig());

    LogCallback setupDirectoryLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true, commandUnitsProgress);
    try {
      setupDirectory(serverlessGenericRequest, setupDirectoryLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      setupDirectoryLogCallback.saveExecutionLog(
          color(format("%n setup directory failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback artifactLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.artifact.toString(), true, commandUnitsProgress);
    try {
      serverlessTaskHelperBase.fetchArtifacts(serverlessGenericRequest.getServerlessArtifactConfig(),
              serverlessGenericRequest.getSidecarServerlessArtifactConfigs(), artifactLogCallback,
          serverlessDelegateTaskParams.getWorkingDirectory());
      artifactLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      artifactLogCallback.saveExecutionLog(color(format("%n artifact download failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    LogCallback configureCredsLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true, commandUnitsProgress);

    try {
        serverlessAwsCommandTaskHelper.setUpConfigureCredential(serverlessAwsLambdaConfig, configureCredsLogCallback,
          serverlessDelegateTaskParams, serverlessAwsLambdaCredentialType, serverlessClient, timeoutInMillis,
          crossAccountAccessFlag, environmentVariables, awsCliClient, serverlessAwsLambdaInfraConfig);
      configureCredsLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      configureCredsLogCallback.saveExecutionLog(
          color(format("%n configure credentials failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }
    LogCallback pluginLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    try {
      serverlessAwsCommandTaskHelper.installPlugins(serverlessManifestSchema, serverlessDelegateTaskParams,
          pluginLogCallback, serverlessClient, timeoutInMillis, serverlessManifestConfig);
      pluginLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      pluginLogCallback.saveExecutionLog(color(format("%n installing plugin failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback deployLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.serverlessShellScript.toString(), true, commandUnitsProgress);
      ShellScriptTaskResponseNG shellScriptTaskResponseNG =  executeServerlessShellScript(serverlessGenericRequest, deployLogCallback, serverlessDelegateTaskParams);
      if(shellScriptTaskResponseNG.getErrorMessage() != null) {
        deployLogCallback.saveExecutionLog(
                color(format("%n Bash Script Failed to execute."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
                CommandExecutionStatus.FAILURE);
      } else {
        deployLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
      return ServerlessGenericResponse.builder()
              .commandExecutionStatus(shellScriptTaskResponseNG.getStatus())
              .delegateMetaInfo(shellScriptTaskResponseNG.getDelegateMetaInfo())
              .executeCommandResponse(shellScriptTaskResponseNG.getExecuteCommandResponse())
              .errorMessage(shellScriptTaskResponseNG.getErrorMessage())
              .unitProgressData(shellScriptTaskResponseNG.getUnitProgressData())
              .build();
  }

  private void setupDirectory(ServerlessGenericRequest serverlessGenericRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("setting up serverless directory..%n%n"));
    serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessGenericRequest.getServerlessManifestConfig();
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
            serverlessGenericRequest.getAccountId(), executionLogCallback, serverlessDelegateTaskParams);
    serverlessManifestSchema = serverlessAwsCommandTaskHelper.parseServerlessManifest(
        executionLogCallback, serverlessGenericRequest.getManifestContent());
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams, serverlessManifestConfig,
            serverlessGenericRequest.getManifestContent(), serverlessManifestSchema);
    executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private ShellScriptTaskResponseNG executeServerlessShellScript(ServerlessGenericRequest serverlessGenericRequest,
                                           LogCallback deployLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    ShellScriptTaskParametersNG taskParameters = serverlessGenericRequest.getShellScriptTaskParametersNG();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    serverlessGenericRequest.getShellScriptTaskParametersNG().getEnvironmentVariables().put("serverlessPath", serverlessDelegateTaskParams.getWorkingDirectory());
    if (taskParameters.isExecuteOnDelegate()) {
      ShellExecutorConfig shellExecutorConfig = getShellExecutorConfig(taskParameters);
      ScriptProcessExecutor executor = ServerlessShellExecutorFactoryNG.getExecutor(shellExecutorConfig, deployLogCallback, commandUnitsProgress);
      // TODO: check later
      // if (taskParameters.isLocalOverrideFeatureFlag()) {
      //   taskParameters.setScript(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(taskParameters.getScript()));
      // }
      ExecuteCommandResponse executeCommandResponse =
              executor.executeCommandString(taskParameters.getScript(), taskParameters.getOutputVars());
      return ShellScriptTaskResponseNG.builder()
              .executeCommandResponse(executeCommandResponse)
              .status(executeCommandResponse.getStatus())
              .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
              .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
              .build();
    } else {
      try {
        SshSessionConfig sshSessionConfig = getSshSessionConfig(taskParameters);
        ScriptSshExecutor executor =
                ServerlessSshExecutorFactoryNG.getExecutor(sshSessionConfig, deployLogCallback, commandUnitsProgress);
        ExecuteCommandResponse executeCommandResponse =
                executor.executeCommandString(taskParameters.getScript(), taskParameters.getOutputVars());
        return ShellScriptTaskResponseNG.builder()
                .executeCommandResponse(executeCommandResponse)
                .status(executeCommandResponse.getStatus())
                .errorMessage(getErrorMessage(executeCommandResponse.getStatus()))
                .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
                .build();
      } catch (Exception e) {
        return ShellScriptTaskResponseNG.builder()
                .status(CommandExecutionStatus.FAILURE)
                .errorMessage("Bash Script Failed to execute. Reason: " + e.getMessage())
                .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
                .build();
      } finally {
        SshSessionManager.evictAndDisconnectCachedSession(taskParameters.getExecutionId(), taskParameters.getHost());
      }
    }
  }


  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Shell Script execution queued.";
      case FAILURE:
        return "Shell Script execution failed. Please check execution logs.";
      case RUNNING:
        return "Shell Script execution running.";
      case SKIPPED:
        return "Shell Script execution skipped.";
      case SUCCESS:
      default:
        return "";
    }
  }

  private SshSessionConfig getSshSessionConfig(ShellScriptTaskParametersNG taskParameters) {
    SshSessionConfig sshSessionConfig = sshSessionConfigMapper.getSSHSessionConfig(
            taskParameters.getSshKeySpecDTO(), taskParameters.getEncryptionDetails());

    sshSessionConfig.setAccountId(taskParameters.getAccountId());
    sshSessionConfig.setExecutionId(taskParameters.getExecutionId());
    sshSessionConfig.setHost(taskParameters.getHost());
    sshSessionConfig.setWorkingDirectory(taskParameters.getWorkingDirectory());
    sshSessionConfig.setCommandUnitName(ServerlessCommandUnitConstants.serverlessShellScript.toString());
    return sshSessionConfig;
  }

  private ShellExecutorConfig getShellExecutorConfig(ShellScriptTaskParametersNG taskParameters) {
    String kubeConfigFileContent = taskParameters.getScript().contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)
            && taskParameters.getK8sInfraDelegateConfig() != null
            ? containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(taskParameters.getK8sInfraDelegateConfig())
            : "";

    return ShellExecutorConfig.builder()
            .accountId(taskParameters.getAccountId())
            .executionId(taskParameters.getExecutionId())
            .commandUnitName(ServerlessCommandUnitConstants.serverlessShellScript.toString())
            .workingDirectory(taskParameters.getWorkingDirectory())
            .environment(taskParameters.getEnvironmentVariables())
            .kubeConfigContent(kubeConfigFileContent)
            .scriptType(taskParameters.getScriptType())
            .build();
  }
}
