/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import com.google.inject.Inject;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import io.harness.common.NGTimeConversionHelper;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.SyncExecutableWithRbac;
import io.harness.utils.RetryUtils;
import java.io.IOException;
import static java.lang.String.format;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections.CollectionUtils;
import retrofit2.Response;

@Slf4j
public class SyncStep implements SyncExecutableWithRbac<StepElementParameters> {
  private static final int NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS = 10;
  private static final int NETWORK_CALL_MAX_RETRY_ATTEMPTS = 3;
  private static final String APPLICATION_REFRESH_TYPE = "normal";
  private static final String FAILED_TO_REFRESH_APPLICATION_WITH_ERR =
      "Failed to refresh application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_GET_APPLICATION_WITH_ERR =
      "Failed to get application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_SYNC_APPLICATION_WITH_ERR =
      "Failed to sync application, name: %s, agent id %s. Error is %s";
  private static final String FAILED_TO_REFRESH_APPLICATION = "Failed to refresh application";
  private static final String FAILED_TO_SYNC_APPLICATION = "Failed to sync application";
  private static final String FAILED_TO_GET_APPLICATION = "Failed to get application";

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
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Starting execution for Sync step [{}]", stepParameters);

    String accountId = AmbianceUtils.getAccountId(ambiance);
//    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.GITOPS_SYNC_STEP)) {
//      throw new InvalidRequestException("Feature Flag GITOPS_SYNC_STEP is not enabled.", USER);
//    }

    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    SyncStepParameters syncStepParameters = (SyncStepParameters) stepParameters.getSpec();

    final LogCallback logger = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    List<Application> applicationsToBeSynced =
        SyncStepHelper.getApplicationsToBeSynced(syncStepParameters.getApplicationsList());
    Set<Application> applicationsFailedToSync = new HashSet<>();

    //refresh applications
    log.info("Refreshing applications {}", applicationsToBeSynced);
    saveExecutionLog(format("Refreshing applications %s", applicationsToBeSynced), logger);

    Instant syncStartTime = Instant.now();
    log.info("Sync start time is {}", syncStartTime);
    saveExecutionLog(format("Sync start time is %s", syncStartTime), logger);

    refreshApplicationsAndSetSyncPolicy(applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId, logger);

    //check sync eligibility for applications
    prepareApplicationForSync(applicationsToBeSynced, applicationsFailedToSync, accountId, orgId, projectId, logger);
    List<Application> applicationsEligibleForSync = getApplicationsToBeSyncedAndPolled(applicationsToBeSynced);

    //sync applications
    log.info("Syncing applications {}", applicationsEligibleForSync);
    saveExecutionLog(format("Syncing applications %s", applicationsEligibleForSync), logger);
    syncApplications(applicationsEligibleForSync, applicationsFailedToSync, accountId, orgId, projectId, syncStepParameters, logger);

    //poll applications
    if(isNotEmpty(applicationsEligibleForSync)) {
      long pollForMillis = getPollerTimeout(stepParameters);
      saveExecutionLog(format("Polling applications %s", applicationsEligibleForSync), logger);
      pollApplications(pollForMillis, applicationsEligibleForSync, applicationsFailedToSync, syncStartTime, accountId, orgId, projectId, logger);
    }

    final SyncStepOutcome outcome = SyncStepOutcome.builder().applications(applicationsEligibleForSync).build();
    executionSweepingOutputResolver.consume(ambiance, GITOPS_SWEEPING_OUTPUT, outcome, StepOutcomeGroup.STAGE.name());

