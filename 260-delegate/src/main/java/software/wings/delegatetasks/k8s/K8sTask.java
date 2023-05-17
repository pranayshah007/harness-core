/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sDelegateTaskParams.K8sDelegateTaskParamsBuilder;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.appmanifest.StoreType;
import software.wings.delegatetasks.k8s.taskhandler.K8sTaskHandler;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sBlueGreenDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTask extends AbstractDelegateRunnableTask {
  @Inject private Map<String, K8sTaskHandler> k8sCommandTaskTypeToTaskHandlerMap;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  private static final String WORKING_DIR_BASE = "./repository/k8s/";

  public K8sTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public K8sTaskExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public K8sTaskExecutionResponse run(TaskParameters parameters) {
    K8sTaskParameters k8sTaskParameters = (K8sTaskParameters) parameters;

    log.info("Starting task execution for Command {}", k8sTaskParameters.getCommandType().name());

    if (k8sTaskParameters.getCommandType() == K8sTaskType.INSTANCE_SYNC) {
      try {
        return k8sCommandTaskTypeToTaskHandlerMap.get(k8sTaskParameters.getCommandType().name())
            .executeTask(k8sTaskParameters, null);
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.warn("Exception in processing K8s instance sync task [{}]",
            k8sTaskParameters.getCommandName() + ":" + k8sTaskParameters.getCommandType(), sanitizedException);
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .build();
      }
    } else {
      String workingDirectory = Paths.get(WORKING_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                    .normalize()
                                    .toAbsolutePath()
                                    .toString();

      try {
        createDirectoryIfDoesNotExist(workingDirectory);
        waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

        containerDeploymentDelegateHelper.persistKubernetesConfig(
            k8sTaskParameters.getK8sClusterConfig(), workingDirectory);

        createDirectoryIfDoesNotExist(Paths.get(workingDirectory, K8sConstants.MANIFEST_FILES_DIR).toString());

        Optional<StoreType> optionalStoreType = getStoreTypeFromK8sTaskParameters(k8sTaskParameters);
        K8sDelegateTaskParams k8SDelegateTaskParams = getK8sDelegateTaskParamsBasedOnManifestType(workingDirectory,
            k8sTaskParameters.getHelmVersion(), k8sTaskParameters.isUseNewKubectlVersion(),
            k8sTaskParameters.isUseLatestKustomizeVersion(), optionalStoreType.orElse(null));

        logK8sVersion(k8sTaskParameters, k8SDelegateTaskParams);

        return k8sCommandTaskTypeToTaskHandlerMap.get(k8sTaskParameters.getCommandType().name())
            .executeTask(k8sTaskParameters, k8SDelegateTaskParams);
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Exception in processing K8s task [{}]",
            k8sTaskParameters.getCommandName() + ":" + k8sTaskParameters.getCommandType(), sanitizedException);
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .build();
      } finally {
        cleanup(workingDirectory);
      }
    }
  }

  private void logK8sVersion(K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) {
    try {
      k8sCommandTaskTypeToTaskHandlerMap.get(K8sTaskType.VERSION.name())
          .executeTask(k8sTaskParameters, k8sDelegateTaskParams);
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

  private K8sDelegateTaskParams getK8sDelegateTaskParamsBasedOnManifestType(String workingDirectory,
      HelmVersion helmVersion, boolean isUseNewKubectlVersion, boolean isUseLatestKustomizeVersion,
      StoreType storeType) {
    K8sDelegateTaskParamsBuilder k8sDelegateTaskParamsBuilder =
        K8sDelegateTaskParams.builder()
            .kubectlPath(k8sGlobalConfigService.getKubectlPath(isUseNewKubectlVersion))
            .kubeconfigPath(K8sConstants.KUBECONFIG_FILENAME)
            .workingDirectory(workingDirectory);

    if (!isNull(storeType)) {
      switch (storeType) {
        case Local:
        case Remote:
        case CUSTOM:
          k8sDelegateTaskParamsBuilder.goTemplateClientPath(k8sGlobalConfigService.getGoTemplateClientPath());
          break;

        case HelmChartRepo:
        case HelmSourceRepo:
          k8sDelegateTaskParamsBuilder.helmPath(k8sGlobalConfigService.getHelmPath(helmVersion));
          break;

        case KustomizeSourceRepo:
          k8sDelegateTaskParamsBuilder
              .kustomizeBinaryPath(k8sGlobalConfigService.getKustomizePath(isUseLatestKustomizeVersion))
              .useLatestKustomizeVersion(isUseLatestKustomizeVersion);
          break;

        case OC_TEMPLATES:
        case CUSTOM_OPENSHIFT_TEMPLATE:
          k8sDelegateTaskParamsBuilder.ocPath(InstallUtils.getLatestVersionPath(OC));
          break;

        default:
          return k8sDelegateTaskParamsBuilder.build();
      }
    }
    return k8sDelegateTaskParamsBuilder.build();
  }

  private Optional<StoreType> getStoreTypeFromK8sTaskParameters(K8sTaskParameters k8sTaskParameters) {
    if (k8sTaskParameters instanceof K8sApplyTaskParameters) {
      K8sApplyTaskParameters k8sApplyTaskParameters = (K8sApplyTaskParameters) k8sTaskParameters;
      if (k8sApplyTaskParameters.getK8sDelegateManifestConfig() != null) {
        return Optional.ofNullable(k8sApplyTaskParameters.getK8sDelegateManifestConfig().getManifestStoreTypes());
      }
    } else if (k8sTaskParameters instanceof K8sBlueGreenDeployTaskParameters) {
      K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters =
          (K8sBlueGreenDeployTaskParameters) k8sTaskParameters;
      if (k8sBlueGreenDeployTaskParameters.getK8sDelegateManifestConfig() != null) {
        return Optional.ofNullable(
            k8sBlueGreenDeployTaskParameters.getK8sDelegateManifestConfig().getManifestStoreTypes());
      }
    } else if (k8sTaskParameters instanceof K8sCanaryDeployTaskParameters) {
      K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters = (K8sCanaryDeployTaskParameters) k8sTaskParameters;
      if (k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig() != null) {
        return Optional.ofNullable(
            k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig().getManifestStoreTypes());
      }
    } else if (k8sTaskParameters instanceof K8sDeleteTaskParameters) {
      K8sDeleteTaskParameters k8sDeleteTaskParameters = (K8sDeleteTaskParameters) k8sTaskParameters;
      if (k8sDeleteTaskParameters.getK8sDelegateManifestConfig() != null) {
        return Optional.ofNullable(k8sDeleteTaskParameters.getK8sDelegateManifestConfig().getManifestStoreTypes());
      }
    } else if (k8sTaskParameters instanceof K8sRollingDeployTaskParameters) {
      K8sRollingDeployTaskParameters k8sRollingDeployTaskParameters =
          (K8sRollingDeployTaskParameters) k8sTaskParameters;
      if (k8sRollingDeployTaskParameters.getK8sDelegateManifestConfig() != null) {
        return Optional.ofNullable(
            k8sRollingDeployTaskParameters.getK8sDelegateManifestConfig().getManifestStoreTypes());
      }
    }
    return Optional.empty();
  }
}
