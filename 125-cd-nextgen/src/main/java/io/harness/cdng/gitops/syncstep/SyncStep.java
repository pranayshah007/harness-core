/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.SyncPolicy;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
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
    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.GITOPS_SYNC_STEP)) {
      throw new InvalidRequestException("Sync operation not supported.", USER);
    }

    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    final StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    CommandExecutionStatus status = FAILURE;
    SyncStepParameters syncStepParameters = (SyncStepParameters) stepParameters.getSpec();

    final LogCallback logger = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    // get application names and their agent ids
    List<Application> applicationsToBeSynced =
        SyncStepHelper.getApplicationsToBeSynced(syncStepParameters.getApplicationsList());

    long currentTimeInMs = getCurrentTime();
    Set<Application> applicationsToBeManuallySynced = new HashSet<>();
    Set<Application> applicationsToBeAutoSynced = new HashSet<>();

    log.info("Refreshing applications {}", applicationsToBeSynced);
    // call refresh and get sync policy from  the app
    // group manual and auto sync apps separately
    refreshAllApplications(applicationsToBeSynced, accountId, orgId, projectId, applicationsToBeManuallySynced,
        applicationsToBeAutoSynced);

    // sync policy - manual - call sync
    if (!applicationsToBeManuallySynced.isEmpty()) {
      syncAllApplications(applicationsToBeManuallySynced, accountId, orgId, projectId, syncStepParameters);
    }

    // start polling for all apps
    // ignore applications which have errormessage set
    // TODO
    // even if sync fails for one application, the whole step fails
    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  private void syncAllApplications(Set<Application> applicationsToBeSynced, String accountId, String orgId,
      String projectId, SyncStepParameters syncStepParameters) {
    for (Application application : applicationsToBeSynced) {
      ApplicationSyncRequest syncRequest = getSyncRequest(syncStepParameters, application, accountId, orgId, projectId);
      if (syncRequest == null) {
        continue;
      }
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
          throw new InvalidRequestException(format(FAILED_TO_SYNC_APPLICATION_WITH_ERR, applicationName, agentId,
              response.errorBody() != null ? response.errorBody().string() : ""));
        }
      } catch (Exception e) {
        log.error(format(FAILED_TO_SYNC_APPLICATION_WITH_ERR, applicationName, agentId, e));
        throw new InvalidRequestException(FAILED_TO_SYNC_APPLICATION);
      }
    }
  }

  private ApplicationSyncRequest getSyncRequest(SyncStepParameters syncStepParameters, Application application,
      String accountId, String orgId, String projectId) {
    // get application
    ApplicationResource latestApplicationState = getApplication(application, accountId, orgId, projectId);

    // if "stale" is true => application is read-only => cannot sync
    if (SyncStepHelper.isStaleApplication(latestApplicationState)) {
      application.setSyncErrorMessage("Application is read-only and cannot be synced.");
      return null;
    }

    // get resources
    List<ApplicationResource.Resource> resources = getResources(latestApplicationState);

    // if resources is null => no resources are present => cannot sync
    if (isEmpty(resources)) {
      application.setSyncErrorMessage("At least one resource should be available to sync.");
      return null;
    }

    String targetRevision = getTargetRevision(latestApplicationState);

    return SyncStepHelper.getSyncRequest(application, targetRevision, syncStepParameters);
  }

  private String getTargetRevision(ApplicationResource latestApplicationState) {
    return latestApplicationState.getApp().getSpec().getSource().getTargetRevision();
  }

  private List<ApplicationResource.Resource> getResources(ApplicationResource latestApplicationState) {
    return latestApplicationState.getApp().getStatus().getResources();
  }

  private ApplicationResource getApplication(
      Application application, String accountId, String orgId, String projectId) {
    String agentId = application.getAgentIdentifier();
    String applicationName = application.getName();
    try {
      final Response<ApplicationResource> response =
          Failsafe.with(getRetryPolicy("Retrying to get application...", FAILED_TO_GET_APPLICATION))
              .get(()
                       -> gitopsResourceClient.getApplication(agentId, applicationName, accountId, orgId, projectId)
                              .execute());
      if (!response.isSuccessful() || response.body() == null) {
        throw new InvalidRequestException(format(FAILED_TO_GET_APPLICATION_WITH_ERR, applicationName, agentId,
            response.errorBody() != null ? response.errorBody().string() : ""));
      }
      return response.body();
    } catch (Exception e) {
      log.error(format(FAILED_TO_GET_APPLICATION_WITH_ERR, applicationName, agentId, e));
      throw new InvalidRequestException(FAILED_TO_GET_APPLICATION);
    }
  }

  private long getCurrentTime() {
    return new Date().getTime();
  }

  private void refreshAllApplications(List<Application> applicationsToBeSynced, String accountId, String orgId,
      String projectId, Set<Application> applicationsToBeManuallySynced, Set<Application> applicationsToBeAutoSynced) {
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
          throw new InvalidRequestException(format(FAILED_TO_REFRESH_APPLICATION_WITH_ERR, applicationName, agentId,
              response.errorBody() != null ? response.errorBody().string() : ""));
        }
        setSyncPolicy(response.body().getApp().getSpec().getSyncPolicy(), application, applicationsToBeManuallySynced,
            applicationsToBeAutoSynced);
      } catch (Exception e) {
        log.error(format(FAILED_TO_REFRESH_APPLICATION_WITH_ERR, applicationName, agentId, e));
        throw new InvalidRequestException(FAILED_TO_REFRESH_APPLICATION);
      }
    }
  }

  private void setSyncPolicy(SyncPolicy syncPolicy, Application application,
      Set<Application> applicationsToBeManuallySynced, Set<Application> applicationsToBeAutoSynced) {
    if (isAutoSyncEnabled(syncPolicy)) {
      applicationsToBeAutoSynced.add(application);
    } else {
      applicationsToBeManuallySynced.add(application);
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
        Duration.ofMillis(NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS), NETWORK_CALL_MAX_RETRY_ATTEMPTS, log);
  }
}
