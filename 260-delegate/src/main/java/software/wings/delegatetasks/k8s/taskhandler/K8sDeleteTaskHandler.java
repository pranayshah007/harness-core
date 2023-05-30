/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getResourcesInStringFormat;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Delete;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.k8s.model.KubernetesResourceId.createKubernetesResourceIdsFromKindName;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.InProgress;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.GrayDark;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sDeleteResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sDeleteTaskHandler extends K8sTaskHandler {
  @Inject private K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  private Kubectl client;
  private String releaseName;
  @Nullable private List<KubernetesResource> resources;
  private List<KubernetesResource> workloads;
  private KubernetesConfig kubernetesConfig;
  private String manifestFilesDirectory;
  private List<KubernetesResourceId> resourceIdsToDelete;
  private K8sReleaseHandler releaseHandler;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sDeleteTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("k8sTaskParameters", "Must be instance of K8sDeleteTaskParameters"));
    }

    K8sDeleteTaskParameters k8sDeleteTaskParameters = (K8sDeleteTaskParameters) k8sTaskParameters;
    releaseName = k8sDeleteTaskParameters.getReleaseName();
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(k8sDeleteTaskParameters.isUseDeclarativeRollback());
    manifestFilesDirectory = Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    ExecutionLogCallback executionLogCallback =
        new ExecutionLogCallback(delegateLogService, k8sDeleteTaskParameters.getAccountId(),
            k8sDeleteTaskParameters.getAppId(), k8sDeleteTaskParameters.getActivityId(), Delete);

    if (isEmpty(k8sDeleteTaskParameters.getResources())) {
      return executeDeleteUsingFiles(k8sDeleteTaskParameters, k8sDelegateTaskParams, executionLogCallback);
    } else if (!isEmpty(k8sDeleteTaskParameters.getFilePaths())) {
      executionLogCallback.saveExecutionLog("Both resources and files are present, giving priority to resources.");
    }
    return executeDeleteUsingResources(k8sDeleteTaskParameters, k8sDelegateTaskParams, executionLogCallback);
  }

  private K8sTaskExecutionResponse executeDeleteUsingFiles(K8sDeleteTaskParameters k8sDeleteTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) throws Exception {
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sDeleteTaskParameters.getTimeoutIntervalInMin());

    ExecutionLogCallback logCallback = k8sTaskHelper.getExecutionLogCallback(k8sDeleteTaskParameters, FetchFiles);
    logCallback.saveExecutionLog(color("\nStarting Kubernetes Delete", LogColor.White, LogWeight.Bold));

    boolean success =
        k8sTaskHelper.fetchManifestFilesAndWriteToDirectory(k8sDeleteTaskParameters.getK8sDelegateManifestConfig(),
            manifestFilesDirectory, logCallback, steadyStateTimeoutInMillis);
    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(
          K8sDeleteResponse.builder().build(), CommandExecutionStatus.FAILURE);
    }
    success = initUsingFilePaths(k8sDeleteTaskParameters, k8sDelegateTaskParams,
        new ExecutionLogCallback(delegateLogService, k8sDeleteTaskParameters.getAccountId(),
            k8sDeleteTaskParameters.getAppId(), k8sDeleteTaskParameters.getActivityId(), Init));
    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(
          K8sDeleteResponse.builder().build(), CommandExecutionStatus.FAILURE);
    }
    k8sTaskHelperBase.deleteManifests(client, resources, k8sDelegateTaskParams, executionLogCallback);
    return k8sTaskHelper.getK8sTaskExecutionResponse(
        K8sDeleteResponse.builder().build(), CommandExecutionStatus.SUCCESS);
  }

  private K8sTaskExecutionResponse executeDeleteUsingResources(K8sDeleteTaskParameters k8sDeleteTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) throws Exception {
    boolean success = init(k8sDeleteTaskParameters, k8sDelegateTaskParams,
        new ExecutionLogCallback(delegateLogService, k8sDeleteTaskParameters.getAccountId(),
            k8sDeleteTaskParameters.getAppId(), k8sDeleteTaskParameters.getActivityId(), Init));
    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(
          K8sDeleteResponse.builder().build(), CommandExecutionStatus.FAILURE);
    }
    if (isEmpty(resourceIdsToDelete)) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(
          K8sDeleteResponse.builder().build(), CommandExecutionStatus.SUCCESS);
    }
    k8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIdsToDelete, executionLogCallback, true);
    return k8sTaskHelper.getK8sTaskExecutionResponse(
        K8sDeleteResponse.builder().build(), CommandExecutionStatus.SUCCESS);
  }

  private boolean init(K8sDeleteTaskParameters k8sDeleteTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams,
      ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    try {
      client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
          k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
      kubernetesConfig =
          containerDeploymentDelegateHelper.getKubernetesConfig(k8sDeleteTaskParameters.getK8sClusterConfig(), false);

      String resourcesToDelete = k8sDeleteTaskParameters.getResources();

      if (k8sDeleteTaskParameters.isK8sCanaryDelete() && resourcesToDelete.contains("${k8s.canaryWorkload}")) {
        executionLogCallback.saveExecutionLog(
            "Unable to find canary workloads from existing canary deployment. Trying to find canary workloads from existing in-progress or failed release",
            WARN);
        resourceIdsToDelete = getCanaryResourceIdsFromReleaseHistory(releaseName, executionLogCallback);

        if (isNotEmpty(resourceIdsToDelete)) {
          executionLogCallback.saveExecutionLog(color("\nResources to delete are: ", White, Bold)
              + color(getResourcesInStringFormat(resourceIdsToDelete), Gray));
        } else {
          executionLogCallback.saveExecutionLog(color("No canary workloads to be deleted. Skipping.\n", White, Bold));
        }
        executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        return true;
      }
      if (StringUtils.isEmpty(k8sDeleteTaskParameters.getResources())) {
        executionLogCallback.saveExecutionLog("\nNo resources found to delete.");
        return true;
      }

      if ("*".equals(k8sDeleteTaskParameters.getResources().trim())) {
        executionLogCallback.saveExecutionLog("All Resources are selected for deletion");
        executionLogCallback.saveExecutionLog(color("Delete Namespace is set to: "
                + k8sDeleteTaskParameters.isDeleteNamespacesForRelease() + ", Skipping deleting Namespace resources",
            GrayDark, Bold));
        executionLogCallback.saveExecutionLog(
            "Delete Namespace is set to: " + k8sDeleteTaskParameters.isDeleteNamespacesForRelease());
        resourceIdsToDelete = fetchAllCreatedResourceIdsForDeletion(k8sDeleteTaskParameters, executionLogCallback);
      } else {
        resourceIdsToDelete = createKubernetesResourceIdsFromKindName(k8sDeleteTaskParameters.getResources());
      }

      executionLogCallback.saveExecutionLog(color("\nResources to delete are: ", White, Bold)
          + color(getResourcesInStringFormat(resourceIdsToDelete), Gray));

      executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);

      return true;
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  @VisibleForTesting
  boolean initUsingFilePaths(K8sDeleteTaskParameters k8sDeleteTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    try {
      client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
          k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
      kubernetesConfig =
          containerDeploymentDelegateHelper.getKubernetesConfig(k8sDeleteTaskParameters.getK8sClusterConfig(), false);

      if (isEmpty(k8sDeleteTaskParameters.getFilePaths())) {
        executionLogCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
        executionLogCallback.saveExecutionLog("\nSuccess.", INFO, SUCCESS);
        return true;
      }
      List<String> deleteFilePaths = Arrays.stream(k8sDeleteTaskParameters.getFilePaths().split(","))
                                         .map(String::trim)
                                         .filter(StringUtils::isNotBlank)
                                         .collect(Collectors.toList());

      if (isEmpty(deleteFilePaths)) {
        executionLogCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
        executionLogCallback.saveExecutionLog("\nSuccess.", INFO, SUCCESS);
        return true;
      }

      executionLogCallback.saveExecutionLog(color("Found following files to be applied in the state", White, Bold));
      StringBuilder sb = new StringBuilder(1024);
      deleteFilePaths.forEach(each -> sb.append(color(format("- %s", each), Gray)).append(System.lineSeparator()));
      executionLogCallback.saveExecutionLog(sb.toString());

      resources = k8sTaskHelper.getResourcesFromManifests(k8sDelegateTaskParams,
          k8sDeleteTaskParameters.getK8sDelegateManifestConfig(), manifestFilesDirectory, deleteFilePaths,
          k8sDeleteTaskParameters.getValuesYamlList(), releaseName, kubernetesConfig.getNamespace(),
          executionLogCallback, k8sDeleteTaskParameters, false);

      executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
      executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));
      return true;
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }
  }

  List<KubernetesResourceId> fetchAllCreatedResourceIdsForDeletion(
      K8sDeleteTaskParameters k8sDeleteTaskParameters, ExecutionLogCallback executionLogCallback) throws IOException {
    return k8sTaskHelperBase.getResourceIdsForDeletion(k8sDeleteTaskParameters.isUseDeclarativeRollback(),
        k8sDeleteTaskParameters.getReleaseName(), kubernetesConfig, executionLogCallback,
        k8sDeleteTaskParameters.isDeleteNamespacesForRelease());
  }
  private List<KubernetesResourceId> getCanaryResourceIdsFromReleaseHistory(String releaseName, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog(format("Getting canary workloads from release %s\n", releaseName));
    IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, releaseName);

    if (isEmpty(releaseHistory)) {
      logCallback.saveExecutionLog(format("Empty or missing release history for release %s", releaseName), WARN);
      return Collections.emptyList();
    }

    // In canary deployment we're leaving current release as in progress (it will be completed by rolling deployment).
    // Since we may catch some interrupted exceptions during task abortions it may happen that we will fail the canary
    // release. To ensure that the latest release is actually a canary release we have a more deep logic
    // in K8s Canary Delete Step (we will rely on release history only when we queued K8s Canary Task and step expire)
    IK8sRelease release = releaseHistory.getLatestRelease();
    if (InProgress != release.getReleaseStatus() && Failed != release.getReleaseStatus()) {
      logCallback.saveExecutionLog(
          format("Unable to identify any canary deployments for release %s.", releaseName), WARN);
      return Collections.emptyList();
    }

    return release.getResourceIds()
        .stream()
        .filter(resource -> resource.getName().endsWith(K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR))
        .collect(Collectors.toList());
  }
}
