/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.insights;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInsightsType;
import io.harness.insights.DelegateInsightsSummaryKey.DelegateInsightsSummaryKeyBuilder;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.DelegateInsightsSummary;
import software.wings.beans.DelegateInsightsSummary.DelegateInsightsSummaryKeys;
import software.wings.beans.DelegateTaskUsageInsights;
import software.wings.beans.DelegateTaskUsageInsights.DelegateTaskUsageInsightsKeys;
import software.wings.beans.DelegateTaskUsageInsightsEventType;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * This job will process all delegate task and perpetual task related delegate insights events and summarize them into
 * the ten minutes intervals of one hour, that will be consumed by the API hit by the UI for chart rendering.
 */
@OwnedBy(DEL)
@Slf4j
public class DelegateInsightsSummaryJob implements Runnable {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;

  @Override
  public void run() {
    try {
      log.debug("Starting delegate insights summary calculations.");
      processTaskInsights();
      processPerpetualTaskInsights();
      log.debug("Finished delegate insights summary calculations.");
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing delegate insights.", ex);
    }
  }

  private void processTaskInsights() {
    long currentTimestamp = System.currentTimeMillis();
    long startTimestamp = calculateTaskInsightsStartTimestamp();
    Set<String> accounts = persistence.createQuery(DelegateTaskUsageInsights.class, excludeAuthority)
                               .field(DelegateTaskUsageInsightsKeys.timestamp)
                               .greaterThanOrEq(startTimestamp)
                               .field(DelegateTaskUsageInsightsKeys.timestamp)
                               .lessThan(currentTimestamp)
                               .project(DelegateTaskUsageInsightsKeys.accountId, true)
                               .asList()
                               .stream()
                               .map(DelegateTaskUsageInsights::getAccountId)
                               .collect(Collectors.toSet());

    for (String accountId : accounts) {
      Map<DelegateInsightsSummaryKey, AtomicLong> accountInsightSummaries = new HashMap<>();

      // Query data and reset timestamps to the beginning of the 10 minutes period and group by those timestamps
      Map<Long, List<DelegateTaskUsageInsights>> timeIntervalData =
          persistence.createQuery(DelegateTaskUsageInsights.class)
              .filter(DelegateTaskUsageInsightsKeys.accountId, accountId)
              .field(DelegateTaskUsageInsightsKeys.timestamp)
              .greaterThanOrEq(startTimestamp)
              .field(DelegateTaskUsageInsightsKeys.timestamp)
              .lessThan(currentTimestamp)
              .asList()
              .stream()
              .map(taskInsightsEvent -> {
                long newTimestamp = resetTimestampToTheBeginningOfTheTimeInterval(taskInsightsEvent.getTimestamp());
                taskInsightsEvent.setTimestamp(newTimestamp);

                return taskInsightsEvent;
              })
              .collect(Collectors.groupingBy(DelegateTaskUsageInsights::getTimestamp));

      for (Map.Entry<Long, List<DelegateTaskUsageInsights>> timeIntervalEntry : timeIntervalData.entrySet()) {
        // Group records for one timestamp by the taskId
        Map<String, List<DelegateTaskUsageInsights>> taskData =
            timeIntervalEntry.getValue().stream().collect(Collectors.groupingBy(DelegateTaskUsageInsights::getTaskId));

        for (Map.Entry<String, List<DelegateTaskUsageInsights>> taskEntry : taskData.entrySet()) {
          // Create key for each of the events and update counter in map based on that key
          DelegateInsightsSummaryKeyBuilder summaryKeyBuilder =
              DelegateInsightsSummaryKey.builder()
                  .accountId(accountId)
                  .delegateGroupId(taskEntry.getValue().get(0).getDelegateGroupId())
                  .periodStartTime(timeIntervalEntry.getKey());
          if (taskEntry.getValue().stream().anyMatch(taskInsightsEvent
                  -> taskInsightsEvent.getEventType() == DelegateTaskUsageInsightsEventType.FAILED
                      || taskInsightsEvent.getEventType() == DelegateTaskUsageInsightsEventType.UNKNOWN)) {
            summaryKeyBuilder.insightsType(DelegateInsightsType.FAILED);
          } else if (taskEntry.getValue().stream().anyMatch(taskInsightsEvent
                         -> taskInsightsEvent.getEventType() == DelegateTaskUsageInsightsEventType.SUCCEEDED)) {
            summaryKeyBuilder.insightsType(DelegateInsightsType.SUCCESSFUL);
          } else {
            summaryKeyBuilder.insightsType(DelegateInsightsType.IN_PROGRESS);
          }

          // add/update map counters
          accountInsightSummaries.computeIfAbsent(summaryKeyBuilder.build(), key -> new AtomicLong()).incrementAndGet();
        }
      }

      // convert map to entities and upsert db and clear map to be used for next account
      log.debug("Upserting {} task insights summary records in db.", accountInsightSummaries.size());
      updateDbWithMapEntries(accountInsightSummaries);
      accountInsightSummaries.clear();
    }
  }

