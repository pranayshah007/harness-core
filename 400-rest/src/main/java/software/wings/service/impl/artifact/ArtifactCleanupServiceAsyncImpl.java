/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskData.TaskDataBuilder;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.delegatetasks.buildsource.BuildSourceCleanupCallback;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.service.intfc.ArtifactCleanupService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactCleanupServiceAsyncImpl implements ArtifactCleanupService {
  @Inject private SettingsService settingsService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;

  public static final Duration timeout = Duration.ofMinutes(10);

  @Override
  public void cleanupArtifacts(ArtifactStream artifactStream, String accountId) {
    log.info("Cleaning build details for artifact stream type {} and source name {} ",
        artifactStream.getArtifactStreamType(), artifactStream.getSourceName());

    String artifactStreamType = artifactStream.getArtifactStreamType();

    BuildSourceParameters buildSourceRequest;

    String waitId = generateUuid();
    final TaskDataBuilder dataBuilder =
        TaskData.builder().async(true).taskType(TaskType.BUILD_SOURCE_TASK.name()).timeout(DEFAULT_ASYNC_CALL_TIMEOUT);
    DelegateTaskBuilder delegateTaskBuilder =
        DelegateTask.builder()
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .waitId(waitId)
            .expiry(artifactCollectionUtils.getDelegateQueueTimeout(artifactStream.getAccountId()));

    if (CUSTOM.name().equals(artifactStreamType)) {
      ArtifactStreamAttributes artifactStreamAttributes =
          artifactCollectionUtils.renderCustomArtifactScriptString((CustomArtifactStream) artifactStream);
      accountId = artifactStreamAttributes.getAccountId();
      delegateTaskBuilder = artifactCollectionUtils.fetchCustomDelegateTask(waitId, artifactStream,
          artifactStreamAttributes, false, BuildSourceParameters.BuildSourceRequestType.GET_BUILDS);
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        log.warn("Artifact Server {} was deleted", artifactStream.getSettingId());
        // TODO:: mark inactive maybe
        return;
      }

      accountId = settingAttribute.getAccountId();
      buildSourceRequest =
          artifactCollectionUtils.getBuildSourceParameters(artifactStream, settingAttribute, false, false);
      delegateTaskBuilder.accountId(accountId);
      delegateTaskBuilder.rank(DelegateTaskRank.OPTIONAL);
      dataBuilder.parameters(new Object[] {buildSourceRequest}).timeout(TimeUnit.MINUTES.toMillis(1));
      delegateTaskBuilder.tags(settingsService.getDelegateSelectors(settingAttribute));
      delegateTaskBuilder.data(dataBuilder.build());
    }

    waitNotifyEngine.waitForAllOn(GENERAL, new BuildSourceCleanupCallback(accountId, artifactStream.getUuid()), waitId);
    log.info("Queuing delegate task with waitId {}", waitId);
    final String taskId = delegateService.queueTask(delegateTaskBuilder.build());
    log.info("Queued delegate taskId {}", taskId);
  }
}
