/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamCollectionStatus;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.artifact.ArtifactStreamPTaskHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.TriggerService;
import software.wings.utils.DelegateArtifactCollectionUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class ArtifactCollectionResponseHandler {
  private static final int MAX_ARTIFACTS_COLLECTION_FOR_WARN = 100;
  private static final int MAX_LOGS = 100;
  public static final int MAX_FAILED_ATTEMPTS = 3500;

  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Inject private ArtifactService artifactService;
  @Inject private TriggerService triggerService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private AlertService alertService;
  @Inject private FeatureFlagService featureFlagService;

  public void processArtifactCollectionResult(@NotNull String accountId, @NotNull String perpetualTaskId,
      @NotNull BuildSourceExecutionResponse buildSourceExecutionResponse) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      ArtifactStream artifactStream = artifactStreamService.get(buildSourceExecutionResponse.getArtifactStreamId());
      if (artifactStream == null) {
        log.warn("Got empty artifact stream in buildSourceExecutionResponse");
        artifactStreamPTaskHelper.deletePerpetualTask(accountId, perpetualTaskId);
        return;
      }

      try (AutoLogContext ignore3 = new ArtifactStreamLogContext(
               artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
        if (!perpetualTaskId.equals(artifactStream.getPerpetualTaskId())
            || !featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, artifactStream.getAccountId())) {
          return;
        }

        if (buildSourceExecutionResponse.getCommandExecutionStatus() != SUCCESS) {
          onFailure(perpetualTaskId, artifactStream);
          return;
        }

        try {
          handleResponseInternal(artifactStream, buildSourceExecutionResponse);
          onSuccess(artifactStream);
        } catch (Exception ex) {
          log.error("Error while processing artifact collection", ex);
        }
      }
    }
  }

  @VisibleForTesting
  void handleResponseInternal(
      ArtifactStream artifactStream, BuildSourceExecutionResponse buildSourceExecutionResponse) {
    // NOTE: buildSourceResponse is not null at this point.
    BuildSourceResponse buildSourceResponse = buildSourceExecutionResponse.getBuildSourceResponse();
    if (buildSourceResponse.isCleanup()) {
      handleCleanup(artifactStream, buildSourceResponse);
    } else {
      handleCollection(artifactStream, buildSourceResponse);
    }
  }

  private void handleCollection(ArtifactStream artifactStream, BuildSourceResponse buildSourceResponse) {
    List<BuildDetails> builds = buildSourceResponse.getBuildDetails();
    List<Artifact> artifacts = artifactCollectionUtils.processBuilds(artifactStream, builds);
    if (isEmpty(artifacts)) {
      return;
    }

    if (artifacts.size() > MAX_ARTIFACTS_COLLECTION_FOR_WARN) {
      log.warn("Collected {} artifacts in single collection", artifacts.size());
    }

    artifacts.stream().limit(MAX_LOGS).forEach(
        artifact -> log.info("New build number [{}] collected", artifact.getBuildNo()));

    if (buildSourceResponse.isStable()) {
      triggerService.triggerExecutionPostArtifactCollectionAsync(
          artifactStream.getAccountId(), artifactStream.fetchAppId(), artifactStream.getUuid(), artifacts);
    }
  }

  private void handleCleanup(ArtifactStream artifactStream, BuildSourceResponse buildSourceResponse) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    if (!DelegateArtifactCollectionUtils.supportsCleanup(artifactStreamType)) {
      return;
    }

    log.info("Artifact cleanup started");
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactCollectionUtils.getArtifactStreamAttributes(artifactStream,
            featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, artifactStream.getAccountId()));

    Set<String> artifactKeys = buildSourceResponse.getToBeDeletedKeys();
    boolean deleted =
        artifactService.deleteArtifactsByUniqueKey(artifactStream, artifactStreamAttributes, artifactKeys);
    log.info("Artifact cleanup completed: deleted = {}, count = {}", deleted, artifactKeys.size());
    log.info("[{}] artifacts deleted for artifactStreamId {}", artifactKeys, artifactStream.getUuid());
  }

  private void onSuccess(ArtifactStream artifactStream) {
    if (!ArtifactStreamCollectionStatus.STABLE.name().equals(artifactStream.getCollectionStatus())
        && featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
      artifactStreamService.updateCollectionStatus(
          artifactStream.getAccountId(), artifactStream.getUuid(), ArtifactStreamCollectionStatus.STABLE.name());
    }

    if (artifactStream.getFailedCronAttempts() == 0) {
      artifactStreamService.updateLastIterationFields(artifactStream.getAccountId(), artifactStream.getUuid(), true);
      return;
    }

    log.info("Successfully fetched builds after {} failures", artifactStream.getFailedCronAttempts());
    artifactStreamService.updateFailedCronAttemptsAndLastIteration(
        artifactStream.getAccountId(), artifactStream.getUuid(), 0, true);

    alertService.closeAlert(artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED,
        ArtifactCollectionFailedAlert.builder().artifactStreamId(artifactStream.getUuid()).build());
  }

  private void onFailure(String perpetualTaskId, ArtifactStream artifactStream) {
    int failedCronAttempts = artifactStream.getFailedCronAttempts() + 1;
    if (failedCronAttempts % 25 == 0) {
      perpetualTaskService.resetTask(artifactStream.getAccountId(), perpetualTaskId, null);
    }

    artifactStreamService.updateFailedCronAttemptsAndLastIteration(
        artifactStream.getAccountId(), artifactStream.getUuid(), failedCronAttempts, false);
    log.warn("Failed to fetch/process builds, total failed attempts: {}", failedCronAttempts);
    if (failedCronAttempts != MAX_FAILED_ATTEMPTS) {
      if (!ArtifactStreamCollectionStatus.UNSTABLE.name().equals(artifactStream.getCollectionStatus())
          && featureFlagService.isEnabled(
              FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
        artifactStreamService.updateCollectionStatus(
            artifactStream.getAccountId(), artifactStream.getUuid(), ArtifactStreamCollectionStatus.UNSTABLE.name());
      }
      return;
    }

    String appId = artifactStream.fetchAppId();
    ArtifactCollectionFailedAlert artifactCollectionFailedAlert;
    if (!GLOBAL_APP_ID.equals(appId)) {
      artifactCollectionFailedAlert = ArtifactCollectionFailedAlert.builder()
                                          .appId(appId)
                                          .serviceId(artifactStream.getServiceId())
                                          .artifactStreamId(artifactStream.getUuid())
                                          .build();
    } else {
      artifactCollectionFailedAlert = ArtifactCollectionFailedAlert.builder()
                                          .settingId(artifactStream.getSettingId())
                                          .artifactStreamId(artifactStream.getUuid())
                                          .build();
    }

    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
      artifactStreamService.updateCollectionStatus(
          artifactStream.getAccountId(), artifactStream.getUuid(), ArtifactStreamCollectionStatus.STOPPED.name());
    }
    alertService.openAlert(
        artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED, artifactCollectionFailedAlert);
    artifactStreamPTaskHelper.deletePerpetualTask(artifactStream.getAccountId(), artifactStream.getPerpetualTaskId());
  }
}
