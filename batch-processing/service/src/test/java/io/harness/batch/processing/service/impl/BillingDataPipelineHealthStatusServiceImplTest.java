/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.dao.intfc.BatchJobScheduledDataDao;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BillingDataPipelineHealthStatusServiceImplTest extends CategoryTest {
  @InjectMocks BillingDataPipelineHealthStatusServiceImpl billingDataPipelineHealthStatusService;
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock BatchJobScheduledDataDao batchJobScheduledDataDao;
  @Mock BatchMainConfig mainConfig;

  private static final String displayName = "displayName";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    BillingDataPipelineRecord billingDataPipelineRecord =
        BillingDataPipelineRecord.builder().cloudProvider("AWS").build();
    when(mainConfig.getBillingDataPipelineConfig())
        .thenReturn(BillingDataPipelineConfig.builder().awsUseNewPipeline(false).gcpUseNewPipeline(false).build());
    when(billingDataPipelineRecordDao.listAllBillingDataPipelineRecords())
        .thenReturn(Collections.singletonList(billingDataPipelineRecord));
    when(batchJobScheduledDataDao.fetchLastBatchJobScheduledData(anyString(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void computeTransferConfigStatusMap() {
    String value = billingDataPipelineHealthStatusService.getTransferStateStringValue(
        Collections.singletonMap(displayName, TransferState.SUCCEEDED), displayName);
    assertThat(value).isEqualTo(TransferState.SUCCEEDED.toString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldUpdateBillingPipelineRecordsStatus() {
    billingDataPipelineHealthStatusService.updateBillingPipelineRecordsStatus(new HashMap<>());
    ArgumentCaptor<BillingDataPipelineRecord> billingDataPipelineRecordArgumentCaptor =
        ArgumentCaptor.forClass(BillingDataPipelineRecord.class);
    verify(billingDataPipelineRecordDao).upsert(billingDataPipelineRecordArgumentCaptor.capture());
    BillingDataPipelineRecord billingDataPipelineRecord = billingDataPipelineRecordArgumentCaptor.getValue();
    assertThat(billingDataPipelineRecord.getDataTransferJobStatus()).isEqualTo(TransferState.UNRECOGNIZED.toString());
  }
}
