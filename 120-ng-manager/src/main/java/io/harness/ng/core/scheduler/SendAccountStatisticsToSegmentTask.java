/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.TelemetryConstants;
import io.harness.account.AccountClient;
import io.harness.beans.PageResponse;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.metrics.AccountStatisticsTracker;
import io.harness.ng.core.entities.metrics.AccountStatisticsTracker.AccountStatisticsTrackerKeys;
import io.harness.ng.core.services.ProjectService;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.user.custom.UserMembershipRepositoryCustom;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class SendAccountStatisticsToSegmentTask implements Runnable {
  @Inject AccountClient accountClient;
  @Inject private ProjectService projectService;
  @Inject private UserMembershipRepositoryCustom userMembershipRepositoryCustom;
  @Inject MongoTemplate mongoTemplate;
  @Inject private TelemetryReporter telemetryReporter;

  public void run() {
    if (shouldPublishMetrics()) {
      publishMetrics();
    }
  }

  private boolean shouldPublishMetrics() {
    return mongoTemplate.findOne(getQueryForEntryInLast24Hours(), AccountStatisticsTracker.class) == null;
  }

  public void publishMetrics() {
    int pageIndex = 0;
    int pageSize = 1000;
    Instant endTime = Instant.now();
    Instant startTime = endTime.minus(24, ChronoUnit.HOURS);

    AccountStatisticsTracker accountAuditMetricsTracker =
        AccountStatisticsTracker.builder().startTime(startTime).endTime(endTime).lastPublishTime(Instant.now()).build();
    mongoTemplate.save(accountAuditMetricsTracker);

    try (ResponseTimeRecorder ignore1 =
             new ResponseTimeRecorder("Account statistics sent for project and users in an account. startTime:"
                 + startTime + "endTime:" + endTime)) {
      do {
        List<String> accountIds = new ArrayList<>();
        PageResponse<AccountDTO> pageResponse =
            CGRestUtils.getResponse(accountClient.listAccounts(pageIndex, pageSize));
        if (pageResponse.size() == 0) {
          break;
        }
        pageIndex++;
        accountIds.addAll(pageResponse.getResponse()
                              .stream()
                              .filter(AccountDTO -> AccountDTO.isNextGenEnabled() == true)
                              .map(AccountDTO::getIdentifier)
                              .collect(Collectors.toList()));

        Map<String, Integer> projectCounts = projectService.getProjectsCountPerAccount(accountIds);
        Map<String, Integer> usersCounts = userMembershipRepositoryCustom.getUserCountPerAccount(accountIds);

        for (String accountIdentifier : accountIds) {
          int projectCount = projectCounts.getOrDefault(accountIdentifier, 0);
          int userCount = usersCounts.getOrDefault(accountIdentifier, 0);
          sendAccountStatisticsMetricsEventToSegment(accountIdentifier, projectCount, userCount);
        }

      } while (true);
    }
  }

  Query getQueryForEntryInLast24Hours() {
    return new Query(
        Criteria.where(AccountStatisticsTrackerKeys.lastPublishTime).gte(Instant.now().minus(24, ChronoUnit.HOURS)));
  }

  private void sendAccountStatisticsMetricsEventToSegment(String accountIdentifier, int projectCount, int userCount) {
    HashMap<String, Object> identifyEventProperties = new HashMap<>();
    identifyEventProperties.put("accountId", accountIdentifier);

    telemetryReporter.sendIdentifyEvent(
        accountIdentifier, identifyEventProperties, Collections.singletonMap(AMPLITUDE, true));

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("projectCount", projectCount);
    properties.put("userCount", userCount);

    telemetryReporter.sendTrackEvent("account_statistics_metrics",
        TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX + accountIdentifier, accountIdentifier, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
  }
}
