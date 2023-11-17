/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.environment.beans.Environment;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static io.harness.gitsync.beans.StoreType.INLINE;
import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.ENV_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.ENV_NAME;
import static io.harness.telemetry.helpers.InstrumentationConstants.ENV_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.STORE_TYPE;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentInstrumentationHelper extends InstrumentationHelper {

  private CompletableFuture<Void> publishEnvironmentInfo(
      Environment environment, String eventName) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, environment.getAccountId());
    eventPropertiesMap.put(ORG, environment.getOrgIdentifier());
    eventPropertiesMap.put(ENV_TYPE, environment.getType());
    eventPropertiesMap.put(ENV_ID, environment.getIdentifier());
    eventPropertiesMap.put(PROJECT, environment.getProjectIdentifier());
    eventPropertiesMap.put(ENV_NAME, environment.getName());
    eventPropertiesMap.put(STORE_TYPE, environment.getStoreType() != null ? environment.getStoreType() : INLINE);
    return sendEvent(eventName, environment.getAccountId(), eventPropertiesMap);
  }
  public CompletableFuture<Void> sendEnvironmentEvent(
      Environment environment) {
    return publishEnvironmentInfo(environment, "environment");
  }
}
