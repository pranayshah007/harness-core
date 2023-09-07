/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.gitops.GitOpsStepUtils;
import io.harness.cdng.gitops.syncstep.EnvironmentClusterListing;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.ApplicationSpec;
import io.harness.gitops.models.ApplicationResource.HelmSource;
import io.harness.gitops.models.ApplicationResource.HelmSourceFileParameters;
import io.harness.gitops.models.ApplicationResource.HelmSourceParameters;
import io.harness.gitops.models.ApplicationResource.KustomizeSource;
import io.harness.gitops.models.ApplicationResource.Replicas;
import io.harness.gitops.models.ApplicationResource.Source;
import io.harness.gitops.models.ApplicationUpdateRequest;
import io.harness.gitops.models.ApplicationUpdateRequest.Application.ApplicationBuilder;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.collections4.CollectionUtils;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Slf4j
public class UpdateGitOpsAppRunnable implements Runnable {
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  private final String taskId;
  private final Ambiance ambiance;
  private final StepElementParameters stepParameters;
  private static final String FAILED_TO_GET_APPLICATION_WITH_ERR =
      "Failed to get application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_UPDATE_APPLICATION_WITH_ERR =
      "Failed to update application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_GET_APPLICATION = "Failed to get application";
  private static final String FAILED_TO_UPDATE_APPLICATION = "Failed to update application";

  public UpdateGitOpsAppRunnable(String taskId, Ambiance ambiance, StepElementParameters stepParameters) {
    this.taskId = taskId;
    this.ambiance = ambiance;
    this.stepParameters = stepParameters;
  }

  @Override
  public void run() {
    final NGLogCallback logger =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, GitOpsStepUtils.LOG_SUFFIX, true);
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

      // fetch application to be updated from pipeline inputs
      UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters =
          (UpdateGitOpsAppStepParameters) stepParameters.getSpec();
      Application applicationToBeUpdated = getApplicationToBeUpdated(updateGitOpsAppsStepParameters);

      if (applicationToBeUpdated == null) {
        GitOpsStepUtils.logExecutionInfo("No application found to be updated", logger);

        waitNotifyEngine.doneWith(taskId, UpdateGitOpsAppResponse.builder().build());
        GitOpsStepUtils.closeLogStream(ambiance, logStreamingStepClientFactory);
        return;
      }

      // fetch service-env-cluster details
      Set<String> serviceIdsInPipelineExecution =
          GitOpsStepUtils.getServiceIdsInPipelineExecution(ambiance, executionSweepingOutputResolver);
      EnvironmentClusterListing envClusterIds =
          GitOpsStepUtils.getEnvAndClusterIdsInPipelineExecution(ambiance, executionSweepingOutputResolver);
      Set<String> envIdsInPipelineExecution =
          (Set<String>) CollectionUtils.emptyIfNull(envClusterIds.getEnvironmentIds());
      Map<String, Set<String>> clusterIdsInPipelineExecution =
          envClusterIds.getClusterIds() != null ? envClusterIds.getClusterIds() : new HashMap<>();

      GitOpsStepUtils.logExecutionInfo(format("Application to be updated is %s", applicationToBeUpdated), logger);

      // validate application with pipeline config
      ApplicationResource fetchedApplicationFromGitOps =
          getApplication(applicationToBeUpdated, accountId, orgId, projectId, logger);
      if (fetchedApplicationFromGitOps == null
          || !isApplicationValidForUpdate(fetchedApplicationFromGitOps, serviceIdsInPipelineExecution,
              envIdsInPipelineExecution, clusterIdsInPipelineExecution, logger)) {
        notifyResponse(applicationToBeUpdated, false, logger);
        waitNotifyEngine.doneWith(taskId, UpdateGitOpsAppResponse.builder().build());
        GitOpsStepUtils.closeLogStream(ambiance, logStreamingStepClientFactory);
        return;
      }

      GitOpsStepUtils.logExecutionInfo("Updating application...", logger);
      updateApplication(
          fetchedApplicationFromGitOps, accountId, orgId, projectId, updateGitOpsAppsStepParameters, logger);

