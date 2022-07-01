package io.harness.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.RestClientUtils;
import io.harness.repositories.CITelemetryStatusRepository;

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
  @Inject AccountClient accountClient;
  @Inject CITelemetryStatusRepository ciTelemetryStatusRepository;
  String COUNT_ACTIVE_DEVELOPERS = "ci_license_developers_used";
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
      List<AccountDTO> accountDTOList = getAllAccounts();
      for (AccountDTO accountDTO : accountDTOList) {
        String accountId = accountDTO.getIdentifier();
        if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)) {
          if (ciTelemetryStatusRepository.updateTimestampIfOlderThan(
                  accountId, System.currentTimeMillis() - A_DAY_MINUS_TEN_MINS, System.currentTimeMillis())) {
            HashMap<String, Object> map = new HashMap<>();
            map.put(GROUP_TYPE, ACCOUNT);
            map.put(GROUP_ID, accountId);
            map.put(COUNT_ACTIVE_DEVELOPERS, ciOverviewDashboardService.getActiveCommitterCount(accountId));
            map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));
            telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
                TelemetryOption.builder().sendForCommunity(true).build());
            log.info("Scheduled CiTelemetryPublisher event sent! for account {}", accountId);
          } else {
            log.info("Skipping already sent account {} in past 24 hours", accountId);
          }
        }
      }
    } catch (Exception e) {
      log.error("CITelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("CITelemetryPublisher recordTelemetry execute finished.");
    }
  }

  List<AccountDTO> getAllAccounts() {
    return RestClientUtils.getResponse(accountClient.getAllAccounts());
  }
}
