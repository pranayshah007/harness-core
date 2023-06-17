/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer;

import static io.harness.rule.OwnerRule.UTSAV;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

@RunWith(MockitoJUnitRunner.class)
public class InstanceBillingAggregationDataTaskletTest extends BatchProcessingTestBase {
  @InjectMocks private InstanceBillingAggregationDataTasklet instanceBillingAggregationDataTasklet;
  @Mock private BillingDataServiceImpl billingDataService;

  @Mock private ChunkContext chunkContext;
  @Mock private StepContext stepContext;
  @Mock private StepExecution stepExecution;
  @Mock private JobParameters parameters;

  private static final String ACCOUNT_ID = "account_id";

  private final Instant END_INSTANT = Instant.now();
  private final Instant START_INSTANT = END_INSTANT.minus(1, ChronoUnit.HOURS);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);

    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_INSTANT.toEpochMilli()));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_INSTANT.toEpochMilli()));
    when(parameters.getString(CCMJobConstants.BATCH_JOB_TYPE))
        .thenReturn(BatchJobType.INSTANCE_BILLING_AGGREGATION.name());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    when(billingDataService.cleanPreAggBillingData(any(), any(), any(), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(true);
    when(billingDataService.generatePreAggBillingData(any(), any(), any(), any(), any())).thenReturn(true);
    when(billingDataService.generatePreAggBillingDataWithId(any(), any(), any(), any(), any())).thenReturn(true);
    long endMillis = END_INSTANT.toEpochMilli();
    long startMillis = START_INSTANT.toEpochMilli();

    RepeatStatus repeatStatus = instanceBillingAggregationDataTasklet.execute(null, chunkContext);

    verify(billingDataService, times(1))
        .cleanPreAggBillingData(eq(ACCOUNT_ID), eq(Instant.ofEpochMilli(startMillis)),
            eq(Instant.ofEpochMilli(endMillis)), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION));
    verify(billingDataService, times(1))
        .generatePreAggBillingData(eq(ACCOUNT_ID), eq(Instant.ofEpochMilli(startMillis)),
            eq(Instant.ofEpochMilli(endMillis)), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION),
            eq(BatchJobType.INSTANCE_BILLING));

    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFailedExecute() throws Exception {
    when(billingDataService.cleanPreAggBillingData(any(), any(), any(), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(true);
    when(billingDataService.generatePreAggBillingData(
             any(), any(), any(), eq(BatchJobType.INSTANCE_BILLING), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(false);

    assertThatThrownBy(() -> instanceBillingAggregationDataTasklet.execute(null, chunkContext))
        .hasMessageContaining(format("BatchJobType:%s failed", BatchJobType.INSTANCE_BILLING_AGGREGATION.name()));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFailedExecuteCleanData() throws Exception {
    when(billingDataService.cleanPreAggBillingData(any(), any(), any(), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(false);
    when(billingDataService.generatePreAggBillingData(
             any(), any(), any(), eq(BatchJobType.INSTANCE_BILLING), eq(BatchJobType.INSTANCE_BILLING_AGGREGATION)))
        .thenReturn(true);

    assertThatThrownBy(() -> instanceBillingAggregationDataTasklet.execute(null, chunkContext))
        .hasMessageContaining(format("BatchJobType:%s failed", BatchJobType.INSTANCE_BILLING_AGGREGATION.name()));
  }
}
