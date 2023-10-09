/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.STABLE;
import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.STOPPED;
import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.UNSTABLE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.beans.Account;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.PermitServiceImpl;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.PermitService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Data
@Slf4j
public class BuildSourceCallback implements OldNotifyCallback {
  private String accountId;
  private String artifactStreamId;
  private String permitId;
  private String settingId;
  private List<BuildDetails> builds;
  private static final int MAX_ARTIFACTS_COLLECTION_FOR_WARN = 100;
  private static final int MAX_LOGS = 2000;

  @Inject private transient ServiceResourceService serviceResourceService;
  @Inject private transient ArtifactService artifactService;
  @Inject private transient WingsPersistence wingsPersistence;
  @Inject private transient ArtifactStreamService artifactStreamService;
  @Inject private transient TriggerService triggerService;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private transient PermitService permitService;
  @Inject private transient AlertService alertService;
  @Inject private transient ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private transient ArtifactStreamServiceBindingService artifactStreamServiceBindingService;

  @Inject @Named("BuildSourceCallbackExecutor") private ExecutorService executorService;

  // Used in tests.
  public BuildSourceCallback() {}

  public BuildSourceCallback(String accountId, String artifactStreamId, String permitId, String settingId) {
    this.accountId = accountId;
    this.artifactStreamId = artifactStreamId;
    this.permitId = permitId;
    this.settingId = settingId;
  }

  // token used for exclusion filter for stack driver
  private static String CDC_LOG_EXCLUSION = "CDC_LOG_EXCLUSION";

  private void handleResponseForSuccess(DelegateResponseData notifyResponseData, ArtifactStream artifactStream) {
    try {
      executorService.submit(() -> {
        try {
          handleResponseForSuccessInternal(notifyResponseData, artifactStream);

        } catch (Exception ex) {
          log.error(
              "Error while processing response for BuildSourceCallback for accountId:[{}] artifactStreamId:[{}] permitId:[{}] settingId:[{}]",
              accountId, artifactStreamId, permitId, settingId, ex);
        }
      });
    } catch (RejectedExecutionException ex) {
      log.error(
          "RejectedExecutionException for BuildSourceCallback for accountId:[{}] artifactStreamId:[{}] permitId:[{}] settingId:[{}]",
          accountId, artifactStreamId, permitId, settingId, ex);
    }
  }

  @VisibleForTesting
  void handleResponseForSuccessInternal(DelegateResponseData notifyResponseData, ArtifactStream artifactStream) {
    log.debug(
        "{}: Processing response for BuildSourceCallback for accountId:[{}] artifactStreamId:[{}] permitId:[{}] settingId:[{}]",
        CDC_LOG_EXCLUSION, accountId, artifactStreamId, permitId, settingId);

    BuildSourceExecutionResponse buildSourceExecutionResponse = (BuildSourceExecutionResponse) notifyResponseData;
    BuildSourceResponse buildSourceResponse = buildSourceExecutionResponse.getBuildSourceResponse();
    if (buildSourceResponse != null) {
      builds = buildSourceExecutionResponse.getBuildSourceResponse().getBuildDetails();
    } else {
      log.warn(
          "ASYNC_ARTIFACT_COLLECTION: null BuildSourceResponse in buildSourceExecutionResponse:[{}] for artifactStreamId [{}]",
          buildSourceExecutionResponse, artifactStreamId);
      updatePermit(artifactStream, false);
      return;
    }

    // NOTE: buildSourceResponse is not null at this point.
    try {
      boolean isUnstable = UNSTABLE.name().equals(artifactStream.getCollectionStatus());
      boolean isStopped = STOPPED.name().equals(artifactStream.getCollectionStatus());
      if ((isUnstable || isStopped || artifactStream.getCollectionStatus() == null) && buildSourceResponse.isStable()) {
        // If the artifact stream is unstable and buildSourceResponse has stable as true, mark this artifact
        // stream as stable.
        artifactStreamService.updateCollectionStatus(accountId, artifactStreamId, STABLE.name());
      }

      List<Artifact> artifacts = processBuilds(artifactStream);

      if (artifacts.size() > MAX_ARTIFACTS_COLLECTION_FOR_WARN) {
        log.warn(
            "Collected {} artifacts in single collection artifactStreamId:[{}]", artifacts.size(), artifactStreamId);
      }
      if (isNotEmpty(artifacts)) {
        artifacts.stream().limit(MAX_LOGS).forEach(artifact -> {
          try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
               AutoLogContext ignore2 = new ArtifactStreamLogContext(
                   artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
            log.info("Build number {} new artifacts collected artifactStreamId:[{}]", artifact.getBuildNo(),
                artifactStreamId);
          }
        });

        // CDS-45417: If artifact collection failed on first attempt after creating artifact stream,
        // we want to trigger with artifact whenever server is available.
        // (Asserting first collection failure by artifact stream being UNSTABLE and failed attempts are more than 0)
        if (isUnstable
            && (artifactStream.getFailedCronAttempts() == 0
                || featureFlagService.isEnabled(FeatureName.TRIGGER_FOR_ALL_ARTIFACTS, accountId))) {
          log.warn(
              "ASYNC_ARTIFACT_COLLECTION: Unstable to trigger execution after collect artifacts [artifactStreamId={}]",
              artifactStreamId);
          updatePermit(artifactStream, false);
          return;
        }

        // Invoke triggers for only stable artifact streams.
        triggerService.triggerExecutionPostArtifactCollectionAsync(
            accountId, artifactStream.fetchAppId(), artifactStreamId, artifacts);
      }

      updatePermit(artifactStream, false);
    } catch (WingsException ex) {
      ex.addContext(Account.class, accountId);
      ex.addContext(ArtifactStream.class, artifactStreamId);
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      updatePermit(artifactStream, true);
    }
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    ArtifactStream artifactStream = getArtifactStreamOrThrow();
    log.debug("In notify for artifact stream id: [{}]", artifactStreamId);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ArtifactStreamLogContext(
             artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
      if (notifyResponseData instanceof BuildSourceExecutionResponse) {
        if (SUCCESS == ((BuildSourceExecutionResponse) notifyResponseData).getCommandExecutionStatus()) {
          handleResponseForSuccess(notifyResponseData, artifactStream);
        } else {
          log.info("Request failed :[{}]", ((BuildSourceExecutionResponse) notifyResponseData).getErrorMessage());
          updatePermit(artifactStream, true);
        }
      } else {
        notifyError(response);
      }
    }
  }

