/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SYNC_SWEEPING_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.common.NGTimeConversionHelper;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.SyncPolicy;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.SyncExecutableWithRbac;
import io.harness.utils.RetryUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections.CollectionUtils;
import retrofit2.Response;

@Slf4j
public class SyncStep implements SyncExecutableWithRbac<StepElementParameters> {
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

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_SYNC.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // check if rbac is there for GitOps apps
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Starting execution for Sync step [{}]", stepParameters);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.GITOPS_SYNC_STEP)) {
      throw new InvalidRequestException("Feature Flag GITOPS_SYNC_STEP is not enabled.", USER);
    }

    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    SyncStepParameters syncStepParameters = (SyncStepParameters) stepParameters.getSpec();

    final LogCallback logger = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    List<Application> applicationsToBeSynced = getApplicationsToBeSynced(ambiance, syncStepParameters);

    Set<Application> applicationsFailedToSync = new HashSet<>();

    logExecutionInfo(format("Applications to be synced %s", applicationsToBeSynced), logger);

    Instant syncStartTime = Instant.now();
    log.info("Sync start time is {}", syncStartTime);

    // refresh applications
    logExecutionInfo("Refreshing applications...", logger);
    refreshApplicationsAndSetSyncPolicy(
        applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId, logger);

    // check sync eligibility for applications
    logExecutionInfo("Checking applications eligibility for sync...", logger);
    prepareApplicationForSync(applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId, logger);
    List<Application> applicationsEligibleForSync =
        getApplicationsToBeSyncedAndPolled(applicationsToBeSynced, applicationsFailedToSync);

    // sync applications
    logExecutionInfo("Syncing applications...", logger);
    syncApplications(
        applicationsEligibleForSync, applicationsFailedToSync, accountId, orgId, projectId, syncStepParameters, logger);

    if (isNotEmpty(applicationsFailedToSync)) {
      printErroredApplications(applicationsFailedToSync, "Applications errored before syncing", logger);
    }

    // poll applications
    if (isNotEmpty(applicationsEligibleForSync)) {
      long pollForMillis = getPollerTimeout(stepParameters);
      logExecutionInfo(format("Polling application statuses %s", applicationsEligibleForSync), logger);
      pollApplications(pollForMillis, applicationsEligibleForSync, applicationsFailedToSync, syncStartTime, accountId,
          orgId, projectId, logger);
    }
    Set<Application> applicationsFailedOnArgoSync = new HashSet<>();
    Set<Application> applicationsSucceededOnArgoSync = new HashSet<>();
    Set<Application> syncStillRunningForApplications = new HashSet<>();
    groupApplicationsOnSyncStatus(applicationsEligibleForSync, applicationsFailedOnArgoSync,
        applicationsSucceededOnArgoSync, syncStillRunningForApplications);

    if (isNotEmpty(applicationsFailedOnArgoSync)) {
      printErroredApplications(applicationsFailedOnArgoSync, "Applications errored while syncing", logger);
    }
    applicationsFailedToSync.addAll(applicationsFailedOnArgoSync);

    final SyncStepOutcome outcome = SyncStepOutcome.builder().applications(applicationsSucceededOnArgoSync).build();
    executionSweepingOutputResolver.consume(
        ambiance, GITOPS_SYNC_SWEEPING_OUTPUT, outcome, StepOutcomeGroup.STAGE.name());

    return prepareResponse(
        applicationsFailedToSync, applicationsSucceededOnArgoSync, syncStillRunningForApplications, outcome, logger)
        .build();
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

    // TODO change to Set
    List<Application> applications = optionalSweepingOutput != null && optionalSweepingOutput.isFound()
        ? ((GitOpsLinkedAppsOutcome) optionalSweepingOutput.getOutput()).getApps()
        : new ArrayList<>();
    applications.addAll(SyncStepHelper.getApplicationsToBeSynced(syncStepParameters.getApplicationsList()));
    return new ArrayList<>(applications);
  }

  private void printErroredApplications(Set<Application> applicationsErrored, String logMessage, LogCallback logger) {
    logExecutionError(format(logMessage, " with error messages %s", applicationsErrored), logger);
    for (Application application : applicationsErrored) {
      logExecutionError(application.getSyncError(), logger);
    }
  }

  private void prepareApplicationForSync(List<Application> applicationsToBeSynced,
      Set<Application> failedToSyncApplications, String accountId, String orgId, String projectId, LogCallback logger) {
    for (Application application : applicationsToBeSynced) {
      if (failedToSyncApplications.contains(application)) {
        continue;
      }
      ApplicationResource latestApplicationState = getApplication(application, accountId, orgId, projectId, logger);

      if (latestApplicationState == null || !isApplicationEligibleForSync(latestApplicationState, application)) {
        failedToSyncApplications.add(application);
        continue;
      }
      application.setRevision(latestApplicationState.getTargetRevision());
    }
  }

  private void saveExecutionLog(String log, LogCallback logger, LogLevel logLevel) {
    logger.saveExecutionLog(log, logLevel);
  }

  private StepResponseBuilder prepareResponse(Set<Application> applicationsFailedToSync,
      Set<Application> applicationsSucceededOnArgoSync, Set<Application> syncStillRunningForApplications,
      SyncStepOutcome outcome, LogCallback logger) {
    logExecutionInfo(format("Sync is successful for applications %s", applicationsSucceededOnArgoSync), logger);
    logExecutionInfo(format("Sync failed for applications %s", applicationsFailedToSync), logger);
    logExecutionInfo(format("Sync is still running for applications %s", syncStillRunningForApplications), logger);
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    if (isNotEmpty(applicationsFailedToSync) || isNotEmpty(syncStillRunningForApplications)) {
      FailureData failureMessage =
          FailureData.newBuilder()
              .addFailureTypes(FailureType.APPLICATION_FAILURE)
              .setLevel(Level.ERROR.name())
              .setCode(GENERAL_ERROR.name())
              .setMessage(format(
                  "Sync is successful for applications %s and failed for applications %s and is still running for applications %s",
                  applicationsSucceededOnArgoSync, applicationsFailedToSync, syncStillRunningForApplications))
              .build();
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addFailureData(failureMessage).build());
    }
    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder().name(GITOPS_SWEEPING_OUTPUT).outcome(outcome).build());
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
    // stopping 10 seconds before the step timeout
    long deadlineInMillis = startTimeMillis + pollForMillis - (SyncStepHelper.STOP_BEFORE_STEP_TIMEOUT_SECS * 1000);

    while (System.currentTimeMillis() < deadlineInMillis) {
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
          logExecutionInfo(
              format("Application %s is successfully synced. Sync status %s, message %s, Application health status %s",
                  application.getName(), syncStatus, syncMessage, healthStatus),
              logger);
        } else {
          log.info(
              "Sync is {}, Last sync start time is {}", syncStatus, currentApplicationState.getLastSyncStartedAt());
        }
      }
      if (applicationsPolled.size() == applicationsToBePolled.size()) {
        if (isNotEmpty(applicationsFailedToSync)) {
          logExecutionInfo("Sync is complete for eligible applications.", logger);
        } else {
          logExecutionInfo("All applications have been successfully synced.", logger);
        }
        return;
      }
      waitingForApplications = getApplicationsToBeSyncedAndPolled(applicationsToBePolled, applicationsPolled);
      logExecutionInfo(format("Waiting for applications %s", waitingForApplications), logger);
      try {
        TimeUnit.SECONDS.sleep(SyncStepHelper.POLLER_SLEEP_SECS);
      } catch (InterruptedException e) {
        log.error(format("Application polling interrupted with error %s", e));
        Thread.currentThread().interrupt();
        throw new RuntimeException("Application polling interrupted.");
      }
    }
    logExecutionWarning(format("Sync is still running for applications %s. Please refer to their statuses in GitOps",
                            waitingForApplications),
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
      if (application.isAutoSyncEnabled()) {
        continue;
      }
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

  private boolean isApplicationEligibleForSync(ApplicationResource latestApplicationState, Application application) {
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
            Failsafe.with(getRetryPolicy("Retrying to refresh applications...", FAILED_TO_REFRESH_APPLICATION))
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

  private void setSyncPolicy(SyncPolicy syncPolicy, Application application) {
    if (isAutoSyncEnabled(syncPolicy)) {
      application.setAutoSyncEnabled(true);
    }
  }

  private boolean isAutoSyncEnabled(SyncPolicy syncPolicy) {
    return syncPolicy.getAutomated() != null;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, null);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return RetryUtils.getRetryPolicy(failedAttemptMessage, failureMessage, Collections.singletonList(IOException.class),
        Duration.ofMillis(SyncStepHelper.NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS),
        SyncStepHelper.NETWORK_CALL_MAX_RETRY_ATTEMPTS, log);
  }
}
