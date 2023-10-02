/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.BatchJobScheduledDataDao;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLNoOpQueryParameters;
import software.wings.graphql.schema.type.aggregation.billing.QLBatchLastProcessedData;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BillingJobProcessedDataFetcherTest extends AbstractDataFetcherTestBase {
  @Inject @InjectMocks BillingJobProcessedDataFetcher billingJobProcessedDataFetcher;
  @Mock private BatchJobScheduledDataDao batchJobScheduledDataDao;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String BATCH_JOB_TYPE = "CLUSTER_DATA_TO_BIG_QUERY";
  private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.DAYS);
  private final Instant LAST_PROCESSED_DATA_START_TIME = NOW.minus(2, ChronoUnit.DAYS);
  private final Instant LAST_PROCESSED_DATA_END_TIME = NOW.minus(1, ChronoUnit.DAYS);

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testFetchLastProcessedTime() {
    when(batchJobScheduledDataDao.fetchLastBatchJobScheduledData(ACCOUNT_ID, BATCH_JOB_TYPE))
        .thenReturn(getBatchJobScheduledData());
    QLBatchLastProcessedData batchLastProcessedData =
        billingJobProcessedDataFetcher.fetch(new QLNoOpQueryParameters(), ACCOUNT_ID);
    assertThat(batchLastProcessedData.getLastProcessedTime()).isEqualTo(LAST_PROCESSED_DATA_END_TIME.toEpochMilli());
  }

  private BatchJobScheduledData getBatchJobScheduledData() {
    return new BatchJobScheduledData(
        ACCOUNT_ID, BATCH_JOB_TYPE, 1200, LAST_PROCESSED_DATA_START_TIME, LAST_PROCESSED_DATA_END_TIME, 111111L);
  }
}
