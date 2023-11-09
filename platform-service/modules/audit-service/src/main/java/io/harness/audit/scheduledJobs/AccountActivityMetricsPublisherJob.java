/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.scheduledJobs;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.TelemetryConstants;
import io.harness.account.AccountClient;
import io.harness.audit.Action;
import io.harness.audit.api.AuditService;
import io.harness.audit.metrics.entities.AccountActivityMetricsTracker;
import io.harness.audit.metrics.entities.AccountActivityMetricsTracker.AccountActivityMetricsTrackerKeys;
import io.harness.audit.metrics.impl.AccountActivityMetricsServiceImpl;
import io.harness.beans.PageResponse;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
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

public class AccountActivityMetricsPublisherJob implements Runnable {
  @Inject AccountClient accountClient;
  @Inject AuditService auditService;
  @Inject private TelemetryReporter telemetryReporter;
  @Inject private AccountActivityMetricsServiceImpl projectAuditMetricsService;
  @Inject MongoTemplate mongoTemplate;

  public void run() {
    if (shouldPublishMetrics()) {
      publishMetrics();
    }
  }

  public void publishMetrics() {
    int pageIndex = 0;
    int pageSize = 100;
    Instant endTime = Instant.now();
    Instant startTime = endTime.minus(24, ChronoUnit.HOURS);
    AccountActivityMetricsTracker accountActivityMetricsTracker = AccountActivityMetricsTracker.builder()
                                                                      .startTime(startTime)
                                                                      .endTime(endTime)
                                                                      .lastPublishTime(Instant.now())
                                                                      .build();
    mongoTemplate.save(accountActivityMetricsTracker);
    try (ResponseTimeRecorder ignore1 =
             new ResponseTimeRecorder("Account Activity metrics sent for active projects and unique logins. startTime:"
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

        List<Action> actions = new ArrayList<>();
        actions.add(Action.LOGIN);
        actions.add(Action.LOGIN2FA);

        Map<String, Integer> uniqueProjectCountPerAccountId =
            auditService.getUniqueProjectCountPerAccountId(accountIds, startTime, endTime);
        Map<String, Integer> uniqueLoginCountPerAccountId =
            auditService.getUniqueActionCount(accountIds, actions, startTime, endTime);

        for (String accountIdentifier : accountIds) {
          int uniqueActiveProjects = uniqueProjectCountPerAccountId.getOrDefault(accountIdentifier, 0);
          int uniqueUserLogins = uniqueLoginCountPerAccountId.getOrDefault(accountIdentifier, 0);
          sendAccountActivityEventMetricsToSegment(accountIdentifier, uniqueActiveProjects, uniqueUserLogins);
          sendAccountActivityEventMetricsToPrometheus(accountIdentifier, uniqueActiveProjects, uniqueUserLogins);
        }
      } while (true);
    }
  }

  private Boolean shouldPublishMetrics() {
    return mongoTemplate.findOne(getQueryForEntryInLast24Hours(), AccountActivityMetricsTracker.class) == null;
  }

  Query getQueryForEntryInLast24Hours() {
    return new Query(Criteria.where(AccountActivityMetricsTrackerKeys.lastPublishTime)
                         .gte(Instant.now().minus(24, ChronoUnit.HOURS)));
  }

  private void sendAccountActivityEventMetricsToSegment(
      String accountIdentifier, int uniqueActiveProjects, int uniqueUserLogins) {
    HashMap<String, Object> identifyEventProperties = new HashMap<>();
    identifyEventProperties.put("accountId", accountIdentifier);

    telemetryReporter.sendIdentifyEvent(
        accountIdentifier, identifyEventProperties, Collections.singletonMap(AMPLITUDE, true));

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("activeProjects", uniqueActiveProjects);
    properties.put("uniqueLogins", uniqueUserLogins);

    telemetryReporter.sendTrackEvent("account_activity_metrics",
        TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX + accountIdentifier, accountIdentifier, properties,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
  }

  private void sendAccountActivityEventMetricsToPrometheus(
      String accountIdentifier, int activeProjects, int uniqueLogins) {
    projectAuditMetricsService.recordAccountActivityMetric(accountIdentifier, activeProjects, uniqueLogins);
  }
}
