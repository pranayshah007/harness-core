/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.repositories.CITelemetryStatusRepository;
import io.harness.repositories.ModuleLicenseRepository;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public class CiTelemetryPublisher {
  @Inject CIOverviewDashboardService ciOverviewDashboardService;
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountUtils accountUtils;
  @Inject CITelemetryStatusRepository ciTelemetryStatusRepository;
  @Inject ModuleLicenseRepository moduleLicenseRepository;

  String COUNT_ACTIVE_DEVELOPERS = "ci_license_developers_used";
  String COUNT_HOSTED_CREDITS_USED = "ci_credits_used";
  String ACCOUNT_DEPLOY_TYPE = "account_deploy_type";
  // Locking for a bit less than one day. It's ok to send a bit more than less considering downtime/etc
  static final long A_DAY_MINUS_TEN_MINS = 85800000;
  private static final String ACCOUNT = "Account";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";

  public void recordTelemetry() {
    log.info("CiTelemetryPublisher recordTelemetry execute started.");
    try {
      List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();
      log.info("Memory before telemetry is {} ", getMemoryUse());
      log.info("Size of the account list is {} ", accountIdentifiers.size());

      for (String accountId : accountIdentifiers) {
        if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)) {
          if (ciTelemetryStatusRepository.updateTimestampIfOlderThan(
                  accountId, System.currentTimeMillis() - A_DAY_MINUS_TEN_MINS, System.currentTimeMillis())) {
            HashMap<String, Object> map = new HashMap<>();
            map.put(GROUP_TYPE, ACCOUNT);
            map.put(GROUP_ID, accountId);
            map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));
            populateDeveloperCount(map, accountId);
            populateCreditUsage(map, accountId);

            telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
                TelemetryOption.builder().sendForCommunity(true).build());
            log.info("Scheduled CiTelemetryPublisher event sent! for account {}", accountId);

          } else {
            log.info("Skipping already sent account {} in past 24 hours", accountId);
          }
        }
      }
      log.info("Memory after telemetry is {} ", getMemoryUse());
    } catch (Exception e) {
      log.error("CITelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("CITelemetryPublisher recordTelemetry execute finished.");
    }
  }

  private void populateDeveloperCount(HashMap<String, Object> map, String accountId) {
    long developersCount = ciOverviewDashboardService.getActiveCommitterCount(accountId);
    if (developersCount >= 0) {
      map.put(COUNT_ACTIVE_DEVELOPERS, developersCount);
    } else {
      log.warn(String.format("Active developers count is %s for account id %s", developersCount, accountId));
      map.put(COUNT_ACTIVE_DEVELOPERS, 0);
    }
  }

  private void populateCreditUsage(HashMap<String, Object> map, String accountId) {
    long hostedCreditUsage = ciOverviewDashboardService.getHostedCreditUsage(accountId);
    if (hostedCreditUsage >= 0) {
      map.put(COUNT_HOSTED_CREDITS_USED, hostedCreditUsage);
    } else {
      log.warn(String.format("Hosted credit usage is %s for account id %s", hostedCreditUsage, accountId));
      map.put(COUNT_HOSTED_CREDITS_USED, 0);
    }
  }

  private long getMemoryUse() {
    long totalMemory = Runtime.getRuntime().totalMemory();
    long freeMemory = Runtime.getRuntime().freeMemory();
    return totalMemory - freeMemory;
  }
}
