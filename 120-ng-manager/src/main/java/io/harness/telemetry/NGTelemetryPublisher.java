/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.telemetry;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.AccountClient;
import io.harness.cdng.pipeline.helpers.CDPipelineInstrumentationHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class NGTelemetryPublisher {
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final long MILLISECONDS_IN_A_DAY = 86400000L;
  private static final long MILLISECONDS_IN_A_MONTH = 2592000000L;
  @Inject TelemetryReporter telemetryReporter;
  @Inject CDPipelineInstrumentationHelper cdPipelineInstrumentationHelper;
  @Inject InstanceService instanceService;
  @Inject AccountClient accountClient;

  String TOTAL_DISTINCT_ACTIVE_SERVICES_IN_A_DAY = "total_distinct_active_services_in_a_day";
  String TOTAL_DISTINCT_ACTIVE_SERVICES_IN_A_MONTH = "total_distinct_active_services_in_a_month";
  String TOTAL_NUMBER_OF_SERVICE_INSTANCES_IN_A_DAY = "total_number_of_service_instances_in_a_day";
  String TOTAL_NUMBER_OF_SERVICE_INSTANCES_IN_A_MONTH = "total_number_of_service_instances_in_a_month";

  public void recordTelemetry() {
    log.info("NGTelemetryPublisher recordTelemetry execute started.");
    try {
      String accountId = getAccountId();
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        long currentTimeMillis = System.currentTimeMillis();
        long totalNumberOfServiceInstancesInAMonth =
            cdPipelineInstrumentationHelper.getCountOfServiceInstancesDeployedInInterval(
                accountId, currentTimeMillis - MILLISECONDS_IN_A_MONTH, currentTimeMillis);
        long totalDistinctActiveServicesInAMonth =
            cdPipelineInstrumentationHelper.getCountOfDistinctActiveServicesDeployedInInterval(
                accountId, currentTimeMillis - MILLISECONDS_IN_A_MONTH, currentTimeMillis);

        long totalNumberOfServiceInstancesInADay =
            cdPipelineInstrumentationHelper.getCountOfServiceInstancesDeployedInInterval(
                accountId, currentTimeMillis - MILLISECONDS_IN_A_DAY, currentTimeMillis);
        long totalDistinctActiveServicesInADay =
            cdPipelineInstrumentationHelper.getCountOfDistinctActiveServicesDeployedInInterval(
                accountId, currentTimeMillis - MILLISECONDS_IN_A_DAY, currentTimeMillis);

        HashMap<String, Object> map = new HashMap<>();
        map.put("group_type", "Account");
        map.put("group_id", accountId);
        map.put(TOTAL_DISTINCT_ACTIVE_SERVICES_IN_A_DAY, totalDistinctActiveServicesInADay);
        map.put(TOTAL_DISTINCT_ACTIVE_SERVICES_IN_A_MONTH, totalDistinctActiveServicesInAMonth);
        map.put(TOTAL_NUMBER_OF_SERVICE_INSTANCES_IN_A_DAY, totalNumberOfServiceInstancesInADay);
        map.put(TOTAL_NUMBER_OF_SERVICE_INSTANCES_IN_A_MONTH, totalNumberOfServiceInstancesInAMonth);
        telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(AMPLITUDE, true),
            TelemetryOption.builder().sendForCommunity(true).build());
        log.info("Scheduled NGTelemetryPublisher event sent!");
      } else {
        log.info("There is no Account found!. Can not send scheduled NGTelemetryPublisher event.");
      }

    } catch (Exception e) {
      log.error("NGTelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("NGTelemetryPublisher recordTelemetry execute finished.");
    }
  }

  private String getAccountId() {
    List<AccountDTO> accountDTOList = CGRestUtils.getResponse(accountClient.listAccounts(0, 2)).getResponse();
    String accountId = accountDTOList.get(0).getIdentifier();
    if (accountDTOList.size() > 1 && accountId.equals(GLOBAL_ACCOUNT_ID)) {
      accountId = accountDTOList.get(1).getIdentifier();
    }
    return accountId;
  }
}
