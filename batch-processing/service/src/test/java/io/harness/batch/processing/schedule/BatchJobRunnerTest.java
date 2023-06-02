/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.service.intfc.BatchJobScheduledDataService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BatchJobRunnerTest extends CategoryTest {
  @InjectMocks private BatchJobRunner batchJobRunner;
  @Mock private BatchJobScheduledDataService batchJobScheduledDataService;
  @Mock private CustomBillingMetaDataService customBillingMetaDataService;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final Instant NOW = Instant.now();
  private final Instant START_TIME = NOW.minus(1, ChronoUnit.HOURS);
  private final Instant END_TIME = NOW;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueIfAllDependentJobFinished() {
    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(ACCOUNT_ID, BatchJobType.K8S_UTILIZATION))
        .thenReturn(NOW.minus(2, ChronoUnit.DAYS));
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, NOW.minus(3, ChronoUnit.DAYS), batchJobType.getDependentBatchJobs());
    assertThat(jobFinished).isTrue();
  }

  // hourly <- daily; hourly job is remaining for last 1 hour (24th) of the day
  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testDailyJobDependencyOnHourlyJob() {
    // last K8S_UTILIZATION completed for [2022-01-25T22:00:00.000Z, 2022-01-25T23:00:00.000Z]
    Instant lastDependentJobEndAt = Instant.parse("2022-01-25T23:00:00.000Z");

    // INSTANCE_BILLING waiting to run from [2022-01-25T00:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant instanceBillingEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(
             eq(ACCOUNT_ID), eq(BatchJobType.K8S_UTILIZATION)))
        .thenReturn(lastDependentJobEndAt);
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, instanceBillingEndAt, batchJobType.getDependentBatchJobs());
    assertThat(jobFinished).isFalse();
  }

  // daily <- daily; all dependent jobs finished
  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testDailyJobDependencyOnDailyJob() {
    // last INSTANCE_BILLING completed for [2022-01-25T00:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant lastDependentJobEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    // ACTUAL_IDLE_COST_BILLING waiting to run from [2022-01-25T00:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant instanceBillingEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(
             eq(ACCOUNT_ID), eq(BatchJobType.INSTANCE_BILLING)))
        .thenReturn(lastDependentJobEndAt);

    BatchJobType batchJobType = BatchJobType.ACTUAL_IDLE_COST_BILLING;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, instanceBillingEndAt, batchJobType.getDependentBatchJobs());

    assertThat(jobFinished).isTrue();
  }

  // daily <- daily; dependent jobs not finished
  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testDailyJobDependencyOnDailyJobNotFinished() {
    // last INSTANCE_BILLING completed for [2022-01-24T00:00:00.000Z, 2022-01-25T00:00:00.000Z]
    Instant lastDependentJobEndAt = Instant.parse("2022-01-25T00:00:00.000Z");

    // ACTUAL_IDLE_COST_BILLING waiting to run from [2022-01-25T00:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant instanceBillingEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(
             eq(ACCOUNT_ID), eq(BatchJobType.INSTANCE_BILLING)))
        .thenReturn(lastDependentJobEndAt);

    BatchJobType batchJobType = BatchJobType.ACTUAL_IDLE_COST_BILLING;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, instanceBillingEndAt, batchJobType.getDependentBatchJobs());

    assertThat(jobFinished).isFalse();
  }

  // hourly <- hourly; all dependent jobs finished
  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testHourlyJobDependencyOnHourlyJobFinished() {
    // last K8S_EVENT completed for [2022-01-25T23:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant lastDependentJobEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    // K8S_UTILIZATION waiting to run from [2022-01-25T23:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant instanceBillingEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(
             eq(ACCOUNT_ID), eq(BatchJobType.K8S_EVENT)))
        .thenReturn(lastDependentJobEndAt);

    BatchJobType batchJobType = BatchJobType.K8S_UTILIZATION;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, instanceBillingEndAt, batchJobType.getDependentBatchJobs());

    assertThat(jobFinished).isTrue();
  }

  // hourly <- hourly; all dependent jobs not finished
  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testHourlyJobDependencyOnHourlyJobNotFinished() {
    // last K8S_EVENT completed for [2022-01-26T23:00:00.000Z, 2022-01-26T00:00:00.000Z]
    Instant lastDependentJobEndAt = Instant.parse("2022-01-26T00:00:00.000Z");

    // K8S_UTILIZATION waiting to run from [2022-01-26T00:00:00.000Z, 2022-01-26T01:00:00.000Z]
    Instant instanceBillingEndAt = Instant.parse("2022-01-26T01:00:00.000Z");

    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(
             eq(ACCOUNT_ID), eq(BatchJobType.K8S_EVENT)))
        .thenReturn(lastDependentJobEndAt);

    BatchJobType batchJobType = BatchJobType.K8S_UTILIZATION;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, instanceBillingEndAt, batchJobType.getDependentBatchJobs());

    assertThat(jobFinished).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnFalseIfAllDependentJobNotFinished() {
    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(ACCOUNT_ID, BatchJobType.K8S_UTILIZATION))
        .thenReturn(NOW.minus(4, ChronoUnit.DAYS));
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished = batchJobRunner.checkDependentJobFinished(
        ACCOUNT_ID, NOW.minus(3, ChronoUnit.DAYS), batchJobType.getDependentBatchJobs());
    assertThat(jobFinished).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnFalseIfOutOfClusterDependentJobsNotFinished() {
    when(customBillingMetaDataService.checkPipelineJobFinished(ACCOUNT_ID, START_TIME, END_TIME))
        .thenReturn(Boolean.FALSE);
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished = batchJobRunner.checkOutOfClusterDependentJobs(ACCOUNT_ID, START_TIME, END_TIME, batchJobType);
    assertThat(jobFinished).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueIfOutOfClusterDependentJobFinished() {
    when(customBillingMetaDataService.checkPipelineJobFinished(ACCOUNT_ID, START_TIME, END_TIME))
        .thenReturn(Boolean.TRUE);
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished = batchJobRunner.checkOutOfClusterDependentJobs(ACCOUNT_ID, START_TIME, END_TIME, batchJobType);
    assertThat(jobFinished).isTrue();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueIfOutOfClusterDependentJobsIsNotApplicable() {
    BatchJobType batchJobType = BatchJobType.K8S_EVENT;
    boolean jobFinished = batchJobRunner.checkOutOfClusterDependentJobs(ACCOUNT_ID, START_TIME, END_TIME, batchJobType);
    assertThat(jobFinished).isTrue();
  }

  /**
   * hourly <- daily; daily job dependent on hourly job
   */
  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnTrueIfDependentJobFinished() {
    when(batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(any(), any(BatchJobType.class)))
        .thenReturn(NOW);
    BatchJobType batchJobType = BatchJobType.INSTANCE_BILLING;
    boolean jobFinished =
        batchJobRunner.checkDependentJobFinished(ACCOUNT_ID, NOW, batchJobType.getDependentBatchJobs());
    assertThat(jobFinished).isTrue();
  }
}
