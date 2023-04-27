/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpOrganization;
import io.harness.ccm.config.GcpOrganizationDao;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GcpBillingDataPipelineTaskletTest extends BaseTaskletTest {
  private static final String accountName = "accountName";
  private static final String gcpProjectId = "projectId";
  private static final String dataSetId = "datasetId";
  private static final String bqProjectId = "BQ_PROJECT_ID";
  private static final String bqDatasetId = "BQ_DATASET_ID";
  private static final String bqRegion = "BQ_REGION";

  @Mock private BatchMainConfig mainConfig;
  @Mock CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock BillingDataPipelineService billingDataPipelineService;
  @Mock GcpOrganizationDao gcpOrganizationDao;
  @InjectMocks GcpBillingDataPipelineTasklet gcpBillingDataPipelineTasklet;

  @Before
  public void setup() throws IOException {
    BillingDataPipelineConfig billingDataPipelineConfig =
        BillingDataPipelineConfig.builder().gcpProjectId(gcpProjectId).build();
    when(mainConfig.getBillingDataPipelineConfig()).thenReturn(billingDataPipelineConfig);

    Account account = Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(accountName).build();
    when(cloudToHarnessMappingService.getAccountInfoFromId(ACCOUNT_ID)).thenReturn(account);

    List<GcpBillingAccount> gcpBillingAccounts = Arrays.asList(GcpBillingAccount.builder()
                                                                   .accountId(ACCOUNT_ID)
                                                                   .bqProjectId(bqProjectId)
                                                                   .bqDatasetId(bqDatasetId)
                                                                   .bqDataSetRegion(bqRegion)
                                                                   .build());
    when(cloudToHarnessMappingService.listGcpBillingAccountUpdatedInDuration(eq(ACCOUNT_ID)))
        .thenReturn(gcpBillingAccounts);
    when(billingDataPipelineService.createDataSet(eq(account))).thenReturn(dataSetId);

    GcpOrganization gcpOrganization = GcpOrganization.builder().build();
    when(gcpOrganizationDao.get(any())).thenReturn(gcpOrganization);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldExecute() throws Exception {
    gcpBillingDataPipelineTasklet.execute(null, chunkContext);
    verify(billingDataPipelineRecordDao)
        .create(eq(BillingDataPipelineRecord.builder()
                       .accountId(ACCOUNT_ID)
                       .accountName(accountName)
                       .cloudProvider("GCP")
                       .dataSetId(dataSetId)
                       .dataTransferJobName("BigQueryCopyTransferJob_BQ_PROJECT_ID_BQ_DATASET_ID")
                       .preAggregatedScheduledQueryName("gcpPreAggQuery_BQ_PROJECT_ID_BQ_DATASET_ID")
                       .gcpBqProjectId(bqProjectId)
                       .gcpBqDatasetId(bqDatasetId)
                       .build()));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldExecuteForUSRegion() throws Exception {
    List<GcpBillingAccount> gcpBillingAccounts = Arrays.asList(GcpBillingAccount.builder()
                                                                   .accountId(ACCOUNT_ID)
                                                                   .bqProjectId(bqProjectId)
                                                                   .bqDatasetId(bqDatasetId)
                                                                   .bqDataSetRegion("US")
                                                                   .build());
    when(cloudToHarnessMappingService.listGcpBillingAccountUpdatedInDuration(eq(ACCOUNT_ID)))
        .thenReturn(gcpBillingAccounts);
    gcpBillingDataPipelineTasklet.execute(null, chunkContext);
    verify(billingDataPipelineRecordDao)
        .create(eq(BillingDataPipelineRecord.builder()
                       .accountId(ACCOUNT_ID)
                       .accountName(accountName)
                       .cloudProvider("GCP")
                       .dataSetId(dataSetId)
                       .dataTransferJobName("gcpCopyScheduledQuery_BQ_PROJECT_ID_BQ_DATASET_ID")
                       .preAggregatedScheduledQueryName("gcpPreAggQuery_BQ_PROJECT_ID_BQ_DATASET_ID")
                       .gcpBqProjectId(bqProjectId)
                       .gcpBqDatasetId(bqDatasetId)
                       .build()));
  }
}
