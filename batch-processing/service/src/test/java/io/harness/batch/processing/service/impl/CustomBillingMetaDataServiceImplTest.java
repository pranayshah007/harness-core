/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CustomBillingMetaDataServiceImplTest extends CategoryTest {
  @Mock private BigQueryHelperService bigQueryHelperService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock CEMetadataRecordDao ceMetadataRecordDao;
  @Mock CloudBillingHelper cloudBillingHelper;
  @Mock private BatchMainConfig mainConfig;
  @Mock private BillingDataServiceImpl billingDataService;
  @InjectMocks private CustomBillingMetaDataServiceImpl customBillingMetaDataService;

  private static final String ACCOUNT_ID = "zEaak-FLS425IEO7OLzMUg";
  private static final String RESOURCE_ID = "resourceId";
  private static final String SETTING_ID = "settingID";
  private static final String AWS_DATA_SETID = "dataSetId";

  private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.HOURS);
  private final Instant START_TIME = NOW.minus(1, ChronoUnit.HOURS);
  private final Instant END_TIME = NOW;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsDataSetId() {
    when(ceMetadataRecordDao.getByAccountId(ACCOUNT_ID))
        .thenReturn(CEMetadataRecord.builder().accountId(ACCOUNT_ID).awsDataPresent(true).build());
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn(AWS_DATA_SETID);
    String awsDataSetId = customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID);
    assertThat(awsDataSetId).isEqualTo(AWS_DATA_SETID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetPipelineJobStatusWhenJobNotFinished() {
    when(mainConfig.isAwsCurBilling()).thenReturn(true);
    when(ceMetadataRecordDao.getByAccountId(ACCOUNT_ID))
        .thenReturn(CEMetadataRecord.builder().accountId(ACCOUNT_ID).awsDataPresent(true).build());
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn(AWS_DATA_SETID);
    when(bigQueryHelperService.getAwsBillingData(START_TIME, END_TIME, AWS_DATA_SETID, ACCOUNT_ID)).thenReturn(null);
    when(billingDataService.isAWSClusterDataPresent(ACCOUNT_ID, START_TIME.minus(3, ChronoUnit.DAYS))).thenReturn(true);
    Boolean jobFinished = customBillingMetaDataService.checkPipelineJobFinished(ACCOUNT_ID, START_TIME, END_TIME);
    assertThat(jobFinished).isFalse();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetPipelineJobStatusWhenJobFinished() {
    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    VMInstanceBillingData vmInstanceBillingData =
        VMInstanceBillingData.builder().computeCost(10).networkCost(0).resourceId(RESOURCE_ID).build();
    vmInstanceBillingDataMap.put(RESOURCE_ID, vmInstanceBillingData);
    when(mainConfig.isAwsCurBilling()).thenReturn(true);
    when(ceMetadataRecordDao.getByAccountId(ACCOUNT_ID))
        .thenReturn(CEMetadataRecord.builder().accountId(ACCOUNT_ID).awsDataPresent(true).build());
    when(customBillingMetaDataService.getAwsDataSetId(ACCOUNT_ID)).thenReturn(AWS_DATA_SETID);
    when(bigQueryHelperService.getAwsBillingData(START_TIME, END_TIME, AWS_DATA_SETID, ACCOUNT_ID))
        .thenReturn(vmInstanceBillingDataMap);
    when(billingDataService.isAWSClusterDataPresent(ACCOUNT_ID, START_TIME.minus(3, ChronoUnit.DAYS))).thenReturn(true);
    Boolean jobFinished = customBillingMetaDataService.checkPipelineJobFinished(ACCOUNT_ID, START_TIME, END_TIME);
    assertThat(jobFinished).isTrue();
  }

  private List<SettingAttribute> ceConnectorSettingAttribute() {
    CEAwsConfig ceAwsConfig = CEAwsConfig.builder().build();
    return Collections.singletonList(settingAttribute(ceAwsConfig));
  }

  private SettingAttribute settingAttribute(SettingValue settingValue) {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid(SETTING_ID);
    settingAttribute.setValue(settingValue);
    settingAttribute.setCategory(SettingAttribute.SettingCategory.CE_CONNECTOR);
    return settingAttribute;
  }
}
