/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.license.CdLicenseUsageCgClient;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.cdlicense.bean.CgServiceUsage;
import io.harness.data.structure.EmptyPredicate;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.RestClientUtils;
import io.harness.repositories.telemetry.CdTelemetryStatusRepository;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@OwnedBy(CDP)
public class CdTelemetryPublisher {
  @Inject private LicenseUsageInterface licenseUsageInterface;
  @Inject private TelemetryReporter telemetryReporter;
  @Inject private AccountClient accountClient;
  @Inject private CdLicenseUsageCgClient licenseUsageCgClient;
  @Inject private CdTelemetryStatusRepository cdTelemetryStatusRepository;

  private static final String COUNT_ACTIVE_SERVICES = "cd_license_services_used";
  private static final String COUNT_ACTIVE_SERVICE_INSTANCES = "cd_license_service_instances_used";
  private static final String ACCOUNT_DEPLOY_TYPE = "account_deploy_type";
  private static final String HARNESS_PROD_CLUSTER_ID = "harness_prod_cluster_id";
  private static final String CG_COUNT_ACTIVE_SERVICES = "cd_license_cg_services_used";
  private static final String CG_COUNT_ACTIVE_SERVICE_INSTANCES = "cd_license_cg_service_instances_used";
  // Locking for a bit less than one day. It's ok to send a bit more than less considering downtime/etc
  //(24*60*60*1000)-(10*60*1000)
  private static final long A_DAY_MINUS_TEN_MINS_IN_MILLIS = 85800000;
  private static final String ACCOUNT = "Account";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";

  public void recordTelemetry() {
    log.info("CdTelemetryPublisher recordTelemetry execute started.");
    try {
      List<AccountDTO> accountDTOList = getAllAccounts();
      for (AccountDTO accountDTO : accountDTOList) {
        String accountId = accountDTO.getIdentifier();
        try {
          sendEvent(accountId);
        } catch (Exception e) {
          log.error("Failed to send telemetry event for account {}", accountId, e);
        }
      }
    } catch (Exception e) {
      log.error("CdTelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("CdTelemetryPublisher recordTelemetry execute finished.");
    }
  }

  private void sendEvent(String accountId) {
    if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)) {
      if (cdTelemetryStatusRepository.updateTimestampIfOlderThan(
              accountId, System.currentTimeMillis() - A_DAY_MINUS_TEN_MINS_IN_MILLIS, System.currentTimeMillis())) {
        CDLicenseUsageDTO cdLicenseUsage = (CDLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(accountId,
            ModuleType.CD, new Date().getTime(), CDUsageRequestParams.builder().cdLicenseType(SERVICES).build());
        HashMap<String, Object> map = new HashMap<>();
        map.put(GROUP_TYPE, ACCOUNT);
        map.put(GROUP_ID, accountId);
        map.put(COUNT_ACTIVE_SERVICES, cdLicenseUsage.getActiveServices().getCount());
        map.put(COUNT_ACTIVE_SERVICE_INSTANCES, cdLicenseUsage.getActiveServiceInstances().getCount());
        map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));

        CgActiveServicesUsageInfo cgLicenseUsage = getCgLicenseUsageInfo(accountId);
        long activeInstances = 0L;
        if (CollectionUtils.isNotEmpty(cgLicenseUsage.getActiveServiceUsage())) {
          activeInstances = cgLicenseUsage.getActiveServiceUsage()
                                .stream()
                                .map(CgServiceUsage::getInstanceCount)
                                .reduce(0L, Long::sum);
        }

        map.put(CG_COUNT_ACTIVE_SERVICES, cgLicenseUsage.getServicesConsumed());
        map.put(CG_COUNT_ACTIVE_SERVICE_INSTANCES, activeInstances);

        telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
            TelemetryOption.builder().sendForCommunity(true).build());
        log.info("Scheduled CdTelemetryPublisher event sent for account [{}], with values: [{}]", accountId, map);
      } else {
        log.info("Skipping already sent account {} in past 24 hours", accountId);
      }
    }
  }

  CgActiveServicesUsageInfo getCgLicenseUsageInfo(String accountId) {
    return RestClientUtils.getResponse(licenseUsageCgClient.getActiveServiceUsage(accountId));
  }

  List<AccountDTO> getAllAccounts() {
    return RestClientUtils.getResponse(accountClient.getAllAccounts());
  }
}
