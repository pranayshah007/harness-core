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
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.GcpBillingEntityStatsDTO;
import io.harness.ccm.billing.GcpBillingServiceImpl;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class GcpBillingEntityStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock GcpBillingServiceImpl gcpBillingServiceImpl;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks @Inject GcpBillingEntityStatsDataFetcher entityStatsDataFetcher;

  private static final String COST = "cost";
  private static final String DISCOUNT = "discount";
  private static final String PRODUCT = "product";
  private static final String PROJECT = "project";
  private static final String SKU = "sku";
  private static final String BILLING_ACCOUNT_ID = "billingAccountId";

  private List<CloudBillingAggregate> cloudBillingAggregates = new ArrayList<>();
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<CloudBillingGroupBy> groupBy = new ArrayList<>();

  @Before
  public void setup() {
    cloudBillingAggregates.add(getBillingAggregate(COST));
    cloudBillingAggregates.add(getBillingAggregate(DISCOUNT));
    filters.addAll(Arrays.asList(getStartTimeGcpBillingFilter(0L), getEndTimeGcpBillingFilter(0L),
        getProductGcpBillingFilter(new String[] {PRODUCT}), getProjectGcpBillingFilter(new String[] {PROJECT}),
        getSkuGcpBillingFilter(new String[] {SKU}),
        getBillingAccountIdGcpBillingFilter(new String[] {BILLING_ACCOUNT_ID})));
    groupBy.addAll(Arrays.asList(
        getProductGroupBy(), getProjectIdGroupBy(), getProjectNumberGroupBy(), getSkuGroupBy(), getSkuIdGroupBy()));

    when(gcpBillingServiceImpl.getGcpBillingEntityStats(anyList(), anyList(), anyList()))
        .thenReturn(GcpBillingEntityStatsDTO.builder().build());
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetchTest() {
    QLData qlData =
        entityStatsDataFetcher.fetch(ACCOUNT1_ID, cloudBillingAggregates, filters, groupBy, Collections.emptyList());
    assertThat(qlData instanceof GcpBillingEntityStatsDTO).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getEntityTypeTest() {
    String entityType = entityStatsDataFetcher.getEntityType();
    assertThat(entityType).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void postFetchTest() {
    QLData postFetchData = entityStatsDataFetcher.postFetch(
        ACCOUNT1_ID, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, null);
    assertThat(postFetchData).isNull();
  }

  private CloudBillingAggregate getBillingAggregate(String columnName) {
    return CloudBillingAggregate.builder().operationType(QLCCMAggregateOperation.SUM).columnName(columnName).build();
  }

  private CloudBillingFilter getStartTimeGcpBillingFilter(Long filterTime) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setStartTime(
        CloudBillingTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getEndTimeGcpBillingFilter(Long filterTime) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setEndTime(
        CloudBillingTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getProductGcpBillingFilter(String[] product) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setProduct(CloudBillingIdFilter.builder().operator(QLIdOperator.EQUALS).values(product).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getProjectGcpBillingFilter(String[] project) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setProjectId(CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(project).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getSkuGcpBillingFilter(String[] sku) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setSku(CloudBillingIdFilter.builder().operator(QLIdOperator.NOT_IN).values(sku).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getBillingAccountIdGcpBillingFilter(String[] billingAccountId) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setBillingAccountId(
        CloudBillingIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(billingAccountId).build());
    return cloudBillingFilter;
  }

  private CloudBillingGroupBy getProductGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.product);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getProjectIdGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.projectId);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getProjectNumberGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.projectNumber);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getSkuGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.sku);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getSkuIdGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.skuId);
    return cloudBillingGroupBy;
  }
}
