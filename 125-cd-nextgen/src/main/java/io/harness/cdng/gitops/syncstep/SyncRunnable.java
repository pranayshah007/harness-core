/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome.ClusterData;
import io.harness.cdng.gitops.syncstep.EnvironmentClusterListing.EnvironmentClusterListingBuilder;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.NGTimeConversionHelper;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.utils.RetryUtils;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.CollectionUtils;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Slf4j
public class SyncRunnable implements Runnable {
  private static final String FAILED_TO_REFRESH_APPLICATION_WITH_ERR =
      "Failed to refresh application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_GET_APPLICATION_WITH_ERR =
      "Failed to get application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_SYNC_APPLICATION_WITH_ERR =
      "Failed to sync application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_REFRESH_APPLICATION = "Failed to refresh application";
  private static final String FAILED_TO_SYNC_APPLICATION = "Failed to sync application";
  private static final String FAILED_TO_GET_APPLICATION = "Failed to get application";
  public static final String GITOPS_LINKED_APPS_OUTCOME = "GITOPS_LINKED_APPS_OUTCOME";
  public static final String SERVICE = "service";
  private static final String LOG_SUFFIX = "Execute";

  private final String taskId;
  private final Ambiance ambiance;
  private final StepElementParameters stepParameters;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  public SyncRunnable(String taskId, Ambiance ambiance, StepElementParameters stepParameters) {
    this.taskId = taskId;
    this.ambiance = ambiance;
    this.stepParameters = stepParameters;
  }

