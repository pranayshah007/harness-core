/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.insights;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.DelegateServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInsightsType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.DelegateInsightsSummary;
import software.wings.beans.DelegateInsightsSummary.DelegateInsightsSummaryKeys;
import software.wings.beans.DelegateTaskUsageInsights;
import software.wings.beans.DelegateTaskUsageInsightsEventType;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(DEL)
public class DelegateInsightsSummaryJobTest extends DelegateServiceTestBase {
  private static final String TEST_DELEGATE_ID = generateUuid();
  private static final String TEST_DELEGATE_GROUP_ID = generateUuid();

  @Mock private DelegateCache delegateCache;
  @InjectMocks @Inject private DelegateInsightsSummaryJob delegateInsightsSummaryJob;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testJobTaskInsightsWithNoDatainDb() {
    String task1Id = generateUuid();
    String accountId = generateUuid();

    long assertTimestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20L);
    long timestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(120L);

    when(delegateCache.get(accountId, TEST_DELEGATE_ID, false))
        .thenReturn(Delegate.builder().delegateGroupId(TEST_DELEGATE_GROUP_ID).build());

    // This one should be skipped as the timestamp is too much in the past
    createDelegateTaskUsageInsightsEvent(accountId, task1Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.STARTED);

    delegateInsightsSummaryJob.run();

    List<DelegateInsightsSummary> delegateInsightsSummaries =
        persistence.createQuery(DelegateInsightsSummary.class)
            .filter(DelegateInsightsSummaryKeys.accountId, accountId)
            .filter(DelegateInsightsSummaryKeys.delegateGroupId, TEST_DELEGATE_GROUP_ID)
            .field(DelegateInsightsSummaryKeys.periodStartTime)
            .greaterThan(assertTimestamp)
            .asList();

    assertThat(delegateInsightsSummaries).hasSize(0);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testJobTaskInsights() {
    String accountId = generateUuid();

    String task1Id = generateUuid();
    String task2Id = generateUuid();
    String task3Id = generateUuid();
    String task4Id = generateUuid();
    String task5Id = generateUuid();
    String task6Id = generateUuid();

    long assertTimestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20L);
    long timestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(8L);

    when(delegateCache.get(accountId, TEST_DELEGATE_ID, false))
        .thenReturn(Delegate.builder().delegateGroupId(TEST_DELEGATE_GROUP_ID).build());

    // This one should be counted as IN_PROGRESS
    createDelegateTaskUsageInsightsEvent(accountId, task1Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.STARTED);

    // This one should be counted as FAILED (EXPIRED)
    createDelegateTaskUsageInsightsEvent(accountId, task2Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.STARTED);
    createDelegateTaskUsageInsightsEvent(accountId, task2Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.UNKNOWN);

    // This one should be counted as FAILED
    createDelegateTaskUsageInsightsEvent(accountId, task3Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.FAILED);

    // This one should be counted as SUCCESSFUL
    createDelegateTaskUsageInsightsEvent(accountId, task4Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.SUCCEEDED);

    // This one should be counted as FAILED
    createDelegateTaskUsageInsightsEvent(accountId, task5Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.STARTED);
    createDelegateTaskUsageInsightsEvent(accountId, task5Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.FAILED);

    // This one should be counted as SUCCESSFUL
    createDelegateTaskUsageInsightsEvent(accountId, task6Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.STARTED);
    createDelegateTaskUsageInsightsEvent(accountId, task6Id, TEST_DELEGATE_ID, TEST_DELEGATE_GROUP_ID, timestamp,
        DelegateTaskUsageInsightsEventType.SUCCEEDED);

    delegateInsightsSummaryJob.run();

    List<DelegateInsightsSummary> delegateInsightsSummaries =
        persistence.createQuery(DelegateInsightsSummary.class)
            .filter(DelegateInsightsSummaryKeys.accountId, accountId)
            .filter(DelegateInsightsSummaryKeys.delegateGroupId, TEST_DELEGATE_GROUP_ID)
            .field(DelegateInsightsSummaryKeys.periodStartTime)
            .greaterThan(assertTimestamp)
            .asList();

    assertThat(delegateInsightsSummaries).hasSize(3);
    assertThat(delegateInsightsSummaries)
        .anyMatch(summary -> summary.getInsightsType() == DelegateInsightsType.IN_PROGRESS);
    assertThat(delegateInsightsSummaries).anyMatch(summary -> summary.getInsightsType() == DelegateInsightsType.FAILED);
    assertThat(delegateInsightsSummaries)
        .anyMatch(summary -> summary.getInsightsType() == DelegateInsightsType.SUCCESSFUL);
    for (DelegateInsightsSummary summary : delegateInsightsSummaries) {
      if (summary.getInsightsType() == DelegateInsightsType.IN_PROGRESS) {
        assertThat(summary.getCount()).isEqualTo(1);
      } else if (summary.getInsightsType() == DelegateInsightsType.FAILED) {
        assertThat(summary.getCount()).isEqualTo(3);
      } else if (summary.getInsightsType() == DelegateInsightsType.SUCCESSFUL) {
        assertThat(summary.getCount()).isEqualTo(2);
      }
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testJobPerpetualTaskInsights() {
    String accountId = generateUuid();
    String delegate1Id = generateUuid();
    String delegate2Id = generateUuid();
    String delegateGroup1Id = generateUuid();
    String delegateGroup2Id = generateUuid();

    long assertTimestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20L);

    when(delegateCache.get(accountId, delegate1Id, false))
        .thenReturn(Delegate.builder().delegateGroupId(delegateGroup1Id).build());
    when(delegateCache.get(accountId, delegate2Id, false))
        .thenReturn(Delegate.builder().delegateGroupId(delegateGroup2Id).build());

    createPerpetualTaskRecord(accountId, delegate1Id);
    createPerpetualTaskRecord(accountId, delegate1Id);
    createPerpetualTaskRecord(accountId, delegate2Id);

    delegateInsightsSummaryJob.run();

    List<DelegateInsightsSummary> delegateInsightsSummaries =
        persistence.createQuery(DelegateInsightsSummary.class)
            .filter(DelegateInsightsSummaryKeys.accountId, accountId)
            .field(DelegateInsightsSummaryKeys.periodStartTime)
            .greaterThan(assertTimestamp)
            .asList();

    assertThat(delegateInsightsSummaries).hasSize(2);
    assertThat(delegateInsightsSummaries)
        .allMatch(summary -> summary.getInsightsType() == DelegateInsightsType.PERPETUAL_TASK_ASSIGNED);

    for (DelegateInsightsSummary summary : delegateInsightsSummaries) {
      if (delegateGroup1Id.equals(summary.getDelegateGroupId())) {
        assertThat(summary.getCount()).isEqualTo(2);
      } else if (delegateGroup2Id.equals(summary.getDelegateGroupId())) {
        assertThat(summary.getCount()).isEqualTo(1);
      }
    }
  }

  private void createPerpetualTaskRecord(String accountId, String delegateId) {
    persistence.save(PerpetualTaskRecord.builder().accountId(accountId).delegateId(delegateId).build());
  }

  private void createDelegateTaskUsageInsightsEvent(String accountId, String taskId, String delegateId,
      String delegateGroupId, long timestamp, DelegateTaskUsageInsightsEventType eventType) {
    persistence.save(DelegateTaskUsageInsights.builder()
                         .accountId(accountId)
                         .taskId(taskId)
                         .eventType(eventType)
                         .timestamp(timestamp)
                         .delegateId(delegateId)
                         .delegateGroupId(delegateGroupId)
                         .build());
  }
}
