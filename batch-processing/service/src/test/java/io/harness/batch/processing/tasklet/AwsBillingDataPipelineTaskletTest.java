/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.preAggQueryKey;
import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.scheduledQueryKey;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord.BillingDataPipelineRecordKeys;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AwsBillingDataPipelineTaskletTest extends BaseTaskletTest {
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock BillingDataPipelineServiceImpl billingDataPipelineService;
  @Mock CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock BatchMainConfig mainConfig;

  @InjectMocks AwsBillingDataPipelineTasklet awsBillingDataPipelineTasklet;

  private static final String accountName = "accountName";
  private static final String settingId = "settingId";
  private static final String masterAccountId = "masterAccountId";
  private static final String curReportName = "curReportName";
  private static final String dataSetId = "datasetId";
  private static final String transferJobName = "transferJobName";
  private static final Instant instant = Instant.now();
  private static final String scheduledQueryName = "scheduledQueryName";
  private static final String preAggQueryName = "preAggQueryName";

  @Before
  public void setup() throws IOException {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withUuid(settingId)
            .withValue(CEAwsConfig.builder().curReportName(curReportName).awsMasterAccountId(masterAccountId).build())
            .build();
    when(mainConfig.getBillingDataPipelineConfig())
        .thenReturn(BillingDataPipelineConfig.builder().awsUseNewPipeline(false).build());

    when(cloudToHarnessMappingService.getAccountInfoFromId(ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount().withAccountName(accountName).build());
    when(cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(
             ACCOUNT_ID, SettingAttribute.SettingCategory.CE_CONNECTOR, SettingVariableTypes.CE_AWS))
        .thenReturn(Collections.singletonList(settingAttribute));
    when(billingDataPipelineService.createDataSet(any())).thenReturn(dataSetId);
    when(billingDataPipelineService.createDataTransferJobFromGCS(
             dataSetId, settingId, ACCOUNT_ID, accountName, curReportName, false))
        .thenReturn(transferJobName);
    when(billingDataPipelineService.createDataTransferJobFromGCS(
             dataSetId, settingId, ACCOUNT_ID, accountName, curReportName, true))
        .thenReturn(transferJobName);
    HashMap<String, String> scheduledQueryJobsMap = new HashMap<>();
    scheduledQueryJobsMap.put(scheduledQueryKey, scheduledQueryName);
    scheduledQueryJobsMap.put(preAggQueryKey, preAggQueryName);
    when(billingDataPipelineService.createScheduledQueriesForAWS(dataSetId, ACCOUNT_ID, accountName))
        .thenReturn(scheduledQueryJobsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldExecute() throws Exception {
    awsBillingDataPipelineTasklet.execute(null, chunkContext);
    ArgumentCaptor<BillingDataPipelineRecord> billingDataPipelineRecordArgumentCaptor =
        ArgumentCaptor.forClass(BillingDataPipelineRecord.class);
    verify(billingDataPipelineRecordDao).create(billingDataPipelineRecordArgumentCaptor.capture());
    BillingDataPipelineRecord value = billingDataPipelineRecordArgumentCaptor.getValue();
    BillingDataPipelineRecord expectedRecord = BillingDataPipelineRecord.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .accountName(accountName)
                                                   .settingId(settingId)
                                                   .cloudProvider("AWS")
                                                   .dataSetId(dataSetId)
                                                   .awsMasterAccountId(masterAccountId)
                                                   .dataTransferJobName(transferJobName)
                                                   .awsFallbackTableScheduledQueryName(scheduledQueryName)
                                                   .preAggregatedScheduledQueryName(preAggQueryName)
                                                   .build();
    assertThat(value).isEqualToIgnoringGivenFields(
        expectedRecord, BillingDataPipelineRecordKeys.uuid, BillingDataPipelineRecordKeys.createdAt);
  }
}