  @Override
  public void run() {
    final NGLogCallback logger = new NGLogCallback(logStreamingStepClientFactory, ambiance, LOG_SUFFIX, true);
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

      SyncStepParameters syncStepParameters = (SyncStepParameters) stepParameters.getSpec();

      List<Application> applicationsToBeSynced = getApplicationsToBeSynced(ambiance, syncStepParameters);
      Set<Application> applicationsFailedOnArgoSync = new HashSet<>();
      Set<Application> applicationsSucceededOnArgoSync = new HashSet<>();
      Set<Application> syncStillRunningForApplications = new HashSet<>();

      if (isEmpty(applicationsToBeSynced)) {
        logExecutionInfo("No application found to be synced", logger);

        notifyResponse(
            applicationsFailedOnArgoSync, applicationsSucceededOnArgoSync, syncStillRunningForApplications, logger);
      }

      Set<String> serviceIdsInPipelineExecution = getServiceIdsInPipelineExecution(ambiance);
      EnvironmentClusterListing envClusterIds = getEnvAndClusterIdsInPipelineExecution(ambiance);
      Set<String> envIdsInPipelineExecution =
          (Set<String>) CollectionUtils.emptyIfNull(envClusterIds.getEnvironmentIds());
      Map<String, Set<String>> clusterIdsInPipelineExecution =
          envClusterIds.getClusterIds() != null ? envClusterIds.getClusterIds() : new HashMap<>();

      Set<Application> applicationsFailedToSync = new HashSet<>();

      logExecutionInfo(format("Application(s) to be synced %s", applicationsToBeSynced), logger);

      Instant syncStartTime = Instant.now();
      log.info("Sync start time is {}", syncStartTime);

      // refresh applications
      logExecutionInfo("Refreshing application(s)...", logger);
      refreshApplicationsAndSetSyncPolicy(
          applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId, logger);

      // check sync eligibility for applications
      logExecutionInfo("Checking application(s) eligibility for sync...", logger);
      prepareApplicationForSync(applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId,
          serviceIdsInPipelineExecution, envIdsInPipelineExecution, clusterIdsInPipelineExecution, logger);
      List<Application> applicationsEligibleForSync =
          getApplicationsToBeSyncedAndPolled(applicationsToBeSynced, applicationsFailedToSync);

      // sync applications
      logExecutionInfo("Syncing application(s)...", logger);
      syncApplications(applicationsEligibleForSync, applicationsFailedToSync, accountId, orgId, projectId,
          syncStepParameters, logger);

      if (isNotEmpty(applicationsFailedToSync)) {
        printErroredApplications(applicationsFailedToSync, "Application(s) errored before syncing", logger);
      }

      // poll applications
      if (isNotEmpty(applicationsEligibleForSync)) {
        long pollForMillis = getPollerTimeout(stepParameters);
        logExecutionInfo(format("Polling application statuses %s", applicationsEligibleForSync), logger);
        pollApplications(pollForMillis, applicationsEligibleForSync, applicationsFailedToSync, syncStartTime, accountId,
            orgId, projectId, logger);
      }

      groupApplicationsOnSyncStatus(applicationsEligibleForSync, applicationsFailedOnArgoSync,
          applicationsSucceededOnArgoSync, syncStillRunningForApplications);

      if (isNotEmpty(applicationsFailedOnArgoSync)) {
        printErroredApplications(applicationsFailedOnArgoSync, "Application(s) errored while syncing", logger);
      }

      // applications failed while syncing/refreshing can still be synced successfully while polling
      applicationsFailedToSync.removeAll(applicationsSucceededOnArgoSync);
      applicationsFailedToSync.addAll(applicationsFailedOnArgoSync);

      notifyResponse(
          applicationsFailedToSync, applicationsSucceededOnArgoSync, syncStillRunningForApplications, logger);
    } catch (Exception ex) {
      waitNotifyEngine.doneWith(taskId,
          ErrorNotifyResponseData.builder().errorMessage(format("Failed to execute Sync step. Error:%s", ex)).build());
      throw new RuntimeException("Failed to execute Sync step ", ex);
    } finally {
      closeLogStream(ambiance);
    }
  }

  private EnvironmentClusterListing getEnvAndClusterIdsInPipelineExecution(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutputForEnv = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(GITOPS_SWEEPING_OUTPUT));

    EnvironmentClusterListingBuilder environmentClusterListing = EnvironmentClusterListing.builder();
    if (optionalSweepingOutputForEnv != null && optionalSweepingOutputForEnv.isFound()) {
      GitopsClustersOutcome gitopsClustersOutcome = (GitopsClustersOutcome) optionalSweepingOutputForEnv.getOutput();
      // ideally, the gitops clusters step should fail when no cluster is present, this is an extra check
      if (gitopsClustersOutcome == null || isEmpty(gitopsClustersOutcome.getClustersData())) {
        log.debug("No GitOps Clusters found");
      } else {
        environmentClusterListing.clusterIds(getScopedClusterIdsInPipelineExecution(gitopsClustersOutcome))
            .environmentIds(getEnvIdsInPipelineExecution(gitopsClustersOutcome));
      }
    }
    return environmentClusterListing.build();
  }

  private Set<String> getEnvIdsInPipelineExecution(GitopsClustersOutcome outcome) {
    return outcome.getClustersData().stream().map(ClusterData::getEnvId).collect(Collectors.toSet());
  }

  @VisibleForTesting
  Map<String, Set<String>> getScopedClusterIdsInPipelineExecution(GitopsClustersOutcome gitopsClustersOutcome) {
    return gitopsClustersOutcome.getClustersData().stream().collect(
        Collectors.groupingBy(ClusterData::getAgentId, Collectors.mapping(cluster -> {
          String scope = cluster.getScope().toLowerCase();
          String clusterId = cluster.getClusterId();
          if (ScopeLevel.PROJECT.toString().equalsIgnoreCase(scope)) {
            return clusterId;
          } else if (ScopeLevel.ORGANIZATION.toString().equalsIgnoreCase(scope)) {
            return Scope.ORG.getYamlRepresentation() + "." + clusterId;
          } else {
            return scope + "." + clusterId;
          }
        }, Collectors.toSet())));
  }

  private Set<String> getServiceIdsInPipelineExecution(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutputForService =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(SERVICE));
    return optionalSweepingOutputForService != null && optionalSweepingOutputForService.isFound()
        ? Stream.of(((ServiceStepOutcome) optionalSweepingOutputForService.getOutput()).getIdentifier())
              .collect(Collectors.toSet())
        : new HashSet<>();
  }

  private void logExecutionInfo(String logMessage, LogCallback logger) {
    log.info(logMessage);
    saveExecutionLog(logMessage, logger, LogLevel.INFO);
  }

  private void logExecutionError(String logMessage, LogCallback logger) {
    log.error(logMessage);
    saveExecutionLog(logMessage, logger, LogLevel.ERROR);
  }

  private void logExecutionWarning(String logMessage, LogCallback logger) {
    log.warn(logMessage);
    saveExecutionLog(logMessage, logger, LogLevel.WARN);
  }

  private List<Application> getApplicationsToBeSynced(Ambiance ambiance, SyncStepParameters syncStepParameters) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(GITOPS_LINKED_APPS_OUTCOME));

    Set<Application> applications = optionalSweepingOutput != null && optionalSweepingOutput.isFound()
        ? new HashSet<>(((GitOpsLinkedAppsOutcome) optionalSweepingOutput.getOutput()).getApps())
        : new HashSet<>();
    if (syncStepParameters.getApplicationsList().getValue() != null) {
      applications.addAll(
          SyncStepHelper.getApplicationsToBeSynced(syncStepParameters.getApplicationsList().getValue()));
    }
    return new ArrayList<>(applications);
  }

  private void printErroredApplications(Set<Application> applicationsErrored, String logMessage, LogCallback logger) {
    logExecutionError(format(logMessage, " with error messages %s", applicationsErrored), logger);
    for (Application application : applicationsErrored) {
      logExecutionError(application.getSyncError(), logger);
    }
  }

  private void prepareApplicationForSync(List<Application> applicationsToBeSynced,
      Set<Application> failedToSyncApplications, String accountId, String orgId, String projectId,
      Set<String> serviceIdsInPipelineExecution, Set<String> envIdsInPipelineExecution,
      Map<String, Set<String>> clusterIdsInPipelineExecution, LogCallback logger) {
    for (Application application : applicationsToBeSynced) {
      if (failedToSyncApplications.contains(application)) {
        continue;
      }
      ApplicationResource latestApplicationState = getApplication(application, accountId, orgId, projectId, logger);

      if (latestApplicationState == null
          || !isApplicationEligibleForSync(latestApplicationState, application, serviceIdsInPipelineExecution,
              envIdsInPipelineExecution, clusterIdsInPipelineExecution)) {
        failedToSyncApplications.add(application);
        continue;
      }
      application.setRevision(latestApplicationState.getTargetRevision());
    }
  }

  private void saveExecutionLog(String log, LogCallback logger, LogLevel logLevel) {
    logger.saveExecutionLog(log, logLevel);
  }

  private void notifyResponse(Set<Application> applicationsFailedToSync,
      Set<Application> applicationsSucceededOnArgoSync, Set<Application> syncStillRunningForApplications,
      LogCallback logger) {
    logExecutionInfo(format("Sync is successful for application(s) %s", applicationsSucceededOnArgoSync), logger);
    logExecutionInfo(format("Sync failed for application(s) %s", applicationsFailedToSync), logger);
    logExecutionInfo(format("Sync is still running for application(s) %s", syncStillRunningForApplications), logger);

    waitNotifyEngine.doneWith(taskId,
        SyncResponse.builder()
            .applicationsSucceededOnArgoSync(applicationsSucceededOnArgoSync)
            .applicationsFailedToSync(applicationsFailedToSync)
            .syncStillRunningForApplications(syncStillRunningForApplications)
            .build());
  }

  private void groupApplicationsOnSyncStatus(List<Application> applicationsToBeSynced,
      Set<Application> applicationsFailedOnArgoSync, Set<Application> applicationsSucceededOnArgoSync,
      Set<Application> syncStillRunningForApplications) {
    for (Application application : applicationsToBeSynced) {
      // TODO check if this can be changed to enum
      if (SyncOperationPhase.SUCCEEDED.getValue().equals(application.getSyncStatus())) {
        applicationsSucceededOnArgoSync.add(application);
      } else if (SyncOperationPhase.RUNNING.getValue().equals(application.getSyncStatus())
          || SyncOperationPhase.TERMINATING.getValue().equals(application.getSyncStatus())) {
        syncStillRunningForApplications.add(application);
      } else {
        applicationsFailedOnArgoSync.add(application);
      }
    }
  }

  private void pollApplications(long pollForMillis, List<Application> applicationsToBePolled,
      Set<Application> applicationsFailedToSync, Instant syncStartTime, String accountId, String orgId,
      String projectId, LogCallback logger) {
    Set<Application> applicationsPolled = new HashSet<>();
    List<Application> waitingForApplications = new ArrayList<>();

    long startTimeMillis = System.currentTimeMillis();
    // stopping 30 seconds before the step timeout
    long deadlineInMillis = startTimeMillis + pollForMillis - (SyncStepHelper.STOP_BEFORE_STEP_TIMEOUT_SECS * 1000);
    long millisRemaining = deadlineInMillis - startTimeMillis;

    while (millisRemaining > 0) {
      for (Application application : applicationsToBePolled) {
        if (applicationsPolled.contains(application)) {
          continue;
        }
        log.info("Polling application {}", application.getName());
        ApplicationResource currentApplicationState = getApplication(application, accountId, orgId, projectId, logger);
        if (currentApplicationState == null) {
          applicationsPolled.add(application);
          applicationsFailedToSync.add(application);
          application.setSyncStatus(SyncOperationPhase.ERROR.getValue());
          continue;
        }
        String syncStatus = currentApplicationState.getSyncOperationPhase();
        String syncMessage = currentApplicationState.getSyncMessage();
        String healthStatus = currentApplicationState.getHealthStatus();
        application.setSyncStatus(syncStatus);
        application.setHealthStatus(healthStatus);
        application.setSyncMessage(syncMessage);

        if (isApplicationSyncComplete(currentApplicationState, syncStatus, syncStartTime)) {
          applicationsPolled.add(application);
          logApplicationSyncStatus(
              format(
                  "Sync is attempted for application %s. Sync status: %s, message: %s, Application health status: %s",
                  application.getName(), syncStatus, syncMessage, healthStatus),
              syncStatus, logger);
        } else {
          log.info(
              "Sync is {}, Last sync start time is {}", syncStatus, currentApplicationState.getLastSyncStartedAt());
        }
      }
      if (applicationsPolled.size() == applicationsToBePolled.size()) {
        if (isNotEmpty(applicationsFailedToSync)) {
          logExecutionInfo("Sync is attempted for eligible application(s).", logger);
        } else {
          logExecutionInfo("Sync is attempted for all application(s).", logger);
        }
        return;
      }
      waitingForApplications = getApplicationsToBeSyncedAndPolled(applicationsToBePolled, applicationsPolled);
      logExecutionInfo(format("Waiting for application(s) %s", waitingForApplications), logger);
      try {
        TimeUnit.SECONDS.sleep(SyncStepHelper.POLLER_SLEEP_SECS);
      } catch (InterruptedException e) {
        log.error(format("Application polling interrupted with error %s", e));
        Thread.currentThread().interrupt();
        throw new RuntimeException("Application polling interrupted.");
      }

      long currentTimeMillis = System.currentTimeMillis();
      millisRemaining = deadlineInMillis - currentTimeMillis;
      log.info("Polling for another {} milliseconds", millisRemaining);
    }
    if (isNotEmpty(waitingForApplications)) {
      logExecutionWarning(
          format("Sync is still running for application(s) %s. Please refer to their statuses in GitOps",
              waitingForApplications),
          logger);
    }
  }

  private void logApplicationSyncStatus(String message, String syncStatus, LogCallback logger) {
    logExecutionInfo(
        LogHelper.color(message,
            SyncOperationPhase.SUCCEEDED.getValue().equals(syncStatus) ? LogColor.Green : LogColor.Red, LogWeight.Bold),
        logger);
  }

  private boolean isApplicationSyncComplete(
      ApplicationResource currentApplicationState, String syncStatus, Instant syncStartTime) {
    return isTerminalPhase(syncStatus) && currentApplicationState.getLastSyncStartedAt() != null
        && currentApplicationState.getLastSyncStartedAt().isAfter(syncStartTime);
  }

  private List<Application> getApplicationsToBeSyncedAndPolled(
      List<Application> applicationsToBePolled, Set<Application> applicationsPolled) {
    return (List<Application>) CollectionUtils.subtract(applicationsToBePolled, applicationsPolled);
  }

  private boolean isTerminalPhase(String syncOperationPhase) {
    return SyncOperationPhase.SUCCEEDED.getValue().equals(syncOperationPhase)
        || SyncOperationPhase.FAILED.getValue().equals(syncOperationPhase)
        || SyncOperationPhase.ERROR.getValue().equals(syncOperationPhase);
  }

  private long getPollerTimeout(StepElementParameters stepParameters) {
    if (stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null) {
      return (long) NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue());
    }
    return NGTimeConversionHelper.convertTimeStringToMilliseconds("10m");
  }

  private void syncApplications(List<Application> applicationsToBeSynced, Set<Application> applicationsFailedToSync,
      String accountId, String orgId, String projectId, SyncStepParameters syncStepParameters, LogCallback logger) {
    for (Application application : applicationsToBeSynced) {
      // we should sync for auto applications too because the sync options can vary between syncing from the pipeline
      // and the sync options configured in the app for auto sync
      //      if (application.isAutoSyncEnabled()) {
      //        continue;
      //      }
      ApplicationSyncRequest syncRequest = SyncStepHelper.getSyncRequest(application, syncStepParameters);
      String agentId = application.getAgentIdentifier();
      String applicationName = application.getName();
      try {
        final Response<ApplicationResource> response =
            Failsafe.with(getRetryPolicy("Retrying to sync application...", FAILED_TO_SYNC_APPLICATION))
                .get(()
                         -> gitopsResourceClient
                                .syncApplication(agentId, applicationName, accountId, orgId, projectId, syncRequest)
                                .execute());
        if (!response.isSuccessful() || response.body() == null) {
          handleErrorWithApplicationResource(
              application, agentId, applicationName, response, FAILED_TO_SYNC_APPLICATION_WITH_ERR, logger);
          applicationsFailedToSync.add(application);
        }
      } catch (Exception e) {
        log.error(format(FAILED_TO_SYNC_APPLICATION_WITH_ERR, applicationName, agentId, e));
        throw new InvalidRequestException(FAILED_TO_SYNC_APPLICATION);
      }
    }
  }

  private boolean isApplicationEligibleForSync(ApplicationResource latestApplicationState, Application application,
      Set<String> serviceIdsInPipelineExecution, Set<String> envIdsInPipelineExecution,
      Map<String, Set<String>> clusterIdsInPipelineExecution) {
    if (!isApplicationCorrespondsToServiceInExecution(latestApplicationState, serviceIdsInPipelineExecution)) {
      application.setSyncMessage(
          "Application does not correspond to the service(s) selected in the pipeline execution.");
      return false;
    }

    if (!isApplicationCorrespondsToEnvInExecution(latestApplicationState, envIdsInPipelineExecution)) {
      application.setSyncMessage(
          "Application does not correspond to the environment(s) selected in the pipeline execution.");
      return false;
    }

    if (!isApplicationCorrespondsToClusterInExecution(latestApplicationState, clusterIdsInPipelineExecution)) {
      application.setSyncMessage(
          "Application does not correspond to the cluster(s) selected in the pipeline execution.");
      return false;
    }

    if (SyncStepHelper.isStaleApplication(latestApplicationState)) {
      application.setSyncMessage("Application is read-only and cannot be synced.");
      return false;
    }

    List<ApplicationResource.Resource> resources = latestApplicationState.getResources();

    if (isEmpty(resources)) {
      application.setSyncMessage("At least one resource should be available to sync.");
      return false;
    }
    return true;
  }

  private boolean isApplicationCorrespondsToEnvInExecution(
      ApplicationResource latestApplicationState, Set<String> envIdsInPipelineExecution) {
    return envIdsInPipelineExecution.contains(latestApplicationState.getEnvironmentRef());
  }

  private boolean isApplicationCorrespondsToClusterInExecution(
      ApplicationResource latestApplicationState, Map<String, Set<String>> clusterIdsInPipelineExecution) {
    String agentIdentifier = latestApplicationState.getAgentIdentifier();
    Set<String> clustersForAgent = clusterIdsInPipelineExecution.get(agentIdentifier);
    return clustersForAgent != null && clustersForAgent.contains(latestApplicationState.getClusterIdentifier());
  }

  private boolean isApplicationCorrespondsToServiceInExecution(
      ApplicationResource latestApplicationState, Set<String> serviceIdsInPipelineExecution) {
    return serviceIdsInPipelineExecution.contains(latestApplicationState.getServiceRef());
  }

  private ApplicationResource getApplication(
      Application application, String accountId, String orgId, String projectId, LogCallback logger) {
    String agentId = application.getAgentIdentifier();
    String applicationName = application.getName();
    try {
      final Response<ApplicationResource> response =
          Failsafe.with(getRetryPolicy("Retrying to get application...", FAILED_TO_GET_APPLICATION))
              .get(()
                       -> gitopsResourceClient.getApplication(agentId, applicationName, accountId, orgId, projectId)
                              .execute());
      if (!response.isSuccessful() || response.body() == null) {
        handleErrorWithApplicationResource(
            application, agentId, applicationName, response, FAILED_TO_GET_APPLICATION_WITH_ERR, logger);
        return null;
      }
      return response.body();
    } catch (Exception e) {
      log.error(format(FAILED_TO_GET_APPLICATION_WITH_ERR, applicationName, agentId, e));
      throw new InvalidRequestException(FAILED_TO_GET_APPLICATION);
    }
  }

  private void handleErrorWithApplicationResource(Application application, String agentId, String applicationName,
      Response<ApplicationResource> response, String applicationErr, LogCallback logger) throws IOException {
    String errorMessage = response.errorBody() != null ? response.errorBody().string() : "";
    logExecutionError(format(applicationErr, applicationName, agentId, errorMessage), logger);
    application.setSyncMessage(errorMessage);
  }

  private void refreshApplicationsAndSetSyncPolicy(List<Application> applicationsToBeSynced,
      Set<Application> applicationsFailedToSync, String accountId, String orgId, String projectId, LogCallback logger) {
    for (Application application : applicationsToBeSynced) {
      String agentId = application.getAgentIdentifier();
      String applicationName = application.getName();

      try {
        // TODO(meena) Optimize this to return the trimmed application resource object from GitOps service
        final Response<ApplicationResource> response =
            Failsafe.with(getRetryPolicy("Retrying to refresh application...", FAILED_TO_REFRESH_APPLICATION))
                .get(()
                         -> gitopsResourceClient
                                .refreshApplication(agentId, applicationName, accountId, orgId, projectId,
                                    SyncStepHelper.APPLICATION_REFRESH_TYPE)
                                .execute());
        if (!response.isSuccessful() || response.body() == null) {
          handleErrorWithApplicationResource(
              application, agentId, applicationName, response, FAILED_TO_REFRESH_APPLICATION_WITH_ERR, logger);
          applicationsFailedToSync.add(application);
          continue;
        }
        setSyncPolicy(response.body().getSyncPolicy(), application);
      } catch (Exception e) {
        log.error(format(FAILED_TO_REFRESH_APPLICATION_WITH_ERR, applicationName, agentId, e));
        throw new InvalidRequestException(FAILED_TO_REFRESH_APPLICATION);
      }
    }
  }

  private void setSyncPolicy(ApplicationResource.SyncPolicy syncPolicy, Application application) {
    if (isAutoSyncEnabled(syncPolicy)) {
      application.setAutoSyncEnabled(true);
    }
  }

  private boolean isAutoSyncEnabled(ApplicationResource.SyncPolicy syncPolicy) {
    return syncPolicy != null && syncPolicy.getAutomated() != null;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return RetryUtils.getRetryPolicy(failedAttemptMessage, failureMessage, Collections.singletonList(IOException.class),
        Duration.ofMillis(SyncStepHelper.NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS),
        SyncStepHelper.NETWORK_CALL_MAX_RETRY_ATTEMPTS, log);
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(LOG_SUFFIX);
  }
}