  private void processPerpetualTaskInsights() {
    long perpetualTaskTimestamp = calculatePerpetualTaskInsightsStartTimestamp();
    Set<String> accounts = persistence.createQuery(PerpetualTaskRecord.class, excludeAuthority)
                               .project(PerpetualTaskRecordKeys.accountId, true)
                               .asList()
                               .stream()
                               .map(PerpetualTaskRecord::getAccountId)
                               .collect(Collectors.toSet());

    for (String accountId : accounts) {
      Map<DelegateInsightsSummaryKey, AtomicLong> accountInsightSummaries = new HashMap<>();

      Map<String, Long> delegateGroupTasks =
          persistence.createQuery(PerpetualTaskRecord.class)
              .filter(PerpetualTaskRecordKeys.accountId, accountId)
              .field(PerpetualTaskRecordKeys.delegateId)
              .exists()
              .project(PerpetualTaskRecordKeys.delegateId, true)
              .asList()
              .stream()
              .map(perpetualTask -> {
                Delegate delegate = delegateCache.get(accountId, perpetualTask.getDelegateId(), false);
                return delegate != null && isNotBlank(delegate.getDelegateGroupId()) ? delegate.getDelegateGroupId()
                                                                                     : perpetualTask.getDelegateId();
              })
              .filter(Objects::nonNull)
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

      for (Map.Entry<String, Long> delegateGroupEntry : delegateGroupTasks.entrySet()) {
        // Create key for each of the events and update counter in map based on that key
        DelegateInsightsSummaryKeyBuilder summaryKeyBuilder =
            DelegateInsightsSummaryKey.builder()
                .accountId(accountId)
                .delegateGroupId(delegateGroupEntry.getKey())
                .periodStartTime(perpetualTaskTimestamp)
                .insightsType(DelegateInsightsType.PERPETUAL_TASK_ASSIGNED);

        // add/update map counters
        accountInsightSummaries.put(summaryKeyBuilder.build(), new AtomicLong(delegateGroupEntry.getValue()));
      }

      // convert map to entities and upsert db and clear map to be used for next account
      log.debug("Upserting {} perpetual task insights summary records in db.", accountInsightSummaries.size());
      updateDbWithMapEntries(accountInsightSummaries);
      accountInsightSummaries.clear();
    }
  }

  private void updateDbWithMapEntries(Map<DelegateInsightsSummaryKey, AtomicLong> accountInsightSummaries) {
    for (Map.Entry<DelegateInsightsSummaryKey, AtomicLong> accountSummaryData : accountInsightSummaries.entrySet()) {
      DelegateInsightsSummaryKey accountSummaryKey = accountSummaryData.getKey();
      Query<DelegateInsightsSummary> query =
          this.persistence.createQuery(DelegateInsightsSummary.class)
              .filter(DelegateInsightsSummaryKeys.accountId, accountSummaryKey.getAccountId())
              .filter(DelegateInsightsSummaryKeys.delegateGroupId, accountSummaryKey.getDelegateGroupId())
              .filter(DelegateInsightsSummaryKeys.periodStartTime, accountSummaryKey.getPeriodStartTime())
              .filter(DelegateInsightsSummaryKeys.insightsType, accountSummaryKey.getInsightsType());

      UpdateOperations<DelegateInsightsSummary> updateOperations =
          this.persistence.createUpdateOperations(DelegateInsightsSummary.class)
              .setOnInsert(DelegateInsightsSummaryKeys.uuid, generateUuid())
              .set(DelegateInsightsSummaryKeys.accountId, accountSummaryKey.getAccountId())
              .set(DelegateInsightsSummaryKeys.delegateGroupId, accountSummaryKey.getDelegateGroupId())
              .set(DelegateInsightsSummaryKeys.periodStartTime, accountSummaryKey.getPeriodStartTime())
              .set(DelegateInsightsSummaryKeys.insightsType, accountSummaryKey.getInsightsType())
              .set(DelegateInsightsSummaryKeys.count, accountSummaryData.getValue().get())
              .set(DelegateInsightsSummaryKeys.validUntil, Date.from(OffsetDateTime.now().plusMonths(1).toInstant()));

      persistence.upsert(query, updateOperations, HPersistence.upsertReturnNewOptions);
    }
  }

