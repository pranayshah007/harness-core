/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ArtifactSourceInstrumentationHelper extends InstrumentationHelper {
  public static final String ARTIFACT_TYPE = "artifact_type";
  public static final String ARTIFACT_IDENTIFIER = "artifact_identifier";
  public static final String ARTIFACT_ACCOUNT = "artifact_account";
  public static final String ARTIFACT_ORG = "artifact_org";
  public static final String ARTIFACT_PROJECT = "artifact_project";
  public static final String IS_ARTIFACT_PRIMARY = "is_artifact_primary";

  private CompletableFuture<Void> publishArtifactInfo(
      ArtifactConfig artifactConfig, String accountId, String orgId, String projectId, String eventName) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ARTIFACT_ACCOUNT, accountId);
    eventPropertiesMap.put(ARTIFACT_ORG, orgId);
    eventPropertiesMap.put(ARTIFACT_TYPE, artifactConfig.getSourceType());
    eventPropertiesMap.put(ARTIFACT_IDENTIFIER, artifactConfig.getIdentifier());
    eventPropertiesMap.put(ARTIFACT_PROJECT, projectId);
    eventPropertiesMap.put(IS_ARTIFACT_PRIMARY, artifactConfig.isPrimaryArtifact());
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  public CompletableFuture<Void> sendLastPublishedTagExpressionEvent(
      ArtifactConfig artifactConfig, String accountId, String orgId, String projectId) {
    return publishArtifactInfo(artifactConfig, accountId, orgId, projectId, "last_published_tag");
  }
}
