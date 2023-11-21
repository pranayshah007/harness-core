/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_IDENTIFIER;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.DEPLOYMENT_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.GLOBAL_ACCOUNT_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.IS_ARTIFACT_PRIMARY;
import static io.harness.telemetry.helpers.InstrumentationConstants.IS_SERVICE_V2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ArtifactDeploymentInstrumentationHelper extends InstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;

  private CompletableFuture<Void> publishArtifactInfo(ArtifactConfig artifactConfig, String accountId, String orgId,
      String projectId, String eventName, String deploymentType, Boolean isServiceV2) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ARTIFACT_ACCOUNT, accountId);
    eventPropertiesMap.put(ARTIFACT_ORG, orgId);
    eventPropertiesMap.put(ARTIFACT_TYPE, artifactConfig.getSourceType());
    eventPropertiesMap.put(ARTIFACT_IDENTIFIER, artifactConfig.getIdentifier());
    eventPropertiesMap.put(ARTIFACT_PROJECT, projectId);
    eventPropertiesMap.put(IS_ARTIFACT_PRIMARY, artifactConfig.isPrimaryArtifact());
    eventPropertiesMap.put(DEPLOYMENT_TYPE, deploymentType);
    eventPropertiesMap.put(IS_SERVICE_V2, isServiceV2);
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  public CompletableFuture<Void> sendArtifactDeploymentEvent(ArtifactConfig artifactConfig, String accountId,
      String orgId, String projectId, String deploymentType, Boolean isServiceV2) {
    return publishArtifactInfo(
        artifactConfig, accountId, orgId, projectId, "artifact_deployment", deploymentType, isServiceV2);
  }

  private CompletableFuture<Void> sendEvent(
      String eventName, String accountId, HashMap<String, Object> eventPropertiesMap) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent(eventName, userId, accountId, eventPropertiesMap,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info("There is no account found for account ID = " + accountId + "!. Cannot send " + eventName + " event.");
      }
    } catch (Exception e) {
      log.error(eventName + " event failed for accountID= " + accountId, e);
    }
    return null;
  }
}
