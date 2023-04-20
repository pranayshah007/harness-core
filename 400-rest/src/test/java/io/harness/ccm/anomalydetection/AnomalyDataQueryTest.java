/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.anomalydetection;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.service.AnomalyDataQueryBuilder;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class AnomalyDataQueryTest extends CategoryTest {
  static String gcpSkuQuery =
      "SELECT t0.*,actualcost - expectedcost AS difference FROM anomalies t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.anomalytime >= '1970-01-01T00:00:00Z') AND (t0.anomalytime <= '1970-01-11T00:00:00Z') AND (t0.gcpskudescription IN ('SKU_DES1','SKU_DES2','SKU_DES3') )) ORDER BY t0.anomalytime ASC,difference DESC";
  String accountId;
  Instant startTime;
  Instant endTime;

  @Before
  public void setUp() {
    startTime = Instant.ofEpochMilli(0);
    endTime = startTime.plus(10, ChronoUnit.DAYS);
    accountId = "ACCOUNT_ID";
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFormCloudQuery() {
    List<CloudBillingFilter> filterList = new ArrayList<>();
    List<CloudBillingGroupBy> groupByList = new ArrayList<>();

    CloudBillingFilter startTimeFilter = new CloudBillingFilter();
    startTimeFilter.setPreAggregatedTableStartTime(CloudBillingTimeFilter.builder()
                                                       .operator(QLTimeOperator.AFTER)
                                                       .variable(CloudBillingFilter.BILLING_GCP_STARTTIME)
                                                       .value(startTime.toEpochMilli())
                                                       .build());
    filterList.add(startTimeFilter);

    CloudBillingFilter endTimeFilter = new CloudBillingFilter();
    endTimeFilter.setPreAggregatedTableStartTime(CloudBillingTimeFilter.builder()
                                                     .operator(QLTimeOperator.BEFORE)
                                                     .variable(CloudBillingFilter.BILLING_GCP_STARTTIME)
                                                     .value(endTime.toEpochMilli())
                                                     .build());
    filterList.add(endTimeFilter);
    CloudBillingFilter skuFilter = new CloudBillingFilter();
    skuFilter.setSku(CloudBillingIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .variable(CloudBillingFilter.BILLING_GCP_SKU)
                         .values(new String[] {"SKU_DES1", "SKU_DES2", "SKU_DES3"})
                         .build());
    filterList.add(skuFilter);

    assertThat(AnomalyDataQueryBuilder.formCloudQuery(accountId, filterList, groupByList)).isEqualTo(gcpSkuQuery);
  }
}
