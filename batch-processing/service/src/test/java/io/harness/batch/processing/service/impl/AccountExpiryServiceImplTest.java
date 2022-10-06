/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.rule.Owner;

import software.wings.beans.Account;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountExpiryServiceImplTest extends CategoryTest {
  public static final String ACCOUNT_ID = "accountId";
  public static final String SETTING_ID = "settingId";
  public static final String CLOUD_PROVIDER = "AWS";
  public static final String DATASET_ID = "datasetId";
  public static final String TRANSFER_JOB = "transferJob";
  public static final String PRE_AGG_JOB = "preAggJob";
  public static final String SCHEDULED_QUERY_JOB = "scheduledQueryJob";

  @Mock private static BigQuery bigQuery;
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;

  @InjectMocks
  @Spy
  AccountExpiryServiceImpl accountExpiryService = new AccountExpiryServiceImpl(billingDataPipelineRecordDao);

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void dataPipelineCleanup() throws IOException {
    Account account = new Account();
    account.setUuid(ACCOUNT_ID);

    DataTransferServiceClient dataTransferServiceClient = mock(DataTransferServiceClient.class);
    doReturn(dataTransferServiceClient).when(accountExpiryService).getDataTransferClient();
    doNothing().when(accountExpiryService).deleteDataTransfer(dataTransferServiceClient, TRANSFER_JOB);
    doNothing().when(accountExpiryService).deleteDataTransfer(dataTransferServiceClient, PRE_AGG_JOB);
    doNothing().when(accountExpiryService).deleteDataTransfer(dataTransferServiceClient, SCHEDULED_QUERY_JOB);

    bigQuery = mock(BigQuery.class);
    doReturn(bigQuery).when(accountExpiryService).getBigQueryClient();
    when(bigQuery.delete(DATASET_ID, BigQuery.DatasetDeleteOption.deleteContents())).thenReturn(true);

    when(billingDataPipelineRecordDao.removeBillingDataPipelineRecord(ACCOUNT_ID, SETTING_ID)).thenReturn(true);

    BillingDataPipelineRecord billingDataPipelineRecord = BillingDataPipelineRecord.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .settingId(SETTING_ID)
                                                              .cloudProvider(CLOUD_PROVIDER)
                                                              .dataSetId(DATASET_ID)
                                                              .dataTransferJobName(TRANSFER_JOB)
                                                              .preAggregatedScheduledQueryName(PRE_AGG_JOB)
                                                              .awsFallbackTableScheduledQueryName(SCHEDULED_QUERY_JOB)
                                                              .build();
    when(billingDataPipelineRecordDao.getAllRecordsByAccountId(ACCOUNT_ID))
        .thenReturn(Arrays.asList(billingDataPipelineRecord));

    boolean dataPipelineCleanup = accountExpiryService.dataPipelineCleanup(account);
    assertThat(dataPipelineCleanup).isEqualTo(true);
  }
}
