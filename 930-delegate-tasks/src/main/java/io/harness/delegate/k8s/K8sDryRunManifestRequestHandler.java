/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.K8sDryRun;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.k8s.model.ServiceHookContext.MANIFEST_FILES_DIRECTORY;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sDryRunManifestRequest;
import io.harness.delegate.task.k8s.K8sDryRunManifestResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.delegate.utils.ServiceHookHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.kubectl.ApplyCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.DryRunOutput;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.LogCallback;

import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.zeroturnaround.exec.ProcessResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class K8sDryRunManifestRequestHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sRollingBaseHandler k8sRollingBaseHandler;

  private static final long MAX_VARIABLE_SIZE = 5 * 1024 * 1024; // 5MB limit for variable containing the manifest yaml
  private static final String DRY_RUN_MANIFEST_FILE_NAME = "manifests-dry-run.yaml";
  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  List<KubernetesResource> resources;
  private String manifestFilesDirectory;
  private String releaseName;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sDryRunManifestRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sDryRunManifestRequest"));
    }

    K8sDryRunManifestRequest k8sDryRunManifestRequest = (K8sDryRunManifestRequest) k8sDeployRequest;

    this.releaseName = k8sDryRunManifestRequest.getReleaseName();
    this.manifestFilesDirectory = Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    kubernetesConfig = k8sDelegateTaskParams.getKubernetesConfig();
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sDeployRequest.getTimeoutIntervalInMin());

    LogCallback logCallback = k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, FetchFiles,
        k8sDryRunManifestRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    ServiceHookDTO serviceHookTaskParams = new ServiceHookDTO(k8sDelegateTaskParams);
    ServiceHookHandler serviceHookHandler = new ServiceHookHandler(
        k8sDryRunManifestRequest.getServiceHooks(), serviceHookTaskParams, steadyStateTimeoutInMillis);
    logCallback.saveExecutionLog(color("\nStarting Kubernetes Dry Run", White, LogWeight.Bold));
    serviceHookHandler.addToContext(MANIFEST_FILES_DIRECTORY.getContextName(), manifestFilesDirectory);
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback);

    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sDryRunManifestRequest.getManifestDelegateConfig(),
        this.manifestFilesDirectory, logCallback, steadyStateTimeoutInMillis, k8sDryRunManifestRequest.getAccountId(),
        false, false);

    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback);
    logCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    String dryRunManifestYaml = manifestDryRunYaml(k8sDryRunManifestRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, K8sDryRun, true, commandUnitsProgress));
    return K8sDeployResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .k8sNGTaskResponse(K8sDryRunManifestResponse.builder().manifestDryRunYaml(dryRunManifestYaml).build())
        .build();
  }

  String manifestDryRunYaml(K8sDryRunManifestRequest request, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(color(format("Release Name: [%s]", this.releaseName), Yellow, Bold));
    if (kubernetesConfig == null) {
      log.warn("Kubernetes config passed to task is NULL. Creating it again...");
      this.kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
          request.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    }
    this.client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());

    KubernetesReleaseDetails releaseDetails = null;
    // For backward compatibility if delegate is released before ng-manager
    if (request.getUseDeclarativeRollback() != null) {
      K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(request.getUseDeclarativeRollback());
      IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, releaseName);
      int currentReleaseNumber = releaseHistory.getNextReleaseNumber(request.isInCanaryWorkflow());
      if (request.getUseDeclarativeRollback() && isEmpty(releaseHistory) && !request.isInCanaryWorkflow()) {
        currentReleaseNumber =
            k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(kubernetesConfig, releaseName);
      }

      releaseDetails = KubernetesReleaseDetails.builder().releaseNumber(currentReleaseNumber).build();
    }

    List<String> manifestOverrideFiles =
        getManifestOverrideFlies(request, releaseDetails != null ? releaseDetails.toContextMap() : emptyMap());

    this.resources = k8sRollingBaseHandler.prepareResourcesAndRenderTemplate(request, k8sDelegateTaskParams,
        manifestOverrideFiles, this.kubernetesConfig, this.manifestFilesDirectory, this.releaseName,
        request.isLocalOverrideFeatureFlag(), isErrorFrameworkSupported(), request.isInCanaryWorkflow(),
        request.isDisableFabric8(), executionLogCallback);
    return dryRunManifests(k8sDelegateTaskParams, executionLogCallback);
  }

  private String dryRunManifests(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback)
      throws Exception {
    try {
      executionLogCallback.saveExecutionLog(color("\nValidating manifests with Dry Run", White, Bold), INFO);
      FileIo.writeUtf8StringToFile(k8sDelegateTaskParams.getWorkingDirectory() + "/" + DRY_RUN_MANIFEST_FILE_NAME,
          ManifestHelper.toYaml(this.resources));

      Kubectl overriddenClient =
          k8sTaskHelperBase.getOverriddenClient(this.client, this.resources, k8sDelegateTaskParams);

      final ApplyCommand dryrun = overriddenClient.apply().filename(DRY_RUN_MANIFEST_FILE_NAME).dryrun(true);
      ProcessResponse response =
          k8sTaskHelperBase.runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, dryrun);
      ProcessResult result = response.getProcessResult();
      if (result.getExitValue() != 0) {
        logExecutableFailed(result, executionLogCallback);
        throw new KubernetesCliTaskRuntimeException(response, KubernetesCliCommandType.DRY_RUN);
      }

      List<DryRunOutput> dryRunOutputList = ManifestHelper.toDryRunOutput(this.resources);
      String dryRunManifestYaml = ManifestHelper.toYamlOutput(dryRunOutputList);
      if (dryRunManifestYaml.getBytes(StandardCharsets.UTF_8).length >= MAX_VARIABLE_SIZE) {
        dryRunManifestYaml = "";
        executionLogCallback.saveExecutionLog(
            color(format("\nSkipped exporting k8s manifest as the file size exceeds limit of %d megabytes",
                      MAX_VARIABLE_SIZE / (1024 * 1024)),
                Yellow, Bold),
            WARN);
      }
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return dryRunManifestYaml;
    } catch (Exception e) {
      log.error("Exception in running dry-run", e);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      throw e;
    }
  }

  private void logExecutableFailed(ProcessResult result, LogCallback logCallback) {
    String output = result.hasOutput() ? result.outputUTF8() : null;
    if (isNotEmpty(output)) {
      logCallback.saveExecutionLog(
          format("\nFailed with exit code: %d and output: %s.", result.getExitValue(), output), INFO, FAILURE);
    } else {
      logCallback.saveExecutionLog(format("\nFailed with exit code: %d.", result.getExitValue()), INFO, FAILURE);
    }
  }
}