      notifyResponse(applicationToBeUpdated, true, logger);
    } catch (Exception ex) {
      waitNotifyEngine.doneWith(taskId,
          ErrorNotifyResponseData.builder()
              .errorMessage(format("Failed to execute Update GitOps Apps step. Error:%s", ex))
              .build());
      throw new RuntimeException("Failed to execute Update GitOps Apps step ", ex);
    } finally {
      GitOpsStepUtils.closeLogStream(ambiance, logStreamingStepClientFactory);
    }
  }

  private Application getApplicationToBeUpdated(UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters) {
    return Application.builder()
        .agentIdentifier(updateGitOpsAppsStepParameters.getAgentId().getValue())
        .name(updateGitOpsAppsStepParameters.getApplicationName().getValue())
        .build();
  }

  private ApplicationResource getApplication(
      Application application, String accountId, String orgId, String projectId, LogCallback logger) {
    String agentId = application.getAgentIdentifier();
    String applicationName = application.getName();
    try {
      final Response<ApplicationResource> response =
          Failsafe.with(GitOpsStepUtils.getRetryPolicy("Retrying to get application...", FAILED_TO_GET_APPLICATION))
              .get(()
                       -> gitopsResourceClient.getApplication(agentId, applicationName, accountId, orgId, projectId)
                              .execute());
      if (!response.isSuccessful() || response.body() == null) {
        logErrorWithApplicationResource(agentId, applicationName, response, FAILED_TO_GET_APPLICATION_WITH_ERR, logger);
        return null;
      }
      return response.body();
    } catch (Exception e) {
      log.error(format(FAILED_TO_GET_APPLICATION_WITH_ERR, applicationName, agentId, e));
      throw new InvalidRequestException(FAILED_TO_GET_APPLICATION);
    }
  }

  private boolean isApplicationValidForUpdate(ApplicationResource fetchedApplication,
      Set<String> serviceIdsInPipelineExecution, Set<String> envIdsInPipelineExecution,
      Map<String, Set<String>> clusterIdsInPipelineExecution, LogCallback logger) {
    if (!serviceIdsInPipelineExecution.contains(fetchedApplication.getServiceRef())) {
      GitOpsStepUtils.logExecutionError(
          "Application does not correspond to the service(s) selected in the pipeline execution.", logger);
      return false;
    }

    if (!envIdsInPipelineExecution.contains(fetchedApplication.getEnvironmentRef())) {
      GitOpsStepUtils.logExecutionError(
          "Application does not correspond to the environment(s) selected in the pipeline execution.", logger);
      return false;
    }

    if (!GitOpsStepUtils.isApplicationCorrespondsToClusterInExecution(
            fetchedApplication, clusterIdsInPipelineExecution)) {
      GitOpsStepUtils.logExecutionError(
          "Application does not correspond to the cluster(s) selected in the pipeline execution.", logger);
      return false;
    }

    return true;
  }

  private void updateApplication(ApplicationResource fetchedApplicationFromGitOps, String accountId, String orgId,
      String projectId, UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters, LogCallback logger) {
    ApplicationUpdateRequest updateRequest =
        getUpdateRequest(fetchedApplicationFromGitOps, updateGitOpsAppsStepParameters);
    String agentId = fetchedApplicationFromGitOps.getAgentIdentifier();
    String applicationName = fetchedApplicationFromGitOps.getName();
    String repoIdentifier = fetchedApplicationFromGitOps.getRepoIdentifier();
    String clusterIdentifier = fetchedApplicationFromGitOps.getClusterIdentifier();

    try {
      final Response<ApplicationResource> response =
          Failsafe
              .with(GitOpsStepUtils.getRetryPolicy("Retrying to update application...", FAILED_TO_UPDATE_APPLICATION))
              .get(()
                       -> gitopsResourceClient
                              .updateApplication(agentId, applicationName, accountId, orgId, projectId,
                                  clusterIdentifier, repoIdentifier, updateRequest)
                              .execute());
      if (!response.isSuccessful() || response.body() == null) {
        logErrorWithApplicationResource(
            agentId, applicationName, response, FAILED_TO_UPDATE_APPLICATION_WITH_ERR, logger);
      }
    } catch (Exception e) {
      log.error(format(FAILED_TO_UPDATE_APPLICATION_WITH_ERR, applicationName, agentId, e));
      throw new InvalidRequestException(FAILED_TO_UPDATE_APPLICATION);
    }
  }

  public static ApplicationUpdateRequest getUpdateRequest(
      ApplicationResource application, UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters) {
    ApplicationSpec applicationSpec = application.getApp().getSpec();

    populateUpdateValues(applicationSpec, updateGitOpsAppsStepParameters);

    ApplicationBuilder applicationBuilder =
        ApplicationUpdateRequest.Application.builder().applicationSpec(applicationSpec);

    return ApplicationUpdateRequest.builder().application(applicationBuilder.build()).build();
  }

  private static void populateUpdateValues(
      ApplicationSpec applicationSpec, UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters) {
    Source source = applicationSpec.getSource();

    if (updateGitOpsAppsStepParameters.getTargetRevision().getValue() != null) {
      source.setTargetRevision(updateGitOpsAppsStepParameters.getTargetRevision().getValue());
    }

    if (updateGitOpsAppsStepParameters.getHelm().getValue() != null) {
      HelmValues pmsHelmValues = updateGitOpsAppsStepParameters.getHelm().getValue();
      populateHelmValues(source, pmsHelmValues);
    }

    if (updateGitOpsAppsStepParameters.getKustomize().getValue() != null) {
      KustomizeValues pmsKustomizeValues = updateGitOpsAppsStepParameters.getKustomize().getValue();
      populateKustomizeValues(source, pmsKustomizeValues);
    }

    applicationSpec.setSource(source);
  }

  private static void populateHelmValues(Source source, HelmValues pmsHelmValues) {
    HelmSource helmSource = source.getHelm();
    /*
    Merging logic example -
    existing helm parameters ->
        {
          "replicas": "0",
          "ingress.annotations.kubernetes\\.io/tls-acme": "true"
        }
     input helm parameters ->
        {
          "replicas": "2",
          "config": "files/config.json"
        }

      final result ->
        {
          "replicas": "2",
          "ingress.annotations.kubernetes\\.io/tls-acme": "true",
          "config": "files/config.json"
        }
     */

    if (pmsHelmValues.getParameters().getValue() != null) {
      // merge
      Map<String, String> mapOfHelmParams = new HashMap<>();
      for (HelmSourceParameters helmSourceParameter : helmSource.getParameters()) {
        mapOfHelmParams.put(helmSourceParameter.getName(), helmSourceParameter.getValue());
      }
      for (HelmParameters helmParameter : pmsHelmValues.getParameters().getValue()) {
        mapOfHelmParams.put(helmParameter.getName().getValue(), helmParameter.getValue().getValue());
      }

      List<HelmSourceParameters> finalHelmSourceParameters = new ArrayList<>();
      for (Map.Entry<String, String> helmParam : mapOfHelmParams.entrySet()) {
        finalHelmSourceParameters.add(HelmSourceParameters.builder()
                                          .name(helmParam.getKey())
                                          .value(helmParam.getValue())
                                          .forceString(true)
                                          .build());
      }

      helmSource.setParameters(finalHelmSourceParameters);
    }

    if (pmsHelmValues.getFileParameters().getValue() != null) {
      // merge
      Map<String, String> mapOfHelmFileParams = new HashMap<>();
      for (HelmSourceFileParameters helmSourceFileParameter : helmSource.getFileParameters()) {
        mapOfHelmFileParams.put(helmSourceFileParameter.getName(), helmSourceFileParameter.getPath());
      }
      for (HelmFileParameters helmFileParameter : pmsHelmValues.getFileParameters().getValue()) {
        mapOfHelmFileParams.put(helmFileParameter.getName().getValue(), helmFileParameter.getPath().getValue());
      }

      List<HelmSourceFileParameters> finalHelmSourceFileParameters = new ArrayList<>();
      for (Map.Entry<String, String> helmFileParam : mapOfHelmFileParams.entrySet()) {
        finalHelmSourceFileParameters.add(
            HelmSourceFileParameters.builder().name(helmFileParam.getKey()).path(helmFileParam.getValue()).build());
      }

      helmSource.setFileParameters(finalHelmSourceFileParameters);
    }

    if (pmsHelmValues.getValueFiles().getValue() != null) {
      helmSource.setValueFiles(pmsHelmValues.getValueFiles().getValue());
    }

    source.setHelm(helmSource);
  }

  private static void populateKustomizeValues(Source source, KustomizeValues pmsKustomizeValues) {
    KustomizeSource kustomizeSource = source.getKustomize();
    if (pmsKustomizeValues.getImages().getValue() != null) {
      kustomizeSource.setImages(pmsKustomizeValues.getImages().getValue());
    }
    if (pmsKustomizeValues.getNamespace().getValue() != null) {
      kustomizeSource.setNamespace(pmsKustomizeValues.getNamespace().getValue());
    }
    if (pmsKustomizeValues.getReplicas().getValue() != null) {
      List<Replicas> replicasList = new ArrayList<>();
      for (KustomizeReplicas kustomizeReplicas : pmsKustomizeValues.getReplicas().getValue()) {
        Replicas replica = Replicas.builder()
                               .name(kustomizeReplicas.getName().getValue())
                               .count(kustomizeReplicas.getCount().getValue())
                               .build();
        replicasList.add(replica);
      }
      kustomizeSource.setReplicas(replicasList);
    }
    source.setKustomize(kustomizeSource);
  }

  private void notifyResponse(Application application, boolean pass, LogCallback logger) {
    String status = pass ? "successful" : "failed";
    GitOpsStepUtils.logExecutionInfo(format("Update %s for application %s", status, application), logger);

    waitNotifyEngine.doneWith(taskId, UpdateGitOpsAppResponse.builder().updatedApplication(application).build());
  }

  private void logErrorWithApplicationResource(String agentId, String applicationName,
      Response<ApplicationResource> response, String applicationErr, LogCallback logger) throws IOException {
    String errorMessage = response.errorBody() != null ? response.errorBody().string() : "";
    GitOpsStepUtils.logExecutionError(format(applicationErr, applicationName, agentId, errorMessage), logger);
  }
}
