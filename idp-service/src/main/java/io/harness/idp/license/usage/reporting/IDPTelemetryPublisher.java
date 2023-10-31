/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.license.usage.reporting;

import static io.harness.telemetry.Destination.ALL;

import io.harness.ModuleType;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.idp.license.usage.dto.IDPLicenseUsageDTO;
import io.harness.idp.license.usage.repositories.IDPTelemetrySentStatusRepository;
import io.harness.idp.license.usage.service.impl.IDPLicenseUsageImpl;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPTelemetryPublisher {
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final long A_DAY_MINUS_TEN_MINUTES = 85800000;
  private static final String GROUP_TYPE = "group_type";
  private static final String ACCOUNT = "Account";
  private static final String GROUP_ID = "group_id";
  private static final String COUNT_ACTIVE_DEVELOPERS = "idp_license_active_developers";

  @Inject AccountUtils accountUtils;
  @Inject IDPTelemetrySentStatusRepository idpTelemetrySentStatusRepository;
  @Inject private IDPLicenseUsageImpl idpLicenseUsage;
  @Inject private TelemetryReporter telemetryReporter;

  public void recordTelemetry() {
    log.info("IDPTelemetryPublisher recordTelemetry execute started.");
    try {
      List<String> accountIdList = accountUtils.getAllNGAccountIds();
      accountIdList.forEach(this::sendEvent);
    } catch (Exception e) {
      log.error("IDPTelemetryPublisher recordTelemetry execute failed. Error = {}", e.getMessage(), e);
    } finally {
      log.info("IDPTelemetryPublisher recordTelemetry execute finished.");
    }
  }

  private void sendEvent(String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)
          && idpTelemetrySentStatusRepository.updateTimestampIfOlderThan(
              accountId, System.currentTimeMillis() - A_DAY_MINUS_TEN_MINUTES, System.currentTimeMillis())) {
        HashMap<String, Object> map = new HashMap<>();
        map.put(GROUP_TYPE, ACCOUNT);
        map.put(GROUP_ID, accountId);

        IDPLicenseUsageDTO idpLicenseUsageDTO =
            idpLicenseUsage.getLicenseUsage(accountId, ModuleType.IDP, new Date().getTime(), null);
        map.put(COUNT_ACTIVE_DEVELOPERS, idpLicenseUsageDTO.getActiveDevelopers().getCount());

        telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(false).build());
      }
    } catch (Exception e) {
      log.error("Failed to send telemetry event in IDPTelemetryPublisher for account {} Error = {}", accountId,
          e.getMessage(), e);
      throw e;
    }
  }
}