  private void updatePermit(ArtifactStream artifactStream, boolean failed) {
    if (failed) {
      int failedCronAttempts = artifactStream.getFailedCronAttempts() + 1;
      artifactStreamService.updateFailedCronAttemptsAndLastIteration(
          artifactStream.getAccountId(), artifactStream.getUuid(), failedCronAttempts, false);
      log.warn(
          "ASYNC_ARTIFACT_COLLECTION: failed to fetch/process builds totalFailedAttempt:[{}] artifactStreamId:[{}]",
          failedCronAttempts, artifactStreamId);
      if (PermitServiceImpl.shouldSendAlert(failedCronAttempts)) {
        String appId = artifactStream.fetchAppId();
        if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, accountId)) {
          artifactStreamService.updateCollectionStatus(accountId, artifactStreamId, STOPPED.name());
        }
        if (!GLOBAL_APP_ID.equals(appId)) {
          alertService.openAlert(accountId, null, AlertType.ARTIFACT_COLLECTION_FAILED,
              ArtifactCollectionFailedAlert.builder()
                  .appId(appId)
                  .serviceId(artifactStream.getServiceId())
                  .artifactStreamId(artifactStreamId)
                  .build());
        } else {
          alertService.openAlert(accountId, null, AlertType.ARTIFACT_COLLECTION_FAILED,
              ArtifactCollectionFailedAlert.builder()
                  .settingId(artifactStream.getSettingId())
                  .artifactStreamId(artifactStreamId)
                  .build());
        }
      } else {
        if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, accountId)
            && !UNSTABLE.name().equals(artifactStream.getCollectionStatus())) {
          artifactStreamService.updateCollectionStatus(accountId, artifactStreamId, UNSTABLE.name());
        }
      }
    } else {
      if (artifactStream.getFailedCronAttempts() != 0) {
        log.warn("ASYNC_ARTIFACT_COLLECTION: successfully fetched builds after [{}] failures for artifactStream[{}]",
            artifactStream.getFailedCronAttempts(), artifactStreamId);
        artifactStreamService.updateFailedCronAttemptsAndLastIteration(
            artifactStream.getAccountId(), artifactStream.getUuid(), 0, false);
        permitService.releasePermitByKey(artifactStream.getUuid());
        alertService.closeAlert(accountId, null, AlertType.ARTIFACT_COLLECTION_FAILED,
            ArtifactCollectionFailedAlert.builder().artifactStreamId(artifactStreamId).build());
      } else {
        artifactStreamService.updateLastIterationFields(accountId, artifactStreamId, true);
      }
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    ArtifactStream artifactStream = getArtifactStreamOrThrow();

    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      log.info("Request failed :[{}]", ((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else {
      log.error("Unexpected  notify response:[{}] during artifact collection", response);
    }
    updatePermit(artifactStream, true);
  }

  private List<Artifact> processBuilds(ArtifactStream artifactStream) {
    if (isEmpty(builds)) {
      return new ArrayList<>();
    }
    if (artifactStream == null) {
      log.info("Artifact stream: [{}] does not exist. Returning", artifactStreamId);
      return new ArrayList<>();
    }

    // New build are filtered at the delegate. So all the builds coming in the BuildSourceExecutionResponse are the ones
    // not present in the DB.
    return builds.stream()
        .map(buildDetails -> artifactService.create(artifactCollectionUtils.getArtifact(artifactStream, buildDetails)))
        .collect(Collectors.toList());
  }

  @NotNull
  private ArtifactStream getArtifactStreamOrThrow() {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new ArtifactStreamNotFound(artifactStreamId);
    }
    return artifactStream;
  }
}
