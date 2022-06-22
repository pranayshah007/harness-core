package io.harness.telemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.RestClientUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.telemetry.Destination.ALL;

@Slf4j
@OwnedBy(CI)
public class CiTelemetryPublisher {
    @Inject
    CIOverviewDashboardService ciOverviewDashboardService;
    @Inject TelemetryReporter telemetryReporter;
    @Inject
    AccountClient accountClient;
    String COUNT_ACTIVE_DEVELOPERS = "ci_license_developers_used";
    private static final String ACCOUNT = "Account";
    public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

    public void recordTelemetry() {
        log.info("CiTelemetryPublisher recordTelemetry execute started.");
        try {
            Long MILLISECONDS_IN_A_DAY = 86400000L;

            String accountId = getAccountId();
            if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
                ciOverviewDashboardService.getActiveCommitterCount(accountId);
                HashMap<String, Object> map = new HashMap<>();
                map.put("group_type", ACCOUNT);
                map.put("group_id", accountId);
                map.put(COUNT_ACTIVE_DEVELOPERS, ciOverviewDashboardService.getActiveCommitterCount(accountId));
                telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
                        TelemetryOption.builder().sendForCommunity(true).build());
                log.info("Scheduled CiTelemetryPublisher event sent!");
            } else {
                log.info("There is no Account found!. Can not send scheduled CiTelemetryPublisher event.");
            }
        } catch (Exception e) {
            log.error("CITelemetryPublisher recordTelemetry execute failed.", e);
        } finally {
            log.info("CITelemetryPublisher recordTelemetry execute finished.");
        }
    }

    @VisibleForTesting
    String getAccountId() {
        List<AccountDTO> accountDTOList = RestClientUtils.getResponse(accountClient.getAllAccounts());
        String accountId = accountDTOList.get(0).getIdentifier();
        if (accountDTOList.size() > 1 && accountId.equals(GLOBAL_ACCOUNT_ID)) {
            accountId = accountDTOList.get(1).getIdentifier();
        }
        return accountId;
    }
}
