/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.logcontext.SettingAttributeLogContext;
import software.wings.service.impl.SettingAttributeObserver;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class ArtifactStreamSettingAttributePTaskManager implements SettingAttributeObserver {
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PerpetualTaskService perpetualTaskService;

  @Override
  public void onSaved(SettingAttribute settingAttribute) {
    // Nothing to do on save.
  }

  @Override
  public void onUpdated(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    String accountId = currSettingAttribute.getAccountId();
    boolean perpetualTaskEnabled = featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, accountId);
    boolean shouldDeleteArtifacts =
        currSettingAttribute.getValue().shouldDeleteArtifact(prevSettingAttribute.getValue());
    if (!shouldDeleteArtifacts && !perpetualTaskEnabled) {
      return;
    }
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new SettingAttributeLogContext(currSettingAttribute.getUuid(), OVERRIDE_ERROR)) {
      List<ArtifactStream> artifactStreams = artifactStreamService.listAllBySettingId(currSettingAttribute.getUuid());
      if (isEmpty(artifactStreams)) {
        return;
      }

      artifactStreams.forEach(artifactStream -> {
        if (shouldDeleteArtifacts) {
          artifactStreamService.deleteArtifacts(accountId, artifactStream);
        }
        if (perpetualTaskEnabled) {
          resetPerpetualTask(artifactStream);
        }
      });
    }
  }

  @Override
  public void onDeleted(SettingAttribute settingAttribute) {
    // Nothing to do on delete as artifact streams are themselves deleted.
  }

  private void resetPerpetualTask(ArtifactStream artifactStream) {
    if (artifactStream.getPerpetualTaskId() != null) {
      if (!perpetualTaskService.resetTask(artifactStream.getAccountId(), artifactStream.getPerpetualTaskId(), null)) {
        log.error(
            format("Unable to reset artifact collection perpetual task: %s", artifactStream.getPerpetualTaskId()));
      }
    }
  }
}
