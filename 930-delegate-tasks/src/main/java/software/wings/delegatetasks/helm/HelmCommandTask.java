/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.filesystem.FileIo.writeUtf8StringToFile;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.replace;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.exception.HarnessException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helm.HelmConstants;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.NoopExecutionCallback;
import io.harness.secret.SecretSanitizerThreadLocal;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.HelmDummyCommandUnitConstants;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmCommandExecutionResponse;
import software.wings.helpers.ext.helm.HelmDeployService;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Created by anubhaw on 3/22/18.
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private HelmDeployService helmDeployService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private HelmCommandHelper helmCommandHelper;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  protected static final String WORKING_DIR = "./repository/helm/source/${"
      + "ACTIVITY_ID"
      + "}";
  public HelmCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public HelmCommandExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public HelmCommandExecutionResponse run(TaskParameters parameters) {
    HelmCommandRequest helmCommandRequest = (HelmCommandRequest) parameters;
    HelmCommandResponse commandResponse;

    try {
      init(helmCommandRequest, getExecutionLogCallback(helmCommandRequest, HelmDummyCommandUnitConstants.Init));

      helmCommandRequest.setExecutionLogCallback(
          getExecutionLogCallback(helmCommandRequest, HelmDummyCommandUnitConstants.Prepare));

      helmCommandRequest.getExecutionLogCallback().saveExecutionLog(
          helmCommandHelper.getDeploymentMessage(helmCommandRequest), LogLevel.INFO, CommandExecutionStatus.RUNNING);

      switch (helmCommandRequest.getHelmCommandType()) {
        case INSTALL:
          commandResponse = helmDeployService.deploy((HelmInstallCommandRequest) helmCommandRequest);
          break;
        case ROLLBACK:
          commandResponse = helmDeployService.rollback((HelmRollbackCommandRequest) helmCommandRequest);
          break;
        case RELEASE_HISTORY:
          commandResponse = helmDeployService.releaseHistory((HelmReleaseHistoryCommandRequest) helmCommandRequest);
          break;
        default:
          throw new HarnessException("Operation not supported");
      }
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMsg = sanitizedException.getMessage();
      helmCommandRequest.getExecutionLogCallback().saveExecutionLog(
          errorMsg + "\n Overall deployment Failed", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      log.error(format("Exception in processing helm task [%s]", helmCommandRequest.toString()), sanitizedException);
      return HelmCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage("Exception in processing helm task: " + errorMsg)
          .build();
    }

    helmCommandRequest.getExecutionLogCallback().saveExecutionLog(
        "Command finished with status " + commandResponse.getCommandExecutionStatus(), LogLevel.INFO,
        commandResponse.getCommandExecutionStatus());
    log.info(commandResponse.getOutput());

    HelmCommandExecutionResponse helmCommandExecutionResponse =
        HelmCommandExecutionResponse.builder()
            .commandExecutionStatus(commandResponse.getCommandExecutionStatus())
            .helmCommandResponse(commandResponse)
            .build();

    if (CommandExecutionStatus.SUCCESS != commandResponse.getCommandExecutionStatus()) {
      helmCommandExecutionResponse.setErrorMessage(commandResponse.getOutput());
    }

    return helmCommandExecutionResponse;
  }

  private void init(HelmCommandRequest helmCommandRequest, LogCallback executionLogCallback) throws Exception {
    helmCommandRequest.setExecutionLogCallback(executionLogCallback);
    executionLogCallback.saveExecutionLog("Creating KubeConfig", LogLevel.INFO, CommandExecutionStatus.RUNNING);
    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateHelper.getKubernetesConfig(helmCommandRequest.getContainerServiceParams());
    String configLocation = containerDeploymentDelegateHelper.createKubeConfig(kubernetesConfig);
    helmCommandRequest.setKubeConfigLocation(configLocation);
    executionLogCallback.saveExecutionLog(
        "Setting KubeConfig\nKUBECONFIG_PATH=" + configLocation, LogLevel.INFO, CommandExecutionStatus.RUNNING);

    boolean isEnvVarSet = helmTaskHelperBase.isHelmLocalRepoSet();
    boolean isNotReleaseHistCmd = !(helmCommandRequest instanceof HelmReleaseHistoryCommandRequest);

    if (isNotReleaseHistCmd && !isEnvVarSet) {
      String workingDir = Paths.get(replace(WORKING_DIR, "${ACTIVITY_ID}", helmCommandRequest.getActivityId()))
                              .normalize()
                              .toAbsolutePath()
                              .toString();
      createDirectoryIfDoesNotExist(workingDir);
      waitForDirectoryToBeAccessibleOutOfProcess(workingDir, 10);
    }

    if (kubernetesConfig.getGcpAccountKeyFileContent().isPresent()) {
      String gcpCredsDirPath =
          HelmConstants.HELM_GCP_CREDS_PATH.replace("${ACTIVITY_ID}", helmCommandRequest.getActivityId());
      createDirectoryIfDoesNotExist(gcpCredsDirPath);
      waitForDirectoryToBeAccessibleOutOfProcess(gcpCredsDirPath, 10);
      Path gcpKeyFilePath = Paths.get(gcpCredsDirPath, K8sConstants.GCP_JSON_KEY_FILE_NAME).toAbsolutePath();
      writeUtf8StringToFile(gcpKeyFilePath.toString(), kubernetesConfig.getGcpAccountKeyFileContent().get());
      executionLogCallback.saveExecutionLog("Created and Setting gcpFileKeyPath at: " + gcpKeyFilePath.toAbsolutePath(),
          LogLevel.INFO, CommandExecutionStatus.RUNNING);
      helmCommandRequest.setGcpKeyPath(gcpKeyFilePath.toString());
    }

    ensureHelmInstalled(helmCommandRequest);
    executionLogCallback.saveExecutionLog("\nDone.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  protected LogCallback getExecutionLogCallback(HelmCommandRequest helmCommandRequest, String name) {
    return isAsync() ? new ExecutionLogCallback(delegateLogService, helmCommandRequest.getAccountId(),
               helmCommandRequest.getAppId(), helmCommandRequest.getActivityId(), name)
                     : new NoopExecutionCallback();
  }

  private void ensureHelmInstalled(HelmCommandRequest helmCommandRequest) {
    LogCallback executionLogCallback = helmCommandRequest.getExecutionLogCallback();

    executionLogCallback.saveExecutionLog("Finding helm version", LogLevel.INFO, CommandExecutionStatus.RUNNING);

    HelmCommandResponse helmCommandResponse = helmDeployService.ensureHelmInstalled(helmCommandRequest);
    log.info(helmCommandResponse.getOutput());
    executionLogCallback.saveExecutionLog(helmCommandResponse.getOutput());
  }
}
