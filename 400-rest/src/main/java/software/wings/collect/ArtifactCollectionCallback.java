/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.collect;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.ApprovalNotification.Builder.anApprovalNotification;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADED;
import static software.wings.persistence.artifact.Artifact.ContentStatus.FAILED;
import static software.wings.persistence.artifact.Artifact.Status.APPROVED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rishi on 12/20/16.
 */
@OwnedBy(CDC)
@Slf4j
public class ArtifactCollectionCallback implements OldNotifyCallback {
  @Inject private ArtifactService artifactService;
  @Inject private NotificationService notificationService;
  @Inject private SettingsService settingsService;
  @Inject private ArtifactStreamService artifactStreamService;

  private String artifactId;

  public ArtifactCollectionCallback() {}

  public ArtifactCollectionCallback(String artifactId) {
    this.artifactId = artifactId;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    Artifact artifact = artifactService.get(artifactId);
    ListNotifyResponseData responseData = (ListNotifyResponseData) response.values().iterator().next();

    if (isEmpty(responseData.getData())) { // Error in Downloading artifact file
      log.warn(
          "Artifact file collection failed for artifactId: [{}]. Marking content status as NOT_DOWNLOADED to retry next artifac check",
          artifactId);
      if (artifact == null) {
        log.info("Artifact Id {} was deleted - nothing to do", artifactId);
        return;
      }
      artifactService.updateStatus(
          artifactId, artifact.getAccountId(), APPROVED, FAILED, "Failed to download artifact file");
    } else {
      log.info("Artifact collection completed - artifactId : {}", artifactId);
      if (artifact == null) {
        log.info("Artifact Id {} was deleted - nothing to do", artifactId);
        return;
      }
      artifactService.addArtifactFile(artifact.getUuid(), artifact.getAccountId(),
          responseData.getData().stream().map(ArtifactFile::fromDTO).collect(Collectors.toList()));
      artifactService.updateStatus(artifactId, artifact.getAccountId(), APPROVED, DOWNLOADED, "");
      String accountId = null;
      ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
      if (artifactStream != null) {
        SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
        if (settingAttribute != null) {
          accountId = settingAttribute.getAccountId();
        }
      }
      notificationService.sendNotificationAsync(anApprovalNotification()
                                                    .withAppId(artifact.fetchAppId())
                                                    .withEntityId(artifact.getUuid())
                                                    .withEntityType(EntityType.ARTIFACT)
                                                    .withEntityName(artifact.getDisplayName())
                                                    .withArtifactStreamId(artifact.getArtifactStreamId())
                                                    .withAccountId(accountId)
                                                    .build());
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.info("Error occurred while collecting content of artifact id {}", artifactId);
    Artifact artifact = artifactService.get(artifactId);
    if (artifact == null) {
      log.info("Artifact Id {} was deleted - nothing to do", artifactId);
      return;
    }
    artifactService.updateStatus(artifact.getUuid(), artifact.getAccountId(), APPROVED, FAILED);
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }
}
