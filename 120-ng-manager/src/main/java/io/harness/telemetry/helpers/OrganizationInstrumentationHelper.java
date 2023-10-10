/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.entities.Organization;
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
@OwnedBy(HarnessTeam.PL)
public class OrganizationInstrumentationHelper extends InstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  String ACCOUNT_ID = "account_id";
  String ORGANIZATION_ID = "organization_id";
  String ORGANIZATION_CREATION_TIME = "organization_creation_time";
  String ORGANIZATION_NAME = "organization_name";
  String ORGANIZATION_VERSION = "organization_version";
  String HARNESS_MANAGED = "harness_managed";
  String UNIQUE_ID = "uniqueId";

  public CompletableFuture sendOrganizationCreateEvent(Organization organization, String accountId, String uniqueId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> map = new HashMap<>();
        map.put(UNIQUE_ID, uniqueId);
        map.put(ACCOUNT_ID, organization.getAccountIdentifier());
        map.put(ORGANIZATION_ID, organization.getIdentifier());
        map.put(ORGANIZATION_CREATION_TIME, organization.getCreatedAt());
        map.put(ORGANIZATION_NAME, organization.getName());
        map.put(ORGANIZATION_VERSION, organization.getVersion());
        map.put(HARNESS_MANAGED, organization.getHarnessManaged());
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent("organization_creation_finished", userId, accountId, map,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info("There is no account found for account ID = " + accountId
            + "!. Cannot send Organization Creation Finished event.");
      }
    } catch (Exception e) {
      log.error("Organization creation event failed for accountID= " + accountId, e);
    }
    return null;
  }
  public CompletableFuture sendOrganizationDeleteEvent(Organization organization, String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> map = new HashMap<>();
        map.put(ACCOUNT_ID, organization.getAccountIdentifier());
        map.put(ORGANIZATION_ID, organization.getIdentifier());
        map.put(ORGANIZATION_CREATION_TIME, organization.getCreatedAt());
        map.put(ORGANIZATION_NAME, organization.getName());
        map.put(ORGANIZATION_VERSION, organization.getVersion());
        map.put(HARNESS_MANAGED, organization.getHarnessManaged());
        String userId = getUserId();
        return CompletableFuture.runAsync(
            ()
                -> telemetryReporter.sendTrackEvent("organization_deletion", userId, accountId, map,
                    ImmutableMap.<Destination, Boolean>builder()
                        .put(Destination.AMPLITUDE, true)
                        .put(Destination.ALL, false)
                        .build(),
                    Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build()));
      } else {
        log.info(
            "There is no account found for account ID = " + accountId + "!. Cannot send Organization Deletion event.");
      }
    } catch (Exception e) {
      log.error("Organization deletion event failed for accountID= " + accountId, e);
    }
    return null;
  }
}
