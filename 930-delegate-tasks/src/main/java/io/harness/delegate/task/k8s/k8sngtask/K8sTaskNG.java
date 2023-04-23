/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.k8s.K8sRequestHandler;
import io.harness.delegate.task.ManifestDelegateConfigHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sDelegateTaskParams.K8sDelegateTaskParamsBuilder;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(CDP)
public class K8sTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<String, K8sRequestHandler> k8sTaskTypeToRequestHandler;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  @Inject private ManifestDelegateConfigHelper manifestDelegateConfigHelper;

  private static final String WORKING_DIR_BASE = "./repository/k8s/";
  public static final String KUBECONFIG_FILENAME = "config";
  public static final String MANIFEST_FILES_DIR = "manifest-files";

  public K8sTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public K8sDeployResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public K8sDeployResponse run(TaskParameters parameters) {
    K8sDeployRequest k8sDeployRequest = (K8sDeployRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = k8sDeployRequest.getCommandUnitsProgress() != null
        ? k8sDeployRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();

    log.info("Starting task execution for Command {}", k8sDeployRequest.getTaskType().name());
    decryptRequestDTOs(k8sDeployRequest);

    if (k8sDeployRequest.getTaskType() == K8sTaskType.INSTANCE_SYNC) {
      try {
        return k8sTaskTypeToRequestHandler.get(k8sDeployRequest.getTaskType().name())
            .executeTask(k8sDeployRequest, null, getLogStreamingTaskClient(), commandUnitsProgress);
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Exception in processing k8s task [{}]",
            k8sDeployRequest.getCommandName() + ":" + k8sDeployRequest.getTaskType(), sanitizedException);
        return K8sDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .build();
      }
    } else {
      String workingDirectory = Paths.get(WORKING_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                    .normalize()
                                    .toAbsolutePath()
                                    .toString();
      K8sRequestHandler requestHandler = k8sTaskTypeToRequestHandler.get(k8sDeployRequest.getTaskType().name());

      try {
        createDirectoryIfDoesNotExist(workingDirectory);
        waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

        KubernetesConfig kubernetesConfig = containerDeploymentDelegateBaseHelper.decryptAndGetKubernetesConfig(
            k8sDeployRequest.getK8sInfraDelegateConfig(), workingDirectory);
        containerDeploymentDelegateBaseHelper.persistKubernetesConfig(kubernetesConfig, workingDirectory);

        createDirectoryIfDoesNotExist(Paths.get(workingDirectory, MANIFEST_FILES_DIR).toString());
        HelmVersion helmVersion =
            (k8sDeployRequest.getManifestDelegateConfig() != null
                && k8sDeployRequest.getManifestDelegateConfig().getManifestType() == ManifestType.HELM_CHART)
            ? ((HelmChartManifestDelegateConfig) k8sDeployRequest.getManifestDelegateConfig()).getHelmVersion()
            : null;

        ManifestType manifestType = k8sDeployRequest.getManifestDelegateConfig() != null
            ? k8sDeployRequest.getManifestDelegateConfig().getManifestType()
            : null;
        K8sDelegateTaskParams k8SDelegateTaskParams = getK8sDelegateTaskParamsBasedOnManifestType(workingDirectory,
            helmVersion, k8sDeployRequest.isUseNewKubectlVersion(), k8sDeployRequest.isUseLatestKustomizeVersion(),
            manifestType);

        // TODO: @anshul/vaibhav , fix this
        //        logK8sVersion(k8sDeployRequest, k8SDelegateTaskParams, commandUnitsProgress);

        K8sDeployResponse k8sDeployResponse = requestHandler.executeTask(
            k8sDeployRequest, k8SDelegateTaskParams, getLogStreamingTaskClient(), commandUnitsProgress);

        k8sDeployResponse.setCommandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
        return k8sDeployResponse;
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Exception in processing k8s task [{}]",
            k8sDeployRequest.getCommandName() + ":" + k8sDeployRequest.getTaskType(), sanitizedException);
        if (requestHandler != null && requestHandler.isErrorFrameworkSupported()) {
          try {
            requestHandler.onTaskFailed(
                k8sDeployRequest, sanitizedException, getLogStreamingTaskClient(), commandUnitsProgress);
          } catch (Exception e) {
            throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress),
                ExceptionMessageSanitizer.sanitizeException(e));
          }

          throw new TaskNGDataException(
              UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
        }

        return K8sDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .commandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      } finally {
        cleanup(workingDirectory);
      }
    }
  }

  @VisibleForTesting
  void logK8sVersion(K8sDeployRequest k8sDeployRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      CommandUnitsProgress commandUnitsProgress) {
    try {
      k8sTaskTypeToRequestHandler.get(K8sTaskType.VERSION.name())
          .executeTask(k8sDeployRequest, k8sDelegateTaskParams, getLogStreamingTaskClient(), commandUnitsProgress);
    } catch (Exception ex) {
      log.error("Error fetching K8s Server Version: ", ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  private void cleanup(String workingDirectory) {
    try {
      log.warn("Cleaning up directory " + workingDirectory);
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (Exception ex) {
      log.warn("Exception in directory cleanup.", ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  public void decryptRequestDTOs(K8sDeployRequest k8sDeployRequest) {
    manifestDelegateConfigHelper.decryptManifestDelegateConfig(k8sDeployRequest.getManifestDelegateConfig());
    containerDeploymentDelegateBaseHelper.decryptK8sInfraDelegateConfig(k8sDeployRequest.getK8sInfraDelegateConfig());
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  public K8sDelegateTaskParams getK8sDelegateTaskParamsBasedOnManifestType(String workingDirectory,
      HelmVersion helmVersion, boolean isUseNewKubectlVersion, boolean isUseLatestKustomizeVersion,
      ManifestType manifestType) {
    K8sDelegateTaskParamsBuilder k8sDelegateTaskParamsBuilder =
        K8sDelegateTaskParams.builder()
            .kubectlPath(k8sGlobalConfigService.getKubectlPath(isUseNewKubectlVersion))
            .kubeconfigPath(KUBECONFIG_FILENAME)
            .workingDirectory(workingDirectory);

    if (!isNull(manifestType)) {
      switch (manifestType) {
        case K8S_MANIFEST:
          k8sDelegateTaskParamsBuilder.goTemplateClientPath(k8sGlobalConfigService.getGoTemplateClientPath());
          break;

        case HELM_CHART:
          k8sDelegateTaskParamsBuilder.helmPath(k8sGlobalConfigService.getHelmPath(helmVersion));
          break;

        case OPENSHIFT_TEMPLATE:
          k8sDelegateTaskParamsBuilder.ocPath(InstallUtils.getLatestVersionPath(OC));
          break;

        case KUSTOMIZE:
          k8sDelegateTaskParamsBuilder.kustomizeBinaryPath(
              k8sGlobalConfigService.getKustomizePath(isUseLatestKustomizeVersion));
          break;

        default:
          return k8sDelegateTaskParamsBuilder.build();
      }
    }
    return k8sDelegateTaskParamsBuilder.build();
  }
}
