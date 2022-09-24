/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInsightsBarDetails;
import io.harness.delegate.beans.DelegateInsightsDetails;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateInsightsService;
import io.harness.service.intfc.PerpetualTaskStateObserver;

import software.wings.beans.DelegateInsightsSummary;
import software.wings.beans.DelegateInsightsSummary.DelegateInsightsSummaryKeys;
import software.wings.beans.DelegateTaskUsageInsights;
import software.wings.beans.DelegateTaskUsageInsights.DelegateTaskUsageInsightsKeys;
import software.wings.beans.DelegateTaskUsageInsightsEventType;
import software.wings.service.impl.DelegateTaskStatusObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateInsightsServiceImpl
    implements DelegateInsightsService, DelegateTaskStatusObserver, PerpetualTaskStateObserver {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;

  @Override
  public void onTaskAssigned(String accountId, String taskId, String delegateId, long taskTimeout) {
    String delegateGroupId = obtainDelegateGroupId(accountId, delegateId);

    DelegateTaskUsageInsights delegateTaskUsageInsightsCreateEvent = createDelegateTaskUsageInsightsEvent(
        accountId, taskId, delegateId, delegateGroupId, 0, DelegateTaskUsageInsightsEventType.STARTED);

    DelegateTaskUsageInsights delegateTaskUsageInsightsUnknownEvent = createDelegateTaskUsageInsightsEvent(
        accountId, taskId, delegateId, delegateGroupId, taskTimeout, DelegateTaskUsageInsightsEventType.UNKNOWN);

    persistence.save(delegateTaskUsageInsightsCreateEvent);
    persistence.save(delegateTaskUsageInsightsUnknownEvent);
  }

  @Override
  public void onTaskCompleted(
      String accountId, String taskId, String delegateId, DelegateTaskUsageInsightsEventType eventType) {
    Query<DelegateTaskUsageInsights> filterQuery =
        persistence.createQuery(DelegateTaskUsageInsights.class)
            .filter(DelegateTaskUsageInsightsKeys.accountId, accountId)
            .filter(DelegateTaskUsageInsightsKeys.taskId, taskId)
            .filter(DelegateTaskUsageInsightsKeys.delegateId, delegateId)
            .filter(DelegateTaskUsageInsightsKeys.eventType, DelegateTaskUsageInsightsEventType.UNKNOWN);

    UpdateOperations<DelegateTaskUsageInsights> updateOperations =
        persistence.createUpdateOperations(DelegateTaskUsageInsights.class)
            .set(DelegateTaskUsageInsightsKeys.eventType, eventType)
            .set(DelegateTaskUsageInsightsKeys.timestamp, System.currentTimeMillis());

    persistence.findAndModify(filterQuery, updateOperations, HPersistence.returnNewOptions);
  }

  @Override
  public void onPerpetualTaskAssigned(String accountId, String taskId, String delegateId) {
    // Do nothing for now
  }

  private String obtainDelegateGroupId(String accountId, String delegateId) {
    Delegate delegate = delegateCache.get(accountId, delegateId, false);

    if (delegate != null && isNotBlank(delegate.getDelegateGroupId())) {
      return delegate.getDelegateGroupId();
    }

    return delegateId;
  }

  private DelegateTaskUsageInsights createDelegateTaskUsageInsightsEvent(String accountId, String taskId,
      String delegateId, String delegateGroupId, long taskTimeout, DelegateTaskUsageInsightsEventType eventType) {
    return DelegateTaskUsageInsights.builder()
        .accountId(accountId)
        .taskId(taskId)
        .eventType(eventType)
        .timestamp(System.currentTimeMillis() + taskTimeout)
        .delegateId(delegateId)
        .delegateGroupId(delegateGroupId)
        .build();
  }

  @Override
  public DelegateInsightsDetails retrieveDelegateInsightsDetails(
      String accountId, String delegateGroupId, long startTimestamp) {
    List<DelegateInsightsBarDetails> insightsBarDetails = new ArrayList<>();
    Map<Long, List<DelegateInsightsSummary>> delegateGroupInsights =
        persistence.createQuery(DelegateInsightsSummary.class)
            .filter(DelegateInsightsSummaryKeys.accountId, accountId)
            .filter(DelegateInsightsSummaryKeys.delegateGroupId, delegateGroupId)
            .field(DelegateInsightsSummaryKeys.periodStartTime)
            .greaterThan(startTimestamp)
            .asList()
            .stream()
            .collect(Collectors.groupingBy(DelegateInsightsSummary::getPeriodStartTime));

    for (Map.Entry<Long, List<DelegateInsightsSummary>> mapEntry : delegateGroupInsights.entrySet()) {
      DelegateInsightsBarDetails barInsights =
          DelegateInsightsBarDetails.builder().timeStamp(mapEntry.getKey()).build();
      mapEntry.getValue().forEach(
          event -> barInsights.getCounts().add(Pair.of(event.getInsightsType(), event.getCount())));

      insightsBarDetails.add(barInsights);
    }

    return DelegateInsightsDetails.builder().insights(insightsBarDetails).build();
  }
}
