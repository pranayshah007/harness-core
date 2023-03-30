/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.k8s.DeleteResourcesType.ResourceName;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getResourcesInStringFormat;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Delete;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sDeleteRequestHandler extends K8sRequestHandler {
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sDeleteBaseHandler k8sDeleteBaseHandler;

  private Kubectl client;
  private String releaseName;
  private String manifestFilesDirectory;
  @Nullable private List<KubernetesResource> resources;
  private List<KubernetesResourceId> resourceIdsToDelete;
  private KubernetesConfig kubernetesConfig;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8SDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sDeleteRequest"));
    }

    K8sDeleteRequest k8sDeleteRequest = (K8sDeleteRequest) k8sDeployRequest;
    releaseName = k8sDeleteRequest.getReleaseName();
    manifestFilesDirectory = Paths.get(k8SDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    LogCallback executionLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Delete, true, commandUnitsProgress);

    return executeDelete(
        k8sDeleteRequest, k8SDelegateTaskParams, executionLogCallback, logStreamingTaskClient, commandUnitsProgress);
  }

  private K8sDeployResponse executeDelete(K8sDeleteRequest k8sDeleteRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    DeleteResourcesType deleteResourcesType = k8sDeleteRequest.getDeleteResourcesType();
    switch (deleteResourcesType) {
      case ResourceName:
      case ReleaseName:
        return executeDeleteUsingResources(k8sDeleteRequest, k8sDelegateTaskParams, executionLogCallback,
            logStreamingTaskClient, commandUnitsProgress);
      case ManifestPath:
        return executeDeleteUsingFiles(k8sDeleteRequest, k8sDelegateTaskParams, executionLogCallback,
            logStreamingTaskClient, commandUnitsProgress);
      default:
        throw new UnsupportedOperationException(
            String.format("Delete resource type: [%s]", deleteResourcesType.name()));
    }
  }

  private K8sDeployResponse executeDeleteUsingFiles(K8sDeleteRequest k8sDeleteRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sDeleteRequest.getTimeoutIntervalInMin());

    LogCallback logCallback = k8sTaskHelperBase.getLogCallback(
        logStreamingTaskClient, FetchFiles, k8sDeleteRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    logCallback.saveExecutionLog(color("\nStarting Kubernetes Delete", LogColor.White, LogWeight.Bold));

    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sDeleteRequest.getManifestDelegateConfig(),
        manifestFilesDirectory, logCallback, steadyStateTimeoutInMillis, k8sDeleteRequest.getAccountId());

    initUsingFilePaths(k8sDeleteRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    k8sTaskHelperBase.deleteManifests(client, resources, k8sDelegateTaskParams, executionLogCallback);

    return k8sDeleteBaseHandler.getSuccessResponse();
  }

  @VisibleForTesting
  void initUsingFilePaths(K8sDeleteRequest k8sDeleteRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    if (EmptyPredicate.isNotEmpty(releaseName)) {
      executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));
    }

    client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());
    kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        k8sDeleteRequest.getK8sInfraDelegateConfig(), executionLogCallback);

    if (isEmpty(k8sDeleteRequest.getFilePaths())) {
      executionLogCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
      executionLogCallback.saveExecutionLog("\nSuccess.", INFO, SUCCESS);
    } else {
      List<String> deleteFilePaths = Arrays.stream(k8sDeleteRequest.getFilePaths().split(","))
                                         .map(String::trim)
                                         .filter(StringUtils::isNotBlank)
                                         .collect(Collectors.toList());

      if (isEmpty(deleteFilePaths)) {
        executionLogCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
        executionLogCallback.saveExecutionLog("\nSuccess.", INFO, SUCCESS);
      } else {
        executionLogCallback.saveExecutionLog(color("Found following files to be applied in the state", White, Bold));
        StringBuilder sb = new StringBuilder(1024);
        deleteFilePaths.forEach(each -> sb.append(color(format("- %s", each), Gray)).append(System.lineSeparator()));
        executionLogCallback.saveExecutionLog(sb.toString());

        List<String> manifestOverrideFiles = getManifestOverrideFlies(k8sDeleteRequest, emptyMap());

        resources = k8sTaskHelperBase.getResourcesFromManifests(k8sDelegateTaskParams,
            k8sDeleteRequest.getManifestDelegateConfig(), manifestFilesDirectory, deleteFilePaths,
            manifestOverrideFiles, releaseName, k8sDeleteRequest.getK8sInfraDelegateConfig().getNamespace(),
            executionLogCallback, k8sDeleteRequest.getTimeoutIntervalInMin(), false);

        executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
        executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));
        executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
      }
    }
  }

  private K8sDeployResponse executeDeleteUsingResources(K8sDeleteRequest k8sDeleteRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    init(k8sDeleteRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    if (isEmpty(resourceIdsToDelete)) {
      return k8sDeleteBaseHandler.getSuccessResponse();
    }

    k8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIdsToDelete, executionLogCallback, true);

    return k8sDeleteBaseHandler.getSuccessResponse();
  }

  private void init(K8sDeleteRequest k8sDeleteRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback) throws IOException {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    if (EmptyPredicate.isNotEmpty(releaseName)) {
      executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));
    }
    client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());
    kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        k8sDeleteRequest.getK8sInfraDelegateConfig(), executionLogCallback);

    try {
      resourceIdsToDelete =
          k8sDeleteBaseHandler.getResourceIdsToDelete(k8sDeleteRequest, kubernetesConfig, executionLogCallback);
    } catch (WingsException e) {
      if (ResourceName == k8sDeleteRequest.getDeleteResourcesType()) {
        throw NestedExceptionUtils.hintWithExplanationException(
            format(KubernetesExceptionHints.INVALID_RESOURCE_KIND_NAME_FORMAT, k8sDeleteRequest.getResources(),
                k8sDeleteRequest.getResources()),
            format(KubernetesExceptionExplanation.INVALID_RESOURCE_KIND_NAME_FORMAT, k8sDeleteRequest.getResources()),
            new KubernetesTaskException(ExceptionUtils.getMessage(e)));
      }

      throw e;
    }

    if (resourceIdsToDelete.isEmpty()) {
      executionLogCallback.saveExecutionLog("\nNo resources found to delete.", INFO);
    } else {
      executionLogCallback.saveExecutionLog(color("\nResources to delete are: ", White, Bold)
          + color(getResourcesInStringFormat(resourceIdsToDelete), Gray));
    }

    executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }
}
