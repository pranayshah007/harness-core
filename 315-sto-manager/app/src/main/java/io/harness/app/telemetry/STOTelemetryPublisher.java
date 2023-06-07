/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.telemetry;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import static java.lang.Math.ceil;
import static java.lang.Math.max;

import io.harness.annotations.dev.OwnedBy;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(STO)
public class STOTelemetryPublisher {
  @Inject TelemetryReporter telemetryReporter;
  @Inject private STOServiceUtils stoServiceUtils;
  String LICENSE_USAGE = "sto_license_usage";
  String ACCOUNT_DEPLOY_TYPE = "account_deploy_type";
  private static final String ACCOUNT = "Account";
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";
  private static final Double SCAN_COUNT_PER_DEVELOPER = 100.0;

  public void recordTelemetry() {
    log.info("STOTelemetryPublisher recordTelemetry execute started.");
    try {
      long timestamp = Instant.now().toEpochMilli();
      final Gson gson = new Gson();
      Type type = new TypeToken<List<STOUsage>>() {}.getType();
      List<STOUsage> allUsage = gson.fromJson(stoServiceUtils.getUsageAllAccounts(timestamp), type);
      log.info("Size of the account list is {} ", allUsage.size());

      for (STOUsage usage : allUsage) {
        HashMap<String, Object> map = new HashMap<>();
        map.put(GROUP_TYPE, ACCOUNT);
        map.put(GROUP_ID, usage.accountId);
        map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));
        map.put(LICENSE_USAGE, max((int) ceil(usage.scanCount / SCAN_COUNT_PER_DEVELOPER), usage.developerCount));
        telemetryReporter.sendGroupEvent(usage.accountId, null, map, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(false).build());
        log.info("Scheduled STOTelemetryPublisher event sent for account {}", usage.accountId);
      }
    } catch (Exception e) {
      log.error("STOTelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("STOTelemetryPublisher recordTelemetry execute finished.");
    }
  }
}

class STOUsage {
  String accountId;
  int developerCount;
  int scanCount;
}