    return prepareResponse(applicationsEligibleForSync, applicationsFailedToSync, outcome).build();
  }

  private void prepareApplicationForSync(List<Application> applicationsToBeSynced, Set<Application> failedToSyncApplications, String accountId, String orgId, String projectId, LogCallback logger) {
    for(Application application : applicationsToBeSynced) {
      if(!application.isSyncAllowed()) {
        continue;
      }
      ApplicationResource latestApplicationState = getApplication(application, accountId, orgId, projectId, logger);

      if(latestApplicationState == null || !isApplicationEligibleForSync(latestApplicationState, application)) {
        application.setSyncAllowed(false);
        failedToSyncApplications.add(application);
        continue;
      }
      application.setRevision(latestApplicationState.getTargetRevision());
    }

  }

  private void saveExecutionLog(String log, LogCallback logger) {
    logger.saveExecutionLog(log);
  }

  private StepResponseBuilder prepareResponse(List<Application> applicationsToBeSynced, Set<Application> failedOnSyncStepApplications, SyncStepOutcome outcome) {
    List<Application> failedOnArgoSyncApplications = getApplicationsFailedOnSync(applicationsToBeSynced);
    failedOnArgoSyncApplications.addAll(failedOnSyncStepApplications);
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    if(isNotEmpty(failedOnArgoSyncApplications)) {
      List<FailureData> failureMessage = getFailureMessage();
      return stepResponseBuilder.status(Status.FAILED).failureInfo(FailureInfo.newBuilder().addAllFailureData(failureMessage).build());
    }
    return stepResponseBuilder.status(Status.SUCCEEDED).stepOutcome(StepResponse.StepOutcome.builder().name(GITOPS_SWEEPING_OUTPUT).outcome(outcome).build());
  }

  //TODO
  private List<FailureData> getFailureMessage() {
    List<FailureData> syncFailureMessage = new ArrayList<>();
    return syncFailureMessage;
  }

  private List<Application> getApplicationsFailedOnSync(List<Application> applicationsToBeSynced) {
    List<Application> failedApplications = new ArrayList<>();
    for (Application application : applicationsToBeSynced) {
      if(!SyncOperationPhase.SUCCEEDED.getValue().equals(application.getSyncStatus())) {
        failedApplications.add(application);
      }
    }
    return failedApplications;
  }

  private void pollApplications(long pollForMillis, List<Application> applicationsToBePolled, Set<Application> applicationsFailedToSync, Instant syncStartTime, String accountId, String orgId, String projectId, LogCallback logger) {
    Set<Application> applicationsPolled = new HashSet<>();
    List<Application> waitingForApplications = new ArrayList<>();
    long startTimeMillis = System.currentTimeMillis();
    //stopping 5 seconds before the step timeout
    long deadlineInMillis = startTimeMillis + pollForMillis - (SyncStepConstants.STOP_BEFORE_STEP_TIMEOUT_SECS * 1000);

    while (System.currentTimeMillis() < deadlineInMillis) {

      for(Application application : applicationsToBePolled) {
        if(applicationsPolled.contains(application)) {
          continue;
        }
        log.info("Polling application {}", application.getName());
        saveExecutionLog(format("Polling application %s", application.getName()), logger);
        ApplicationResource currentApplicationState = getApplication(application, accountId, orgId, projectId, logger);
        if(currentApplicationState == null) {
          applicationsFailedToSync.add(application);
          applicationsPolled.add(application);
          continue;
        }
        String syncStatus = currentApplicationState.getSyncOperationPhase();
        String syncMessage = currentApplicationState.getSyncMessage();
        String healthStatus = currentApplicationState.getHealthStatus();
        application.setSyncStatus(syncStatus);
        application.setHealthStatus(healthStatus);
        application.setSyncMessage(syncMessage);

        if(isTerminalPhase(syncStatus) && currentApplicationState.getLastSyncStartedAt() != null && currentApplicationState.getLastSyncStartedAt().isAfter(syncStartTime)) {
          applicationsPolled.add(application);
          log.info("Application {} is successfully synced. Sync status {}, Sync message {}, Application health status {}", application.getName(), syncStatus, syncMessage, healthStatus);
          saveExecutionLog(format("Application %s is successfully synced. Sync status %s, Sync message %s, Application health status %s",
                  application.getName(), syncStatus, syncMessage, healthStatus), logger);
        }
        //TODO check for NP in getLastSyncStartedAt here
        log.info("Sync is {}, Last sync start time is {}", syncStatus, currentApplicationState.getLastSyncStartedAt());
        saveExecutionLog(format("Sync is %s, Last sync start time is %s", syncStatus, currentApplicationState.getLastSyncStartedAt()), logger);
      }
      if (applicationsPolled.size() == applicationsToBePolled.size()) {
        if(isNotEmpty(applicationsFailedToSync)) {
          log.info("Sync is complete for eligible applications.");
          saveExecutionLog("Sync is complete for eligible applications.", logger);
        } else {
          log.info("All applications have been successfully synced.");
          saveExecutionLog("All applications have been successfully synced.", logger);
        }
        return;
      }
      waitingForApplications = (List<Application>) CollectionUtils.subtract(applicationsToBePolled, applicationsPolled);
      log.info("Applications yet to be synced {}", waitingForApplications);
      saveExecutionLog(format("Applications yet to be synced %s", waitingForApplications), logger);
      try {
        TimeUnit.SECONDS.sleep(SyncStepConstants.POLLER_SLEEP_SECS);
      } catch (InterruptedException e) {
        log.error(format("Application polling interrupted with error %s", e));
        throw new RuntimeException("Application polling interrupted.");
      }
    }
    saveExecutionLog(format("Sync is still running for applications %s. Please refer to their statuses in GitOps", waitingForApplications), logger);
  }

  private List<Application> getApplicationsToBeSyncedAndPolled(List<Application> applicationsToBeSynced) {
    return applicationsToBeSynced.stream().filter(Application::isSyncAllowed).collect(Collectors.toList());
  }

  private boolean isTerminalPhase(String syncOperationPhase) {
    return SyncOperationPhase.SUCCEEDED.getValue().equals(syncOperationPhase) || SyncOperationPhase.FAILED.getValue().equals(syncOperationPhase) ||
            SyncOperationPhase.ERROR.getValue().equals(syncOperationPhase);
  }


  private long getPollerTimeout(StepElementParameters stepParameters) {
    if (stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null) {
      return
              (long) NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue());
    }
    return NGTimeConversionHelper.convertTimeStringToMilliseconds("10m");
  }

  private void syncApplications(List<Application> applicationsToBeSynced, Set<Application> applicationsFailedToSync, String accountId, String orgId,
                                String projectId, SyncStepParameters syncStepParameters, LogCallback logger) {
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
          handleErrorWithApplicationResource(application, agentId, applicationName, response, FAILED_TO_SYNC_APPLICATION_WITH_ERR, logger);
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
        handleErrorWithApplicationResource(application, agentId, applicationName, response, FAILED_TO_GET_APPLICATION_WITH_ERR, logger);
        application.setSyncAllowed(false);
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
    log.error(format(applicationErr, applicationName, agentId, errorMessage));
    saveExecutionLog(format(applicationErr, applicationName, agentId, errorMessage), logger);
    application.setSyncMessage(errorMessage);
  }

  private void refreshApplicationsAndSetSyncPolicy(List<Application> applicationsToBeSynced, Set<Application> applicationsFailedToSync, String accountId, String orgId,
                                                   String projectId, LogCallback logger) {
    for (Application application : applicationsToBeSynced) {
      String agentId = application.getAgentIdentifier();
      String applicationName = application.getName();

      try {
        // TODO(meena) Optimize this to return the trimmed application resource object from GitOps service
        final Response<ApplicationResource> response =
            Failsafe.with(getRetryPolicy("Retrying to refresh applications...", FAILED_TO_REFRESH_APPLICATION))
                .get(()
                         -> gitopsResourceClient
                                .refreshApplication(
                                    agentId, applicationName, accountId, orgId, projectId, APPLICATION_REFRESH_TYPE)
                                .execute());
        if (!response.isSuccessful() || response.body() == null) {
          handleErrorWithApplicationResource(application, agentId, applicationName, response, FAILED_TO_REFRESH_APPLICATION_WITH_ERR, logger);
          application.setSyncAllowed(false);
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
    return syncPolicy.getAutomated() != null;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, null);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return RetryUtils.getRetryPolicy(failedAttemptMessage, failureMessage, Collections.singletonList(IOException.class),
        Duration.ofMillis(NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS), NETWORK_CALL_MAX_RETRY_ATTEMPTS, log);
  }
}
