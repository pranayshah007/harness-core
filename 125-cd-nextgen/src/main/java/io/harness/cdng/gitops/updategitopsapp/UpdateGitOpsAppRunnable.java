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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.gitops.GitOpsStepUtils;
import io.harness.cdng.gitops.syncstep.EnvironmentClusterListing;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.App;
import io.harness.gitops.models.ApplicationResource.ApplicationSpec;
import io.harness.gitops.models.ApplicationResource.HelmSource;
import io.harness.gitops.models.ApplicationResource.HelmSourceFileParameters;
import io.harness.gitops.models.ApplicationResource.HelmSourceParameters;
import io.harness.gitops.models.ApplicationResource.KustomizeSource;
import io.harness.gitops.models.ApplicationResource.Replicas;
import io.harness.gitops.models.ApplicationResource.Source;
import io.harness.gitops.models.ApplicationUpdateRequest;
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
@OwnedBy(HarnessTeam.GITOPS)
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
  private static final String UPDATE_APP_STEP_FAILED = "Failed to execute Update GitOps Apps step. Error: %s";

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
      Application applicationToBeUpdated = getApplicationToBeUpdated(updateGitOpsAppsStepParameters, logger);

      if (applicationToBeUpdated == null) {
        GitOpsStepUtils.logExecutionInfo("No application found to be updated in pipeline input", logger);

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
      if (fetchedApplicationFromGitOps == null) {
        notifyFailedResponse(applicationToBeUpdated, logger,
            format("No applications found with name %s on agent %s. Please check if the agent and application exist.",
                applicationToBeUpdated.getName(), applicationToBeUpdated.getAgentIdentifier()));
        return;
      } else if (!isApplicationValidForUpdate(fetchedApplicationFromGitOps, serviceIdsInPipelineExecution,
                     envIdsInPipelineExecution, clusterIdsInPipelineExecution, applicationToBeUpdated, logger)) {
        return;
      }

      GitOpsStepUtils.logExecutionInfo("Updating application...", logger);
      updateApplication(
          fetchedApplicationFromGitOps, accountId, orgId, projectId, updateGitOpsAppsStepParameters, logger);

      notifySuccessfulResponse(applicationToBeUpdated, logger);
    } catch (Exception ex) {
      waitNotifyEngine.doneWith(
          taskId, ErrorNotifyResponseData.builder().errorMessage(format(UPDATE_APP_STEP_FAILED, ex)).build());
      throw new RuntimeException("Failed to execute Update GitOps Apps step ", ex);
    } finally {
      GitOpsStepUtils.closeLogStream(ambiance, logStreamingStepClientFactory);
    }
  }

  private Application getApplicationToBeUpdated(
      UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters, LogCallback logger) {
    if (updateGitOpsAppsStepParameters.getApplicationName() == null
        || updateGitOpsAppsStepParameters.getApplicationName().getValue() == null) {
      GitOpsStepUtils.logExecutionError("No Application Name was passed in Pipeline input.", logger);
      return null;
    } else if (updateGitOpsAppsStepParameters.getAgentId() == null
        || updateGitOpsAppsStepParameters.getAgentId().getValue() == null) {
      GitOpsStepUtils.logExecutionError("No Agent Identifier was passed in Pipeline input.", logger);
      return null;
    }
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
      Map<String, Set<String>> clusterIdsInPipelineExecution, Application applicationToBeUpdated, LogCallback logger) {
    String logMessage;
    if (!serviceIdsInPipelineExecution.contains(fetchedApplication.getServiceRef())) {
      logMessage = "Application does not correspond to the service(s) selected in the pipeline execution.";
    } else if (!envIdsInPipelineExecution.contains(fetchedApplication.getEnvironmentRef())) {
      logMessage = "Application does not correspond to the environment(s) selected in the pipeline execution.";
    } else if (!GitOpsStepUtils.isApplicationCorrespondsToClusterInExecution(
                   fetchedApplication, clusterIdsInPipelineExecution)) {
      logMessage = "Application does not correspond to the cluster(s) selected in the pipeline execution.";
    } else {
      return true;
    }

    GitOpsStepUtils.logExecutionError(logMessage, logger);
    notifyFailedResponse(applicationToBeUpdated, logger, logMessage);

    return false;
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
        String errorMsg = logErrorWithApplicationResource(
            agentId, applicationName, response, FAILED_TO_UPDATE_APPLICATION_WITH_ERR, logger);
        waitNotifyEngine.doneWith(
            taskId, ErrorNotifyResponseData.builder().errorMessage(format(UPDATE_APP_STEP_FAILED, errorMsg)).build());
      }
    } catch (Exception e) {
      log.error(format(FAILED_TO_UPDATE_APPLICATION_WITH_ERR, applicationName, agentId, e));
      throw new InvalidRequestException(FAILED_TO_UPDATE_APPLICATION);
    }
  }

  public static ApplicationUpdateRequest getUpdateRequest(
      ApplicationResource application, UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters) {
    App applicationEntity = application.getApp();
    ApplicationSpec applicationSpec = applicationEntity.getSpec();
    if (applicationSpec == null) {
      applicationSpec = ApplicationSpec.builder().build();
    }

    populateUpdateValues(applicationSpec, updateGitOpsAppsStepParameters);

    applicationEntity.setSpec(applicationSpec);
    applicationEntity.setStatus(null); // We do not update the sync status of an application. Also, if we remove this
                                       // null then we encounter a marshalling error from GitOps Service.

    return ApplicationUpdateRequest.builder().application(applicationEntity).build();
  }

  private static void populateUpdateValues(
      ApplicationSpec applicationSpec, UpdateGitOpsAppStepParameters updateGitOpsAppsStepParameters) {
    Source source = applicationSpec.getSource();
    if (source == null) {
      source = Source.builder().build();
    }

    if (updateGitOpsAppsStepParameters.getTargetRevision() != null
        && updateGitOpsAppsStepParameters.getTargetRevision().getValue() != null) {
      source.setTargetRevision(updateGitOpsAppsStepParameters.getTargetRevision().getValue());
    }

    if (updateGitOpsAppsStepParameters.getHelm() != null
        && updateGitOpsAppsStepParameters.getHelm().getValue() != null) {
      HelmValues pmsHelmValues = updateGitOpsAppsStepParameters.getHelm().getValue();
      populateHelmValues(source, pmsHelmValues);
    }

    if (updateGitOpsAppsStepParameters.getKustomize() != null
        && updateGitOpsAppsStepParameters.getKustomize().getValue() != null) {
      KustomizeValues pmsKustomizeValues = updateGitOpsAppsStepParameters.getKustomize().getValue();
      populateKustomizeValues(source, pmsKustomizeValues);
    }

    applicationSpec.setSource(source);
  }

  private static void populateHelmValues(Source source, HelmValues pmsHelmValues) {
    HelmSource helmSource = source.getHelm();
    if (helmSource == null) {
      helmSource = HelmSource.builder().build();
    }

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

    if (pmsHelmValues.getParameters() != null && pmsHelmValues.getParameters().getValue() != null) {
      List<HelmSourceParameters> finalHelmSourceParameters =
          populateHelmParameters(helmSource, pmsHelmValues.getParameters().getValue());
      helmSource.setParameters(finalHelmSourceParameters);
    }

    if (pmsHelmValues.getFileParameters() != null && pmsHelmValues.getFileParameters().getValue() != null) {
      List<HelmSourceFileParameters> finalHelmSourceFileParameters =
          populateHelmFileParameters(helmSource, pmsHelmValues.getFileParameters().getValue());
      helmSource.setFileParameters(finalHelmSourceFileParameters);
    }

    if (pmsHelmValues.getValueFiles() != null && pmsHelmValues.getValueFiles().getValue() != null) {
      helmSource.setValueFiles(pmsHelmValues.getValueFiles().getValue());
    }

    source.setHelm(helmSource);
  }

  private static List<HelmSourceParameters> populateHelmParameters(
      HelmSource helmSource, List<HelmParameters> pmsHelmParameters) {
    Map<String, String> mapOfHelmParams = new HashMap<>();
    if (helmSource.getParameters() != null) {
      for (HelmSourceParameters helmSourceParameter : helmSource.getParameters()) {
        if (helmSourceParameter.getName() != null && helmSourceParameter.getValue() != null) {
          mapOfHelmParams.put(helmSourceParameter.getName(), helmSourceParameter.getValue());
        }
      }
    }
    for (HelmParameters helmParameter : pmsHelmParameters) {
      if (helmParameter.getName() != null && helmParameter.getName().getValue() != null
          && helmParameter.getValue() != null && helmParameter.getValue().getValue() != null) {
        mapOfHelmParams.put(helmParameter.getName().getValue(), helmParameter.getValue().getValue());
      }
    }

    List<HelmSourceParameters> finalHelmSourceParameters = new ArrayList<>();
    for (Map.Entry<String, String> helmParam : mapOfHelmParams.entrySet()) {
      finalHelmSourceParameters.add(HelmSourceParameters.builder()
                                        .name(helmParam.getKey())
                                        .value(helmParam.getValue())
                                        .forceString(true)
                                        .build());
    }
    return finalHelmSourceParameters;
  }

  private static List<HelmSourceFileParameters> populateHelmFileParameters(
      HelmSource helmSource, List<HelmFileParameters> pmsHelmFileParameters) {
    Map<String, String> mapOfHelmFileParams = new HashMap<>();
    if (helmSource.getFileParameters() != null) {
      for (HelmSourceFileParameters helmSourceFileParameter : helmSource.getFileParameters()) {
        if (helmSourceFileParameter.getName() != null && helmSourceFileParameter.getPath() != null) {
          mapOfHelmFileParams.put(helmSourceFileParameter.getName(), helmSourceFileParameter.getPath());
        }
      }
    }
    for (HelmFileParameters helmFileParameter : pmsHelmFileParameters) {
      if (helmFileParameter.getName() != null && helmFileParameter.getName().getValue() != null
          && helmFileParameter.getPath() != null && helmFileParameter.getPath().getValue() != null) {
        mapOfHelmFileParams.put(helmFileParameter.getName().getValue(), helmFileParameter.getPath().getValue());
      }
    }

    List<HelmSourceFileParameters> finalHelmSourceFileParameters = new ArrayList<>();
    for (Map.Entry<String, String> helmFileParam : mapOfHelmFileParams.entrySet()) {
      finalHelmSourceFileParameters.add(
          HelmSourceFileParameters.builder().name(helmFileParam.getKey()).path(helmFileParam.getValue()).build());
    }
    return finalHelmSourceFileParameters;
  }

  private static void populateKustomizeValues(Source source, KustomizeValues pmsKustomizeValues) {
    KustomizeSource kustomizeSource = source.getKustomize();
    if (kustomizeSource == null) {
      kustomizeSource = KustomizeSource.builder().build();
    }

    if (pmsKustomizeValues.getImages() != null && pmsKustomizeValues.getImages().getValue() != null) {
      kustomizeSource.setImages(pmsKustomizeValues.getImages().getValue());
    }
    if (pmsKustomizeValues.getNamespace() != null && pmsKustomizeValues.getNamespace().getValue() != null) {
      kustomizeSource.setNamespace(pmsKustomizeValues.getNamespace().getValue());
    }
    if (pmsKustomizeValues.getReplicas() != null && pmsKustomizeValues.getReplicas().getValue() != null) {
      List<Replicas> replicasList = new ArrayList<>();
      for (KustomizeReplicas kustomizeReplicas : pmsKustomizeValues.getReplicas().getValue()) {
        if (kustomizeReplicas.getName() != null && kustomizeReplicas.getName().getValue() != null
            && kustomizeReplicas.getCount() != null && kustomizeReplicas.getCount().getValue() != null) {
          Replicas replica = Replicas.builder()
                                 .name(kustomizeReplicas.getName().getValue())
                                 .count(kustomizeReplicas.getCount().getValue())
                                 .build();
          replicasList.add(replica);
        }
      }
      kustomizeSource.setReplicas(replicasList);
    }
    source.setKustomize(kustomizeSource);
  }

  private void notifySuccessfulResponse(Application application, LogCallback logger) {
    GitOpsStepUtils.logExecutionInfo(format("Update successful for application %s", application), logger);
    waitNotifyEngine.doneWith(taskId, UpdateGitOpsAppResponse.builder().updatedApplication(application).build());
  }

  private void notifyFailedResponse(Application application, LogCallback logger, String errorMsg) {
    GitOpsStepUtils.logExecutionInfo(format("Update failed for application %s", application), logger);
    waitNotifyEngine.doneWith(
        taskId, ErrorNotifyResponseData.builder().errorMessage(format(UPDATE_APP_STEP_FAILED, errorMsg)).build());
  }

  private String logErrorWithApplicationResource(String agentId, String applicationName,
      Response<ApplicationResource> response, String applicationErr, LogCallback logger) throws IOException {
    String errorMessage = response.errorBody() != null ? response.errorBody().string() : "";
    GitOpsStepUtils.logExecutionError(format(applicationErr, applicationName, agentId, errorMessage), logger);
    return errorMessage;
  }
}
