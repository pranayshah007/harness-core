/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord.BillingDataPipelineRecordKeys;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BillingDataPipelineRecordDaoImplTest extends BatchProcessingTestBase {
  @Inject private BillingDataPipelineRecordDaoImpl billingDataPipelineRecordDao;
  @Inject private HPersistence hPersistence;

  private final String accountId = "accountId_" + this.getClass().getSimpleName();
  private final String accountName = "accountName_" + this.getClass().getSimpleName();
  private final String settingId = "settingId_" + this.getClass().getSimpleName();
  private final String dataSetId = "dataSetId_" + this.getClass().getSimpleName();
  private final String dataTransferJobName = "dataTransferJobName_" + this.getClass().getSimpleName();
  private final String fallBackTableName = "fallBackTableName_" + this.getClass().getSimpleName();
  private final String preAggTableName = "preAggTableName_" + this.getClass().getSimpleName();
  private BillingDataPipelineRecord dataPipelineRecord;

  @Before
  public void setUp() {
    dataPipelineRecord = BillingDataPipelineRecord.builder()
                             .accountId(accountId)
                             .accountName(accountName)
                             .settingId(settingId)
                             .dataSetId(dataSetId)
                             .dataTransferJobName(dataTransferJobName)
                             .awsFallbackTableScheduledQueryName(fallBackTableName)
                             .preAggregatedScheduledQueryName(preAggTableName)
                             .build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreateAndGet() {
    billingDataPipelineRecordDao.create(dataPipelineRecord);

    BillingDataPipelineRecord billingDataPipelineRecord =
        hPersistence.createQuery(BillingDataPipelineRecord.class)
            .filter(BillingDataPipelineRecordKeys.accountId, accountId)
            .filter(BillingDataPipelineRecordKeys.dataSetId, dataSetId)
            .get();

    assertThat(billingDataPipelineRecord).isEqualTo(dataPipelineRecord);

    BillingDataPipelineRecord bySettingId = billingDataPipelineRecordDao.getBySettingId(accountId, settingId);
    assertThat(bySettingId).isEqualTo(dataPipelineRecord);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldUpdateBillingDataPipelineRecordDao() {
    billingDataPipelineRecordDao.create(dataPipelineRecord);
    dataPipelineRecord.setAwsFallbackTableScheduledQueryStatus(TransferState.SUCCEEDED.toString());
    dataPipelineRecord.setPreAggregatedScheduledQueryStatus(TransferState.SUCCEEDED.toString());
    dataPipelineRecord.setDataTransferJobStatus(TransferState.SUCCEEDED.toString());
    dataPipelineRecord.setLastSuccessfulS3Sync(Instant.MIN);

    BillingDataPipelineRecord upsertedBillingDataPipelineRecord =
        billingDataPipelineRecordDao.upsert(dataPipelineRecord);

    assertThat(upsertedBillingDataPipelineRecord).isNotNull();
    assertThat(upsertedBillingDataPipelineRecord.getDataTransferJobStatus())
        .isEqualTo(TransferState.SUCCEEDED.toString());
    assertThat(upsertedBillingDataPipelineRecord.getPreAggregatedScheduledQueryStatus())
        .isEqualTo(TransferState.SUCCEEDED.toString());
    assertThat(upsertedBillingDataPipelineRecord.getAwsFallbackTableScheduledQueryStatus())
        .isEqualTo(TransferState.SUCCEEDED.toString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testBillingDataPipelineRecordDaoForCleanup() {
    BillingDataPipelineRecord dataPipelineRecord = BillingDataPipelineRecord.builder()
                                                       .accountId(accountId)
                                                       .accountName(accountName)
                                                       .settingId(settingId)
                                                       .dataSetId(dataSetId)
                                                       .dataTransferJobName(dataTransferJobName)
                                                       .awsFallbackTableScheduledQueryName(fallBackTableName)
                                                       .preAggregatedScheduledQueryName(preAggTableName)
                                                       .build();
    billingDataPipelineRecordDao.create(dataPipelineRecord);

    List<BillingDataPipelineRecord> allRecordsByAccountId =
        billingDataPipelineRecordDao.getAllRecordsByAccountId(accountId);
    assertThat(allRecordsByAccountId.size()).isEqualTo(1);
    billingDataPipelineRecordDao.removeBillingDataPipelineRecord(accountId, settingId);
  }
}