  /**
   * It updates the record with the new timestamp that was reset to the beginning of the 10 minutes period during
   * original hour. Conversion of Instant to UTC LocalDateTime was necessary because Instant does not support
   * MINUTE_OF_HOUR.
   *
   * E.g. If the time was 11:53:56, it will truncate everything after minutes and reset the minutes to the beginning of
   * the 10 minutes period and will give final value of 11:50:00.
   *
   * Start times could be H:00, H:10, H:20, H:30, H:40, H:50
   */
  private long resetTimestampToTheBeginningOfTheTimeInterval(long timestamp) {
    LocalDateTime initTimestamp =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp).truncatedTo(ChronoUnit.MINUTES), ZoneOffset.UTC);

    LocalDateTime finalTimestamp =
        initTimestamp.minus(initTimestamp.get(ChronoField.MINUTE_OF_HOUR) % 10, ChronoUnit.MINUTES);

    return finalTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Calculates start timestamp to be used in db query. It takes the current timestamp decreased for desired amount of
   * time and then resets this newly calculated timestamp to the beginning of the 10 minutes period during one hour.
   *
   * Start times could be H:00, H:10, H:20, H:30, H:40, H:50
   */
  private long calculateTaskInsightsStartTimestamp() {
    // We will take last 10 mins, since job is scheduled to run every 10 minutes and decrease that value to the 10
    // minutes start period. This will make sure that we fetch one complete 10 minutes period to calculate final
    // summaries and existing ones covering the partial 10 minutes period until the moment of the job execution. E.g.
    // Now=11:53, we take 11:43 and reset it to 11:40 to make sure we cover period from 11:40-11:50 and make final
    // summary entries. Period from 11:50 to 12:00 will be partially covered in this iteration and fully in the nex one.
    // Conversion of Instant to UTC LocalDateTime was necessary because Instant does not support MINUTE_OF_HOUR.

    LocalDateTime initTimestamp = LocalDateTime.ofInstant(
        Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES), ZoneOffset.UTC);

    LocalDateTime finalTimestamp =
        initTimestamp.minus(initTimestamp.get(ChronoField.MINUTE_OF_HOUR) % 10, ChronoUnit.MINUTES);

    return finalTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Calculates start timestamp to be used in db query. It takes the current timestamp and then resets this newly
   * calculated timestamp to the beginning of the 10 minutes period during one hour.
   *
   * Start times could be H:00, H:10, H:20, H:30, H:40, H:50
   */
  private long calculatePerpetualTaskInsightsStartTimestamp() {
    // E.g.
    // Now=11:53, we reset it to 11:50 to mark the beginning of the 10 minutes period. Perpetual tasks are long term
    // ones and not that frequently assigned, so if any new tasks are assigned in period between 11:53 and 12:00, they
    // will be counted for 10 minutes period from 12:00. Conversion of Instant to UTC LocalDateTime was necessary
    // because Instant does not support MINUTE_OF_HOUR.

    LocalDateTime initTimestamp =
        LocalDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MINUTES), ZoneOffset.UTC);

    LocalDateTime finalTimestamp =
        initTimestamp.minus(initTimestamp.get(ChronoField.MINUTE_OF_HOUR) % 10, ChronoUnit.MINUTES);

    return finalTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
  }
}
