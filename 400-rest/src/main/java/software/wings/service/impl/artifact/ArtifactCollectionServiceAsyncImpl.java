/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskData.TaskDataBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.delegatetasks.buildsource.BuildSourceCallback;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.PermitServiceImpl;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.PermitService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/***
 * Service responsible to glue all artifact
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ArtifactCollectionServiceAsyncImpl implements ArtifactCollectionService {
  @Inject private BuildSourceService buildSourceService;
  @Inject private ArtifactService artifactService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private SettingsService settingsService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private PermitService permitService;
  @Inject private AlertService alertService;
  @Inject private FeatureFlagService featureFlagService;

  // Default timeout of 1 minutes.
  private static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(1);

  @Override
  public Artifact collectArtifact(String artifactStreamId, BuildDetails buildDetails) {
    // Disable fetching labels for now as it has many issues.
    return collectArtifactWithoutLabels(artifactStreamId, buildDetails);
  }

  private Artifact collectArtifactWithoutLabels(String artifactStreamId, BuildDetails buildDetails) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new InvalidRequestException("Artifact stream was deleted", USER);
    }
    return collectArtifactWithoutLabels(artifactStream, buildDetails);
  }

  private Artifact collectArtifactWithoutLabels(ArtifactStream artifactStream, BuildDetails buildDetails) {
    final Artifact savedArtifact;
    if (!artifactStream.isArtifactStreamParameterized()) {
      savedArtifact = artifactService.create(artifactCollectionUtils.getArtifact(artifactStream, buildDetails));
    } else {
      savedArtifact = artifactService.create(
          artifactCollectionUtils.getArtifact(artifactStream, buildDetails), artifactStream, false);
    }
    if (artifactStream.getFailedCronAttempts() != 0) {
      artifactStreamService.updateFailedCronAttemptsAndLastIteration(
          artifactStream.getAccountId(), savedArtifact.getArtifactStreamId(), 0, false);
      permitService.releasePermitByKey(artifactStream.getUuid());
      alertService.closeAlert(artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED,
          ArtifactCollectionFailedAlert.builder().artifactStreamId(artifactStream.getUuid()).build());
    }
    return savedArtifact;
  }

  @Override
  public void collectNewArtifactsAsync(ArtifactStream artifactStream, String permitId) {
    String artifactStreamType = artifactStream.getArtifactStreamType();

    String accountId;
    BuildSourceParameters buildSourceRequest;

    String waitId = generateUuid();
    final TaskDataBuilder dataBuilder = TaskData.builder().async(true).taskType(TaskType.BUILD_SOURCE_TASK.name());
    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder().waitId(waitId).expiry(
        artifactCollectionUtils.getDelegateQueueTimeout(artifactStream.getAccountId()));

    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_DELEGATE_SCOPING, artifactStream.getAccountId())) {
      delegateTaskBuilder.setupAbstraction(Cd1SetupFields.APP_ID_FIELD, artifactStream.getAppId());
    } else {
      delegateTaskBuilder.setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID);
    }

    if (CUSTOM.name().equals(artifactStreamType)) {
      ArtifactStreamAttributes artifactStreamAttributes =
          artifactCollectionUtils.renderCustomArtifactScriptString((CustomArtifactStream) artifactStream);
      accountId = artifactStreamAttributes.getAccountId();
      delegateTaskBuilder = artifactCollectionUtils.fetchCustomDelegateTask(waitId, artifactStream,
          artifactStreamAttributes, true, BuildSourceParameters.BuildSourceRequestType.GET_BUILDS);
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        log.warn("Artifact Server {} was deleted of artifactStreamId {}", artifactStream.getSettingId(),
            artifactStream.getUuid());
        // TODO:: mark inactive maybe
        int failedCronAttempts = artifactStream.getFailedCronAttempts() + 1;
        artifactStreamService.updateFailedCronAttemptsAndLastIteration(
            artifactStream.getAccountId(), artifactStream.getUuid(), failedCronAttempts, false);
        if (PermitServiceImpl.shouldSendAlert(failedCronAttempts)) {
          String appId = artifactStream.fetchAppId();
          if (!GLOBAL_APP_ID.equals(appId)) {
            alertService.openAlert(artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED,
                ArtifactCollectionFailedAlert.builder()
                    .appId(appId)
                    .serviceId(artifactStream.getServiceId())
                    .artifactStreamId(artifactStream.getUuid())
                    .build());
          } else {
            alertService.openAlert(artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED,
                ArtifactCollectionFailedAlert.builder()
                    .settingId(artifactStream.getSettingId())
                    .artifactStreamId(artifactStream.getUuid())
                    .build());
          }
        }
        return;
      }

      accountId = settingAttribute.getAccountId();
      buildSourceRequest =
          artifactCollectionUtils.getBuildSourceParameters(artifactStream, settingAttribute, true, true);

      // Set timeout.
      dataBuilder.parameters(new Object[] {buildSourceRequest}).timeout(DEFAULT_TIMEOUT);
      delegateTaskBuilder.tags(settingsService.getDelegateSelectors(settingAttribute));
      delegateTaskBuilder.accountId(accountId);
      delegateTaskBuilder.rank(DelegateTaskRank.OPTIONAL);
      delegateTaskBuilder.data(dataBuilder.build());
    }

    waitNotifyEngine.waitForAllOn(GENERAL,
        new BuildSourceCallback(accountId, artifactStream.getUuid(), permitId, artifactStream.getSettingId()), waitId);
    log.info("Queuing delegate task for artifactStream with waitId {}", waitId);
    final String taskId = delegateService.queueTask(delegateTaskBuilder.build());
    log.info("Queued delegate taskId {} for artifactStream type: {} and source: {}", taskId,
        artifactStream.getArtifactStreamType(), artifactStream.getSourceName());
  }

  @Override
  public Artifact collectNewArtifacts(String appId, ArtifactStream artifactStream, String buildNumber) {
    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      Artifact artifact = saveCustomArtifactWithNoScript((CustomArtifactStream) artifactStream, buildNumber);
      if (artifact != null) {
        return artifact;
      }
    }
    List<BuildDetails> builds =
        buildSourceService.getBuilds(appId, artifactStream.getUuid(), artifactStream.getSettingId());
    if (isNotEmpty(builds)) {
      Optional<BuildDetails> buildDetails =
          builds.stream().filter(build -> buildNumber.equals(build.getNumber())).findFirst();
      if (buildDetails.isPresent()) {
        return collectArtifactWithoutLabels(artifactStream.getUuid(), buildDetails.get());
      }
    }
    return null;
  }

  private Artifact saveCustomArtifactWithNoScript(CustomArtifactStream artifactStream, String buildNumber) {
    if (artifactStream.getScripts() == null) {
      return null;
    }
    CustomArtifactStream.Script versionScript =
        artifactStream.getScripts()
            .stream()
            .filter(script
                -> script.getAction() == null || script.getAction() == CustomArtifactStream.Action.FETCH_VERSIONS)
            .findFirst()
            .orElse(CustomArtifactStream.Script.builder().build());
    if (isNotEmpty(versionScript.getScriptString())) {
      return null;
    }
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber(buildNumber).build();
    return artifactService.create(artifactCollectionUtils.getArtifact(artifactStream, buildDetails));
  }

  @Override
  public Artifact collectNewArtifacts(
      String appId, ArtifactStream artifactStream, String buildNumber, Map<String, Object> artifactVariables) {
    BuildDetails buildDetails =
        buildSourceService.getBuild(appId, artifactStream.getUuid(), artifactStream.getSettingId(), artifactVariables);
    if (buildDetails != null) {
      return collectArtifactWithoutLabels(artifactStream, buildDetails);
    }
    return null;
  }

  @Override
  public List<Artifact> collectNewArtifacts(String appId, String artifactStreamId) {
    throw new InvalidRequestException("Method not supported");
  }
}
