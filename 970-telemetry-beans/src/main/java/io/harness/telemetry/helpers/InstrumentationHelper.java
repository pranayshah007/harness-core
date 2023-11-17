/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.telemetry.helpers.InstrumentationConstants.GLOBAL_ACCOUNT_ID;

import io.harness.data.structure.EmptyPredicate;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
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
public class InstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;

  public String getUserId() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    String identity = null;
    if (principal != null) {
      switch (principal.getType()) {
        case USER:
          UserPrincipal userPrincipal = (UserPrincipal) principal;
          identity = userPrincipal.getEmail();
          break;
        case SERVICE_ACCOUNT:
          ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
          identity = serviceAccountPrincipal.getEmail();
          break;
        case API_KEY:
        case SERVICE:
          identity = principal.getName();
          break;
        default:
          log.warn("Unknown principal type from SecurityContextBuilder when reading identity");
      }
    }
    if (isEmpty(identity)) {
      log.debug("Failed to read identity from principal, use system user instead");
      identity = "system";
    }
    return identity;
  }

  public CompletableFuture<Void> sendEvent(
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
