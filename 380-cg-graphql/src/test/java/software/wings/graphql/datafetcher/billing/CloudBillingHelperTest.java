/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.dao.BillingDataPipelineRecordDao;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CloudBillingHelperTest extends CategoryTest {
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @InjectMocks CloudBillingHelper cloudBillingHelper;

  private static final String accountId = "accountId";
  private static final String datasetId = "datasetId";
  private static final List<String> excludeAccounts = new ArrayList<String>() {
    {
      add("Account1");
      add("Account2");
      add("Account3");
    }
  };

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getBillingDataPipelineCacheObject() {
    BillingDataPipelineRecord billingDataPipelineRecord = BillingDataPipelineRecord.builder()
                                                              .cloudProvider("AWS")
                                                              .dataSetId(datasetId)
                                                              .awsLinkedAccountsToExclude(excludeAccounts)
                                                              .build();
    when(billingDataPipelineRecordDao.fetchBillingPipelineRecords(accountId))
        .thenReturn(Arrays.asList(billingDataPipelineRecord));
    BillingDataPipelineCacheObject billingDataPipelineCacheObject =
        cloudBillingHelper.getBillingDataPipelineCacheObject(accountId);
    assertThat(billingDataPipelineCacheObject.getDataSetId()).isEqualTo(datasetId);
    assertThat(billingDataPipelineCacheObject.getAwsLinkedAccountsToExclude().size()).isEqualTo(3);
    assertThat(billingDataPipelineCacheObject.getAwsLinkedAccountsToExclude().get(0)).isEqualTo("Account1");
    assertThat(billingDataPipelineCacheObject.getAwsLinkedAccountsToExclude().get(1)).isEqualTo("Account2");
    assertThat(billingDataPipelineCacheObject.getAwsLinkedAccountsToExclude().get(2)).isEqualTo("Account3");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void processAndAddLinkedAccountsFilter() {
    BillingDataPipelineRecord billingDataPipelineRecord = BillingDataPipelineRecord.builder()
                                                              .cloudProvider("AWS")
                                                              .dataSetId(datasetId)
                                                              .awsLinkedAccountsToExclude(excludeAccounts)
                                                              .build();
    when(billingDataPipelineRecordDao.fetchBillingPipelineRecords(accountId))
        .thenReturn(Arrays.asList(billingDataPipelineRecord));
    List<CloudBillingFilter> filters = new ArrayList<>();
    cloudBillingHelper.processAndAddLinkedAccountsFilter(accountId, filters);
    assertThat(filters.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void processAndAddLinkedAccountsFilterWhitelist() {
    BillingDataPipelineRecord billingDataPipelineRecord = BillingDataPipelineRecord.builder()
                                                              .cloudProvider("AWS")
                                                              .dataSetId(datasetId)
                                                              .awsLinkedAccountsToInclude(excludeAccounts)
                                                              .build();
    when(billingDataPipelineRecordDao.fetchBillingPipelineRecords(accountId))
        .thenReturn(Arrays.asList(billingDataPipelineRecord));
    List<CloudBillingFilter> filters = new ArrayList<>();
    cloudBillingHelper.processAndAddLinkedAccountsFilter(accountId, filters);
    assertThat(filters.size()).isEqualTo(1);
  }
}
